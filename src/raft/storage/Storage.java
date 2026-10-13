/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.storage;

import java.util.List;
import raft.core.LogEntry;

/**
 * Persistence layer of a node. Raft says that currentTerm, votedFor and the
 * log must survive crashes. Three implementations exist: in-memory, plain
 * files (Java I/O) and SQLite (JDBC).
 */
public interface Storage {
    void saveState(int nodeId, long currentTerm, int votedFor);

    /** @return the stored state or null if nothing was saved yet */
    PersistentState loadState(int nodeId);

    void saveLog(int nodeId, List<LogEntry<String>> entries);

    List<LogEntry<String>> loadLog(int nodeId);

    /** Deletes everything (fresh start). */
    void clear();

    String name();

    default void close() {
    }
}
