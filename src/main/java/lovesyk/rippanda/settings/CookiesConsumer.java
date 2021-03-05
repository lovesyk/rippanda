package lovesyk.rippanda.settings;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.TypeConversionException;

/**
 * The consumer to process user-input cookies.
 */
public class CookiesConsumer implements IParameterConsumer {
    /**
     * {@inheritDoc}
     */
    @Override
    public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec) {
        Map<String, String> map = new HashMap<>();

        String value = args.pop();
        for (String cookie : value.split(";")) {
            String[] cookieSplit = cookie.split("=");
            if (cookieSplit.length != 2) {
                throw new TypeConversionException(String.format("\"%s\" is not a valid cookie.", cookie));
            }

            map.put(cookieSplit[0].trim(), cookieSplit[1].trim());
        }

        CookiesWrapper result = new CookiesWrapper(map);
        argSpec.setValue(result);
    }
}
