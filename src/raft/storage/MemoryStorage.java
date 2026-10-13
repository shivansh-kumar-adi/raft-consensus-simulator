/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import raft.core.LogEntry;

/** Keeps everything in RAM. It survives a simulated node crash but not a program exit. */
public class MemoryStorage implements Storage {
    private final Map<Integer, PersistentState> states = new HashMap<>();
    private final Map<Integer, List<LogEntry<String>>> logs = new HashMap<>();

    @Override
    public synchronized void saveState(int nodeId, long currentTerm, int votedFor) {
        states.put(nodeId, new PersistentState(currentTerm, votedFor));
    }

    @Override
    public synchronized PersistentState loadState(int nodeId) {
        return states.get(nodeId);
    }

    @Override
    public synchronized void saveLog(int nodeId, List<LogEntry<String>> entries) {
        logs.put(nodeId, new ArrayList<>(entries));
    }

    @Override
    public synchronized List<LogEntry<String>> loadLog(int nodeId) {
        return new ArrayList<>(logs.getOrDefault(nodeId, new ArrayList<>()));
    }

    @Override
    public synchronized void clear() {
        states.clear();
        logs.clear();
    }

    @Override
    public String name() {
        return "memory";
    }
}
