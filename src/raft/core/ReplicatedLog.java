/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The log of a node. Indexes start at 1 (index 0 means "empty log").
 * Backed by an ArrayList and implements Iterable so it can be used in a
 * for-each loop (Unit 4: collections and iterators, Unit 5: generics).
 */
public class ReplicatedLog<T> implements Iterable<LogEntry<T>> {
    private final List<LogEntry<T>> entries = new ArrayList<>();

    public int lastIndex() {
        return entries.size();
    }

    public long lastTerm() {
        return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).getTerm();
    }

    /** @return the term at the given index, or 0 if the index does not exist */
    public long termAt(int index) {
        if (index <= 0 || index > entries.size()) {
            return 0;
        }
        return entries.get(index - 1).getTerm();
    }

    public LogEntry<T> get(int index) {
        return entries.get(index - 1);
    }

    /** Appends a new entry and returns it. */
    public LogEntry<T> append(long term, T command) {
        LogEntry<T> entry = new LogEntry<>(entries.size() + 1, term, command);
        entries.add(entry);
        return entry;
    }

    /** Deletes the entry at fromIndex and everything after it. */
    public void truncateFrom(int fromIndex) {
        while (entries.size() >= fromIndex && !entries.isEmpty()) {
            entries.remove(entries.size() - 1);
        }
    }

    /** Copy of at most max entries starting at fromIndex (inclusive). */
    public List<LogEntry<T>> slice(int fromIndex, int max) {
        List<LogEntry<T>> result = new ArrayList<>();
        for (int i = fromIndex; i <= entries.size() && result.size() < max; i++) {
            result.add(entries.get(i - 1));
        }
        return result;
    }

    public void replaceAll(List<LogEntry<T>> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
    }

    public List<LogEntry<T>> toList() {
        return new ArrayList<>(entries);
    }

    @Override
    public Iterator<LogEntry<T>> iterator() {
        return entries.iterator();
    }
}
