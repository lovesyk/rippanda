package lovesyk.rippanda.service.archival;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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
 * The service responsible for the archival of a web search result.
 */
public class DownloadModeArchivalService extends AbstractArchivalService implements IArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(DownloadModeArchivalService.class);

    private final IWebClient webClient;

    /**
     * Constructs a new search result archival service instance.
     * 
     * @param settings                the application settings
     * @param webClient               the web client for network access
     * @param elementArchivalServices the archival services which should be invoked
     *                                for archiving elements
     */
    @Inject
    public DownloadModeArchivalService(Settings settings, IWebClient webClient, Instance<IElementArchivalService> elementArchivalServices) {
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
            LOGGER.info("Activating download mode.");
            run();
        }
    }

    /**
     * Checks if the download mode should be executed based in application settings.
     * 
     * @return <code>true</code> if update mode is enabled, <code>false</false>
     *         otherwise.
     */
    private boolean isRequired() {
        return getSettings().getOperationMode() == OperationMode.DOWNLOAD;
    }

    /**
     * Runs the download archival service, retrying if necessary.
     * 
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    public void run() throws RipPandaException, InterruptedException {
        initDirs();
        initSuccessIds();

        String pageUrl = getSettings().getUri().toString();
        while (pageUrl != null) {
            Document searchResultPage = null;
            for (int remainingTries = 3; remainingTries > 0; --remainingTries) {
                LOGGER.debug("Loading search result: {}", pageUrl);
                try {
                    searchResultPage = getWebClient().loadDocument(pageUrl);
                    break;
                } catch (RipPandaException e) {
                    LOGGER.warn("Loading search result page failed, {} tries remain.", remainingTries, e);
                    if (remainingTries > 0) {
                        LOGGER.warn("Waiting 10 seconds before retrying...");
                        Thread.sleep(1000 * 10);
                    } else {
                        throw e;
                    }
                }
            }

            List<Gallery> galleries = parseGalleries(searchResultPage);
            if (galleries.isEmpty()) {
                LOGGER.info("No galleries found.");
                break;
            }

            for (Gallery gallery : galleries) {
                process(gallery);
            }

            pageUrl = parseNextPageUrl(searchResultPage);
        }

        deleteSuccessTempFile();
    }

    /**
     * Parses the URL for the next search result page.
     * 
     * @param document the search result page
     * @return the URL of the next search result page.
     */
    private String parseNextPageUrl(Document document) {
        Element nextPageElement = document.selectFirst(".ptds + td:not(.ptdd) > a");
        if (nextPageElement != null) {
            return nextPageElement.attr("href");
        }

        return null;
    }

    /**
     * Processes a single gallery of a search result.
     * 
     * @param gallery the gallery to process
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void process(Gallery gallery) throws RipPandaException, InterruptedException {
        LOGGER.info("Processing gallery with ID \"{}\" and token \"{}\"", gallery.getId(), gallery.getToken());

        if (isInSuccessIds(gallery.getId())) {
            LOGGER.info("Gallery exists in success file. Assume it's archived and skipping...");
        } else {
            addTempSuccessId(gallery.getId());
            for (IElementArchivalService archivingService : getArchivingServiceList()) {
                for (int remainingTries = 3; remainingTries > 0; --remainingTries) {
                    try {
                        archivingService.process(gallery);
                        break;
                    } catch (RipPandaException e) {
                        LOGGER.warn("Archiving element failed, {} tries remain.", remainingTries, e);
                        if (remainingTries > 0) {
                            LOGGER.warn("Waiting 10 seconds before retrying...");
                            Thread.sleep(1000 * 10);
                        } else {
                            throw e;
                        }
                    }
                }
            }
            addSuccessId(gallery.getId());

            updateSuccessIds();
        }
    }

    /**
     * Parses all galleries on the search result page.
     * 
     * @param searchResultPage the search result page
     * @return all galleries, never <code>null</code>
     * @throws RipPandaException on failure
     */
    private List<Gallery> parseGalleries(Document searchResultPage) throws RipPandaException {
        List<Gallery> galleries = new ArrayList<>();

        Elements subGalleryElements = searchResultPage.select("table.gltc tr > td.gl1c");
        for (Element subGalleryElement : subGalleryElements) {
            Element galleryElement = subGalleryElement.parent();

            int id = parseGalleryId(galleryElement);
            String token = parseGalleryToken(galleryElement);
            Gallery gallery = new Gallery(id, token, getSettings().getWritableArchiveDirectory().resolve(String.valueOf(id)));

            galleries.add(gallery);
        }

        return galleries;
    }

    /**
     * Parses the ID of a gallery.
     * 
     * @param galleryElement the gallery element on the search result page
     * @return the ID of the gallery
     * @throws RipPandaException on failure
     */
    private int parseGalleryId(Element galleryElement) throws RipPandaException {
        if (galleryElement == null) {
            throw new RipPandaException("Could not find gallery element.");
        }

        Element galleryIdElement = galleryElement.select(".glname > a").first();
        if (galleryIdElement == null) {
            throw new RipPandaException("Could not find element to extract gallery ID from.");
        }

        Pattern pattern = Pattern.compile("/g/(.+?)/");
        Matcher matcher = pattern.matcher(galleryIdElement.attr("href"));
        if (!matcher.find()) {
            throw new RipPandaException("Could not find gallery ID.");
        }

        int result;
        try {
            result = Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new RipPandaException(e);
        }
        return result;
    }

    /**
     * Parses the token of a gallery.
     * 
     * @param galleryElement the gallery element on the search result page
     * @return the token of the gallery
     * @throws RipPandaException on failure
     */
    private String parseGalleryToken(Element galleryElement) throws RipPandaException {
        if (galleryElement == null) {
            throw new RipPandaException("Could not find gallery element.");
        }

        Element galleryTokenElement = galleryElement.select(".glname > a").first();
        if (galleryTokenElement == null) {
            throw new RipPandaException("Could not find element to extract gallery token from.");
        }

        Pattern pattern = Pattern.compile("/g/.+?/(.+?)/");
        Matcher matcher = pattern.matcher(galleryTokenElement.attr("href"));
        if (!matcher.find()) {
            throw new RipPandaException("Could not find gallery token.");
        }

        String result = matcher.group(1);

        return result;
    }

    /**
     * Gets the network web client.
     * 
     * @return the web client
     */
    private IWebClient getWebClient() {
        return webClient;
    }
}
