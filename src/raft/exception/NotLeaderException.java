/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.exception;

/**
 * Thrown when a client sends a command to a node that is not the leader
 * (or when the cluster currently has no leader at all).
 */
public class NotLeaderException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int leaderHint;

    public NotLeaderException(String message, int leaderHint) {
        super(message);
        this.leaderHint = leaderHint;
    }

    /** @return id of the node believed to be the leader, or -1 if unknown */
    public int getLeaderHint() {
        return leaderHint;
    }
}
