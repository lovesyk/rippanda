package lovesyk.rippanda.service.archival.element;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.Settings;

/**
 * The archival service for gallery thumbnail elements.
 */
@ApplicationScoped
public class ThumbnailArchivalService extends AbstractElementArchivalService implements IElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(ThumbnailArchivalService.class);
    private static final String FILENAME = "thumbnail.jpg";

    private MetadataArchivalService apiArchivingService;

    /**
     * Constructs a new archival service instance.
     * 
     * @param settings            the application settings
     * @param webClient           the network web client
     * @param apiArchivingService the metadata archival service
     */
    @Inject
    public ThumbnailArchivalService(Settings settings, IWebClient webClient,
            MetadataArchivalService apiArchivingService) {
        super(settings, webClient);
        this.apiArchivingService = apiArchivingService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Gallery gallery) throws RipPandaException, InterruptedException {
        if (isRequired(gallery)) {
            LOGGER.info("Saving thumbnail...");
            save(gallery);
        } else {
            LOGGER.debug("Thumbnail does not need to be archived.");
        }
    }

    /**
     * Checks if the thumbnail should be saved or not.
     * 
     * @return <code>true</code> if thumbnail archival is active but no thumbnail
     *         was found on disk, <code>false</false> otherwise.
     * @throws RipPandaException on failure
     */
    private boolean isRequired(Gallery gallery) throws RipPandaException {
        boolean isRequired = getSettings().isThumbnailActive();

        if (isRequired) {
            ensureFilesLoaded(gallery);
            isRequired = gallery.getFiles().stream().noneMatch(x -> FILENAME.equals(String.valueOf(x.getFileName())));
        }

        return isRequired;
    }

    /**
     * Saves the thumbnail of the gallery to disk.
     * 
     * @param gallery the gallery
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void save(Gallery gallery) throws RipPandaException, InterruptedException {
        getApiArchivingService().ensureLoadedUpToDate(gallery);

        JsonElement thumbElement = gallery.getMetadata().get("thumb");
        if (thumbElement == null || !thumbElement.isJsonPrimitive()) {
            throw new RipPandaException("Unexpected JSON.");
        }
        String thumbString = thumbElement.getAsString();

        String url = thumbString.toString().replaceAll("_l\\.jpg$", "_300.jpg");
        if (url.equals(thumbString)) {
            throw new RipPandaException("Failed creating HQ thumbnail URL. The format might have changed.");
        }

        getWebClient().downloadFile(url, (downloadableThumbnail) -> {
            String mimeType = downloadableThumbnail.getMimeType();
            if (!"image/jpeg".equals(mimeType)) {
                throw new RipPandaException("Unexpected mime type \"" + mimeType + "\".");
            }

            initDir(gallery.getDir());
            save(downloadableThumbnail.getStream(), gallery.getDir(), FILENAME);

            return true;
        });
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
