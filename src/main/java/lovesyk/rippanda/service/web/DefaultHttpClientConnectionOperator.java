package lovesyk.rippanda.service.web;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import javax.net.ssl.SSLSocket;

import org.apache.hc.client5.http.ConnectExceptionSupport;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultHttpClientConnectionOperator
        extends org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClientConnectionOperator.class);

    static final DetachedSocketFactory PLAIN_SOCKET_FACTORY = socksProxy -> socksProxy == null ? new Socket()
            : new Socket(socksProxy);

    private final DetachedSocketFactory detachedSocketFactory;
    private final Lookup<TlsSocketStrategy> tlsSocketStrategyLookup;
    private final SchemePortResolver schemePortResolver;
    private final DnsResolver dnsResolver;

    public DefaultHttpClientConnectionOperator(
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final Lookup<TlsSocketStrategy> tlsSocketStrategyLookup) {
        this(PLAIN_SOCKET_FACTORY, schemePortResolver, dnsResolver, tlsSocketStrategyLookup);
    }

    public DefaultHttpClientConnectionOperator(
            final DetachedSocketFactory detachedSocketFactory,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final Lookup<TlsSocketStrategy> tlsSocketStrategyLookup) {
        super(detachedSocketFactory, schemePortResolver, dnsResolver, tlsSocketStrategyLookup);
        this.detachedSocketFactory = Args.notNull(detachedSocketFactory, "Plain socket factory");
        this.tlsSocketStrategyLookup = Args.notNull(tlsSocketStrategyLookup, "Socket factory registry");
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
        this.dnsResolver = dnsResolver != null ? dnsResolver : SystemDefaultDnsResolver.INSTANCE;
    }

    @Override
    public void connect(
            final ManagedHttpClientConnection conn,
            final HttpHost endpointHost,
            final NamedEndpoint endpointName,
            final InetSocketAddress localAddress,
            final Timeout connectTimeout,
            final SocketConfig socketConfig,
            final Object attachment,
            final HttpContext context) throws IOException {
        Args.notNull(conn, "Connection");
        Args.notNull(endpointHost, "Host");
        Args.notNull(socketConfig, "Socket config");
        Args.notNull(context, "Context");

        final InetAddress[] remoteAddresses;
        // custom start: temporarily use null as remote address if proxy is to be used
        if (endpointHost.getAddress() != null || socketConfig.getSocksProxyAddress() != null) {
            // custom end
            remoteAddresses = new InetAddress[] { endpointHost.getAddress() };
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} resolving remote address", endpointHost.getHostName());
            }

            remoteAddresses = this.dnsResolver.resolve(endpointHost.getHostName());

            if (LOG.isDebugEnabled()) {
                LOG.debug("{} resolved to {}", endpointHost.getHostName(),
                        remoteAddresses == null ? "null" : Arrays.asList(remoteAddresses));
            }

            if (remoteAddresses == null || remoteAddresses.length == 0) {
                throw new UnknownHostException(endpointHost.getHostName());
            }
        }

        final Timeout soTimeout = socketConfig.getSoTimeout();
        final SocketAddress socksProxyAddress = socketConfig.getSocksProxyAddress();
        final Proxy socksProxy = socksProxyAddress != null ? new Proxy(Proxy.Type.SOCKS, socksProxyAddress) : null;
        final int port = this.schemePortResolver.resolve(endpointHost.getSchemeName(), endpointHost);
        for (int i = 0; i < remoteAddresses.length; i++) {
            final InetAddress address = remoteAddresses[i];
            final boolean last = i == remoteAddresses.length - 1;
            // custom start: use unresolved remote address if proxy is in use
            InetSocketAddress remoteAddress;
            if (socksProxy == null) {
                remoteAddress = new InetSocketAddress(address, port);
            } else {
                remoteAddress = InetSocketAddress.createUnresolved(endpointHost.getHostName(),
                        port);
            }
            // custom end
            onBeforeSocketConnect(context, endpointHost);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} connecting {}->{} ({})", endpointHost, localAddress, remoteAddress, connectTimeout);
            }
            final Socket socket = detachedSocketFactory.create(socksProxy);
            try {
                conn.bind(socket);
                if (soTimeout != null) {
                    socket.setSoTimeout(soTimeout.toMillisecondsIntBound());
                }
                socket.setReuseAddress(socketConfig.isSoReuseAddress());
                socket.setTcpNoDelay(socketConfig.isTcpNoDelay());
                socket.setKeepAlive(socketConfig.isSoKeepAlive());
                if (socketConfig.getRcvBufSize() > 0) {
                    socket.setReceiveBufferSize(socketConfig.getRcvBufSize());
                }
                if (socketConfig.getSndBufSize() > 0) {
                    socket.setSendBufferSize(socketConfig.getSndBufSize());
                }

                final int linger = socketConfig.getSoLinger().toMillisecondsIntBound();
                if (linger >= 0) {
                    socket.setSoLinger(true, linger);
                }

                if (localAddress != null) {
                    socket.bind(localAddress);
                }
                socket.connect(remoteAddress,
                        TimeValue.isPositive(connectTimeout) ? connectTimeout.toMillisecondsIntBound() : 0);
                conn.bind(socket);
                onAfterSocketConnect(context, endpointHost);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} {} connected {}->{}", ConnPoolSupport.getId(conn), endpointHost,
                            conn.getLocalAddress(), conn.getRemoteAddress());
                }
                conn.setSocketTimeout(soTimeout);
                final TlsSocketStrategy tlsSocketStrategy = tlsSocketStrategyLookup != null
                        ? tlsSocketStrategyLookup.lookup(endpointHost.getSchemeName())
                        : null;
                if (tlsSocketStrategy != null) {
                    final NamedEndpoint tlsName = endpointName != null ? endpointName : endpointHost;
                    onBeforeTlsHandshake(context, endpointHost);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} {} upgrading to TLS", ConnPoolSupport.getId(conn), tlsName);
                    }
                    final SSLSocket sslSocket = tlsSocketStrategy.upgrade(socket, tlsName.getHostName(),
                            tlsName.getPort(), attachment, context);
                    conn.bind(sslSocket, socket);
                    onAfterTlsHandshake(context, endpointHost);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} {} upgraded to TLS", ConnPoolSupport.getId(conn), tlsName);
                    }
                }
                return;
            } catch (final RuntimeException ex) {
                Closer.closeQuietly(socket);
                throw ex;
            } catch (final IOException ex) {
                Closer.closeQuietly(socket);
                if (last) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} connection to {} failed ({}); terminating operation", endpointHost, remoteAddress,
                                ex.getClass());
                    }
                    throw ConnectExceptionSupport.enhance(ex, endpointHost, remoteAddresses);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} connection to {} failed ({}); retrying connection to the next address", endpointHost,
                            remoteAddress, ex.getClass());
                }
            }
        }
    }
}
