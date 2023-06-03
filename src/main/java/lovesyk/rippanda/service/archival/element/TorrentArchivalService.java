package lovesyk.rippanda.service.archival.element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.enterprise.context.ApplicationScoped;
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
@ApplicationScoped
public class TorrentArchivalService extends AbstractElementArchivalService implements IElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(TorrentArchivalService.class);

    private final static String FILENAME_EXTENSION = ".torrent";

    private MetadataArchivalService apiArchivingService;

    /**
     * Constructs a new archival service instance.
     * 
     * @param settings            the application settings
     * @param webClient           the network web client
     * @param apiArchivingService the metadata archival service
     */
    @Inject
    public TorrentArchivalService(Settings settings, IWebClient webClient,
            MetadataArchivalService apiArchivingService) {
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
            ensureFilesLoaded(gallery);
            if (!isUnavailable(gallery)) {
                apiTorrents.addAll(parseApiTorrents(gallery));
                for (Path file : gallery.getFiles()) {
                    if (file.toString().endsWith(FILENAME_EXTENSION)) {
                        ApiTorrent apiTorrent = null;
                        for (ApiTorrent apiTorrentCandidate : apiTorrents) {
                            long size;
                            FileTime lastModifiedTime;
                            try {
                                size = Files.size(file);
                                lastModifiedTime = Files.getLastModifiedTime(file);
                            } catch (IOException e) {
                                throw new RipPandaException("Could not read file attributes.", e);
                            }
                            if (apiTorrentCandidate.getTorrentSize() == size
                                    && lastModifiedTime.toInstant().isAfter(apiTorrentCandidate.getAddedDateTime())) {
                                apiTorrent = apiTorrentCandidate;
                                break;
                            }
                        }

                        if (apiTorrent == null) {
                            LOGGER.debug("Deleting archived torrent not found on API: \"{}\"", file.getFileName());
                            try {
                                Files.delete(file);
                            } catch (IOException e) {
                                throw new RipPandaException("Could not delete file.", e);
                            }
                        } else {
                            LOGGER.trace("Skipping archived torrent found on API: \"{}\"", file.getFileName());
                            apiTorrents.remove(apiTorrent);
                        }
                    }
                }
            }
        }

        if (apiTorrents.isEmpty()) {
            LOGGER.debug("Torrents do not need to be archived.");
        } else {
            LOGGER.info("Saving torrents...");
            Document document = getWebClient().loadTorrentPage(gallery.getId(), gallery.getToken());
            Element torrentInfoElement = document.getElementById("torrentinfo");
            if (torrentInfoElement == null) {
                if (processUnavailability(gallery, document)) {
                    return;
                }
                throw new RipPandaException("Could not verify the torrent page got loaded correctly.");
            }

            Elements torrentUrlElements = torrentInfoElement.select("form a[href*=.torrent]");
            List<String> torrentUrlList = torrentUrlElements.stream().map(x -> x.attr("href"))
                    .filter(x -> isUrlInApiTorrents(x, apiTorrents))
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
        getApiArchivingService().ensureLoadedUpToDate(gallery);

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
    private void tryDownloadTorrentFile(String torrentUrl, Elements torrentUrlElements, Gallery gallery)
            throws RipPandaException, InterruptedException {
        boolean success = downloadTorrentFile(torrentUrl, gallery, true);

        if (!success) {
            LOGGER.debug("Did not receive torrent file, tracker cookies might be missing. Trying to obtain them...");
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

            // add dummy query parameter in case CF cached the previous failure
            downloadTorrentFile(torrentUrl + "?cache=bypass", gallery, false);
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
    private boolean downloadTorrentFile(String torrentUrl, Gallery gallery, boolean failAcceptable)
            throws RipPandaException, InterruptedException {
        LOGGER.debug("Saving torrent from URL: " + torrentUrl);
        return getWebClient().downloadFile(torrentUrl, (downloadableTorrent) -> {
            if ("application/x-bittorrent".equals(downloadableTorrent.getMimeType())) {
                String sanitizedFileName = sanitizeFileName(gallery.getDir(), downloadableTorrent.getName(), false);
                save(downloadableTorrent.getStream(), gallery.getDir(), sanitizedFileName);

                return true;
            }

            if (failAcceptable) {
                return false;
            }

            if (LOGGER.isTraceEnabled()) {
                try {
                    String streamString = IOUtils.toString(downloadableTorrent.getStream(),
                            StandardCharsets.UTF_8.name());
                    LOGGER.trace("Received stream content: {}", streamString);
                } catch (IOException e) {
                    LOGGER.trace("Could not receive stream content.", e);
                }
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
