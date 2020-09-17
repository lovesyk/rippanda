package lovesyk.rippanda.service.web;

import java.util.List;

/**
 * Represents a network API request.
 */
class ApiRequest {
    private String method;
    private List<List<Object>> gidlist;
    private Integer namespace;

    /**
     * Gets the request method.
     * 
     * @return the method
     */
    String getMethod() {
        return method;
    }

    /**
     * Sets the request method.
     * 
     * @param method the method
     */
    void setMethod(String method) {
        this.method = method;
    }

    /**
     * Gets the request gallery ID list.
     * 
     * @return the gallery ID list
     */
    List<List<Object>> getGidlist() {
        return gidlist;
    }

    /**
     * Sets the request gallery ID list.
     * 
     * @param gidlist the gallery ID list
     */
    void setGidlist(List<List<Object>> gidlist) {
        this.gidlist = gidlist;
    }

    /**
     * Gets the request namespace.
     * 
     * @return the namespace
     */
    Integer getNamespace() {
        return namespace;
    }

    /**
     * Sets the request namespace.
     * 
     * @param namespace the namespace
     */
    void setNamespace(Integer namespace) {
        this.namespace = namespace;
    }
}
