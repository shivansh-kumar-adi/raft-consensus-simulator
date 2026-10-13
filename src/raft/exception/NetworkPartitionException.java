/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.exception;

/**
 * Thrown internally by the simulated network when a message tries to cross
 * a network partition. The network catches it and drops the message.
 */
public class NetworkPartitionException extends Exception {
    private static final long serialVersionUID = 1L;

    public NetworkPartitionException(int from, int to) {
        super("Link " + from + " -> " + to + " is cut by a network partition");
    }
}
