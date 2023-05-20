package lovesyk.rippanda.service.archival.element;

import java.nio.file.Path;
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
import lovesyk.rippanda.settings.Settings;

/**
 * The archival service for gallery expunge log elements.
 */
@ApplicationScoped
public class ExpungeLogArchivalService extends AbstractElementArchivalService implements IElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(ExpungeLogArchivalService.class);
    private static final String FILENAME = "expungelog.html";

    private MetadataArchivalService apiArchivingService;

    /**
     * Constructs a new archival service instance.
     * 
     * @param settings            the application settings
     * @param webClient           the network web client
     * @param apiArchivingService the metadata archival service
     */
    @Inject
    public ExpungeLogArchivalService(Settings settings, IWebClient webClient,
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
            LOGGER.info("Saving expunge log...");
            save(gallery);
        } else {
            LOGGER.debug("Expunge log does not need to be archived.");
        }
    }

    /**
     * Checks if the expunge log should be saved or not.
     * 
     * @return <code>true</code> if gallery has been expunged and expunge log
     *         archival is
     *         active but no log has been found on disk, <code>false</false>
     *         otherwise.
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private boolean isRequired(Gallery gallery) throws RipPandaException, InterruptedException {
        boolean isRequired = getSettings().isPageActive();

        if (isRequired) {
            ensureFilesLoaded(gallery);
            if (isUnavailable(gallery)) {
                isRequired = false;
            } else {
                Optional<Path> expungeLogFile = gallery.getFiles().stream()
                        .filter(x -> FILENAME.equals(String.valueOf(x.getFileName()))).findAny();
                if (expungeLogFile.isPresent()) {
                    isRequired = false;
                } else {
                    getApiArchivingService().ensureLoadedUpToDate(gallery);
                    isRequired = gallery.isExpunged();
                }
            }
        }

        return isRequired;
    }

    /**
     * Saves the expunge log of the gallery to disk.
     * 
     * @param gallery the gallery
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void save(Gallery gallery) throws RipPandaException, InterruptedException {
        Document document = getWebClient().loadExpungeLogPage(gallery.getId(), gallery.getToken());
        Element verificationElement = document.getElementById("form_expunge_vote");
        if (verificationElement == null) {
            if (processUnavailability(gallery, document)) {
                return;
            }
            throw new RipPandaException("Could not verify the gallery expunge log got loaded correctly.");
        }

        initDir(gallery.getDir());
        save(document.outerHtml(), gallery.getDir(), FILENAME);
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
