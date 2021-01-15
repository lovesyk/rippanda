package lovesyk.rippanda.service.archival.element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.JsonElement;

import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.Settings;

/**
 * The archival service for gallery torrent elements.
 */
public class TorrentArchivalService extends AbstractElementArchivalService implements IElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(TorrentArchivalService.class);

    private MetadataArchivalService apiArchivingService;

    /**
     * Constructs a new archival service instance.
     * 
     * @param settings            the application settings
     * @param webClient           the network web client
     * @param apiArchivingService the metadata archival service
     */
    @Inject
    public TorrentArchivalService(Settings settings, IWebClient webClient, MetadataArchivalService apiArchivingService) {
        super(settings, webClient);
        this.apiArchivingService = apiArchivingService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Gallery gallery) throws RipPandaException, InterruptedException {
        getApiArchivingService().ensureLoaded(gallery);

        List<Path> existingTorrentFiles = new ArrayList<>();
        if (Files.isDirectory(gallery.getDir())) {
            try {
                Files.list(gallery.getDir()).filter(x -> x.toString().endsWith(".torrent")).forEach(existingTorrentFiles::add);
            } catch (IOException e) {
                throw new RipPandaException("Could not look up existing torrent files.", e);
            }
        }

        boolean isRequired = parseTorrentCount(gallery) != existingTorrentFiles.size();

        if (isRequired) {
            LOGGER.info("Torrents need to be archived.");
            Document document = getWebClient().loadTorrentPage(gallery.getId(), gallery.getToken());

            Elements torrentUrlElements = document.select("#torrentinfo form a[href*=.torrent]");
            List<String> torrentUrlList = torrentUrlElements.stream().map(x -> x.attr("href")).collect(Collectors.toList());

            for (Path existingTorrentFile : existingTorrentFiles) {
                try {
                    Files.deleteIfExists(existingTorrentFile);
                } catch (IOException e) {
                    throw new RipPandaException("Failed deleting old torrent files.");
                }
            }

            for (String torrentUrl : torrentUrlList) {
                initDir(gallery.getDir());
                tryDownloadTorrentFile(torrentUrl, torrentUrlElements, gallery);
            }
        } else {
            LOGGER.info("Torrents do not need to be archived.");
        }
    }

    private int parseTorrentCount(Gallery gallery) throws RipPandaException, InterruptedException {
        getApiArchivingService().ensureLoaded(gallery);

        JsonElement torrentCountElement = gallery.getMetadata().get("torrentcount");
        if (torrentCountElement == null || !torrentCountElement.isJsonPrimitive()) {
            throw new RipPandaException("Unexpected JSON.");
        }

        String torrentCountString = torrentCountElement.getAsString();
        int torrentCount;
        try {
            torrentCount = Integer.valueOf(torrentCountString);
        } catch (NumberFormatException e) {
            throw new RipPandaException("Failed parsing torrent count.", e);
        }

        return torrentCount;
    }

    /**
     * Tries to download the torrent file from the given URL.
     * <p>
     * Multiple web requests may be made to update the cookie store in case
     * tracker-specific cookies might be missing.
     * 
     * @param torrentUrl         the torrent URL to download
     * @param torrentUrlElements the torrent URL elements as found on the respective
     *                           HTML page
     * @param gallery            the gallery
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void tryDownloadTorrentFile(String torrentUrl, Elements torrentUrlElements, Gallery gallery) throws RipPandaException, InterruptedException {
        boolean success = downloadTorrentFile(torrentUrl, gallery, true);

        if (!success) {
            LOGGER.info("Did not receive torrent file, tracker cookies might be missing. Trying to obtain them...");
            Element personalizedElement = torrentUrlElements.first();

            Pattern pattern = Pattern.compile("document\\.location='(.+?)'");
            Matcher matcher = pattern.matcher(personalizedElement.attr("onclick"));
            if (!matcher.find()) {
                throw new RipPandaException("Could not find personalized torrent URL.");
            }
            String personalizedUrl = matcher.group(1);

            LOGGER.debug("Fetching personalized torrent file to obtain cookies.");
            getWebClient().downloadFile(personalizedUrl, (file) -> {
                return true;
            });

            downloadTorrentFile(torrentUrl, gallery, false);
        }
    }

    /**
     * Downloads the torrent file from the given URL according to fail acceptance.
     * 
     * @param torrentUrl     the torrent URL to download
     * @param gallery        the gallery
     * @param failAcceptable whether it's acceptable to fail the download
     * @return <code>true</code> if the download succeeded, <code>false</code> if it
     *         did not and the fail is acceptable
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private boolean downloadTorrentFile(String torrentUrl, Gallery gallery, boolean failAcceptable) throws RipPandaException, InterruptedException {
        return getWebClient().downloadFile(torrentUrl, (downloadableTorrent) -> {
            if ("application/x-bittorrent".equals(downloadableTorrent.getMimeType())) {
                String sanitizedFileName = sanitizeFileName(gallery.getDir(), downloadableTorrent.getName());
                save(downloadableTorrent.getStream(), gallery.getDir(), sanitizedFileName);

                return true;
            }

            if (failAcceptable) {
                return false;
            }

            throw new RipPandaException("Could not load torrent file.");
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
