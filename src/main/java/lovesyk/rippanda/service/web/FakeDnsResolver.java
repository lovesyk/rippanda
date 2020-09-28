package lovesyk.rippanda.service.web;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.DnsResolver;

/**
 * Fake DNS resolver to prevent Apache HTTP client from resolving names locally.
 * 
 * @see <a href="https://stackoverflow.com/a/25203021">How to use Socks 5 proxy
 *      with Apache HTTP Client 4?</a>
 * 
 */
class FakeDnsResolver implements DnsResolver {
    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        // return an RFC 5737 example IP address as it will never be used
        return new InetAddress[] { InetAddress.getByAddress(new byte[] { (byte) 192, 0, 2, 1 }) };
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        // return an empty name as it will never be used
        return StringUtils.EMPTY;
    }
}
