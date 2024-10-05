package lovesyk.rippanda.service.web;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.io.HttpClientConnectionOperator;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;

public class PoolingHttpClientConnectionManagerBuilder
        extends org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder {

    public static PoolingHttpClientConnectionManagerBuilder create() {
        return new PoolingHttpClientConnectionManagerBuilder();
    }

    @Override
    protected HttpClientConnectionOperator createConnectionOperator(
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final TlsSocketStrategy tlsSocketStrategy) {
        return new DefaultHttpClientConnectionOperator(schemePortResolver, dnsResolver,
                RegistryBuilder.<TlsSocketStrategy>create()
                        .register(URIScheme.HTTPS.id, tlsSocketStrategy)
                        .build());
    }
}
