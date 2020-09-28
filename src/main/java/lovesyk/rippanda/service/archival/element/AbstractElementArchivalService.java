package lovesyk.rippanda.service.archival.element;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.Settings;

/**
 * The base of all archival services processing separate elements.
 */
abstract class AbstractElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(AbstractElementArchivalService.class);

    /**
     * 259 characters according to Windows MAX_PATH.
     */
    private static final int MAX_PATH_LENGTH = 259;
    /**
     * 127 characters to cover file systems which allow a maximum filename length of
     * 255 UTF-16 bytes. Subtracting 4 characters to accommodate 3-letter temporary
     * filename extensions.
     */
    private static final int MAX_FILENAME_LENGTH = 123;

    private Settings settings;
    private IWebClient webClient;
    private static Map<Character, Character> illegalCharMapping;

    static {
        illegalCharMapping = new HashMap<>();
        illegalCharMapping.put('\\', '＼');
        illegalCharMapping.put('/', '／');
        illegalCharMapping.put('|', '｜');
        illegalCharMapping.put(':', '：');
        illegalCharMapping.put('?', '？');
        illegalCharMapping.put('*', '＊');
        illegalCharMapping.put('\"', '＂');
        illegalCharMapping.put('<', '＜');
        illegalCharMapping.put('>', '＞');
    }

    /**
     * Initializes the abstract archival service.
     * 
     * @param settings  the application settings
     * @param webClient the web client
     */
    @Inject
    public AbstractElementArchivalService(Settings settings, IWebClient webClient) {
        this.settings = settings;
        this.webClient = webClient;
    }

    /**
     * Initializes the directory by creating it if at least the parent directory
     * exists.
     * 
     * @throws RipPandaException on failure
     */
    protected void initDir(Path dir) throws RipPandaException {
        if (!Files.isDirectory(dir.getParent())) {
            throw new RipPandaException("Parent of the directory does not exist.");
        }

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RipPandaException("Could not create directory.", e);
        }
    }

    /**
     * Saves the stream to the specified path.
     * 
     * @param input    the input to save
     * @param dir      the directory to save to
     * @param fileName the filename
     * @throws RipPandaException on failure
     */
    protected void save(InputStream input, Path dir, String fileName) throws RipPandaException {
        save(file -> write(input, file), dir, fileName);
    }

    /**
     * Saves the string to the specified path.
     * 
     * @param input    the input to save
     * @param dir      the directory to save to
     * @param fileName the filename
     * @throws RipPandaException on failure
     */
    protected void save(String input, Path dir, String fileName) throws RipPandaException {
        save(file -> write(input, file), dir, fileName);
    }

    /**
     * Writes the stream to the specified path, overwriting any existing file.
     * 
     * @param input the input to write
     * @param file  the file to save to
     * @throws IOException on failure
     */
    protected void write(InputStream input, Path file) throws IOException {
        Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Writes the string to the specified path, overwriting any existing file.
     * 
     * @param input the input to write
     * @param file  the file to save to
     * @throws IOException on failure
     */
    protected void write(String input, Path file) throws IOException {
        Files.writeString(file, input);
    }

    /**
     * Saves a file using the specified writer while making sure there is a backup
     * available at all times.
     * 
     * @param fileWriter the file writer to use
     * @param dir        the directory to save to
     * @param fileName   the filename
     * @throws RipPandaException on failure
     */
    protected void save(ArchivableElementWriter fileWriter, Path dir, String fileName) throws RipPandaException {
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
     * Sanitizes a filename by removing potentially incompatible characters as well
     * as trimming the filename if required.
     * 
     * @param dir      the directory the file be later saved to
     * @param filename the filename to sanitize
     * @return the sanitized filename
     * @throws RipPandaException on failure
     */
    protected String sanitizeFileName(Path dir, String filename) throws RipPandaException {
        LOGGER.debug("Sanitizing the filename \"{}\"...", filename);

        String sanitizedFilename = filename;

        for (Entry<Character, Character> entry : illegalCharMapping.entrySet()) {
            Character key = entry.getKey();
            Character value = entry.getValue();

            sanitizedFilename = sanitizedFilename.replace(key, value);
        }

        if (LOGGER.isDebugEnabled() && !sanitizedFilename.equals(filename)) {
            LOGGER.debug("Removed potentially unsafe characters resulting in \"{}\".", sanitizedFilename);
        }

        Path file = dir.resolve(sanitizedFilename).toAbsolutePath();
        int maxPathLengthDiff = file.toString().length() - MAX_PATH_LENGTH;
        int maxFilenameLengthDiff = sanitizedFilename.length() - MAX_FILENAME_LENGTH;
        int excessiveLength = Math.max(maxPathLengthDiff, maxFilenameLengthDiff);

        if (excessiveLength > 0) {
            LOGGER.debug("Filename needs to be shortened by {} characters.", excessiveLength);

            String baseName = FilenameUtils.getBaseName(sanitizedFilename);
            String dottedExtension = sanitizedFilename.substring(baseName.length());

            if (excessiveLength > baseName.length()) {
                throw new RipPandaException("Cannot shorten file name enough to fulfil limits.");
            }

            String baseNameCut = baseName.substring(0, baseName.length() - excessiveLength).trim();
            sanitizedFilename = baseNameCut + dottedExtension;

            LOGGER.debug("Filename was shortened to \"{}\".", sanitizedFilename);
        }

        return sanitizedFilename;
    }

    /**
     * Gets the application settings.
     * 
     * @return the settings
     */
    protected Settings getSettings() {
        return settings;
    }

    /**
     * Gets the network web client.
     * 
     * @return the web client
     */
    protected IWebClient getWebClient() {
        return webClient;
    }

    /**
     * The file writer interface to use for saving files.
     */
    interface ArchivableElementWriter {
        /**
         * Writes to the specified file.
         * 
         * @param file the file to write to
         * @throws IOException on failure
         */
        void write(Path file) throws IOException;
    }
}
