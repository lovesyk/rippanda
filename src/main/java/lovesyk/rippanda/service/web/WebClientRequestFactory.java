package lovesyk.rippanda.service.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.settings.Settings;

/**
 * The request factory for the web client.
 */
@ApplicationScoped
public class WebClientRequestFactory {
    private static final Gson GSON = new GsonBuilder().create();

    private Settings settings;

    private URI baseUri;

    /**
     * Constructs a new request factory instance.
     * 
     * @param settings the application settings
     */
    @Inject
    public WebClientRequestFactory(Settings settings) {
        this.settings = settings;
    }

    /**
     * Initializes the request factory instance.
     */
    @PostConstruct
    public void init() {
        try {
            initBaseUri();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Could not initialize base URLs.", e);
        }
    }

    /**
     * Initializes the base URI based on application settings.
     * 
     * @throws URISyntaxException should never be thrown assuming settings were
     *                            initialized properly
     */
    private void initBaseUri() throws URISyntaxException {
        URIBuilder builder = new URIBuilder(getSettings().getUri()).setPathSegments().removeQuery();
        baseUri = builder.build();
    }

    /**
     * Creates a "gdata" API call to query gallery metadata.
     * 
     * @param idTokenPairs pairs of gallery ID and tokens
     * @return the HTTP request, never <code>null</code>
     * @throws RipPandaException on failure
     */
    public HttpPost createLoadMetadataRequest(Map<Integer, String> idTokenPairs) throws RipPandaException {
        if (idTokenPairs.size() > 25) {
            throw new RipPandaException("API does not allow to query more than 25 galleries at once.");
        }

        URIBuilder builder = new URIBuilder(getBaseUri()).setPath("api.php");
        URI uri;
        try {
            uri = builder.build();
        } catch (URISyntaxException e) {
            throw new RipPandaException("Invalid URL.", e);
        }

        List<List<Object>> gidlist = idTokenPairs.entrySet().stream().map(WebClientRequestFactory::toGid)
                .collect(Collectors.toList());
        ApiRequest gdataRequest = new ApiRequest();
        gdataRequest.setMethod("gdata");
        gdataRequest.setGidlist(gidlist);
        gdataRequest.setNamespace(1);

        String metadataRequestString;
        try {
            metadataRequestString = GSON.toJson(gdataRequest);
        } catch (JsonParseException e) {
            throw new RuntimeException("Could not map request entity.", e);
        }

        StringEntity entity = new StringEntity(metadataRequestString, ContentType.APPLICATION_JSON);

        HttpPost request = new HttpPost(uri);
        request.setEntity(entity);

        return request;
    }

    /**
     * Converts a pair of gallery ID and token to the format required by the API.
     * 
     * @param idTokenPair pair of gallery ID and token
     * @return the gid list required by the API
     */
    private static List<Object> toGid(Entry<Integer, String> idTokenPair) {
        List<Object> gid = new ArrayList<>(2);
        gid.add(idTokenPair.getKey());
        gid.add(idTokenPair.getValue());
        return gid;
    }

    /**
     * Creates a request to load a gallery page.
     * 
     * @param id    the gallery ID
     * @param token the gallery token
     * @return the HTTP request, never <code>null</code>
     * @throws RipPandaException on failure
     */
    public HttpGet createLoadPageRequest(int id, String token) throws RipPandaException {
        URIBuilder builder = new URIBuilder(getBaseUri()).setPathSegments("g", String.valueOf(id), token);
        URI uri;
        try {
            uri = builder.build();
        } catch (URISyntaxException e) {
            throw new RipPandaException("Invalid page URL.", e);
        }

        HttpGet request = new HttpGet(uri);

        return request;
    }

    /**
     * Creates a request to load a gallery MPV page.
     * 
     * @param id    the gallery ID
     * @param token the gallery token
     * @return the HTTP request, never <code>null</code>
     * @throws RipPandaException on failure
     */
    public HttpGet createLoadMpvPageRequest(int id, String token) throws RipPandaException {
        URIBuilder builder = new URIBuilder(getBaseUri()).setPathSegments("mpv", String.valueOf(id), token);
        URI uri;
        try {
            uri = builder.build();
        } catch (URISyntaxException e) {
            throw new RipPandaException("Invalid page URL.", e);
        }

        HttpGet request = new HttpGet(uri);

        return request;
    }

    /**
     * Creates a request to load the torrent page for a specific gallery.
     * 
     * @param id    the gallery ID
     * @param token the gallery token
     * @return the HTTP request, never <code>null</code>
     * @throws RipPandaException on failure
     */
    public HttpGet createLoadTorrentPageRequest(int id, String token) throws RipPandaException {
        URIBuilder builder = new URIBuilder(getBaseUri()).setPath("gallerytorrents.php")
                .addParameter("gid", String.valueOf(id)).addParameter("t", token);
        URI uri;
        try {
            uri = builder.build();
        } catch (URISyntaxException e) {
            throw new RipPandaException("Invalid torrent URL.", e);
        }

        HttpGet request = new HttpGet(uri);

        return request;
    }

    /**
     * Creates a request to load the archive preparation page.
     * 
     * @param archiverUrl the gallery archiver URL as found on the gallery page
     * @return the HTTP request, never <code>null</code>
     * @throws RipPandaException on failure
     */
    public HttpPost createLoadArchivePreparationPageRequest(String archiverUrl)
            throws RipPandaException {
        URI uri;
        try {
            uri = new URI(archiverUrl);
        } catch (URISyntaxException e) {
            throw new RipPandaException("Invalid archive preparation page URL.", e);
        }

        NameValuePair dltypeEntityParameter = new BasicNameValuePair("dltype", "org");
        NameValuePair dlcheckEntityParameter = new BasicNameValuePair("dlcheck", "Download+Original+Archive");
        List<NameValuePair> entityParameters = Arrays.asList(dltypeEntityParameter, dlcheckEntityParameter);
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(entityParameters, StandardCharsets.UTF_8);

        HttpPost request = new HttpPost(uri);
        request.setEntity(entity);

        return request;
    }

    /**
     * Creates a request to load a downloadable file based on its URL.
     * 
     * @param url the URL
     * @return the HTTP request, never <code>null</code>
     * @throws RipPandaException on failure
     */
    public HttpGet createLoadDownloadableFileRequest(String url) throws RipPandaException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new RipPandaException("Invalid downloadable file URL.", e);
        }

        HttpGet request = new HttpGet(uri);

        return request;
    }

    /**
     * Create a request to load the search result page based on application settings
     * and the specified page.
     * 
     * @param pageIndex the page index, not page number
     * @return the HTTP request, never <code>null</code>
     * @throws RipPandaException on failure
     */
    public HttpGet createLoadSearchResultPageRequest(int pageIndex) throws RipPandaException {
        URIBuilder builder = new URIBuilder(getSettings().getUri()).setParameter("page", String.valueOf(pageIndex));
        URI uri;
        try {
            uri = builder.build();
        } catch (URISyntaxException e) {
            throw new RipPandaException("Invalid search result page URL.", e);
        }

        HttpGet request = new HttpGet(uri);

        return request;
    }

    /**
     * Creates a request to load a gallery expunge log page.
     * 
     * @param id    the gallery ID
     * @param token the gallery token
     * @return the HTTP request, never <code>null</code>
     * @throws RipPandaException on failure
     */
    public HttpGet createLoadExpungeLogPageRequest(int id, String token) throws RipPandaException {
        URIBuilder builder = new URIBuilder(getBaseUri()).setPathSegments("g", String.valueOf(id), token)
                .setParameter("act", "expunge");
        URI uri;
        try {
            uri = builder.build();
        } catch (URISyntaxException e) {
            throw new RipPandaException("Invalid page URL.", e);
        }

        HttpGet request = new HttpGet(uri);

        return request;
    }

    /**
     * Creates a request to load a HTML document based on its URL.
     * 
     * @param url the URL
     * @return the HTTP request, never <code>null</code>
     * @throws RipPandaException on failure
     */
    public HttpGet createLoadDocumentRequest(String url) throws RipPandaException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new RipPandaException("Invalid URL.", e);
        }

        HttpGet request = new HttpGet(uri);

        return request;
    }

    /**
     * Gets the application settings.
     * 
     * @return the settings
     */
    protected Settings getSettings() {
        return settings;
    }

    /**
     * Gets the request base URI.
     * 
     * @return the base URI
     */
    public URI getBaseUri() {
        return baseUri;
    }
}
