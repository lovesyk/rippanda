package lovesyk.rippanda.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lovesyk.rippanda.exception.RipPandaException;

/**
 * Model of an unique gallery.
 */
public class Gallery {
    private final int id;
    private final String token;
    private final Path dir;

    private JsonObject metadata;
    private MetadataState metadataState;
    private List<Path> files;
    private Instant updateThreshold;
    private boolean expunged;

    /**
     * Constructs a new gallery.
     * 
     * @param id    the gallery ID
     * @param token the gallery token
     * @param dir   the local file system directory designated for the gallery
     */
    public Gallery(int id, String token, Path dir) {
        this.id = id;
        this.token = token;
        this.dir = dir;
    }

    /**
     * Gets the gallery ID.
     * 
     * @return the gallery ID
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the gallery token.
     * 
     * @return the gallery token
     */
    public String getToken() {
        return token;
    }

    /**
     * Gets the local file system directory designated for the gallery.
     * 
     * @return the local file system directory designated for the gallery
     */
    public Path getDir() {
        return dir;
    }

    /**
     * Checks if the gallery's metadata is loaded.
     * 
     * @return <code>true</code> if the metadata is loaded, <code>false</code>
     *         otherwise
     */
    public boolean isMetadataLoaded() {
        return metadata != null;
    }

    /**
     * Gets the gallery metadata.
     * 
     * @return the gallery metadata
     */
    public JsonObject getMetadata() {
        return metadata;
    }

    /**
     * Sets the gallery metadata.
     * <p>
     * The validity of the metadata is not checked at this point.
     * 
     * @param metadata      the gallery metadata
     * @param metadataState the current state of the given metadata
     * @throws RipPandaException on failure
     */
    public void setMetadata(JsonObject metadata, MetadataState metadataState) throws RipPandaException {
        this.metadata = metadata;
        this.metadataState = metadataState;

        setExpunged();
    }

    /**
     * Gets the state of the currently loaded metadata.
     * 
     * @return the state of the metadata
     */
    public MetadataState getMetadataState() {
        return metadataState;
    }

    /**
     * Gets a list of all files in the gallery's directory.
     * 
     * @return all files in the gallery's directory
     */
    public List<Path> getFiles() {
        return files;
    }

    /**
     * Sets the list of all files in the gallery's directory.
     * 
     * @param files all files in the gallery's directory
     */
    public void setFiles(List<Path> files) {
        this.files = files;
    }

    /**
     * Checks if the gallery's file list is loaded.
     * 
     * @return <code>true</code> if the file list is loaded, <code>false</code>
     *         otherwise
     */
    public boolean isFilesLoaded() {
        return files != null;
    }

    /**
     * Gets the date threshold of when to update gallery elements.
     * 
     * @return the gallery's update threshold
     */
    public Instant getUpdateThreshold() {
        return updateThreshold;
    }

    /**
     * Sets the date threshold of when to update gallery elements.
     * 
     * @param updateThreshold the gallery's update threshold
     */
    public void setUpdateThreshold(Instant updateThreshold) {
        this.updateThreshold = updateThreshold;
    }

    /**
     * Checks if the gallery has been expunged or not.
     * 
     * @return <code>true</code> if the gallery has been expunged, <code>false</code>
     *         otherwise
     */
    public boolean isExpunged() {
        return expunged;
    }

    /**
     * Sets the gallery's expunged state from the API metadata.
     *
     * @throws RipPandaException on failure
     */
    private void setExpunged() throws RipPandaException {
        JsonElement expungedElement = getMetadata().get("expunged");
        if (expungedElement == null || !expungedElement.isJsonPrimitive()) {
            throw new RipPandaException("Unexpected JSON.");
        }
        expunged = expungedElement.getAsBoolean();
    }
}
