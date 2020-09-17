package lovesyk.rippanda.exception;

/**
 * The exception used for application-specific failures.
 */
public class RipPandaException extends Exception {
    private static final long serialVersionUID = 554366635820838423L;

    /**
     * @see java.lang.Exception#Exception(String)
     */
    public RipPandaException(String message) {
        super(message);
    }

    /**
     * @see java.lang.Exception#Exception(Throwable)
     */
    public RipPandaException(Throwable cause) {
        super(cause);
    }

    /**
     * @see java.lang.Exception#Exception(String, Throwable)
     */
    public RipPandaException(String message, Throwable cause) {
        super(message, cause);
    }
}