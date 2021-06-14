package lovesyk.rippanda.service.archival.element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String UNAVAILABLE = "UNAVAILABLE";
    private static final Logger LOGGER = LogManager.getLogger(ZipArchivalService.class);
    private static final String FILENAME_EXTENSION = ".zip";
    private static final int ARCHIVE_PREPARATION_RETRIES = 30;
    private static final Pattern CONTINUE_SCRIPT_TIMEOUT_PATTERN = Pattern.compile("setTimeout\\(.*?, (\\d+)\\)");

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
            LOGGER.info("Saving ZIP...");
            save(gallery);
        } else {
            LOGGER.debug("ZIP does not need to be archived.");
        }
    }

    /**
     * Checks if the ZIP should be saved or not.
     * 
     * @return <code>true</code> if ZIP archival is active but not ZIP could be
     *         found, <code>false</false> otherwise.
     * @throws RipPandaException on failure
     */
    private boolean isRequired(Gallery gallery) throws RipPandaException {
        boolean isRequired = getSettings().isZipActive();

        if (isRequired) {
            ensureFilesLoaded(gallery);
            isRequired = !isUnavailable(gallery) && gallery.getFiles().stream().noneMatch(x -> x.toString().endsWith(FILENAME_EXTENSION));
        }

        return isRequired;
    }

    /**
     * Saves the ZIP of the gallery to disk.
     * 
     * @param gallery the gallery
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void save(Gallery gallery) throws RipPandaException, InterruptedException {
        getApiArchivingService().ensureLoadedOnline(gallery);
        String archiverKey = parseArchiverKey(gallery.getMetadata());

        String archiveUrl = loadArchiveUrl(gallery, archiverKey);
        if (archiveUrl == UNAVAILABLE) {
            return;
        }

        getWebClient().downloadFile(archiveUrl, (downloadableArchive) -> {
            String mimeType = downloadableArchive.getMimeType();
            if (!"application/zip".equals(mimeType)) {
                throw new RipPandaException("Unexpected mime type \"" + mimeType + "\".");
            }

            initDir(gallery.getDir());
            String sanitizedFileName = sanitizeFileName(gallery.getDir(), downloadableArchive.getName(), true);
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
     * @param document    the gallery document
     * @param archiverKey the gallery archiver key as returned by the API
     * @return the gallery ZIP URL
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private String loadArchiveUrl(Gallery gallery, String archiverKey) throws RipPandaException, InterruptedException {
        LOGGER.debug("Generating ZIP URL...");
        Document archivePreparationPage = getWebClient().loadArchivePreparationPage(gallery.getId(), gallery.getToken(), archiverKey);

        for (int i = 0;; ++i) {
            Element continueUrlElement = archivePreparationPage.selectFirst("#continue a");
            if (continueUrlElement == null) {
                Element archiveUrlElement = archivePreparationPage.selectFirst("#db a");
                if (archiveUrlElement == null) {
                    if (processUnavailability(gallery, archivePreparationPage)) {
                        return UNAVAILABLE;
                    }
                    throw new RipPandaException("Could not find archive URL element.");
                }
                return archiveUrlElement.attr("abs:href");
            }

            if (i >= ARCHIVE_PREPARATION_RETRIES) {
                break;
            }

            Element scriptElement = archivePreparationPage.selectFirst("script");
            if (scriptElement == null) {
                throw new RipPandaException("Unexpected HTML.");
            }
            Matcher timeoutMatcher = CONTINUE_SCRIPT_TIMEOUT_PATTERN.matcher(scriptElement.html());
            if (!timeoutMatcher.find()) {
                throw new RipPandaException("Unexpected HTML.");
            }
            String timeoutString = timeoutMatcher.group(1);
            long timeout;
            try {
                timeout = Long.parseLong(timeoutString);
            } catch (NumberFormatException e) {
                throw new RipPandaException("Could not parse timeout.", e);
            }

            LOGGER.debug("Waiting for {}ms as instructed by archiver...", timeout);
            Thread.sleep(timeout);

            String continueUrl = continueUrlElement.attr("abs:href");
            archivePreparationPage = getWebClient().loadDocument(continueUrl);
        }

        throw new RipPandaException("Could not retrieve prepared file on download server.");
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
