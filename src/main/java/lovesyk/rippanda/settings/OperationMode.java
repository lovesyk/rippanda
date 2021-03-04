package lovesyk.rippanda.settings;

/**
 * The operation mode of the application.
 */
public enum OperationMode {
    /**
     * Download mode results in the archival of new galleries.
     */
    DOWNLOAD,

    /**
     * Update mode results in already archived galleries being updated.
     */
    UPDATE,

    /**
     * Cleanup mode results in outdated galleries being removed.
     */
    CLEANUP
}
