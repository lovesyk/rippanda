package lovesyk.rippanda.service.archival.element;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.model.Gallery;
import lovesyk.rippanda.service.archival.api.FilesUtils;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.Settings;

/**
 * The base of all archival services processing separate elements.
 */
abstract class AbstractElementArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(AbstractElementArchivalService.class);
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String UNAVAILABLE_FILENAME = "unavailable.txt";

    /**
     * 258 characters according to Windows MAX_PATH for files inside drive root.
     */
    private static final int MAX_PATH_LENGTH = 258;

    /**
     * Example suffix of maximum length for temporary extensions.
     */
    private static final String FILENAME_SUFFIX_EXAMPLE_TMP = ".tmp";
    /**
     * Example suffix of maximum length for duplicate filenames.
     */
    private static final String FILENAME_SUFFIX_EXAMPLE_DUPLICATE = " (99)";

    /**
     * Account for file systems which allow up to 255 character file names.
     */
    private static final int MAX_FILENAME_LENGTH = 255;
    /**
     * Account for the temporary ".tmp" file extension.
     */
    private static final int MAX_FILENAME_TMP_OVERHEAD_LENGTH = 4;
    /**
     * The maximum filename size in bytes for unique filenames on UTF8 file systems.
     */
    private static final int MAX_FILENAME_UNIQUE_UTF8_BYTES = MAX_FILENAME_LENGTH - MAX_FILENAME_TMP_OVERHEAD_LENGTH;
    /**
     * The maximum filename size in bytes for unique filenames on UTF16 file
     * systems.
     */
    private static final int MAX_FILENAME_UNIQUE_UTF16_BYTES = MAX_FILENAME_LENGTH
            - MAX_FILENAME_TMP_OVERHEAD_LENGTH * 2;
    /**
     * Account for non-unique suffixes up to 99 -> " (99)"
     */
    private static final int MAX_FILENAME_NON_UNIQUE_OVERHEAD_LENGTH = 5;
    /**
     * The maximum filename size in bytes for non-unique filenames on UTF8 file
     * systems.
     */
    private static final int MAX_FILENAME_NON_UNIQUE_UTF8_BYTES = MAX_FILENAME_UNIQUE_UTF8_BYTES
            - MAX_FILENAME_NON_UNIQUE_OVERHEAD_LENGTH;
    /**
     * The maximum filename size in bytes for non-unique filenames on UTF16 file
     * systems.
     */
    private static final int MAX_FILENAME_NON_UNIQUE_UTF16_BYTES = MAX_FILENAME_UNIQUE_UTF16_BYTES
            - MAX_FILENAME_NON_UNIQUE_OVERHEAD_LENGTH * 2;

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
        LOGGER.trace("Sanitizing the filename \"{}\"...", filename);

        String sanitizedFilename = filename;

        String legalizedFilename = legalizeFilename(sanitizedFilename);
        if (!legalizedFilename.equals(sanitizedFilename)) {
            sanitizedFilename = legalizedFilename;
            LOGGER.debug("Legalized filename to \"{}\".", sanitizedFilename);
        }

        String truncatedFilename = truncateFilename(dir, sanitizedFilename, unique);
        if (!truncatedFilename.equals(sanitizedFilename)) {
            sanitizedFilename = truncatedFilename;
            LOGGER.debug("Truncated filename to \"{}\".", sanitizedFilename);
        }

        String nonCollidingFilename = decollideFilename(dir, sanitizedFilename, unique);
        if (!nonCollidingFilename.equals(sanitizedFilename)) {
            sanitizedFilename = nonCollidingFilename;
            LOGGER.debug("Removed filename collisions to \"{}\".", sanitizedFilename);
        }

        return sanitizedFilename;
    }

    /**
     * Legalizes the filename by ensuring it contains no illegal characters.
     * 
     * @param filename the filename to legalize, not <code>null</code>
     * @return the legalized filename, never <code>null</code>
     */
    private String legalizeFilename(String filename) {
        for (Entry<Character, Character> entry : illegalCharMapping.entrySet()) {
            Character key = entry.getKey();
            Character value = entry.getValue();

            filename = filename.replace(key, value);
        }
        return filename.replaceAll(FILENAME_CLEANUP_PATTERN, StringUtils.EMPTY);
    }

    /**
     * Truncates the filename by ensuring path and filename length do not exceed
     * file system limits.
     * 
     * @param dir      the directory the file be later saved to, not
     *                 <code>null</code>
     * @param filename the filename to truncate, not <code>null</code>
     * @param unique   whether the filename is unique or a (1), (2) etc. should be
     *                 appended
     * @return the truncated filename, never <code>null</code>
     * @throws RipPandaException on failure
     */
    private String truncateFilename(Path dir, String filename, boolean unique) throws RipPandaException {
        String baseName = FilenameUtils.getBaseName(filename);
        String dottedExtension = filename.substring(baseName.length());

        String maxSuffix = (unique ? StringUtils.EMPTY : FILENAME_SUFFIX_EXAMPLE_DUPLICATE) + dottedExtension
                + FILENAME_SUFFIX_EXAMPLE_TMP;

        Path file = dir.resolve(baseName + maxSuffix).toAbsolutePath();
        int excessivePathLength = file.toString().length() - MAX_PATH_LENGTH;
        String truncatedBaseNameForPath = baseName;
        if (excessivePathLength > 0) {
            if (excessivePathLength >= baseName.length()) {
                throw new RipPandaException("Cannot truncate file name enough to fulfil limits.");
            }
            truncatedBaseNameForPath = StringUtils.substring(baseName, 0, -excessivePathLength);
        }

        int maxUtf8ByteCount = unique ? MAX_FILENAME_UNIQUE_UTF8_BYTES : MAX_FILENAME_NON_UNIQUE_UTF8_BYTES;
        String truncatedBaseNameUtf8 = truncateBaseName(baseName, maxSuffix, maxUtf8ByteCount, StandardCharsets.UTF_8);

        int maxUtf16ByteCount = unique ? MAX_FILENAME_UNIQUE_UTF16_BYTES : MAX_FILENAME_NON_UNIQUE_UTF16_BYTES;
        String truncatedBaseNameUtf16 = truncateBaseName(baseName, maxSuffix, maxUtf16ByteCount,
                StandardCharsets.UTF_16);

        String minBaseName = Arrays.asList(truncatedBaseNameForPath, truncatedBaseNameUtf8, truncatedBaseNameUtf16)
                .stream()
                .min(Comparator.comparingInt(String::length)).get();
        String strippedFilename = StringUtils.strip(minBaseName + dottedExtension);

        return strippedFilename;
    }

    /**
     * Truncates the base name by ensuring it does not exceed a certain byte count
     * while considering possible suffixes.
     * 
     * @param baseName  the base name to truncate, not <code>null</code>
     * @param suffix    a possible suffix to consider for truncation, not
     *                  <code>null</code>
     * @param byteCount the maximum byte count the whole filename should fit in
     * @param charset   the character set used for encoding, not <code>null</code>
     * @return the truncated base name, never <code>null</code>
     * @throws RipPandaException on failure
     */
    private String truncateBaseName(String baseName, String suffix, int byteCount, Charset charset)
            throws RipPandaException {
        int baseNameByteCount = byteCount - suffix.getBytes(charset).length;
        if (baseNameByteCount <= 0) {
            throw new RipPandaException("Cannot truncate file name enough to fulfil limits.");
        }
        byte[] outputBytes = new byte[baseNameByteCount];
        CharBuffer inBuffer = CharBuffer.wrap(baseName.toCharArray());
        ByteBuffer outBuffer = ByteBuffer.wrap(outputBytes);

        CharsetEncoder encoder = charset.newEncoder();
        encoder.encode(inBuffer, outBuffer, true);

        return new String(outputBytes, 0, outBuffer.position(), charset);
    }

    /**
     * Removes possible collisions from the filename by appending a numerical suffix
     * in case it already exists or removing it if it should be unique.
     * 
     * @param dir      the directory the file be later saved to, not
     *                 <code>null</code>
     * @param filename the filename to truncate, not <code>null</code>
     * @param unique   whether the filename is unique or a (1), (2) etc. should be
     *                 appended
     * @return the collision-free filename, never <code>null</code>
     * @throws RipPandaException on failure
     */
    private String decollideFilename(Path dir, String filename, boolean unique) throws RipPandaException {
        String baseName = FilenameUtils.getBaseName(filename);
        String dottedExtension = filename.substring(baseName.length());
        String nonCollidingFilename = filename;

        int maxSuffix = unique ? 1 : 99;
        for (int i = 1; i <= maxSuffix; ++i) {
            if (i > 1) {
                nonCollidingFilename = String.format("%s (%s)%s", baseName, i, dottedExtension);
            }

            Map<String, Path> existingFilenames;
            try {
                existingFilenames = Files.list(dir)
                        .collect(Collectors.toMap(x -> x.getFileName().toString().toLowerCase(), x -> x));
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

            return nonCollidingFilename;
        }

        throw new RuntimeException("Unexpected exception while looking for non-colliding filename.");
    }

    /**
     * Ensures the gallery's initial files are loaded, reading from file system if
     * required.
     * 
     * @param gallery the gallery to check
     * @throws RipPandaException on failure
     */
    public void ensureFilesLoaded(Gallery gallery) throws RipPandaException {
        if (!gallery.isFilesLoaded()) {
            List<Path> files = new ArrayList<Path>();

            Path dir = gallery.getDir();
            if (Files.isDirectory(dir)) {
                try {
                    Files.list(dir).filter(Files::isRegularFile).forEach(files::add);
                } catch (IOException e) {
                    throw new RipPandaException("Could not retrieve directory's files.");
                }
            }

            gallery.setFiles(files);
        }
    }

    /**
     * Processes the gallery to check whether it needs to be marked as unavailable
     * and if yes, does so.
     * 
     * @param gallery  the gallery to process
     * @param document the document indicating whether the gallery could be
     *                 unavailable
     * @return <code>true</code> if the the gallery was successfully marked as
     *         unavailable, <code>false</code> otherwise
     * @throws RipPandaException on failure
     */
    protected boolean processUnavailability(Gallery gallery, Document document) throws RipPandaException {
        if (document.title().contains("Gallery Not Available")) {
            Element messageElement = document.selectFirst(".d > p:first-child");
            if (messageElement != null) {
                markAsUnavailable(gallery, messageElement.text());

                return true;
            }
        }

        return false;
    }

    /**
     * Marks the given gallery as no longer being available online.
     * 
     * @param gallery the gallery to mark
     * @param reason  the reason for the unavailability
     * @throws RipPandaException on failure
     */
    protected void markAsUnavailable(Gallery gallery, String reason) throws RipPandaException {
        LOGGER.warn("Marking the gallery as no longer available according to: {}", reason);

        save(reason, gallery.getDir(), UNAVAILABLE_FILENAME);

        if (gallery.isFilesLoaded()) {
            gallery.getFiles().add(gallery.getDir().resolve(UNAVAILABLE_FILENAME));
        }
    }

    /**
     * Checks whether the given gallery is no longer available online.
     * 
     * @param gallery the gallery to check
     * @return <code>true</code> if the the gallery is unavailable,
     *         <code>false</code> otherwise
     */
    protected boolean isUnavailable(Gallery gallery) {
        return gallery.getFiles().stream().anyMatch(x -> UNAVAILABLE_FILENAME.equals(String.valueOf(x.getFileName())));
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
