package lovesyk.rippanda.service.archival.element;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.service.archival.api.FilesUtils;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.Settings;

/**
 * The base of all archival services processing separate elements.
 */
abstract class AbstractElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(AbstractElementArchivalService.class);
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 259 characters according to Windows MAX_PATH.
     */
    private static final int MAX_PATH_LENGTH = 259;
    /**
     * 127 characters to cover file systems which allow a maximum filename length of
     * 255 UTF-16 bytes. Subtracting 4 characters to accommodate 3-letter temporary
     * filename extensions.
     */
    private static final int MAX_FILENAME_LENGTH_UNIQUE = 123;
    /**
     * Non-unique filenames need an additional 4 characters to accommodate for
     * suffixes of (2) up to (99).
     */
    private static final int MAX_FILENAME_LENGTH = MAX_FILENAME_LENGTH_UNIQUE - 5;
    /**
     * The regular expression to apply for cleanup of filenames during sanitization.
     */
    private static final String FILENAME_CLEANUP_PATTERN = "[\u0000-\u001f\u007f]";

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
        FilesUtils.save(file -> write(input, file), dir, fileName);
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
        FilesUtils.save(file -> write(input, file), dir, fileName);
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
     * Writes a JSON object to file.
     * 
     * @param json the JSON
     * @param file the file to write to
     * @throws IOException on failure
     */
    protected void write(JsonElement json, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            GSON.toJson(json, writer);
        }
    }

    /**
     * Sanitizes a filename by removing potentially incompatible characters as well
     * as trimming the filename if required.
     * 
     * @param dir      the directory the file be later saved to
     * @param filename the filename to sanitize
     * @param unique   whether the filename is unique or a (1), (2) etc. should be
     *                 appended
     * @return the sanitized filename
     * @throws RipPandaException on failure
     */
    protected String sanitizeFileName(Path dir, String filename, boolean unique) throws RipPandaException {
        LOGGER.debug("Sanitizing the filename \"{}\"...", filename);

        String sanitizedFilename = filename;

        for (Entry<Character, Character> entry : illegalCharMapping.entrySet()) {
            Character key = entry.getKey();
            Character value = entry.getValue();

            sanitizedFilename = sanitizedFilename.replace(key, value);
        }
        sanitizedFilename = sanitizedFilename.replaceAll(FILENAME_CLEANUP_PATTERN, StringUtils.EMPTY);

        if (LOGGER.isDebugEnabled() && !sanitizedFilename.equals(filename)) {
            LOGGER.debug("Removed potentially unsafe characters resulting in \"{}\".", sanitizedFilename);
        }

        Path file = dir.resolve(sanitizedFilename).toAbsolutePath();
        int maxPathLengthDiff = file.toString().length() - MAX_PATH_LENGTH;
        int maxFilenameLength = unique ? MAX_FILENAME_LENGTH_UNIQUE : MAX_FILENAME_LENGTH;
        int maxFilenameLengthDiff = sanitizedFilename.length() - maxFilenameLength;
        int excessiveLength = Math.max(maxPathLengthDiff, maxFilenameLengthDiff);

        String baseName = FilenameUtils.getBaseName(sanitizedFilename);
        String dottedExtension = sanitizedFilename.substring(baseName.length());

        if (excessiveLength > 0) {
            LOGGER.debug("Filename needs to be shortened by {} characters.", excessiveLength);

            if (excessiveLength > baseName.length()) {
                throw new RipPandaException("Cannot shorten file name enough to fulfil limits.");
            }

            String baseNameCut = baseName.substring(0, baseName.length() - excessiveLength).trim();
            sanitizedFilename = baseNameCut + dottedExtension;

            LOGGER.debug("Filename was shortened to \"{}\".", sanitizedFilename);
        }

        String nonCollidingFilename = sanitizedFilename;
        int maxSuffix = unique ? 1 : 99;
        for (int i = 1; i <= maxSuffix; ++i) {
            if (i > 1) {
                nonCollidingFilename = String.format("%s (%s)%s", baseName, i, dottedExtension);
            }

            Map<String, Path> existingFilenames;
            try {
                existingFilenames = Files.list(dir).collect(Collectors.toMap(x -> x.getFileName().toString().toLowerCase(), x -> x));
            } catch (IOException e) {
                throw new RipPandaException("Could not list directory files.", e);
            }

            Path existingFilename = existingFilenames.get(nonCollidingFilename.toLowerCase());
            if (existingFilename != null) {
                if (unique) {
                    try {
                        Files.delete(existingFilename);
                    } catch (IOException e) {
                        throw new RipPandaException("Could not delete file colliding by filename.", e);
                    }
                } else {
                    if (i == maxSuffix) {
                        throw new RipPandaException("Non-colliding filenames exhausted.");
                    } else {
                        continue;
                    }
                }
            }

            if (!nonCollidingFilename.equals(sanitizedFilename)) {
                sanitizedFilename = nonCollidingFilename;
                LOGGER.debug("Non-colliding filename was adjusted to \"{}\".", sanitizedFilename);
            }
            break;
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
}
