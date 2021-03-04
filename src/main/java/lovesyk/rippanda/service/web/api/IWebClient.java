package lovesyk.rippanda.service.web.api;

import java.nio.file.Path;
import java.util.Map;

import org.jsoup.nodes.Document;

import com.google.gson.JsonObject;

import lovesyk.rippanda.exception.RipPandaException;

/**
 * The web client to access remote resources.
 */
public interface IWebClient {
    /**
     * Downloads the file from the given URL by using a file writer.
     * 
     * @param url    the URL to download
     * @param writer the writer
     * @return flag returned by the writer
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    boolean downloadFile(String url, ArchivableElementWriter writer) throws RipPandaException, InterruptedException;

    /**
     * Loads a remote URL resulting in a HTML response.
     * <p>
     * Delays specified by application settings will be honored.
     * 
     * @param url the remote URL
     * @return the parsed HTML response, never <code>null</code>
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    Document loadDocument(String url) throws RipPandaException, InterruptedException;

    /**
     * Loads the metadata for the specified galleries by querying the remote API.
     * <p>
     * Delays specified by application settings will be honored.
     * 
     * @param idTokenPairs up to 25 pairs of ID and token
     * @return the API response, never <code>null</code>
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    JsonObject loadMetadata(Map<Integer, String> idTokenPairs) throws RipPandaException, InterruptedException;

    /**
     * Loads the HTML for a gallery page.
     * <p>
     * Delays specified by application settings will be honored.
     * 
     * @param id    the gallery ID
     * @param token the gallery token
     * @return the parsed HTML response, never <code>null</code>
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    Document loadPage(int id, String token) throws RipPandaException, InterruptedException;

    /**
     * Loads the HTML for torrent page of a gallery.
     * <p>
     * Delays specified by application settings will be honored.
     * 
     * @param id    the gallery ID
     * @param token the gallery token
     * @return the parsed HTML response, never <code>null</code>
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    Document loadTorrentPage(int id, String token) throws RipPandaException, InterruptedException;

    /**
     * Loads the HTML for a archive preparation page.
     * <p>
     * Delays specified by application settings will be honored.
     * 
     * @param id          the gallery ID
     * @param token       the gallery token
     * @param archiverKey the gallery archiver key as received from the API
     * @return the parsed HTML response, never <code>null</code>
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    Document loadArchivePreparationPage(int id, String token, String archiverKey) throws RipPandaException, InterruptedException;

    /**
     * Loads a local HTML file parsing it into a document.
     * 
     * @param path the path to the file to load
     * @return the parsed HTML file, never <code>null</code>
     * @throws RipPandaException on failure
     */
    Document loadDocument(Path path) throws RipPandaException;
}
