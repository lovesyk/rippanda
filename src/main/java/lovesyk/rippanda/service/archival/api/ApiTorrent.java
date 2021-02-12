package lovesyk.rippanda.service.archival.api;

import java.time.Instant;

public class ApiTorrent {
    private final String hash;
    private final int torrentSize;
    private final Instant addedDateTime;

    public ApiTorrent(String hash, int torrentSize, Instant addedDateTime) {
        this.hash = hash;
        this.torrentSize = torrentSize;
        this.addedDateTime = addedDateTime;
    }

    public String getHash() {
        return hash;
    }

    public int getTorrentSize() {
        return torrentSize;
    }

    public Instant getAddedDateTime() {
        return addedDateTime;
    }
}
