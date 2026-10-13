/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.core;

/** The three roles a Raft node can play. */
public enum NodeState {
    FOLLOWER("Follower"),
    CANDIDATE("Candidate"),
    LEADER("Leader");

    private final String label;

    NodeState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
