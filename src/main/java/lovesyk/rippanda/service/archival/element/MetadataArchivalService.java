package lovesyk.rippanda.service.archival.element;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.OperationMode;
import lovesyk.rippanda.settings.Settings;

/**
 * The archival service for gallery metadata elements.
 */
public class MetadataArchivalService extends AbstractElementArchivalService implements IElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(MetadataArchivalService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILENAME = "api-metadata.json";

    /**
     * Constructs a new archival service instance.
     * 
     * @param settings  the application settings
     * @param webClient the network web client
     */
    @Inject
    public MetadataArchivalService(Settings settings, IWebClient webClient) {
        super(settings, webClient);
    }

    /**
     * Ensures the metadata is loaded for the given gallery, loading it if required.
     * 
     * @param gallery the gallery to check
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    public void ensureLoaded(Gallery gallery) throws RipPandaException, InterruptedException {
        if (!gallery.isMetadataLoaded()) {
            load(gallery);
        }
    }

    /**
     * Loads gallery metadata by querying the API.
     * 
     * @param gallery the gallery to load metadata for
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    public void load(Gallery gallery) throws RipPandaException, InterruptedException {
        LOGGER.info("Parsing metadata...");

        Map<Integer, String> idTokenPairs = Collections.singletonMap(gallery.getId(), gallery.getToken());
        JsonObject gdata = getWebClient().loadMetadata(idTokenPairs);

        JsonElement gmetadataElement = gdata.get("gmetadata");
        if (gmetadataElement != null && gmetadataElement.isJsonArray()) {
            JsonArray gmetadataArray = gmetadataElement.getAsJsonArray();
            if (gmetadataArray.size() == 1) {
                JsonElement metadataElement = gmetadataArray.get(0);
                if (metadataElement != null && metadataElement.isJsonObject()) {
                    JsonObject metadata = metadataElement.getAsJsonObject();
                    gallery.setMetadata(metadata);
                    return;
                }
            }
        }

        throw new RipPandaException("Unexpected metadata.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Gallery gallery) throws RipPandaException, InterruptedException {
        if (isRequired(gallery)) {
            LOGGER.info("Metadata needs to be archived.");
            save(gallery);
        } else {
            LOGGER.info("Metadata does not need to be archived.");
        }
    }

    /**
     * Checks if metadata should be saved or not.
     * 
     * @return <code>true</code> if metadata archival is active but no recent
     *         metadata has been found on disk, <code>false</false> otherwise.
     */
    private boolean isRequired(Gallery gallery) {
        boolean isRequired = getSettings().isMetadataActive();

        if (isRequired) {
            Path metadataFile = gallery.getDir().resolve(FILENAME);
            isRequired = getSettings().getOperationMode() == OperationMode.UPDATE || !Files.isRegularFile(metadataFile);
        }

        return isRequired;
    }

    /**
     * Saves the thumbnail for the given gallery to disk.
     * 
     * @param gallery the gallery
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    private void save(Gallery gallery) throws RipPandaException, InterruptedException {
        ensureLoaded(gallery);

        LOGGER.info("Saving metadata...");
        initDir(gallery.getDir());
        save(file -> write(gallery.getMetadata(), file), gallery.getDir(), FILENAME);
    }

    /**
     * Writes a JSON object to file.
     * 
     * @param json the JSON
     * @param file the file to write to
     * @throws IOException on failure
     */
    private void write(JsonObject json, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            GSON.toJson(json, writer);
        }
    }
}
