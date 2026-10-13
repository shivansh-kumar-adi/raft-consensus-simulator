/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.core;

/**
 * One entry of the replicated log. Generic over the command type T
 * (Unit 5: generic class). In this project T is always String.
 */
public class LogEntry<T> {
    private final int index;   // position in the log, starting from 1
    private final long term;   // term in which the leader created the entry
    private final T command;   // the client command

    public LogEntry(int index, long term, T command) {
        this.index = index;
        this.term = term;
        this.command = command;
    }

    public int getIndex() {
        return index;
    }

    public long getTerm() {
        return term;
    }

    public T getCommand() {
        return command;
    }

    @Override
    public String toString() {
        return "#" + index + "(t" + term + ") " + command;
    }
}
