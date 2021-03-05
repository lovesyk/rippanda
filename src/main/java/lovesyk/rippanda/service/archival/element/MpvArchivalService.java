package lovesyk.rippanda.service.archival.element;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.Settings;

/**
 * The archival service for gallery MPV page elements.
 */
public class MpvArchivalService extends AbstractElementArchivalService implements IElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(MpvArchivalService.class);
    private static final String FILENAME = "mpv.html";

    /**
     * Constructs a new archival service instance.
     * 
     * @param settings  the application settings
     * @param webClient the network web client
     */
    @Inject
    public MpvArchivalService(Settings settings, IWebClient webClient) {
        super(settings, webClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Gallery gallery) throws RipPandaException, InterruptedException {
        if (isRequired(gallery)) {
            LOGGER.info("Saving MPV page...");
            save(gallery);
        } else {
            LOGGER.debug("MPV page does not need to be archived.");
        }
    }

    /**
     * Checks if the MPV page should be saved or not.
     * 
     * @return <code>true</code> if MPV page archival is active but no page has been
     *         found on disk, <code>false</false> otherwise.
     * @throws RipPandaException on failure
     */
    private boolean isRequired(Gallery gallery) throws RipPandaException {
        boolean isRequired = getSettings().isMpvActive();

        if (isRequired) {
            Path mpvPageFile = gallery.getDir().resolve(FILENAME);
            isRequired = !Files.isRegularFile(mpvPageFile);
        }

        return isRequired;
    }

    /**
     * Saves the MPV page of the gallery to disk.
     * 
     * @param gallery the gallery
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void save(Gallery gallery) throws RipPandaException, InterruptedException {
        Document document = getWebClient().loadMpvPage(gallery.getId(), gallery.getToken());
        Element verificationElement = document.getElementById("bar3");
        if (verificationElement == null) {
            throw new RipPandaException("Could not verify the gallery MPV page got loaded correctly.");
        }

        initDir(gallery.getDir());
        save(document.outerHtml(), gallery.getDir(), FILENAME);
    }
}
