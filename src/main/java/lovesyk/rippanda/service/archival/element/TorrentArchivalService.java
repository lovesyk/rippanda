package lovesyk.rippanda.service.archival.element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.api.ApiTorrent;
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
        boolean isRequired = getSettings().isTorrentActive();

        List<ApiTorrent> apiTorrents = new ArrayList<>();
        if (isRequired) {
            apiTorrents.addAll(parseApiTorrents(gallery));
            if (Files.exists(gallery.getDir())) {
                try (Stream<Path> stream = Files.list(gallery.getDir()).filter(x -> Files.isRegularFile(x) && x.toString().endsWith(".torrent"))) {
                    for (Path torrent : (Iterable<Path>) stream::iterator) {
                        ApiTorrent apiTorrent = null;
                        for (ApiTorrent apiTorrentCandidate : apiTorrents) {
                            if (apiTorrentCandidate.getTorrentSize() == Files.size(torrent)
                                    && Files.getLastModifiedTime(torrent).toInstant().isAfter(apiTorrentCandidate.getAddedDateTime())) {
                                apiTorrent = apiTorrentCandidate;
                                break;
                            }
                        }

                        if (apiTorrent == null) {
                            LOGGER.debug("Deleting archived torrent not found on API: \"{}\"", torrent.getFileName());
                            Files.delete(torrent);
                        } else {
                            LOGGER.debug("Skipping archived torrent found on API: \"{}\"", torrent.getFileName());
                            apiTorrents.remove(apiTorrent);
                        }
                    }
                } catch (IOException e) {
                    throw new RipPandaException("Could not traverse the given archive directory.", e);
                }
            }
        }

        if (apiTorrents.isEmpty()) {
            LOGGER.info("Torrents do not need to be archived.");
        } else {
            LOGGER.info("Torrents need to be archived.");
            Document document = getWebClient().loadTorrentPage(gallery.getId(), gallery.getToken());

            Elements torrentUrlElements = document.select("#torrentinfo form a[href*=.torrent]");
            List<String> torrentUrlList = torrentUrlElements.stream().map(x -> x.attr("href")).filter(x -> isUrlInApiTorrents(x, apiTorrents))
                    .collect(Collectors.toList());

            for (String torrentUrl : torrentUrlList) {
                initDir(gallery.getDir());
                tryDownloadTorrentFile(torrentUrl, torrentUrlElements, gallery);
            }
        }
    }

    /**
     * Checks whether the given torrent URL contains any of the API torrent hashes.
     * 
     * @param url         the torrent URL to check
     * @param apiTorrents the API torrent list to look for hashes, never
     *                    <code>null</code>
     * @return <code>true</code> if hash was found in URL, <code>false</code>
     *         otherwise
     */
    private boolean isUrlInApiTorrents(String url, List<ApiTorrent> apiTorrents) {
        return apiTorrents.stream().anyMatch(x -> url.contains(x.getHash()));
    }

    /**
     * Parses API torrent entries from the gallery metadata.
     * 
     * @param gallery the gallery
     * @return all API torrents found in the gallery metadata, never
     *         <code>null</code>
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private List<ApiTorrent> parseApiTorrents(Gallery gallery) throws RipPandaException, InterruptedException {
        getApiArchivingService().ensureLoaded(gallery);

        List<ApiTorrent> result = new ArrayList<>();

        JsonElement torrentsElement = gallery.getMetadata().get("torrents");
        if (torrentsElement == null || !torrentsElement.isJsonArray()) {
            throw new RipPandaException("Unexpected JSON.");
        }

        JsonArray torrentsArray = torrentsElement.getAsJsonArray();
        for (JsonElement torrentElement : torrentsArray) {
            if (torrentElement == null || !torrentElement.isJsonObject()) {
                throw new RipPandaException("Unexpected JSON.");
            }
            JsonObject torrentObject = torrentElement.getAsJsonObject();

            JsonElement addedElement = torrentObject.get("added");
            if (addedElement == null || !addedElement.isJsonPrimitive()) {
                throw new RipPandaException("Unexpected JSON.");
            }
            String addedString = addedElement.getAsString();
            long addedLong;
            try {
                addedLong = Long.valueOf(addedString);
            } catch (NumberFormatException e) {
                throw new RipPandaException("Failed parsing added date time.", e);
            }

            Instant addedDateTime = Instant.ofEpochSecond(addedLong);

            JsonElement tsizeElement = torrentObject.get("tsize");
            if (addedElement == null || !addedElement.isJsonPrimitive()) {
                throw new RipPandaException("Unexpected JSON.");
            }
            String tsizeString = tsizeElement.getAsString();
            int torrentSize;
            try {
                torrentSize = Integer.valueOf(tsizeString);
            } catch (NumberFormatException e) {
                throw new RipPandaException("Failed parsing torrent size.", e);
            }

            JsonElement hashElement = torrentObject.get("hash");
            if (hashElement == null || !hashElement.isJsonPrimitive()) {
                throw new RipPandaException("Unexpected JSON.");
            }
            String hash = hashElement.getAsString();

            ApiTorrent apiTorrent = new ApiTorrent(hash, torrentSize, addedDateTime);

            result.add(apiTorrent);
        }

        return result;
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
                String sanitizedFileName = sanitizeFileName(gallery.getDir(), downloadableTorrent.getName(), false);
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
