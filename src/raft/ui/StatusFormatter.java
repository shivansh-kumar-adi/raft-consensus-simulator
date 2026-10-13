/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.ui;

import java.util.List;
import java.util.Map;
import raft.core.LogEntry;
import raft.core.NodeSnapshot;
import raft.net.Network;
import raft.sim.Cluster;
import raft.util.Util;

/** Turns snapshots into text; shared by the console UI and the Swing dashboard. */
public final class StatusFormatter {
    private StatusFormatter() {
    }

    public static String table(Cluster cluster) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s %-10s %-5s %-6s %-7s %-5s %-7s %-8s %s%n",
                "Node", "Role", "Term", "Voted", "Leader", "Log", "Commit", "Applied", "State machine"));
        for (NodeSnapshot s : cluster.snapshots()) {
            sb.append(String.format("%-5s %-10s %-5d %-6s %-7s %-5d %-7d %-8d %s%n",
                    "N" + s.id, s.roleText(), s.term,
                    s.votedFor < 0 ? "-" : "N" + s.votedFor,
                    s.leaderId < 0 ? "-" : "N" + s.leaderId,
                    s.log.size(), s.commitIndex, s.lastApplied, s.stateMachine));
        }
        Network net = cluster.getNetwork();
        sb.append(String.format("Network: sent=%d delivered=%d dropped=%d | loss=%d%% | partition=%s%n",
                net.getSent(), net.getDelivered(), net.getDropped(),
                Math.round(net.getDropRate() * 100), net.describePartition()));
        return sb.toString();
    }

    public static String logs(Cluster cluster) {
        StringBuilder sb = new StringBuilder();
        for (NodeSnapshot s : cluster.snapshots()) {
            sb.append("N").append(s.id).append(" [").append(s.roleText()).append(", term ")
                    .append(s.term).append("]\n");
            if (s.log.isEmpty()) {
                sb.append("   (empty log)\n");
            }
            for (LogEntry<String> e : s.log) {
                boolean committed = e.getIndex() <= s.commitIndex;
                sb.append("   ").append(committed ? "[committed] " : "[pending]   ")
                        .append(e).append("\n");
            }
            sb.append("   state machine: ").append(s.stateMachine).append("\n");
        }
        return sb.toString();
    }

    public static String leaderLine(Cluster cluster) {
        var leader = cluster.findLeader();
        if (leader == null) {
            return "No leader right now";
        }
        return "Leader: N" + leader.getId() + " (term " + leader.getCurrentTerm() + ")";
    }

    public static String stateMachines(List<NodeSnapshot> list) {
        StringBuilder sb = new StringBuilder();
        for (NodeSnapshot s : list) {
            Map<String, String> m = s.stateMachine;
            sb.append("N").append(s.id).append("=").append(Util.join(m.entrySet(), ",")).append(" ");
        }
        return sb.toString();
    }
}
