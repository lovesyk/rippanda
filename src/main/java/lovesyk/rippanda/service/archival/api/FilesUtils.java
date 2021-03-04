package lovesyk.rippanda.service.archival.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lovesyk.rippanda.exception.RipPandaException;

/**
 * Utility methods for file system tasks across services.
 */
public class FilesUtils {
    private static final Logger LOGGER = LogManager.getLogger(FilesUtils.class);

    /**
     * Saves a file using the specified writer while making sure there is a backup
     * available at all times.
     * 
     * @param fileWriter the file writer to use
     * @param dir        the directory to save to
     * @param fileName   the filename
     * @throws RipPandaException on failure
     */
    public static void save(ArchivableElementWriter fileWriter, Path dir, String fileName) throws RipPandaException {
        Path file = dir.resolve(fileName);

        String tempFileExtension = ".tmp";
        String tempFileName = fileName + tempFileExtension;
        Path tempFile = dir.resolve(tempFileName);

        String backupFileExtension = ".bak";
        String backupFileName = fileName + backupFileExtension;
        Path backupFile = dir.resolve(backupFileName);

        try {
            LOGGER.debug("Writing to temporary file \"{}\"...", tempFile);
            fileWriter.write(tempFile);

            if (Files.exists(file)) {
                LOGGER.debug("Creating backup of existing file \"{}\" as \"{}\"...", file, backupFile);
                Files.move(file, backupFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }

            LOGGER.debug("Removing temporary file extension from \"{}\" into \"{}\"...", tempFile, file);
            Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e1) {
                LOGGER.error("Could not delete temporary file.");
                throw new RipPandaException("Could not delete temporary file.", e1);
            }

            throw new RipPandaException("Could not save file.", e);
        }

        try {
            if (Files.exists(backupFile)) {
                LOGGER.debug("Removing backup file \"{}\"...", backupFile);
                Files.deleteIfExists(backupFile);
            }
        } catch (IOException e) {
            LOGGER.warn("Removing backup file failed. Manual clean-up required.", e);
        }
    }

    /**
     * The file writer interface to use for saving files.
     */
    public interface ArchivableElementWriter {
        /**
         * Writes to the specified file.
         * 
         * @param file the file to write to
         * @throws IOException on failure
         */
        void write(Path file) throws IOException;
    }
}
