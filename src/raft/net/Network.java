/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.net;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import raft.core.RaftConfig;
import raft.core.RaftNode;
import raft.exception.NetworkPartitionException;
import raft.util.EventLog;

/**
 * Simulated network. Delivers messages between nodes of the same JVM with a
 * random delay, and can drop messages randomly or because of a partition.
 * No sockets are needed, so the whole simulation runs with plain Java.
 */
public class Network {
    private final Map<Integer, RaftNode> nodes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Random random = new Random();
    private final EventLog events;

    private volatile double dropRate = 0.0;
    private volatile int minDelay;
    private volatile int maxDelay;
    private volatile boolean trace = false;
    private volatile Map<Integer, Integer> groupOf = new HashMap<>(); // empty = no partition

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    public Network(RaftConfig config, EventLog events) {
        this.events = events;
        this.minDelay = config.minNetworkDelay;
        this.maxDelay = config.maxNetworkDelay;
        this.scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread t = new Thread(runnable, "network-delivery");
            t.setDaemon(true);
            return t;
        });
    }

    public void register(RaftNode node) {
        nodes.put(node.getId(), node);
    }

    /** Sends a message: it may be dropped, otherwise it is delivered after a random delay. */
    public void send(Message message) {
        sent.incrementAndGet();
        try {
            checkLink(message.getFrom(), message.getTo());
        } catch (NetworkPartitionException e) {
            dropped.incrementAndGet();
            if (trace) {
                events.log("NET  dropped (partition): " + message.describe());
            }
            return;
        }
        if (random.nextDouble() < dropRate) {
            dropped.incrementAndGet();
            if (trace) {
                events.log("NET  dropped (random loss): " + message.describe());
            }
            return;
        }
        int span = Math.max(0, maxDelay - minDelay);
        int delay = minDelay + (span == 0 ? 0 : random.nextInt(span + 1));
        if (trace) {
            events.log("NET  " + message.describe());
        }
        try {
            scheduler.schedule(() -> deliver(message), delay, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            dropped.incrementAndGet(); // scheduler already shut down
        }
    }

    private void deliver(Message message) {
        try {
            checkLink(message.getFrom(), message.getTo()); // partition may appear while in flight
        } catch (NetworkPartitionException e) {
            dropped.incrementAndGet();
            return;
        }
        RaftNode target = nodes.get(message.getTo());
        if (target == null || target.isCrashed()) {
            dropped.incrementAndGet(); // a crashed node receives nothing
            return;
        }
        target.receive(message);
        delivered.incrementAndGet();
    }

    private void checkLink(int from, int to) throws NetworkPartitionException {
        Map<Integer, Integer> groups = groupOf;
        if (groups.isEmpty()) {
            return;
        }
        int a = groups.getOrDefault(from, -1);
        int b = groups.getOrDefault(to, -1);
        if (a != b) {
            throw new NetworkPartitionException(from, to);
        }
    }

    /** Splits the cluster: nodes can only talk to nodes in their own group. */
    public void partition(List<Set<Integer>> groups) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int g = 0; g < groups.size(); g++) {
            for (int id : groups.get(g)) {
                map.put(id, g);
            }
        }
        groupOf = map;
        events.log("NET  *** PARTITION created: " + describePartition());
    }

    public void heal() {
        groupOf = new HashMap<>();
        events.log("NET  *** partition healed, all nodes can talk again");
    }

    public boolean isPartitioned() {
        return !groupOf.isEmpty();
    }

    /** @return partition group number of a node, or -1 when there is no partition */
    public int groupOfNode(int id) {
        Map<Integer, Integer> groups = groupOf;
        return groups.isEmpty() ? -1 : groups.getOrDefault(id, -1);
    }

    public String describePartition() {
        Map<Integer, Integer> groups = groupOf;
        if (groups.isEmpty()) {
            return "none";
        }
        Map<Integer, List<Integer>> byGroup = new java.util.TreeMap<>();
        for (Map.Entry<Integer, Integer> e : new java.util.TreeMap<>(groups).entrySet()) {
            byGroup.computeIfAbsent(e.getValue(), k -> new java.util.ArrayList<>()).add(e.getKey());
        }
        StringBuilder out = new StringBuilder();
        for (List<Integer> members : byGroup.values()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append("{").append(raft.util.Util.join(members, ",")).append("}");
        }
        return out.toString();
    }

    public void setDropRate(double rate) {
        this.dropRate = Math.max(0.0, Math.min(1.0, rate));
    }

    public double getDropRate() {
        return dropRate;
    }

    public void setDelay(int min, int max) {
        this.minDelay = Math.max(0, min);
        this.maxDelay = Math.max(this.minDelay, max);
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    public long getSent() {
        return sent.get();
    }

    public long getDelivered() {
        return delivered.get();
    }

    public long getDropped() {
        return dropped.get();
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
