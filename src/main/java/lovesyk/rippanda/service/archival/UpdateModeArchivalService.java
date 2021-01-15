package lovesyk.rippanda.service.archival;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.api.IArchivalService;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.settings.OperationMode;
import lovesyk.rippanda.settings.Settings;

/**
 * The service responsible for the updating of already archived galleries.
 */
public class UpdateModeArchivalService extends AbstractArchivalService implements IArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(UpdateModeArchivalService.class);
    private static final String PAGE_FILENAME = "page.html";
    private static final Pattern GALLERY_URL_PATTERN = Pattern.compile("<a href=\".*?/g/(\\d+)/([0-9a-f]{10})/\\?report=select\">Report Gallery</a>");

    /**
     * Constructs a new search result archival service instance.
     * 
     * @param settings                the application settings
     * @param elementArchivalServices the archival services which should be invoked
     *                                for archiving elements
     */
    @Inject
    public UpdateModeArchivalService(Settings settings, Instance<IElementArchivalService> elementArchivalServices) {
        super(settings, elementArchivalServices);
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
        for (Gallery gallery : parseGalleries(getSettings().getArchiveDirectory())) {
            if (failureCount > maxFailureCount) {
                throw new RipPandaException(String.format("Encountered more than %s failures successively. Aborting...", maxFailureCount));
            }

            try {
                process(gallery);
                failureCount = 0;
            } catch (RipPandaException e) {
                LOGGER.warn("Failed processing gallery. Waiting for 10 seconds before continuing...", e);
                ++failureCount;
                Thread.sleep(1000 * 10);
            }
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

        addTempSuccessId(id);

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
        Instant threshold = Instant.now().minus(30, ChronoUnit.DAYS);
        FileTime lastModifiedTime;
        try {
            lastModifiedTime = Files.getLastModifiedTime(dir);
        } catch (IOException e) {
            throw new RipPandaException("Could not read last modified time.", e);
        }
        return lastModifiedTime.toInstant().isBefore(threshold);
    }

    /**
     * Parses all galleries in the given directory.
     * 
     * @param path the path to search for galleries in
     * @return all galleries, never <code>null</code>
     * @throws RipPandaException on failure
     */
    private List<Gallery> parseGalleries(Path path) throws RipPandaException {
        List<Gallery> galleries = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(path).skip(1)) {
            for (Path entry : (Iterable<Path>) stream::iterator) {
                if (entry.getFileName().toString().equals(PAGE_FILENAME)) {
                    Gallery gallery = parseGallery(entry);
                    if (gallery != null) {
                        LOGGER.debug("Found gallery with ID \"{}\" and token \"{}\".", gallery.getId(), gallery.getToken());
                        galleries.add(gallery);
                    }
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Could not traverse the given archive directory.", e);
        }

        return galleries;
    }

    /**
     * Parses a single gallery, assuming it resides in the same folder as the page
     * file given.
     * <p>
     * To ensure quick parsing this is done purely by text regex without actually
     * parsing the HTML.
     * 
     * @param pageFile the page file to use for base information
     * @return the parsed gallery
     * @throws RipPandaException on failure
     */
    private Gallery parseGallery(Path pageFile) throws RipPandaException {
        Gallery gallery = null;

        Path parent = pageFile.getParent();
        if (isRequired(parent)) {
            try (Stream<String> stream = Files.lines(pageFile)) {
                for (String line : (Iterable<String>) stream::iterator) {
                    Matcher matcher = GALLERY_URL_PATTERN.matcher(line);
                    if (matcher.find()) {
                        int id;
                        try {
                            id = Integer.valueOf(matcher.group(1));
                        } catch (NumberFormatException e) {
                            LOGGER.warn("Failed parsing gallery ID, this line will be skipped.", e);
                            continue;
                        }
                        String token = matcher.group(2);
                        gallery = new Gallery(id, token, pageFile.getParent());
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed reading file, it will be skipped.", e);
            }
        } else {
            LOGGER.debug("Found possible gallery but it has been changed recently and will be skipped: \"{}\"", parent);
        }

        return gallery;
    }
}
