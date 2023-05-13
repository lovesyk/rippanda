package lovesyk.rippanda.service.archival;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.helper.ProgressRecorder;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.model.MetadataState;
import lovesyk.rippanda.service.archival.api.IArchivalService;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.OperationMode;
import lovesyk.rippanda.settings.Settings;
import lovesyk.rippanda.settings.UpdateInterval;

/**
 * The service responsible for the updating of already archived galleries.
 */
@ApplicationScoped
public class UpdateModeArchivalService extends AbstractArchivalService implements IArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(UpdateModeArchivalService.class);

    private static final String METADATA_FILENAME = "api-metadata.json";

    private static final Gson GSON = new Gson();

    private IWebClient webClient;

    /**
     * Constructs a new search result archival service instance.
     * 
     * @param settings                the application settings
     * @param webClient               the web client
     * @param elementArchivalServices the archival services which should be invoked
     *                                for archiving elements
     */
    @Inject
    public UpdateModeArchivalService(Settings settings, IWebClient webClient, Instance<IElementArchivalService> elementArchivalServices) {
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
            LOGGER.info("Activating update mode.");
            run();
        }
    }

    /**
     * Checks if the update mode should be executed based in application settings.
     * 
     * @return <code>true</code> if update mode is enabled, <code>false</false>
     *         otherwise.
     */
    private boolean isRequired() {
        return getSettings().getOperationMode() == OperationMode.UPDATE;
    }

    /**
     * Runs the update archival service.
     * <p>
     * Processing will be aborted on over 3 failures in a row.
     * 
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    public void run() throws RipPandaException, InterruptedException {
        initDirs();
        initSuccessIds();
        ProgressRecorder progress = new ProgressRecorder();

        int failureCount = 0;
        int maxFailureCount = 3;

        try (Stream<Path> stream = Files.walk(getSettings().getWritableArchiveDirectory()).filter(Files::isDirectory)) {
            for (Path directory : (Iterable<Path>) stream::iterator) {
                LOGGER.trace("Processing directory \"{}\"...", directory);
                Gallery gallery = parseGallery(directory);
                if (gallery == null) {
                    LOGGER.debug("Directory does not appear to contain a gallery: \"{}\"", directory);
                } else {
                    LOGGER.info(
                            "Processing gallery with ID \"{}\" and token \"{}\" in directory: \"{}\".{}", gallery.getId(), gallery.getToken(),
                            gallery.getDir(), progress.toProgressString(getSuccessIdCount()));
                    try {
                        process(gallery);
                        failureCount = 0;
                    } catch (RipPandaException e) {
                        LOGGER.warn("Failed processing gallery.", e);
                        ++failureCount;
                        if (failureCount > maxFailureCount) {
                            throw new RipPandaException(String.format("Encountered more than %s failures successively. Aborting...", maxFailureCount));
                        }
                        LOGGER.warn("Waiting 10 seconds before continuing...", e);
                        Thread.sleep(1000 * 10);
                    }
                    progress.saveMilestone();
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Could not traverse the given archive directory.", e);
        }

        deleteSuccessTempFile();
    }

    /**
     * Processes a single gallery of a search result.
     * <p>
     * Element archiving failures will be retried up to 3 times and gallery
     * processing will be finished in any case.
     * 
     * @param gallery the gallery to process
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void process(Gallery gallery) throws RipPandaException, InterruptedException {
        int id = gallery.getId();
        if (!isInSuccessIds(id)) {
            addTempSuccessId(id);
        }

        RipPandaException lastException = null;
        for (IElementArchivalService archivingService : getArchivingServiceList()) {
            for (int remainingTries = 3; remainingTries > 0;) {
                try {
                    archivingService.process(gallery);
                    break;
                } catch (RipPandaException e) {
                    --remainingTries;
                    LOGGER.warn("Archiving element failed, {} tries remain.", remainingTries, e);
                    lastException = e;
                    if (remainingTries > 0) {
                        LOGGER.warn("Waiting 10 seconds before retrying...");
                        Thread.sleep(1000 * 10);
                    }
                }
            }
        }
        if (lastException != null) {
            throw new RipPandaException("Gallery processing finished with at least one failure.", lastException);
        }

        if (!isInSuccessIds(id)) {
            updateSuccessIds();
            if (!isInSuccessIds(id)) {
                addSuccessId(id);
            }
        }
    }

    /**
     * Parses a single gallery from the metadata file in the same directory.
     * 
     * @param directory the parent directory of the potential gallery
     * @return the parsed gallery or <code>null</code> if no gallery is to be
     *         processed for this directory
     * @throws RipPandaException on failure
     */
    private Gallery parseGallery(Path directory) throws RipPandaException {
        Gallery gallery = null;

        Path metadataFile = directory.resolve(METADATA_FILENAME);
        if (Files.exists(metadataFile)) {
            JsonObject metadata;
            try (BufferedReader reader = Files.newBufferedReader(metadataFile, StandardCharsets.UTF_8)) {
                metadata = GSON.fromJson(reader, JsonObject.class);
            } catch (IOException | JsonSyntaxException e) {
                throw new RipPandaException("Unexpected JSON.", e);
            }

            int id = parseId(metadata);
            String token = parseToken(metadata);
            Instant posted = parsePostedInstant(metadata);
            gallery = new Gallery(id, token, directory);
            gallery.setMetadata(metadata, MetadataState.DISK);

            Instant threshold = calculateUpdateThreshold(posted);
            gallery.setUpdateThreshold(threshold);
        }

        return gallery;
    }

    /**
     * Parses the gallery's posted instant from the API metadata.
     * 
     * @param metadata the API metadata
     * @return the parsed posted instant
     * @throws RipPandaException on failure
     */
    private Instant parsePostedInstant(JsonObject metadata) throws RipPandaException {
        JsonElement postedElement = metadata.get("posted");
        if (postedElement == null || !postedElement.isJsonPrimitive()) {
            throw new RipPandaException("Unexpected JSON.");
        }
        long postedMilli;
        try {
            postedMilli = postedElement.getAsLong();
        } catch (NumberFormatException e) {
            throw new RipPandaException("Unexpected JSON.");
        }
        return Instant.ofEpochSecond(postedMilli);
    }

    /**
     * Calculates the gallery update threshold based on the configured min / max
     * intervals and gallery file attributes.
     * 
     * @param attributes the gallery file attributes
     * @return the update threshold for the gallery
     */
    private Instant calculateUpdateThreshold(Instant posted) {
        Instant now = Instant.now();
        UpdateInterval updateInterval = getSettings().getUpdateInterval();
        Duration minThreshold = updateInterval.getMinThreshold();
        Duration minDuration = updateInterval.getMinDuration();
        Duration maxThreshold = updateInterval.getMaxThreshold();
        Duration maxDuration = updateInterval.getMaxDuration();

        Duration postedDuration = Duration.between(posted, now);
        double ratio;
        if (postedDuration.compareTo(minThreshold) < 0) {
            ratio = 0;
        } else if (postedDuration.compareTo(maxThreshold) > 0) {
            ratio = 1;
        } else {
            Duration position = postedDuration.minus(minThreshold);
            Duration thresholdDifference = maxThreshold.minus(minThreshold);
            ratio = (double) position.getSeconds() / thresholdDifference.getSeconds();
        }

        long millisToAdd = Math.round((maxDuration.toMillis() - minDuration.toMillis()) * ratio);
        Duration updateDuration = minDuration.plusMillis(millisToAdd);
        Instant threshold = now.minus(updateDuration);

        LOGGER.trace("As the gallery was posted on {} the update threshold is: {}.", posted, threshold);
        return threshold;
    }

    /**
     * Parses the gallery ID from the API metadata.
     * 
     * @param metadata the API metadata
     * @return the parsed ID
     * @throws RipPandaException on failure
     */
    private int parseId(JsonObject metadata) throws RipPandaException {
        JsonElement gidElement = metadata.get("gid");
        if (gidElement == null || !gidElement.isJsonPrimitive()) {
            throw new RipPandaException("Unexpected JSON.");
        }
        int id;
        try {
            id = gidElement.getAsInt();
        } catch (NumberFormatException e) {
            throw new RipPandaException("Unexpected JSON.", e);
        }
        return id;
    }

    /**
     * Parses the gallery token from the API metadata.
     * 
     * @param metadata the API metadata
     * @return the parsed token
     * @throws RipPandaException on failure
     */
    private String parseToken(JsonObject metadata) throws RipPandaException {
        JsonElement tokenElement = metadata.get("token");
        if (tokenElement == null || !tokenElement.isJsonPrimitive()) {
            throw new RipPandaException("Unexpected JSON.");
        }
        return tokenElement.getAsString();
    }

    /**
     * Gets the network web client.
     * 
     * @return the web client
     */
    protected IWebClient getWebClient() {
        return webClient;
    }
}
