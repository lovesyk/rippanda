package lovesyk.rippanda.service.archival.api;

import lovesyk.rippanda.exception.RipPandaException;

/**
 * The interface of all archival modes.
 */
public interface IArchivalService {
    /**
     * Processes the search result according to application settings.
     * 
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    void process() throws RipPandaException, InterruptedException;
}
