package lovesyk.rippanda.service.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.service.web.api.ArchivableElementWriter;
import lovesyk.rippanda.service.web.api.DownloadableFile;

/**
 * The response factory for the web client.
 */
public class WebClientResponseFactory {
    private static final Logger LOGGER = LogManager.getLogger(WebClientResponseFactory.class);

    private static final Gson GSON = new GsonBuilder().create();
    private static final String CONTENT_DISPOSITION_HEADER_NAME = "Content-Disposition";

    /**
     * Parses the response received from the API "gdata" call.
     * 
     * @param response the HTTP response
     * @return the JSON object, never <code>null</code>
     * @throws RipPandaException on failure
     */
    public JsonObject parseLoadMetadataResponse(CloseableHttpResponse response) throws RipPandaException {
        InputStream entityContent = tryGetContent(tryGetEntity(response));

        JsonElement responseElement;
        try (Reader reader = new InputStreamReader(entityContent)) {
            responseElement = GSON.fromJson(reader, JsonElement.class);
        } catch (IOException | JsonParseException e) {
            throw new RipPandaException("Cannot load metadata.", e);
        }

        if (responseElement == null || !responseElement.isJsonObject()) {
            throw new RipPandaException("Unexpected JSON.");
        }

        JsonObject gdata = responseElement.getAsJsonObject();
        return gdata;
    }

    /**
     * Try to get the entity of a HTTP response.
     * 
     * @param response the HTTP response
     * @return the HTTP entity, never <code>null</code>
     * @throws RipPandaException on failure
     */
    private HttpEntity tryGetEntity(CloseableHttpResponse response) throws RipPandaException {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            throw new RipPandaException("Expected entity not found in HTTP response.");
        }

        return entity;
    }

    /**
     * Try to get the content stream of a HTTP response entity.
     * 
     * @param entity the HTTP response entity
     * @return the content stream, never <code>null</code>
     * @throws RipPandaException on failure
     */
    private InputStream tryGetContent(HttpEntity entity) throws RipPandaException {
        try {
            return entity.getContent();
        } catch (IOException e) {
            throw new RipPandaException("Failed retrieving entity content of HTTP response.");
        }
    }

    /**
     * Downloads the file received from the remote endpoint to disk using the given
     * writer.
     * 
     * @param request  the HTTP request
     * @param response the HTTP response
     * @param writer   the write to use for saving to disk
     * @return flag as returned by the writer
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    public boolean downloadFile(ClassicHttpRequest request, CloseableHttpResponse response, ArchivableElementWriter writer)
            throws RipPandaException, InterruptedException {
        HttpEntity entity = tryGetEntity(response);
        InputStream stream = tryGetContent(entity);
        ContentType contentType = ContentType.parseLenient(entity.getContentType());
        URI responseUri = findResponseUri(request, response);
        String filename = getFilename(responseUri, response);

        DownloadableFile downloadableFile = new DownloadableFile(stream, filename, contentType.getMimeType());
        return writer.write(downloadableFile);
    }

    /**
     * Gets the filename of a HTTP response from headers with a fallback to the
     * response URI if not found in headers.
     * 
     * @param responseUri the response URI
     * @param response    the HTTP response
     * @return the filename
     */
    private String getFilename(URI responseUri, CloseableHttpResponse response) {
        LOGGER.trace("Trying to find filename in HTTP headers...");
        String filename = null;

        Header contentDispositionHeader = response.getFirstHeader(CONTENT_DISPOSITION_HEADER_NAME);
        if (contentDispositionHeader != null) {
            Optional<String> filenameIso = Arrays.stream(MessageSupport.parse(contentDispositionHeader)).map(element -> element.getParameterByName("filename"))
                    .filter(Objects::nonNull).map(NameValuePair::getValue).findFirst();
            if (filenameIso.isPresent()) {
                filename = new String(filenameIso.get().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                filename = Parser.unescapeEntities(filename, false);
            }
        }

        if (filename == null) {
            LOGGER.trace("No filename found in HTTP headers, inferring it from the URL...");
            filename = FilenameUtils.getName(responseUri.getPath());
        }

        LOGGER.trace("Using filename \"{}\".", filename);
        return filename;
    }

    /**
     * Parses a HTTP response into a HTML document.
     * 
     * @param request  the HTTP request
     * @param response the HTTP response
     * @return the HTML document, never <code>null</code>
     * @throws RipPandaException on failure
     */
    public Document parseToDocument(ClassicHttpRequest request, CloseableHttpResponse response) throws RipPandaException {
        HttpEntity entity = tryGetEntity(response);
        InputStream stream = tryGetContent(entity);
        ContentType contentType = ContentType.parseLenient(entity.getContentType());
        URI responseUri = findResponseUri(request, response);

        Document document;
        try {
            document = Jsoup.parse(stream, contentType.getCharset().name(), responseUri.toString());
        } catch (IOException e) {
            throw new RipPandaException("Failed parsing HTML document.", e);
        }

        return document;
    }

    /**
     * Finds the response URI based on response headers with a fallback to the
     * request URI if not present in headers.
     * 
     * @param request  the HTTP request
     * @param response the HTTP response
     * @return the response URI
     */
    private URI findResponseUri(ClassicHttpRequest request, CloseableHttpResponse response) {
        Header locationHeader = response.getFirstHeader(HttpHeaders.LOCATION);
        URI responseUri = null;
        if (locationHeader != null) {
            try {
                responseUri = new URI(locationHeader.getValue());
            } catch (URISyntaxException e) {
                LOGGER.warn("Redirect URL is not valid.", e);
            }
        }

        if (responseUri == null) {
            try {
                responseUri = request.getUri();
            } catch (URISyntaxException e) {
                LOGGER.warn("Could not parse request URI.", e);
            }
        }

        return responseUri;
    }
}
