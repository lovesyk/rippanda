package lovesyk.rippanda.service.web;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;

import com.google.gson.JsonObject;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.service.web.api.ArchivableElementWriter;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.Settings;

/**
 * Implements a web client for remote resources.
 */
@ApplicationScoped
public class WebClient implements IWebClient {
    private static final Logger LOGGER = LogManager.getLogger(WebClient.class);
    private static final int HTTP_RESPONSE_CODE_SUCCESS = 200;
    private static final int HTTP_RESPONSE_CODE_NOT_FOUND = 404;
    private static final Timeout DEFAULT_TIMEOUT = Timeout.ofSeconds(30);

    private Settings settings;
    private WebClientRequestFactory requestFactory;
    private WebClientResponseFactory responseFactory;

    private Duration requestDelay;
    private LocalDateTime previousRequestTime;
    private HttpClientBuilder httpClientBuilder;

    /**
     * Constructs a new web client instance.
     * 
     * @param settings        the application settings
     * @param requestFactory  the web client request factory
     * @param responseFactory the web client response factory
     */
    @Inject
    public WebClient(Settings settings, WebClientRequestFactory requestFactory,
            WebClientResponseFactory responseFactory) {
        this.settings = settings;
        this.requestFactory = requestFactory;
        this.responseFactory = responseFactory;
    }

    /**
     * Initializes the web client instance.
     */
    @PostConstruct
    public void init() {
        initHttpClientBuilder();
        initRequestDelay();
        initPreviousRequestTime();
    }

    /**
     * Initializes a new HTTP client builder used for network requests.
     */
    private void initHttpClientBuilder() {
        HttpClientBuilder httpClientBuilder = HttpClients.custom();

        String userAgent = getSettings().getUserAgent();
        if (userAgent != null) {
            httpClientBuilder.setUserAgent(getSettings().getUserAgent());
        }

        CookieStore cookieStore = new BasicCookieStore();
        String cookieDomain = getSettings().getUri().getHost();
        // cookies cannot be null as it is a required command line argument
        for (Entry<String, String> cookie : getSettings().getCookies().entrySet()) {
            BasicClientCookie clientCookie = new BasicClientCookie(cookie.getKey(), cookie.getValue());
            clientCookie.setDomain(cookieDomain);
            clientCookie.setAttribute(Cookie.DOMAIN_ATTR, cookieDomain);
            cookieStore.addCookie(clientCookie);
        }
        httpClientBuilder.setDefaultCookieStore(cookieStore);

        RequestConfig requestConfig = RequestConfig.custom() //
                .setConnectTimeout(DEFAULT_TIMEOUT) //
                .setConnectionRequestTimeout(DEFAULT_TIMEOUT) //
                .setResponseTimeout(DEFAULT_TIMEOUT).build();
        httpClientBuilder.setDefaultRequestConfig(requestConfig);

        PoolingHttpClientConnectionManager connectionManager = createConnectionManager();
        httpClientBuilder.setConnectionManager(connectionManager).setConnectionManagerShared(true);

        this.httpClientBuilder = httpClientBuilder;
    }

    /**
     * Created the connection manager to use while considering proxy settings.
     * 
     * @return the optionally proxied connection manager
     */
    private PoolingHttpClientConnectionManager createConnectionManager() {
        ConnectionSocketFactory plainConnectionSocketFactory;
        ConnectionSocketFactory sslConnectionSocketFactory;
        DnsResolver dnsResolver;
        if (getSettings().getProxy() != null) {
            // https://stackoverflow.com/a/25203021
            plainConnectionSocketFactory = new ProxiedPlainConnectionSocketFactory();
            sslConnectionSocketFactory = new ProxiedSSLConnectionSocketFactory(SSLContexts.createSystemDefault());
            dnsResolver = new FakeDnsResolver();
        } else {
            // org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.build()
            plainConnectionSocketFactory = PlainConnectionSocketFactory.getSocketFactory();
            sslConnectionSocketFactory = SSLConnectionSocketFactory.getSocketFactory();
            // default DNS resolver will be set by Apache
            dnsResolver = null;
        }

        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create() //
                .register(URIScheme.HTTP.id, plainConnectionSocketFactory) //
                .register(URIScheme.HTTPS.id, sslConnectionSocketFactory) //
                .build();
        return new PoolingHttpClientConnectionManager(registry, null, null, null, null, dnsResolver, null);
    }

    /**
     * Initializes the network request delay from application settings.
     */
    private void initRequestDelay() {
        requestDelay = getSettings().getRequestDelay();
    }

    /**
     * Initializes the previous network request time.
     */
    private void initPreviousRequestTime() {
        previousRequestTime = LocalDateTime.now();
    }

    /**
     * If required, waits until the configured network request delay is honored.
     * 
     * @throws InterruptedException on interruption
     */
    private void waitToHonorRequestDelay() throws InterruptedException {
        LocalDateTime until = getPreviousRequestTime().plus(getRequestDelay());
        LOGGER.trace("Waiting until {} would be required to honor the request delay.", until);
        sleep(until);
    }

    /**
     * Sleeps until the specified time.
     * <p>
     * If the time has already passed, will not do anything.
     * 
     * @param until the time until when to sleep
     * @throws InterruptedException on interruption
     */
    private void sleep(LocalDateTime until) throws InterruptedException {
        LocalDateTime now = LocalDateTime.now();
        long millisToWait = now.until(until, ChronoUnit.MILLIS);
        if (millisToWait > 0) {
            LOGGER.debug("Waiting for {} seconds...", millisToWait / 1000.0);
            Thread.sleep(millisToWait);
        } else {
            LOGGER.trace("No waiting required at this point.");
        }
    }

    /**
     * Updates the previous network request time to the current time.
     */
    private void updatePreviousRequestTime() {
        previousRequestTime = LocalDateTime.now();
    }

    /**
     * Checks the response code of the HTTP response to make sure the call
     * succeeded.
     * 
     * @param response the HTTP response
     * @throws RipPandaException on failure
     */
    private void checkResponseCode(CloseableHttpResponse response) throws RipPandaException {
        int statusCode = response.getCode();
        if (statusCode != HTTP_RESPONSE_CODE_SUCCESS) {
            throw new RipPandaException(String.format("Expected HTTP response code %s but received %s.",
                    HTTP_RESPONSE_CODE_SUCCESS, statusCode));
        }
    }

    /**
     * Checks the response code of the HTTP response to make sure the call succeeded
     * for the case where code 404 is allowed.
     * 
     * @param response the HTTP response
     * @throws RipPandaException on failure
     */
    private void checkResponseCodeAllowNotFound(CloseableHttpResponse response) throws RipPandaException {
        int statusCode = response.getCode();
        if (statusCode != HTTP_RESPONSE_CODE_SUCCESS && statusCode != HTTP_RESPONSE_CODE_NOT_FOUND) {
            throw new RipPandaException(
                    String.format("Expected HTTP response code %s or  but received %s.", HTTP_RESPONSE_CODE_SUCCESS,
                            HTTP_RESPONSE_CODE_NOT_FOUND, statusCode));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonObject loadMetadata(Map<Integer, String> idTokenPairs) throws RipPandaException, InterruptedException {
        ClassicHttpRequest request = getRequestFactory().createLoadMetadataRequest(idTokenPairs);
        waitToHonorRequestDelay();
        try (ProxyableHttpClient httpClient = createHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                try {
                    checkResponseCode(response);
                    return getResponseFactory().parseLoadMetadataResponse(response);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                    updatePreviousRequestTime();
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Failed executing network request.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document loadPage(int id, String token) throws RipPandaException, InterruptedException {
        ClassicHttpRequest request = getRequestFactory().createLoadPageRequest(id, token);
        waitToHonorRequestDelay();
        try (ProxyableHttpClient httpClient = createHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                try {
                    checkResponseCodeAllowNotFound(response);
                    return getResponseFactory().parseToDocument(request, response);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                    updatePreviousRequestTime();
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Failed executing network request.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document loadMpvPage(int id, String token) throws RipPandaException, InterruptedException {
        ClassicHttpRequest request = getRequestFactory().createLoadMpvPageRequest(id, token);
        waitToHonorRequestDelay();
        try (ProxyableHttpClient httpClient = createHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                try {
                    checkResponseCodeAllowNotFound(response);
                    return getResponseFactory().parseToDocument(request, response);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                    updatePreviousRequestTime();
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Failed executing network request.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document loadTorrentPage(int id, String token) throws RipPandaException, InterruptedException {
        ClassicHttpRequest request = getRequestFactory().createLoadTorrentPageRequest(id, token);
        waitToHonorRequestDelay();
        try (ProxyableHttpClient httpClient = createHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                try {
                    checkResponseCodeAllowNotFound(response);
                    return getResponseFactory().parseToDocument(request, response);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                    updatePreviousRequestTime();
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Failed executing network request.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document loadArchivePreparationPage(String archiverUrl)
            throws RipPandaException, InterruptedException {
        ClassicHttpRequest request = getRequestFactory().createLoadArchivePreparationPageRequest(archiverUrl);
        waitToHonorRequestDelay();
        try (ProxyableHttpClient httpClient = createHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                try {
                    checkResponseCodeAllowNotFound(response);
                    return getResponseFactory().parseToDocument(request, response);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                    updatePreviousRequestTime();
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Failed executing network request.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean downloadFile(String url, ArchivableElementWriter writer)
            throws RipPandaException, InterruptedException {
        ClassicHttpRequest request = getRequestFactory().createLoadDownloadableFileRequest(url);
        waitToHonorRequestDelay();
        try (ProxyableHttpClient httpClient = createHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                try {
                    checkResponseCode(response);
                    return getResponseFactory().downloadFile(request, response, writer);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                    updatePreviousRequestTime();
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Failed executing network request for URL \"" + url + "\".", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document loadDocument(String url) throws RipPandaException, InterruptedException {
        ClassicHttpRequest request = getRequestFactory().createLoadDocumentRequest(url);
        waitToHonorRequestDelay();
        try (ProxyableHttpClient httpClient = createHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                try {
                    checkResponseCode(response);
                    return getResponseFactory().parseToDocument(request, response);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                    updatePreviousRequestTime();
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Failed executing network request for URL \"" + url + "\".", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document loadExpungeLogPage(int id, String token) throws RipPandaException, InterruptedException {
        ClassicHttpRequest request = getRequestFactory().createLoadExpungeLogPageRequest(id, token);
        waitToHonorRequestDelay();
        try (ProxyableHttpClient httpClient = createHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                try {
                    checkResponseCodeAllowNotFound(response);
                    return getResponseFactory().parseToDocument(request, response);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                    updatePreviousRequestTime();
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Failed executing network request.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document loadDocument(Path path) throws RipPandaException {
        URI baseUri = getRequestFactory().getBaseUri();
        return getResponseFactory().parseToDocument(path, baseUri);
    }

    /**
     * Gets the application settings.
     * 
     * @return the application settings
     */
    private Settings getSettings() {
        return settings;
    }

    /**
     * Gets the web client request factory.
     * 
     * @return the web client request factory
     */
    private WebClientRequestFactory getRequestFactory() {
        return requestFactory;
    }

    /**
     * Gets the web client response factory.
     * 
     * @return the web client response factory
     */
    private WebClientResponseFactory getResponseFactory() {
        return responseFactory;
    }

    /**
     * Gets the configured request delay.
     * 
     * @return the configured request delay
     */
    private Duration getRequestDelay() {
        return requestDelay;
    }

    /**
     * Gets the time of the previous network request.
     * 
     * @return the time of the previous network request
     */
    private LocalDateTime getPreviousRequestTime() {
        return previousRequestTime;
    }

    /**
     * Gets the HTTP client builder used for network requests.
     * 
     * @return the HTTP client builder
     */
    private HttpClientBuilder getHttpClientBuilder() {
        return httpClientBuilder;
    }

    /**
     * Creates a new HTTP client to use for network requests.
     * 
     * @return the HTTP client
     */
    private ProxyableHttpClient createHttpClient() {
        CloseableHttpClient closeableHttpClient = getHttpClientBuilder().build();
        ProxyableHttpClient httpClient = new ProxyableHttpClient(closeableHttpClient, getSettings().getProxy());

        return httpClient;
    }
}
