package lovesyk.rippanda.service.archival.element;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.Settings;

/**
 * The archival service for gallery ZIP elements.
 */
public class ZipArchivalService extends AbstractElementArchivalService implements IElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(ZipArchivalService.class);
    private static final Duration ARCHIVE_PREPARATION_BASE_DELAY = Duration.ofSeconds(1);
    private static final int ARCHIVE_PREPARATION_RETRIES = 10;

    private MetadataArchivalService apiArchivingService;

    /**
     * Constructs a new archival service instance.
     * 
     * @param settings            the application settings
     * @param webClient           the network web client
     * @param apiArchivingService the metadata archival service
     */
    @Inject
    public ZipArchivalService(Settings settings, IWebClient webClient, MetadataArchivalService apiArchivingService) {
        super(settings, webClient);
        this.apiArchivingService = apiArchivingService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Gallery gallery) throws RipPandaException, InterruptedException {
        if (isRequired(gallery)) {
            LOGGER.info("ZIP needs to be archived.");
            save(gallery);
        } else {
            LOGGER.info("ZIP does not need to be archived.");
        }
    }

    /**
     * Checks if the ZIP should be saved or not.
     * 
     * @return <code>true</code> if the ZIP should be saved, <code>false</false>
     *         otherwise.
     * @throws RipPandaException on failure
     */
    private boolean isRequired(Gallery gallery) throws RipPandaException {
        if (Files.isDirectory(gallery.getDir())) {
            try {
                return Files.list(gallery.getDir()).noneMatch(x -> x.toString().endsWith(".zip"));
            } catch (IOException e) {
                throw new RipPandaException("Could not check if ZIP file already exists.", e);
            }
        }

        return true;
    }

    /**
     * Saves the ZIP of the gallery to disk.
     * 
     * @param gallery the gallery
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void save(Gallery gallery) throws RipPandaException, InterruptedException {
        getApiArchivingService().ensureLoaded(gallery);
        String archiverKey = parseArchiverKey(gallery.getMetadata());

        String archiveUrl = loadArchiveUrl(gallery.getId(), gallery.getToken(), archiverKey);

        LOGGER.info("Saving archive...");
        getWebClient().downloadFile(archiveUrl, (downloadableArchive) -> {
            initDir(gallery.getDir());
            String sanitizedFileName = sanitizeFileName(gallery.getDir(), downloadableArchive.getName());
            save(downloadableArchive.getStream(), gallery.getDir(), sanitizedFileName);
            
            return true;
        });
    }

    /**
     * Parses the archival key from a single-gallery API metadata response.
     * 
     * @param metadata the single-gallery metadata
     * @return the archival key
     * @throws RipPandaException on failure
     */
    private String parseArchiverKey(JsonObject metadata) throws RipPandaException {
        JsonElement archiverKeyElement = metadata.get("archiver_key");
        if (archiverKeyElement == null || !archiverKeyElement.isJsonPrimitive()) {
            throw new RipPandaException("Unexpected JSON.");
        }
        String archiverKey = archiverKeyElement.getAsString();

        return archiverKey;
    }

    /**
     * Loads the gallery ZIP URL by querying the web service and waiting for the
     * file to become available.
     * 
     * @param id          the gallery ID
     * @param token       the gallery token
     * @param archiverKey the gallery archiver key as returned by the API
     * @return the gallery ZIP URL
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private String loadArchiveUrl(int id, String token, String archiverKey) throws RipPandaException, InterruptedException {
        LOGGER.info("Generating ZIP URL...");
        Element continueLink = null;
        for (int i = 0; i < ARCHIVE_PREPARATION_RETRIES && continueLink == null; ++i) {
            if (i > 0 && continueLink == null) {
                Duration delay = ARCHIVE_PREPARATION_BASE_DELAY.multipliedBy(i * i);
                LOGGER.info(String.format("Archive not ready yet. Waiting for {}...", delay));
                Thread.sleep(delay.toMillis());
            }

            Document archivePreparationPage = getWebClient().loadArchivePreparationPage(id, token, archiverKey);
            Element continueElement = archivePreparationPage.selectFirst("#continue");
            if (continueElement == null) {
                throw new RipPandaException("Unexpected HTML.");
            }

            continueLink = continueElement.selectFirst("a");
        }

        if (continueLink == null) {
            throw new RipPandaException("Could not retrieve prepared file on download server.");
        }

        String archiveUrl = continueLink.attr("href") + "?start=1";

        return archiveUrl;
    }

    /**
     * Gets the gallery metadata archiving service.
     * 
     * @return the metadata archiving service
     */
    private MetadataArchivalService getApiArchivingService() {
        return apiArchivingService;
    }
}
