/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.storage;

/** The part of a node's state that Raft requires to survive a crash. */
public class PersistentState {
    public final long currentTerm;
    public final int votedFor; // -1 = nobody

    public PersistentState(long currentTerm, int votedFor) {
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
    }
}
