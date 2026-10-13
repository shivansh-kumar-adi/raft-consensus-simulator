/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.core;

import java.util.List;
import java.util.Map;

/**
 * An immutable photograph of a node at one moment. The GUI / CLI / tests read
 * snapshots instead of touching the live node, which keeps them thread safe.
 */
public class NodeSnapshot {
    public final int id;
    public final NodeState state;
    public final boolean crashed;
    public final long term;
    public final int votedFor;
    public final int leaderId;
    public final int commitIndex;
    public final int lastApplied;
    public final List<LogEntry<String>> log;
    public final Map<String, String> stateMachine;

    public NodeSnapshot(int id, NodeState state, boolean crashed, long term, int votedFor, int leaderId,
                        int commitIndex, int lastApplied, List<LogEntry<String>> log,
                        Map<String, String> stateMachine) {
        this.id = id;
        this.state = state;
        this.crashed = crashed;
        this.term = term;
        this.votedFor = votedFor;
        this.leaderId = leaderId;
        this.commitIndex = commitIndex;
        this.lastApplied = lastApplied;
        this.log = log;
        this.stateMachine = stateMachine;
    }

    public String roleText() {
        return crashed ? "CRASHED" : state.getLabel();
    }
}
