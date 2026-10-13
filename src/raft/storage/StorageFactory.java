/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.storage;

import raft.exception.StorageException;
import raft.util.EventLog;

/** Chooses the storage backend from the command line option. */
public final class StorageFactory {
    private StorageFactory() {
    }

    /**
     * @param type "memory", "file", "sqlite" or "auto" (SQLite if the jar is
     *             present, otherwise files)
     */
    public static Storage create(String type, String dataDir, EventLog events) {
        switch (type) {
            case "memory":
                return new MemoryStorage();
            case "file":
                return new FileStorage(dataDir);
            case "sqlite":
            case "auto":
            default:
                try {
                    return new SqliteStorage(dataDir + "/raft.db");
                } catch (StorageException e) {
                    events.log("STORAGE SQLite not available (" + e.getMessage()
                            + "). Falling back to file storage.");
                    return new FileStorage(dataDir);
                }
        }
    }
}
