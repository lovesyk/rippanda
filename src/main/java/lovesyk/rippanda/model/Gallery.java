package lovesyk.rippanda.model;

import java.nio.file.Path;

import com.google.gson.JsonObject;

/**
 * Model of an unique gallery.
 */
public class Gallery {
    private final int id;
    private final String token;
    private final Path dir;

    private JsonObject metadata;

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
     * @param metadata the gallery metadata
     */
    public void setMetadata(JsonObject metadata) {
        this.metadata = metadata;
    }
}
