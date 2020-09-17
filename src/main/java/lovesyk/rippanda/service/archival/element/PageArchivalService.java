package lovesyk.rippanda.service.archival.element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.OperationMode;
import lovesyk.rippanda.settings.Settings;

/**
 * The archival service for gallery page elements.
 */
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
            LOGGER.info("Page needs to be archived.");
            save(gallery);
        } else {
            LOGGER.info("Page does not need to be archived.");
        }
    }

    /**
     * Checks if the page should be saved or not.
     * 
     * @return <code>true</code> if no recent page has been found on disk,
     *         <code>false</false> otherwise.
     * @throws RipPandaException on failure
     */
    private boolean isRequired(Gallery gallery) throws RipPandaException {
        Path pageFile = gallery.getDir().resolve(FILENAME);
        boolean result = getSettings().getOperationMode() == OperationMode.UPDATE || !Files.isRegularFile(pageFile);
        if (result) {
            Instant threshold = Instant.now().minus(5, ChronoUnit.DAYS);
            FileTime lastModifiedTime;
            try {
                lastModifiedTime = Files.getLastModifiedTime(pageFile);
            } catch (IOException e) {
                throw new RipPandaException("Could not read last modified time.", e);
            }
            result = lastModifiedTime.toInstant().isBefore(threshold);
        }

        return result;
    }

    /**
     * Saves the page of the gallery to disk.
     * 
     * @param gallery the gallery
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void save(Gallery gallery) throws RipPandaException, InterruptedException {
        LOGGER.info("Saving HTML...");

        Document document = getWebClient().loadPage(gallery.getId(), gallery.getToken());
        Element verificationElement = document.getElementById("rating_label");
        if (verificationElement == null) {
            throw new RipPandaException("Could not verify the gallery page got loaded correctly.");
        }

        initDir(gallery.getDir());
        save(document.outerHtml(), gallery.getDir(), FILENAME);
    }
}
