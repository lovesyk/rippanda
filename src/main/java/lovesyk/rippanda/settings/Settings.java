package lovesyk.rippanda.settings;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import jakarta.enterprise.context.ApplicationScoped;
import lovesyk.rippanda.exception.RipPandaException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The application settings that represent the command line arguments.
 */
@ApplicationScoped
@Command(name = "rippanda", sortOptions = false)
public class Settings implements Callable<Integer> {
    private static final String SKIP_ELEMENTS_METADATA = "metadata";
    private static final String SKIP_ELEMENTS_PAGE = "page";
    private static final String SKIP_ELEMENTS_IMAGELIST = "imagelist";
    private static final String SKIP_ELEMENTS_EXPUNGELOG = "expungelog";
    private static final String SKIP_ELEMENTS_THUMBNAIL = "thumbnail";
    private static final String SKIP_ELEMENTS_TORRENT = "torrent";
    private static final String SKIP_ELEMENTS_ZIP = "zip";

    private static final Logger LOGGER = LogManager.getLogger(Settings.class);

    @Parameters(paramLabel = "mode", description = "Operation mode: ${COMPLETION-CANDIDATES}", defaultValue = "download", showDefaultValue = Visibility.ALWAYS)
    private OperationMode operationMode;

    @Option(names = { "-c",
            "--cookies" }, paramLabel = "cookies", description = "Log-in / perk cookies in key=value pairs separated by ;", required = true, parameterConsumer = CookiesConsumer.class)
    private CookiesWrapper cookies;
    @Option(names = { "-p",
            "--proxy" }, paramLabel = "host:port", description = "SOCKS5 proxy to use for network requests and DNS resolution.", converter = InetSocketAddressConverter.class)
    private InetSocketAddress proxy;
    @Option(names = { "-u",
            "--url" }, paramLabel = "url", description = "Base URL to use for web requests or a more specific search URL if in download mode", required = true)
    private URI uri;
    @Option(names = { "-d",
            "--delay" }, paramLabel = "time", description = "Minimum delay between web request in ISO-8601 time format", defaultValue = "15S", showDefaultValue = Visibility.ALWAYS, converter = TimeConverter.class)
    private Duration requestDelay;
    @Option(names = { "-i",
            "--update-interval" }, paramLabel = "interval", description = "Update interval when deciding whether to update a gallery as ISO-8601 periods in format minThreshold=minDuration-maxThreshold=maxDuration", defaultValue = "0D=7D-365D=90D", showDefaultValue = Visibility.ALWAYS, converter = UpdateIntervalConverter.class)
    private UpdateInterval updateInterval;
    @Option(names = { "-a",
            "--archive-dir" }, paramLabel = "path", description = "Directories containing archived galleries (first occurence denotes writable primary path)", defaultValue = ".", showDefaultValue = Visibility.ALWAYS)
    private List<Path> archiveDirectories;
    @Option(names = { "-s",
            "--success-dir" }, paramLabel = "path", description = "Directory containing success files", defaultValue = ".", showDefaultValue = Visibility.ALWAYS)
    private Path successDirectory;
    @Option(names = { "-e",
            "--skip" }, paramLabel = "element", description = "Specify multiple times to skip elements during archival process. (metadata, page, imagelist, expungelog, thumbnail, torrent, zip)")
    private HashSet<String> elementsToSkip = new HashSet<String>();
    @Option(names = { "-t",
            "--catchup" }, description = "Enables catch-up download mode to stop processing once a fully archived page has been encountered.", arity = "0", showDefaultValue = Visibility.ALWAYS)
    private boolean catchup = false;
    @Option(names = { "-v",
            "--verbose" }, description = "Specify up to 7 times to override logging verbosity (4 times by default)")
    private boolean[] verbosity = new boolean[] { true, true, true, true };

    // not configurable for now
    private String userAgent = null;
    private String successFileId;

    /**
     * Initializes the global settings instance.
     * <p>
     * Has to be called manually before using any other part of the application.
     * 
     * @param args the command line arguments
     * @throws RipPandaException on failure
     */
    public void init(String[] args) throws RipPandaException {
        LOGGER.debug("Initializing settings...");

        int exitCode = new CommandLine(this).setUsageHelpLongOptionsMaxWidth(26).setUsageHelpWidth(160).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
        if (exitCode != 0) {
            throw new RipPandaException("Invalid command line arguments.");
        }

        successDirectory = successDirectory.toAbsolutePath();
        archiveDirectories = archiveDirectories.stream().map(x -> x.toAbsolutePath()).collect(Collectors.toList());
        initLogging();
        initCookies();
        validateElementsToSkip();
        print();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer call() {
        return 0;
    }

    /**
     * Initializes output logging according to specified settings.
     */
    private void initLogging() {
        // https://stackoverflow.com/a/23434603
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("lovesyk.rippanda");
        loggerConfig.setLevel(getLoggingVerbosity());
        ctx.updateLoggers();
    }

    /**
     * Initializes the success file ID which is represented by the user ID found in
     * the cookies as well as cleaning up the cookies.
     * 
     * @throws RipPandaException on failure
     */
    private void initCookies() throws RipPandaException {
        LOGGER.debug("Looking for member ID to use as success file ID...");

        String memberIdString = getCookies().get("ipb_member_id");
        if (memberIdString == null) {
            throw new RipPandaException("No valid member ID found in cookies.");
        }
        successFileId = memberIdString;
        LOGGER.debug("Success file ID set successfully.");

        LOGGER.debug("Cleaning up cookies...");
        // do not warn on questionable content
        getCookies().put("nw", "1");
        // do not send daily event timestamp
        getCookies().remove("event");
        // do not send CloudFlare ID
        getCookies().remove("__cfduid");
    }

    /**
     * Validates the entered elements to be skipped.
     * 
     * @throws RipPandaException if any invalid element is encountered
     */
    private void validateElementsToSkip() throws RipPandaException {
        List<String> validElements = Arrays.asList(SKIP_ELEMENTS_METADATA, SKIP_ELEMENTS_PAGE, SKIP_ELEMENTS_IMAGELIST, SKIP_ELEMENTS_THUMBNAIL,
                SKIP_ELEMENTS_TORRENT, SKIP_ELEMENTS_ZIP);
        for (String element : elementsToSkip) {
            if (!validElements.contains(element)) {
                throw new RipPandaException("Invalid elements to be skipped.");
            }
        }
    }

    /**
     * Prints the settings the application will be run with.
     */
    private void print() {
        LOGGER.info("Using the following configuration:");
        LOGGER.info("Operation mode: {}", getOperationMode());
        LOGGER.info("Proxy: {}", getProxy());
        LOGGER.info("URL: {}", getUri());
        LOGGER.info("Request delay: {}", getRequestDelay());
        LOGGER.info("Min update threshold: {}", getUpdateInterval().getMinThreshold());
        LOGGER.info("Min update interval: {}", getUpdateInterval().getMinDuration());
        LOGGER.info("Max update threshold: {}", getUpdateInterval().getMaxThreshold());
        LOGGER.info("Max update interval: {}", getUpdateInterval().getMaxDuration());
        LOGGER.info("Archive directories: {}", getArchiveDirectories());
        LOGGER.info("Writable archive directory: {}", getWritableArchiveDirectory());
        LOGGER.info("Success directory: {}", getSuccessDirectory());
        LOGGER.info("Metadata active: {}", isMetadataActive());
        LOGGER.info("Page active: {}", isPageActive());
        LOGGER.info("Image list active: {}", isImageListActive());
        LOGGER.info("Expunge log active: {}", isExpungeLogActive());
        LOGGER.info("Thumbnail active: {}", isThumbnailActive());
        LOGGER.info("Torrent active: {}", isTorrentActive());
        LOGGER.info("ZIP active: {}", isZipActive());
        LOGGER.info("Catch-up download mode: {}", isCatchup());
        LOGGER.info("Logging verbosity: {}", getLoggingVerbosity());
    }

    /**
     * Gets the application operation mode.
     * 
     * @return the operation mode
     */
    public OperationMode getOperationMode() {
        return operationMode;
    }

    /**
     * Gets the cookies to use for initial network requests.
     * 
     * @return the cookies
     */
    public Map<String, String> getCookies() {
        return cookies.getCookies();
    }

    /**
     * Gets the SOCKS5 proxy to use for network requests.
     * 
     * @return the proxy
     */
    public InetSocketAddress getProxy() {
        return proxy;
    }

    /**
     * Gets the URI to use for network requests.
     * 
     * @return the URI
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Gets the request delay to be waited between network requests.
     * 
     * @return the request delay
     */
    public Duration getRequestDelay() {
        return requestDelay;
    }

    /**
     * Gets the update interval for deciding whether to update a gallery.
     * 
     * @return the update interval
     */
    public UpdateInterval getUpdateInterval() {
        return updateInterval;
    }

    /**
     * Gets all directories which contain archived galleries.
     * 
     * @return the archive directories
     */
    public List<Path> getArchiveDirectories() {
        return archiveDirectories;
    }

    /**
     * Gets the writable directory which contains archived galleries.
     * 
     * @return the writable archive directory
     */
    public Path getWritableArchiveDirectory() {
        return archiveDirectories.get(0);
    }

    /**
     * Gets the directory which contains the success files.
     * 
     * @return the success directory
     */
    public Path getSuccessDirectory() {
        return successDirectory;
    }

    /**
     * Gets the user agent to use for network requests.
     * 
     * @return the user agent
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * Gets the success file ID to use for the current run.
     * 
     * @return the success file ID
     */
    public String getSuccessFileId() {
        return successFileId;
    }

    /**
     * Whether metadata archival should be done.
     * 
     * @return true if it should be done, false otherwise
     */
    public boolean isMetadataActive() {
        return !elementsToSkip.contains(SKIP_ELEMENTS_METADATA);
    }

    /**
     * Whether page archival should be done.
     * 
     * @return true if it should be done, false otherwise
     */
    public boolean isPageActive() {
        return !elementsToSkip.contains(SKIP_ELEMENTS_PAGE);
    }

    /**
     * Whether image list archival should be done.
     * 
     * @return true if it should be done, false otherwise
     */
    public boolean isImageListActive() {
        return !elementsToSkip.contains(SKIP_ELEMENTS_IMAGELIST);
    }

    /**
     * Whether expunge log archival should be done.
     * 
     * @return true if it should be done, false otherwise
     */
    public boolean isExpungeLogActive() {
        return !elementsToSkip.contains(SKIP_ELEMENTS_EXPUNGELOG);
    }

    /**
     * Whether thumbnail archival should be done.
     * 
     * @return true if it should be done, false otherwise
     */
    public boolean isThumbnailActive() {
        return !elementsToSkip.contains(SKIP_ELEMENTS_THUMBNAIL);
    }

    /**
     * Whether torrent archival should be done.
     * 
     * @return true if it should be done, false otherwise
     */
    public boolean isTorrentActive() {
        return !elementsToSkip.contains(SKIP_ELEMENTS_TORRENT);
    }

    /**
     * Whether ZIP archival should be done.
     * 
     * @return true if it should be done, false otherwise
     */
    public boolean isZipActive() {
        return !elementsToSkip.contains(SKIP_ELEMENTS_ZIP);
    }

    /**
     * Whether catchup download mode is enabled or not.
     * 
     * @return <code>true</code> if enabled, <code>false</code> otherwise
     */
    public boolean isCatchup() {
        return catchup;
    }

    /**
     * Creates a logging level based on user-input.
     * 
     * @return the logging level to use for the application
     */
    public Level getLoggingVerbosity() {
        Level level;
        switch (verbosity.length) {
            case 0:
                level = Level.OFF;
                break;
            case 1:
                level = Level.FATAL;
                break;
            case 2:
                level = Level.ERROR;
                break;
            case 3:
                level = Level.WARN;
                break;
            case 4:
                level = Level.INFO;
                break;
            case 5:
                level = Level.DEBUG;
                break;
            case 6:
                level = Level.TRACE;
                break;
            default:
                level = Level.ALL;
                break;
        }

        return level;
    }
}
