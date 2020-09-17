package lovesyk.rippanda.service.web.api;

import java.io.InputStream;

/**
 * Represents a remote file that can be downloaded for archival purposes.
 */
public class DownloadableFile {
    private final InputStream stream;
    private final String name;
    private final String mimeType;

    /**
     * Constructs a new downloadable file instance.
     * 
     * @param stream   the file stream
     * @param name     the filename
     * @param mimeType the content mime type
     */
    public DownloadableFile(InputStream stream, String name, String mimeType) {
        this.stream = stream;
        this.name = name;
        this.mimeType = mimeType;
    }

    /**
     * Gets the file stream.
     * 
     * @return the file stream
     */
    public InputStream getStream() {
        return stream;
    }

    /**
     * Gets the filename.
     * 
     * @return the filename
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the content mime type.
     * 
     * @return the mime type
     */
    public String getMimeType() {
        return mimeType;
    }
}
