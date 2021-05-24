package lovesyk.rippanda.service.archival.element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.gson.JsonArray;
import com.google.gson.JsonSyntaxException;

import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.api.FilesUtils;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.Settings;

/**
 * The archival service for gallery image list elements.
 */
public class ImageListArchivalService extends AbstractElementArchivalService implements IElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(ImageListArchivalService.class);
    private static final String FILENAME = "imagelist.json";
    private static final Pattern IMAGELIST_PATTERN = Pattern.compile("var imagelist = (.+?);");

    /**
     * Constructs a new archival service instance.
     * 
     * @param settings  the application settings
     * @param webClient the network web client
     */
    @Inject
    public ImageListArchivalService(Settings settings, IWebClient webClient) {
        super(settings, webClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Gallery gallery) throws RipPandaException, InterruptedException {
        if (isRequired(gallery)) {
            LOGGER.info("Saving image list...");
            save(gallery);
        } else {
            LOGGER.debug("Image list does not need to be archived.");
        }
    }

    /**
     * Checks if the image list should be saved or not.
     * 
     * @return <code>true</code> if image list archival is active but no list has
     *         been found on disk, <code>false</false> otherwise.
     * @throws RipPandaException on failure
     */
    private boolean isRequired(Gallery gallery) throws RipPandaException {
        boolean isRequired = getSettings().isImageListActive();

        if (isRequired) {
            ensureFilesLoaded(gallery);
            isRequired = !isUnavailable(gallery) && gallery.getFiles().stream().noneMatch(x -> FILENAME.equals(String.valueOf(x.getFileName())));
        }

        return isRequired;
    }

    /**
     * Saves the image list of the gallery to disk.
     * 
     * @param gallery the gallery
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void save(Gallery gallery) throws RipPandaException, InterruptedException {
        Document document = getWebClient().loadMpvPage(gallery.getId(), gallery.getToken());
        Element verificationElement = document.getElementById("pane_outer");
        Element mpvInfoElement = document.selectFirst("body > script:nth-child(3)");
        if (verificationElement == null || mpvInfoElement == null) {
            if (processUnavailability(gallery, document)) {
                return;
            }
            throw new RipPandaException("Could not find MPV info element.");
        }

        Matcher imageListMatcher = IMAGELIST_PATTERN.matcher(mpvInfoElement.html());
        if (!imageListMatcher.find()) {
            throw new RipPandaException("Unexpected HTML.");
        }
        String imageListString = imageListMatcher.group(1);
        JsonArray imageList;
        try {
            imageList = GSON.fromJson(imageListString, JsonArray.class);
        } catch (JsonSyntaxException e) {
            throw new RipPandaException("Invalid image list JSON.", e);
        }

        initDir(gallery.getDir());
        FilesUtils.save(file -> write(imageList, file), gallery.getDir(), FILENAME);
    }
}
