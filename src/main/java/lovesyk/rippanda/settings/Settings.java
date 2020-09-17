package lovesyk.rippanda.settings;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.inject.Singleton;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lovesyk.rippanda.exception.RipPandaException;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The application settings that represent the command line arguments.
 */
@Singleton
public class Settings implements Callable<Integer> {
    private static final String DESCRIPTION_DEFAULT_FRAGMENT = " (default: ${DEFAULT-VALUE})";

    private static final Logger LOGGER = LogManager.getLogger(Settings.class);

    @Parameters(index = "0", description = "Operation mode: ${COMPLETION-CANDIDATES}" + DESCRIPTION_DEFAULT_FRAGMENT, defaultValue = "DOWNLOAD")
    private OperationMode operationMode;

    @Option(names = { "-c",
            "--cookies" }, description = "Log-in / perk cookies in key=value pairs separated by ;", required = true, parameterConsumer = CookiesConsumer.class)
    private Map<String, String> cookies;
    @Option(names = { "-u", "--url" }, description = "Base URL to use for web requests or a more specific search URL if in download mode", required = true)
    private URI uri;
    @Option(names = { "-d", "--delay" }, description = "Minimum delay between web request in ISO-8601 time format"
            + DESCRIPTION_DEFAULT_FRAGMENT, defaultValue = "1S", converter = DelayConverter.class)
    private Duration requestDelay;
    @Option(names = { "-a", "--archive-dir" }, description = "Directory containing archived galleries" + DESCRIPTION_DEFAULT_FRAGMENT, defaultValue = ".")
    private Path archiveDirectory;
    @Option(names = { "-s", "--success-dir" }, description = "Directory containing success files" + DESCRIPTION_DEFAULT_FRAGMENT, defaultValue = ".")
    private Path successDirectory;

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

        int exitCode = new CommandLine(this).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
        if (exitCode != 0) {
            throw new RipPandaException("Invalid command line arguments.");
        }

        initCookies();
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
     * Prints the settings the application will be run with.
     */
    private void print() {
        LOGGER.info("Using the following configuration:");
        LOGGER.info("Operation mode: {}", getOperationMode());
        LOGGER.info("URL: {}", getUri());
        LOGGER.info("Request delay: {}", getRequestDelay());
        LOGGER.info("Archive directory: {}", getArchiveDirectory());
        LOGGER.info("Success directory: {}", getSuccessDirectory());
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
        return cookies;
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
     * Gets the directory which contains archived galleries.
     * 
     * @return the archive directory
     */
    public Path getArchiveDirectory() {
        return archiveDirectory;
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
}
