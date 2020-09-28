package lovesyk.rippanda.service.web;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;

/**
 * HTTP Client allowing requests to be tunneled through a SOCKS5 proxy.
 */
public class ProxyableHttpClient implements ModalCloseable {
    private final CloseableHttpClient httpClient;
    private final InetSocketAddress socksAddress;

    /**
     * Constructs a new HTTP client.
     * 
     * @param httpClient   the source HTTP client of Apache
     * @param socksAddress the SOCKS5 proxy address to use or <code>null</code> if
     *                     no tunneling required
     */
    public ProxyableHttpClient(CloseableHttpClient httpClient, InetSocketAddress socksAddress) {
        this.httpClient = httpClient;
        this.socksAddress = socksAddress;
    }

    /**
     * Executes a HTTP request, tunneling the network connection if necessary.
     * 
     * @see org.apache.hc.client5.http.classic.HttpClient#execute(ClassicHttpRequest,
     *      HttpContext)
     * 
     * @param request the HTTP request to execute
     * @return the HTTP response received
     * @throws IOException on failure
     */
    public CloseableHttpResponse execute(final ClassicHttpRequest request) throws IOException {
        HttpClientContext context;
        if (getSocksAddress() == null) {
            context = null;
        } else {
            context = HttpClientContext.create();
            context.setAttribute("socks.address", getSocksAddress());
        }
        return getHttpClient().execute(request, context);
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    @Override
    public void close(CloseMode closeMode) {
        httpClient.close(closeMode);
    }

    /**
     * Gets the HTTP client to use for network requests.
     * 
     * @return the HTTP client
     */
    private CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Gets the configured SOCKS5 proxy address to use for network requests.
     * 
     * @return the SOCKS5 proxy address or <code>null</code>
     */
    private InetSocketAddress getSocksAddress() {
        return socksAddress;
    }
}
