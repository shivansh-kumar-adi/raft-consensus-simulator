/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import raft.core.KeyValueStateMachine;
import raft.core.LogEntry;
import raft.core.NodeSnapshot;
import raft.core.RaftConfig;
import raft.core.RaftNode;
import raft.exception.NodeCrashedException;
import raft.exception.NotLeaderException;
import raft.net.Network;
import raft.storage.Storage;
import raft.util.EventLog;

/**
 * Creates the nodes, starts their threads and gives the user interfaces one
 * simple place to inject failures (crash, recover, partition, packet loss)
 * and to send client commands.
 */
public class Cluster {
    private final List<RaftNode> nodes = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();
    private final Network network;
    private final Storage storage;
    private final EventLog events;
    private final RaftConfig config;

    public Cluster(int size, RaftConfig config, Storage storage, EventLog events) {
        this.config = config;
        this.storage = storage;
        this.events = events;
        this.network = new Network(config, events);
        for (int id = 1; id <= size; id++) {
            List<Integer> peers = new ArrayList<>();
            for (int other = 1; other <= size; other++) {
                if (other != id) {
                    peers.add(other);
                }
            }
            RaftNode node = new RaftNode(id, peers, network, storage, new KeyValueStateMachine(), config, events);
            nodes.add(node);
            network.register(node);
        }
    }

    public void start() {
        events.log("Cluster starting with " + nodes.size() + " nodes, storage: " + storage.name());
        for (RaftNode node : nodes) {
            Thread t = new Thread(node, "raft-node-" + node.getId());
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
    }

    public void stop() {
        for (RaftNode node : nodes) {
            node.stop();
        }
        for (Thread t : threads) {
            try {
                t.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        network.shutdown();
        storage.close();
    }

    public int size() {
        return nodes.size();
    }

    public Network getNetwork() {
        return network;
    }

    public EventLog getEvents() {
        return events;
    }

    public RaftConfig getConfig() {
        return config;
    }

    public RaftNode getNode(int id) {
        if (id < 1 || id > nodes.size()) {
            throw new IllegalArgumentException("No such node: " + id + " (valid: 1.." + nodes.size() + ")");
        }
        return nodes.get(id - 1);
    }

    // ------------------------------------------------------------------
    // Client interface
    // ------------------------------------------------------------------

    /** @return the live leader with the highest term, or null if there is none */
    public RaftNode findLeader() {
        RaftNode best = null;
        long bestTerm = -1;
        for (RaftNode node : nodes) {
            if (node.isLeader() && node.getCurrentTerm() > bestTerm) {
                best = node;
                bestTerm = node.getCurrentTerm();
            }
        }
        return best;
    }

    /** Sends a command to the current leader. */
    public int submit(String command) throws NotLeaderException, NodeCrashedException {
        RaftNode leader = findLeader();
        if (leader == null) {
            throw new NotLeaderException("The cluster currently has no leader", -1);
        }
        return leader.submit(command);
    }

    // ------------------------------------------------------------------
    // Failure injection
    // ------------------------------------------------------------------

    public void crash(int id) {
        getNode(id).crash();
    }

    public void recover(int id) {
        getNode(id).recover();
    }

    /** Crashes the current leader and returns its id (or -1 if there is none). */
    public int crashLeader() {
        RaftNode leader = findLeader();
        if (leader == null) {
            return -1;
        }
        leader.crash();
        return leader.getId();
    }

    public void partition(List<Set<Integer>> groups) {
        network.partition(groups);
    }

    public void heal() {
        network.heal();
    }

    // ------------------------------------------------------------------
    // Observation helpers (used by UI, demo and tests)
    // ------------------------------------------------------------------

    public List<NodeSnapshot> snapshots() {
        List<NodeSnapshot> list = new ArrayList<>();
        for (RaftNode node : nodes) {
            list.add(node.snapshot());
        }
        return list;
    }

    /** Polls the condition every 20 ms until it is true or the timeout expires. */
    public boolean waitUntil(BooleanSupplier condition, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    public boolean waitForLeader(long timeoutMs) {
        return waitUntil(() -> findLeader() != null, timeoutMs);
    }

    /**
     * True when there is a leader and every live node has an identical log,
     * the same commit index and the same state machine content.
     */
    public boolean isConverged() {
        if (findLeader() == null) {
            return false;
        }
        NodeSnapshot reference = null;
        for (NodeSnapshot s : snapshots()) {
            if (s.crashed) {
                continue;
            }
            if (reference == null) {
                reference = s;
                continue;
            }
            if (s.commitIndex != reference.commitIndex || s.log.size() != reference.log.size()
                    || !s.stateMachine.equals(reference.stateMachine)) {
                return false;
            }
            for (int i = 0; i < s.log.size(); i++) {
                LogEntry<String> a = s.log.get(i);
                LogEntry<String> b = reference.log.get(i);
                if (a.getTerm() != b.getTerm() || !a.getCommand().equals(b.getCommand())) {
                    return false;
                }
            }
        }
        return reference != null && reference.commitIndex == reference.log.size();
    }

    public boolean waitForConvergence(long timeoutMs) {
        return waitUntil(this::isConverged, timeoutMs);
    }
}
