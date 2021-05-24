package lovesyk.rippanda.service.archival;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Element;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.service.archival.api.FilesUtils;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.settings.Settings;

/**
 * The abstract archival service for common success file handling.
 */
public abstract class AbstractArchivalService {
    private static final String SUCCESS_FILENAME_PREFIX = "success-";
    private static final String SUCCESS_FILENAME_SUFFIX = ".txt";
    private static final String SUCCESS_FILENAME_FORMAT = SUCCESS_FILENAME_PREFIX + "%s" + SUCCESS_FILENAME_SUFFIX;
    private static final String SUCCESS_TEMP_FILENAME_FORMAT = String.format(SUCCESS_FILENAME_FORMAT, "%s-temp");
    private static final String LINE_ENDING = StringUtils.CR + StringUtils.LF;
    private static final Pattern GALLERY_URL_ID_TOKEN_PATTERN = Pattern.compile("/g/(.+?)/(.+?)(/|\")");

    private static final Logger LOGGER = LogManager.getLogger(AbstractArchivalService.class);

    private Path successFile;
    private Path successTempFile;
    private Map<Path, Set<Integer>> successFileIdsMap;
    private Instant successIdsUpdated;

    private final Settings settings;
    private final Instance<IElementArchivalService> elementArchivalServices;

    /**
     * Constructs a new archival service instance.
     * 
     * @param settings                the application settings
     * @param elementArchivalServices the archival services which should be invoked
     *                                for archiving elements
     */
    @Inject
    public AbstractArchivalService(Settings settings, Instance<IElementArchivalService> elementArchivalServices) {
        this.settings = settings;
        this.elementArchivalServices = elementArchivalServices;
    }

    /**
     * Initializes the instance.
     */
    @PostConstruct
    public void init() {
        initSuccessFiles();
    }

    /**
     * Initializes the success file paths to use for the archival process.
     */
    private void initSuccessFiles() {
        String successFilename = String.format(SUCCESS_FILENAME_FORMAT, getSettings().getSuccessFileId());
        successFile = getSettings().getSuccessDirectory().resolve(successFilename);

        String successTempFilename = String.format(SUCCESS_TEMP_FILENAME_FORMAT, getSettings().getSuccessFileId());
        successTempFile = getSettings().getSuccessDirectory().resolve(successTempFilename);
    }

    /**
     * Initializes the directories specified by the settings and required by this
     * service by creating them if at least the parent directory exists.
     * 
     * @throws RipPandaException on failure
     */
    protected void initDirs() throws RipPandaException {
        LOGGER.trace("Making sure success and archive directories exist...");
        if (!Files.isDirectory(getSettings().getSuccessDirectory().getParent())) {
            throw new RipPandaException("Parent of the success directory does not exist.");
        }
        if (!Files.isDirectory(getSettings().getWritableArchiveDirectory().getParent())) {
            throw new RipPandaException("Parent of the writable archive directory does not exist.");
        }

        try {
            Files.createDirectories(getSettings().getSuccessDirectory());
            Files.createDirectories(getSettings().getWritableArchiveDirectory());
        } catch (IOException e) {
            throw new RipPandaException("Could not create directory.", e);
        }
    }

    /**
     * Initializes the IDs from all available success files to check while
     * processing galleries.
     * <p>
     * A possibly existing temporary success file from previous runs will be
     * deleted.
     * 
     * @throws RipPandaException on failure
     */
    protected void initSuccessIds() throws RipPandaException {
        successFileIdsMap = new HashMap<>();
        successIdsUpdated = Instant.now();

        deleteSuccessTempFile();

        try (Stream<Path> stream = Files.walk(getSettings().getSuccessDirectory()).filter(Files::isRegularFile)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (isSuccessFile(file)) {
                    loadSuccessFile(file);
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Failed reading success file.", e);
        }
    }

    /**
     * Deleted the temporary success file if it exists.
     * 
     * @throws RipPandaException on failure
     */
    protected void deleteSuccessTempFile() throws RipPandaException {
        LOGGER.debug("Cleaning up possibly remaining temporary success file...");
        try {
            Files.deleteIfExists(successTempFile);
        } catch (IOException e) {
            throw new RipPandaException("Could not delete temporary success file.", e);
        }
    }

    /**
     * Updates already initialized success IDs by reloading the respective success
     * files.
     * 
     * @throws RipPandaException on failure
     */
    protected void updateSuccessIds() throws RipPandaException {
        Instant now = Instant.now();

        try (Stream<Path> stream = Files.walk(getSettings().getSuccessDirectory()).filter(Files::isRegularFile)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (isSuccessFile(file) && !file.equals(getSuccessFile()) && !file.equals(getSuccessTempFile())) {
                    loadSuccessFileIfUpdated(file);
                }
            }
        } catch (IOException e) {
            throw new RipPandaException("Failed reading success file.", e);
        }

        LOGGER.trace("Success files up-to-date as of " + now);
        setSuccessIdsUpdated(now);
    }

    /**
     * Checks if the give file is a success file.
     * 
     * @param file the file to check
     * @return <code>true</code> if it is a success file, <code>false</code>
     *         otherwise
     */
    private boolean isSuccessFile(Path file) {
        String filename = file.getFileName().toString();
        boolean result = filename.startsWith(SUCCESS_FILENAME_PREFIX) && filename.endsWith(SUCCESS_FILENAME_SUFFIX);

        LOGGER.trace("File \"" + file + "\" is " + (result ? StringUtils.EMPTY : "not ") + "a success file.");

        return result;
    }

    /**
     * Loads a success file if it was updated since the last time it got loaded.
     * 
     * @param file the success file to load
     * @throws RipPandaException on logical failure
     * @throws IOException       on I/O failure
     */
    private void loadSuccessFileIfUpdated(Path file) throws RipPandaException, IOException {
        FileTime lastModifiedTime = Files.getLastModifiedTime(file);
        if (lastModifiedTime.toInstant().isAfter(getSuccessIdsUpdated())) {
            LOGGER.trace("Success file was updated after " + getSuccessIdsUpdated());
            loadSuccessFile(file);
        } else {
            LOGGER.trace("Success file will not be updated since it was was not updated after " + getSuccessIdsUpdated());
        }
    }

    /**
     * Loads a success file.
     * 
     * @param file the success file to load
     * @throws RipPandaException on logical failure
     * @throws IOException       on I/O failure
     */
    private void loadSuccessFile(Path file) throws RipPandaException, IOException {
        LOGGER.debug("Loading success file: " + file);

        Set<Integer> successIds = new LinkedHashSet<>();
        successFileIdsMap.put(file, successIds);
        try (Stream<String> stream = Files.lines(file)) {
            for (String line : (Iterable<String>) stream::iterator) {
                int id;
                try {
                    id = Integer.valueOf(line);
                } catch (NumberFormatException e) {
                    throw new RipPandaException("Invalid gallery ID.", e);
                }
                successIds.add(id);
            }
        }
    }

    /**
     * Gets the settings.
     * 
     * @return the settings
     */
    protected Settings getSettings() {
        return settings;
    }

    /**
     * Gets all element archival services.
     * 
     * @return all element archival services, never <code>null</code>
     */
    protected Instance<IElementArchivalService> getArchivingServiceList() {
        return elementArchivalServices;
    }

    /**
     * Gets the success file.
     * 
     * @return the success file
     */
    private Path getSuccessFile() {
        return successFile;
    }

    /**
     * Gets the temporary success file.
     * 
     * @return the temporary success file
     */
    private Path getSuccessTempFile() {
        return successTempFile;
    }

    /**
     * Adds a temporary success ID.
     * 
     * @param id the ID to add
     * @throws RipPandaException on failure
     */
    protected void addTempSuccessId(int id) throws RipPandaException {
        LOGGER.debug("Adding gallery ID \"" + id + "\" to temporary success file...");
        try {
            Files.writeString(getSuccessTempFile(), String.valueOf(id) + LINE_ENDING, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RipPandaException("Could not add gallery ID to temporary success file.", e);
        }
    }

    /**
     * Adds a success ID.
     * 
     * @param id the ID to add
     * @throws RipPandaException on failure.
     */
    protected void addSuccessId(int id) throws RipPandaException {
        LOGGER.debug("Adding gallery ID \"" + id + "\" to success file...");
        try {
            Files.writeString(getSuccessFile(), String.valueOf(id) + LINE_ENDING, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RipPandaException("Could not add gallery ID to success file.", e);
        }
    }

    /**
     * Checks if the ID is part of any success file.
     * 
     * @param id the ID to check
     * @return <code>true</code> if the ID is present, <code>false</code> otherwise
     */
    protected boolean isInSuccessIds(int id) {
        for (Set<Integer> successIds : successFileIdsMap.values()) {
            if (successIds.contains(id)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the time of the last time success files were updated.
     * 
     * @return the last time success files were updated
     */
    private Instant getSuccessIdsUpdated() {
        return successIdsUpdated;
    }

    /**
     * Sets a new time for when success files were updated.
     * 
     * @param successIdsUpdated the new time for when success files were updated
     */
    private void setSuccessIdsUpdated(Instant successIdsUpdated) {
        this.successIdsUpdated = successIdsUpdated;
    }

    /**
     * Removes a gallery ID from the users success file if present.
     * 
     * @param id the gallery ID to remove
     * @throws RipPandaException on failure
     */
    protected void removeSuccessId(int id) throws RipPandaException {
        Set<Integer> successIds = successFileIdsMap.get(successFile);
        if (successIds.remove(id)) {
            LOGGER.debug("Removing gallery ID \"{}\" from success file...", id);

            Path successFile = getSuccessFile();
            Path dir = successFile.getParent();
            String fileName = successFile.getFileName().toString();
            FilesUtils.save(this::writeSuccessFile, dir, fileName);
        }
    }

    /**
     * Writes the users success file to the specified path.
     * 
     * @param file the path to save the success file to
     * @throws IOException on failure
     */
    protected void writeSuccessFile(Path file) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(file))) {
            for (Integer successId : successFileIdsMap.get(getSuccessFile())) {
                writer.write(successId + LINE_ENDING);
            }
        }
    }

    /**
     * Parses the gallery ID and token from its URL.
     * 
     * @param linkElement a link element containing the gallery URL, not
     *                    <code>null</null>
     * @return a pair of the gallery ID and token, never <code>null</null>
     * @throws RipPandaException on failure
     */
    protected Pair<Integer, String> parseGalleryUrlIdToken(Element linkElement) throws RipPandaException {
        Matcher matcher = GALLERY_URL_ID_TOKEN_PATTERN.matcher(linkElement.attr("href"));
        if (!matcher.find()) {
            throw new RipPandaException("Could not find gallery ID or token in URL.");
        }

        Integer id;
        try {
            id = Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new RipPandaException("Could not parse gallery ID.", e);
        }
        String token = matcher.group(2);

        return new ImmutablePair<Integer, String>(id, token);
    }
}
