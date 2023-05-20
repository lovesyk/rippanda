package lovesyk.rippanda.service.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

/**
 * Socket factory for plain HTTP requests to be proxied through a SOCKS5 proxy.
 * 
 * @see <a href="https://stackoverflow.com/a/25203021">How to use Socks 5 proxy
 *      with Apache HTTP Client 4?</a>
 */
class ProxiedPlainConnectionSocketFactory extends PlainConnectionSocketFactory {
    @Override
    public Socket createSocket(final HttpContext context) throws IOException {
        InetSocketAddress socksaddr = (InetSocketAddress) context.getAttribute("socks.address");
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
        return new Socket(proxy);
    }

    @Override
    public Socket connectSocket(final TimeValue connectTimeout, final Socket socket, final HttpHost host,
            final InetSocketAddress remoteAddress,
            final InetSocketAddress localAddress, final HttpContext context) throws IOException {
        // convert address to unresolved
        InetSocketAddress unresolvedRemote = InetSocketAddress.createUnresolved(host.getHostName(),
                remoteAddress.getPort());
        return super.connectSocket(connectTimeout, socket, host, unresolvedRemote, localAddress, context);
    }
}
