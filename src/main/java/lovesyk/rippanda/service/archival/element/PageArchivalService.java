package lovesyk.rippanda.service.archival.element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.OperationMode;
import lovesyk.rippanda.settings.Settings;

/**
 * The archival service for gallery page elements.
 */
@ApplicationScoped
public class PageArchivalService extends AbstractElementArchivalService implements IElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(PageArchivalService.class);
    private static final String FILENAME = "page.html";

    /**
     * Constructs a new archival service instance.
     * 
     * @param settings  the application settings
     * @param webClient the network web client
     */
    @Inject
    public PageArchivalService(Settings settings, IWebClient webClient) {
        super(settings, webClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Gallery gallery) throws RipPandaException, InterruptedException {
        if (isRequired(gallery)) {
            LOGGER.info("Saving page...");
            save(gallery);
        } else {
            LOGGER.debug("Page does not need to be archived.");
        }
    }

    /**
     * Checks if the page should be saved or not.
     * 
     * @return <code>true</code> if page archival is active but no page has been
     *         found on disk or update mode is active, <code>false</false>
     *         otherwise.
     * @throws RipPandaException on failure
     */
    private boolean isRequired(Gallery gallery) throws RipPandaException {
        boolean isRequired = getSettings().isPageActive();

        if (isRequired) {
            ensureFilesLoaded(gallery);
            if (isUnavailable(gallery)) {
                isRequired = false;
            } else {
                Optional<Path> pageFile = gallery.getFiles().stream()
                        .filter(x -> FILENAME.equals(String.valueOf(x.getFileName()))).findAny();
                if (pageFile.isPresent()) {
                    if (getSettings().getOperationMode() == OperationMode.UPDATE) {
                        FileTime lastModifiedTime;
                        try {
                            lastModifiedTime = Files.getLastModifiedTime(pageFile.get());
                        } catch (IOException e) {
                            throw new RipPandaException("Could not retrieve last modified time.", e);
                        }
                        isRequired = lastModifiedTime.toInstant().isBefore(gallery.getUpdateThreshold());
                    } else {
                        isRequired = false;
                    }
                }
            }
        }

        return isRequired;
    }

    /**
     * Saves the page of the gallery to disk.
     * 
     * @param gallery the gallery
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void save(Gallery gallery) throws RipPandaException, InterruptedException {
        Document document = getWebClient().loadPage(gallery.getId(), gallery.getToken());
        Element verificationElement = document.getElementById("rating_label");
        if (verificationElement == null) {
            if (processUnavailability(gallery, document)) {
                return;
            }
            throw new RipPandaException("Could not verify the gallery page got loaded correctly.");
        }

        initDir(gallery.getDir());
        save(document.outerHtml(), gallery.getDir(), FILENAME);
    }
}
