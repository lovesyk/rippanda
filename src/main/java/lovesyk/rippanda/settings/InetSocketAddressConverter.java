package lovesyk.rippanda.settings;

import java.net.InetSocketAddress;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * The converter to process proxy addresses.
 * 
 * @see <a href=
 *      "https://github.com/remkop/picocli/blob/master/picocli-examples/src/main/java/picocli/examples/typeconverter/InetSocketAddressConverterDemo.java">InetSocketAddressConverterDemo.java</a>
 */
class InetSocketAddressConverter implements ITypeConverter<InetSocketAddress> {
    @Override
    public InetSocketAddress convert(String value) {
        int pos = value.lastIndexOf(':');
        if (pos < 0) {
            throw new TypeConversionException("Invalid format: must be 'host:port' but was '" + value + "'");
        }
        String adr = value.substring(0, pos);
        int port = Integer.parseInt(value.substring(pos + 1)); // invalid port shows the generic error message
        return new InetSocketAddress(adr, port);
    }
}
