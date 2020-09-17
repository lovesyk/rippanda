package lovesyk.rippanda.service.archival.element.api;

import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;

/**
 * The interface of all archival services processing separate elements.
 */
public interface IElementArchivalService {
    /**
     * Processes the given gallery according to application settings.
     * 
     * @param gallery the gallery
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    void process(Gallery gallery) throws RipPandaException, InterruptedException;
}
