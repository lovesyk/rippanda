package lovesyk.rippanda.service.archival;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lovesyk.rippanda.exception.RipPandaException;
import lovesyk.rippanda.service.archival.api.IArchivalService;
import lovesyk.rippanda.service.archival.element.api.IElementArchivalService;
import lovesyk.rippanda.service.web.api.IWebClient;
import lovesyk.rippanda.settings.OperationMode;
import lovesyk.rippanda.settings.Settings;

/**
 * The service responsible for the cleanup of outdated galleries.
 */
@ApplicationScoped
public class CleanupModeService extends AbstractArchivalService implements IArchivalService {
    private static final Logger LOGGER = LogManager.getLogger(CleanupModeService.class);

    private static final String PAGE_FILENAME = "page.html";
    private static final String EXPUNGELOG_FILENAME = "expungelog.html";
    private static final Pattern GALLERY_URL_PATTERN = Pattern.compile("http(s)://.+?/g/(\\d+)/");

    private IWebClient webClient;

    /**
     * Constructs a new cleanup mode service instance.
     * 
     * @param settings                the application settings
     * @param webClient               the web client
     * @param elementArchivalServices the archival services which should be invoked
     *                                for archiving elements
     */
    @Inject
    public CleanupModeService(Settings settings, IWebClient webClient,
            Instance<IElementArchivalService> elementArchivalServices) {
        super(settings, elementArchivalServices);
        this.webClient = webClient;
    }

    /**
     * Initializes the instance.
     */
    @PostConstruct
    public void init() {
        super.init();
    }

    /**
     * {@inheritDoc}
     */
    public void process() throws RipPandaException, InterruptedException {
        if (isRequired()) {
            LOGGER.info("Activating cleanup mode.");
            run();
        }
    }

    /**
     * Checks if the cleanup mode should be executed based in application settings.
     * 
     * @return <code>true</code> if cleanup mode is enabled, <code>false</false>
     *         otherwise.
     */
    private boolean isRequired() {
        return getSettings().getOperationMode() == OperationMode.CLEANUP;
    }

    /**
     * Runs the cleanup mode service.
     * 
     * @throws RipPandaException    on failure
     * @throws InterruptedException on interruption
     */
    public void run() throws RipPandaException, InterruptedException {
        initDirs();
        initSuccessIds();

        Map<Integer, Set<Path>> galleryIdToRemovableDirectoryMap = new HashMap<>();
        Set<Integer> parentIds = new HashSet<Integer>();
        Map<Integer, Set<Integer>> childToParentIdMap = new HashMap<>();
        Map<Integer, Set<Integer>> parentToConflictIdMap = new HashMap<>();

        for (Path archiveDirectory : getSettings().getArchiveDirectories()) {
            LOGGER.debug("Looking through directory \"{}\"...", archiveDirectory);
            try (Stream<Path> stream = Files.walk(archiveDirectory).filter(Files::isDirectory)) {
                for (Path directory : (Iterable<Path>) stream::iterator) {
                    process(directory, galleryIdToRemovableDirectoryMap, parentIds, childToParentIdMap,
                            parentToConflictIdMap);
                }
            } catch (IOException e) {
                throw new RipPandaException("Could not traverse the given archive directory.", e);
            }
        }

        cleanupGalleries(galleryIdToRemovableDirectoryMap, parentIds, childToParentIdMap, parentToConflictIdMap);
    }

    /**
     * Process a directory in case it contains a gallery by memorizing relevant data
     * for the cleanup process.
     * 
     * @param directory               the directory to process, not
     *                                <code>null</null>
     * @param galleryIdToDirectoryMap the map storing ID to removable gallery
     *                                relationships, not <code>null</null>
     * @param parentIds               all gallery IDs deemed as being parents, not
     *                                <code>null</null>
     * @param childToParentIdMap      the map storing child to parent gallery ID
     *                                relationship, not <code>null</null>
     * @param parentToConflictIdMap   the map storing child to conflicting gallery
     *                                ID
     *                                relationship, not <code>null</null>
     * @throws RipPandaException on failure
     */
    private void process(Path directory, Map<Integer, Set<Path>> galleryIdToDirectoryMap, Set<Integer> parentIds,
            Map<Integer, Set<Integer>> childToParentIdMap, Map<Integer, Set<Integer>> parentToConflictIdMap)
            throws RipPandaException {
        Path pageFile = directory.resolve(PAGE_FILENAME);
        if (Files.exists(pageFile)) {
            LOGGER.info("Processing gallery in directory: \"{}\"", directory);
            Document page = null;
            try {
                page = getWebClient().loadDocument(pageFile);
            } catch (RipPandaException e) {
                LOGGER.warn("Failed reading file, it will be skipped.", e);
            }

            if (page != null) {
                Integer id = memorizeGalleryId(directory, galleryIdToDirectoryMap, page);
                memorizeParentId(parentIds, page);
                memorizeChildIds(id, childToParentIdMap, page);

                processExpungeLog(directory, id, parentToConflictIdMap);
            }
        } else {
            LOGGER.debug("Directory does not appear to contain a gallery: \"{}\"", directory);
        }
    }

    /**
     * Memorizes the gallery ID of the given directory.
     * <p>
     * Directory will only be memorized in case it is allowed to be removed.
     * 
     * @param directory               the directory of the gallery, not
     *                                <code>null</null>
     * @param galleryIdToDirectoryMap the map storing ID to removable gallery
     *                                relationships, not <code>null</null>
     * @param page                    the parsed HTML page, not <code>null</null>
     * @return the gallery ID, never <code>null</null>
     * @throws RipPandaException on failure
     */
    private Integer memorizeGalleryId(Path directory, Map<Integer, Set<Path>> galleryIdToDirectoryMap, Document page)
            throws RipPandaException {
        Element reportElement = page.selectFirst("#gd5 > .g3 > a");
        if (reportElement == null) {
            throw new RipPandaException("Could not find report element.");
        }
        Integer id = parseGalleryUrlIdToken(reportElement).getLeft();

        Set<Path> writableDirectories = galleryIdToDirectoryMap.computeIfAbsent(id, directories -> new HashSet<Path>());
        if (directory.startsWith(getSettings().getWritableArchiveDirectory())) {
            LOGGER.debug("Memorizing gallery ID {} with the directory as possibly removable.", id);
            writableDirectories.add(directory);
        } else {
            LOGGER.debug("Memorizing gallery ID {} with the directory as not removable.", id);
        }

        return id;
    }

    /**
     * Memorizes the parent ID of the gallery if available.
     * 
     * @param parentIds all gallery IDs deemed as being parents, not
     *                  <code>null</null>
     * @param page      the parsed gallery HTML page, not <code>null</null>
     * @throws RipPandaException on failure
     */
    private void memorizeParentId(Set<Integer> parentIds, Document page) throws RipPandaException {
        Element parentHeaderElement = page.selectFirst(".gdt1:contains(Parent:)");
        if (parentHeaderElement == null) {
            throw new RipPandaException("Parent header element not found");
        }

        Element parentElement = parentHeaderElement.parent().selectFirst(".gdt2 > a");
        if (parentElement != null) {
            Integer parentId;
            try {
                parentId = Integer.valueOf(parentElement.text());
            } catch (NumberFormatException e) {
                throw new RipPandaException("Could not parse gallery ID.", e);
            }

            LOGGER.debug("Memorizing parent gallery ID {}...", parentId);
            parentIds.add(parentId);
        }
    }

    /**
     * Memorizes all child IDs of the gallery if available as well as their
     * relationship to the current gallery.
     * 
     * @param id                 the ID of the current gallery, not
     *                           <code>null</null>
     * @param childToParentIdMap the map storing child to parent gallery ID
     *                           relationship, not <code>null</null>
     * @param page               the parsed gallery HTML page, not <code>null</null>
     * @throws RipPandaException on failure
     */
    private void memorizeChildIds(Integer id, Map<Integer, Set<Integer>> childToParentIdMap, Document page)
            throws RipPandaException {
        for (Element childElement : page.select("#gnd > a")) {
            int childId = parseGalleryUrlIdToken(childElement).getLeft();

            LOGGER.debug("Memorizing child gallery ID {}...", childId);
            childToParentIdMap.computeIfAbsent(childId, ids -> new HashSet<Integer>()).add(id);
        }
    }

    /**
     * Process the gallery expunge log if available by memorizing relevant data for
     * the
     * cleanup process.
     * 
     * @param directory             the directory being processed, not
     *                              <code>null</null>
     * @param id                    the gallery ID being processed
     * @param parentToConflictIdMap the map storing parent to conflicting gallery ID
     *                              relationship, not <code>null</null>
     * @throws RipPandaException on failure
     */
    private void processExpungeLog(Path directory, int id, Map<Integer, Set<Integer>> parentToConflictIdMap)
            throws RipPandaException {
        Path expungeLogFile = directory.resolve(EXPUNGELOG_FILENAME);
        if (Files.exists(expungeLogFile)) {
            LOGGER.debug("Processing gallery expunge log...");
            Document expungeLog = null;
            try {
                expungeLog = getWebClient().loadDocument(expungeLogFile);
            } catch (RipPandaException e) {
                LOGGER.warn("Failed reading file, it will be skipped.", e);
            }

            if (expungeLog != null) {
                memorizeConflictIds(id, parentToConflictIdMap, expungeLog);
            }
        }
    }

    /**
     * Memorizes all conflicting IDs of the gallery if available as well as their
     * relationship to the current gallery.
     * 
     * @param id                    the ID of the current gallery
     * @param parentToConflictIdMap the map storing parent to conflicting gallery ID
     *                              relationship, not <code>null</null>
     * @param page                  the parsed gallery HTML page, not
     *                              <code>null</null>
     * @throws RipPandaException on failure
     */
    private void memorizeConflictIds(int id, Map<Integer, Set<Integer>> parentToConflictIdMap, Document page)
            throws RipPandaException {
        if (page.selectFirst(".exp_outer:contains(administratively expunged)") == null) {
            Element expungeTable = page.selectFirst(".exp_table");
            Matcher matcher = GALLERY_URL_PATTERN.matcher(expungeTable.html());
            while (matcher.find()) {
                Integer conflictingId;
                try {
                    conflictingId = Integer.valueOf(matcher.group(2));
                } catch (NumberFormatException e) {
                    throw new RipPandaException("Could not parse gallery ID.", e);
                }

                // exclude the case where the description contains its own gallery ID
                if (conflictingId != id) {
                    LOGGER.debug("Memorizing conflicting gallery ID {}...", conflictingId);
                    parentToConflictIdMap.computeIfAbsent(id, conflictingIds -> new HashSet<Integer>())
                            .add(conflictingId);
                }
            }
        }
    }

    /**
     * Cleans up galleries using the data collected previously.
     * 
     * @param galleryIdToDirectoryMap the map storing ID to removable gallery
     *                                relationships, not <code>null</null>
     * @param parentIds               all gallery IDs deemed as being parents, not
     *                                <code>null</null>
     * @param childToParentIdMap      the map storing child to parent gallery ID
     *                                relationship, not <code>null</null>
     * @param parentToConflictIdMap   the map storing parent to conflicting gallery
     *                                ID
     *                                relationship, not <code>null</null>
     * @throws RipPandaException on failure
     */
    private void cleanupGalleries(Map<Integer, Set<Path>> galleryIdToRemovableDirectoryMap, Set<Integer> parentIds,
            Map<Integer, Set<Integer>> childToParentIdMap, Map<Integer, Set<Integer>> parentToConflictIdMap)
            throws RipPandaException {
        LOGGER.info("Running cleaning process...");

        long totalBytesSaved = 0l;
        for (Integer id : galleryIdToRemovableDirectoryMap.keySet()) {
            LOGGER.debug("Processing gallery ID {}...", id);

            Set<Integer> outdatedIds = new HashSet<>();
            if (parentIds.remove(id)) {
                LOGGER.debug("Gallery was marked as parent of another gallery and will be attempted to be removed.");
                outdatedIds.add(id);
            }

            Set<Integer> outdatedParentIds = childToParentIdMap.remove(id);
            if (outdatedParentIds != null) {
                LOGGER.debug(
                        "Gallery IDs {} were marked as parents of this gallery and will be attempted to be removed.",
                        outdatedParentIds);
                outdatedIds.addAll(outdatedParentIds);
            }

            Set<Integer> conflictIds = parentToConflictIdMap.remove(id);
            if (conflictIds != null) {
                Integer conflictingId = conflictIds.stream().filter(galleryIdToRemovableDirectoryMap::containsKey)
                        .findAny().orElse(null);
                if (conflictingId != null) {
                    LOGGER.debug(
                            "Gallery was marked as conflicting with at least gallery ID {} and will be attempted to be removed.",
                            conflictingId);
                    outdatedIds.add(id);
                }
            }

            for (Integer outdatedId : outdatedIds) {
                Set<Path> outdatedDirectories = galleryIdToRemovableDirectoryMap.get(outdatedId);
                for (Path outdatedDirectory : outdatedDirectories) {
                    File outdatedDirectoryLegacy = outdatedDirectory.toFile();
                    long bytesSaved = FileUtils.sizeOfDirectory(outdatedDirectoryLegacy);
                    totalBytesSaved += bytesSaved;

                    try {
                        FileUtils.deleteDirectory(outdatedDirectoryLegacy);
                    } catch (IOException e) {
                        throw new RipPandaException("Failed removing directory.", e);
                    }

                    LOGGER.info("Saved {} by removing: {}", FileUtils.byteCountToDisplaySize(bytesSaved),
                            outdatedDirectories);
                }
                outdatedDirectories.clear();

                removeSuccessId(outdatedId);
            }
        }

        LOGGER.info("Cleaned up {}.", FileUtils.byteCountToDisplaySize(totalBytesSaved));
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
