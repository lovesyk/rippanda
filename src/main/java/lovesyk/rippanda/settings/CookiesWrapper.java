package lovesyk.rippanda.settings;

import java.util.Map;

/**
 * Cookie wrapper as workaround for not customizable picocli help display of
 * maps.
 */
public class CookiesWrapper {
    private final Map<String, String> cookies;

    /**
     * Instantiates a new wrapper to store cookies.
     * 
     * @param cookies the cookie map to store
     */
    public CookiesWrapper(Map<String, String> cookies) {
        this.cookies = cookies;
    }

    /**
     * Gets the contained cookies map.
     * 
     * @return the container cookies.
     */
    public Map<String, String> getCookies() {
        return cookies;
    }
}
