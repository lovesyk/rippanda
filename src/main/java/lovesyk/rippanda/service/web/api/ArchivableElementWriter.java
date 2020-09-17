package lovesyk.rippanda.service.web.api;

import lovesyk.rippanda.exception.RipPandaException;

/**
 * The interface to use for passing methods to the web client which will save
 * the file in question to disk.
 */
public interface ArchivableElementWriter {
    /**
     * Saves the downloadable file to disk.
     * 
     * @param downloadableFile the file to download
     * @return custom logic flag for further processing
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    boolean write(DownloadableFile downloadableFile) throws RipPandaException, InterruptedException;
}