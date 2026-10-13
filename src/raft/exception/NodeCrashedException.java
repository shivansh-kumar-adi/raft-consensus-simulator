/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.exception;

/**
 * Checked exception thrown when an operation is attempted on a node that is
 * currently crashed (Unit 2: user defined exceptions, throw / throws).
 */
public class NodeCrashedException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int nodeId;

    public NodeCrashedException(int nodeId) {
        super("Node " + nodeId + " is crashed");
        this.nodeId = nodeId;
    }

    public int getNodeId() {
        return nodeId;
    }
}
