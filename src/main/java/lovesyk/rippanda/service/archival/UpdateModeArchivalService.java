package lovesyk.rippanda.service.archival;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.api.IArchivalService;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.OperationMode;
import lovesyk.rippanda.settings.Settings;

/**
 * The service responsible for the updating of already archived galleries.
 */
public class UpdateModeArchivalService extends AbstractArchivalService implements IArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(UpdateModeArchivalService.class);
    private static final String PAGE_FILENAME = "page.html";

    private IWebClient webClient;

    /**
     * Constructs a new search result archival service instance.
     * 
     * @param settings                the application settings
     * @param webClient               the web client
     * @param elementArchivalServices the archival services which should be invoked
     *                                for archiving elements
     */
    @Inject
    public UpdateModeArchivalService(Settings settings, IWebClient webClient, Instance<IElementArchivalService> elementArchivalServices) {
        super(settings, elementArchivalServices);
        this.webClient = webClient;
    }

    /**
     * Initializes the instance.
     */
    @PostConstruct
    public void init() {
        super.init();
    }

    /**
     * {@inheritDoc}
     */
    public void process() throws RipPandaException, InterruptedException {
        if (isRequired()) {
            LOGGER.info("Activating update mode.");
            run();
        }
    }

    /**
     * Checks if the update mode should be executed based in application settings.
     * 
     * @return <code>true</code> if update mode is enabled, <code>false</false>
     *         otherwise.
     */
    private boolean isRequired() {
        return getSettings().getOperationMode() == OperationMode.UPDATE;
    }

    /**
     * Runs the update archival service.
     * <p>
     * Processing will be aborted on over 3 failures in a row.
     * 
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    public void run() throws RipPandaException, InterruptedException {
        initDirs();
        initSuccessIds();

        int failureCount = 0;
        int maxFailureCount = 3;

        try (Stream<Path> stream = Files.walk(getSettings().getWritableArchiveDirectory()).filter(Files::isDirectory)) {
            for (Path directory : (Iterable<Path>) stream::iterator) {
                Gallery gallery = parseGallery(directory);
                if (gallery != null) {
                    try {
                        process(gallery);
                        failureCount = 0;
                    } catch (RipPandaException e) {
                        LOGGER.warn("Failed processing gallery. Waiting for 10 seconds before continuing...", e);
                        ++failureCount;
                        Thread.sleep(1000 * 10);
                    }

                    if (failureCount > maxFailureCount) {
                        throw new RipPandaException(String.format("Encountered more than %s failures successively. Aborting...", maxFailureCount));
                    }
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Could not traverse the given archive directory.", e);
        }

        deleteSuccessTempFile();
    }

    /**
     * Processes a single gallery of a search result.
     * <p>
     * Element archiving failures will be retried up to 3 times and gallery
     * processing will be finished in any case.
     * 
     * @param gallery the gallery to process
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void process(Gallery gallery) throws RipPandaException, InterruptedException {
        int id = gallery.getId();
        LOGGER.info("Processing gallery with ID \"{}\" and token \"{}\"", gallery.getId(), gallery.getToken());

        if (!isInSuccessIds(id)) {
            addTempSuccessId(id);
        }

        RipPandaException lastException = null;
        for (IElementArchivalService archivingService : getArchivingServiceList()) {
            for (int remainingTries = 3; remainingTries > 0; --remainingTries) {
                try {
                    archivingService.process(gallery);
                    break;
                } catch (RipPandaException e) {
                    LOGGER.warn("Archiving element failed, {} tries remain.", remainingTries, e);
                    lastException = e;
                    if (remainingTries > 0) {
                        LOGGER.warn("Waiting 10 seconds before retrying...");
                        Thread.sleep(1000 * 10);
                    }
                }
            }
        }
        if (lastException != null) {
            throw new RipPandaException("Gallery processing finished with at least one failure.", lastException);
        }

        if (!isInSuccessIds(id)) {
            addSuccessId(id);
        }
        updateSuccessIds();
    }

    /**
     * Checks if the gallery should be processed or not.
     * 
     * @param dir the directory of the gallery
     * @return <code>true</code> if the gallery hasn't been modified for a specific
     *         time, <code>false</false> otherwise.
     * @throws RipPandaException on failure
     */
    private boolean isRequired(Path dir) throws RipPandaException {
        Instant threshold = Instant.now().minus(getSettings().getUpdateInterval());
        FileTime lastModifiedTime;
        try {
            lastModifiedTime = Files.getLastModifiedTime(dir);
        } catch (IOException e) {
            throw new RipPandaException("Could not read last modified time.", e);
        }
        return lastModifiedTime.toInstant().isBefore(threshold);
    }

    /**
     * Parses a single gallery, assuming it resides in the same folder as the page
     * file given.
     * <p>
     * To ensure quick parsing this is done purely by text regex without actually
     * parsing the HTML.
     * 
     * @param directory the parent directory of the potential gallery
     * @return the parsed gallery
     * @throws RipPandaException on failure
     */
    private Gallery parseGallery(Path directory) throws RipPandaException {
        Gallery gallery = null;

        Path pageFile = directory.resolve(PAGE_FILENAME);
        if (Files.exists(pageFile)) {
            if (isRequired(directory)) {
                Document page = null;
                try {
                    page = getWebClient().loadDocument(pageFile);
                } catch (RipPandaException e) {
                    LOGGER.warn("Failed reading file, it will be skipped.", e);
                }

                if (page != null) {
                    Element reportElement = page.selectFirst("#gd5 > .g3 > a");
                    if (reportElement == null) {
                        throw new RipPandaException("Could not find report element.");
                    }
                    Pair<Integer, String> idTokenPair = parseGalleryUrlIdToken(reportElement);

                    gallery = new Gallery(idTokenPair.getLeft(), idTokenPair.getRight(), pageFile.getParent());
                }
            } else {
                LOGGER.debug("Found possible gallery but it has been changed recently and will be skipped: \"{}\"", directory);
            }
        } else {
            LOGGER.debug("Directory does not appear to contain a gallery: \"{}\"", directory);
        }

        return gallery;
    }

    /**
     * Gets the network web client.
     * 
     * @return the web client
     */
    protected IWebClient getWebClient() {
        return webClient;
    }
}
