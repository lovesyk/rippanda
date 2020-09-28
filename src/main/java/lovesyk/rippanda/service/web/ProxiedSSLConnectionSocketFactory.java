package lovesyk.rippanda.service.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

/**
 * Socket factory for HTTPS requests to be proxied through a SOCKS5 proxy.
 * 
 * @see <a href="https://stackoverflow.com/a/25203021">How to use Socks 5 proxy
 *      with Apache HTTP Client 4?</a>
 */
class ProxiedSSLConnectionSocketFactory extends SSLConnectionSocketFactory {

    /**
     * Constructs a new socket factory instance for the given context.
     * 
     * @param sslContext the SSL context
     */
    public ProxiedSSLConnectionSocketFactory(final SSLContext sslContext) {
        super(sslContext);
    }

    @Override
    public Socket createSocket(final HttpContext context) throws IOException {
        InetSocketAddress socksaddr = (InetSocketAddress) context.getAttribute("socks.address");
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
        return new Socket(proxy);
    }

    @Override
    public Socket connectSocket(final TimeValue connectTimeout, final Socket socket, final HttpHost host, final InetSocketAddress remoteAddress,
            final InetSocketAddress localAddress, final HttpContext context) throws IOException {
        // Convert address to unresolved
        InetSocketAddress unresolvedRemote = InetSocketAddress.createUnresolved(host.getHostName(), remoteAddress.getPort());
        return super.connectSocket(connectTimeout, socket, host, unresolvedRemote, localAddress, context);
    }
}