/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.sim;

import raft.core.NodeSnapshot;
import raft.core.RaftNode;
import raft.exception.NodeCrashedException;
import raft.exception.NotLeaderException;
import raft.ui.StatusFormatter;

/**
 * Scripted walk-through of everything the simulator can do. Run it with
 * "--demo". Perfect for the presentation: it prints each step and checks the
 * result.
 */
public class DemoScenario {
    private final Cluster cluster;

    public DemoScenario(Cluster cluster) {
        this.cluster = cluster;
    }

    private void step(String title) {
        System.out.println();
        System.out.println("==================================================");
        System.out.println(" " + title);
        System.out.println("==================================================");
    }

    private void send(String command) throws NotLeaderException, NodeCrashedException {
        int index = cluster.submit(command);
        System.out.println(">> client sent '" + command + "' (log index " + index + ")");
    }

    public boolean run() throws Exception {
        boolean ok = true;

        step("STEP 1: Leader election - nodes start as followers, one becomes leader");
        ok &= check("a leader was elected", cluster.waitForLeader(15000));
        RaftNode firstLeader = cluster.findLeader();

        step("STEP 2: Log replication - client commands are copied to all nodes");
        send("SET x 10");
        send("SET y 20");
        ok &= check("all nodes converged", cluster.waitForConvergence(15000));
        System.out.print(StatusFormatter.table(cluster));

        step("STEP 3: Fault tolerance - crash the leader, a new one is elected");
        int crashed = cluster.crashLeader();
        ok &= check("new leader elected", cluster.waitForLeader(15000));
        ok &= check("new leader is a different node", cluster.findLeader().getId() != crashed);
        send("SET z 30");
        ok &= check("remaining nodes converged", cluster.waitForConvergence(15000));
        System.out.print(StatusFormatter.table(cluster));

        step("STEP 4: Recovery - old leader restarts and catches up");
        cluster.recover(crashed);
        ok &= check("recovered node caught up", cluster.waitForConvergence(15000));
        System.out.print(StatusFormatter.table(cluster));

        step("STEP 5: Network partition - old leader is cut off with one follower");
        RaftNode leader = cluster.findLeader();
        int buddy = leader.getId() % cluster.size() + 1;
        java.util.List<java.util.Set<Integer>> groups = new java.util.ArrayList<>();
        java.util.Set<Integer> minority = new java.util.HashSet<>();
        minority.add(leader.getId());
        minority.add(buddy);
        java.util.Set<Integer> majority = new java.util.HashSet<>();
        for (int id = 1; id <= cluster.size(); id++) {
            if (!minority.contains(id)) {
                majority.add(id);
            }
        }
        groups.add(minority);
        groups.add(majority);
        cluster.partition(groups);
        System.out.println(">> sending a command directly to the isolated old leader N" + leader.getId());
        try {
            leader.submit("SET stale 1");
        } catch (NotLeaderException | NodeCrashedException e) {
            System.out.println("   rejected: " + e.getMessage());
        }
        boolean newLeader = cluster.waitUntil(() -> {
            RaftNode l = cluster.findLeader();
            return l != null && majority.contains(l.getId());
        }, 20000);
        ok &= check("majority side elected its own leader", newLeader);
        send("SET w 99");
        Thread.sleep(1500);
        NodeSnapshot oldLeader = leader.snapshot();
        ok &= check("isolated leader could NOT commit 'SET stale 1'",
                !oldLeader.stateMachine.containsKey("stale"));
        System.out.print(StatusFormatter.table(cluster));

        step("STEP 6: Heal the partition - the stale entry is discarded");
        cluster.heal();
        ok &= check("cluster converged again", cluster.waitForConvergence(20000));
        ok &= check("stale command never reached the state machine",
                !cluster.findLeader().snapshot().stateMachine.containsKey("stale"));
        System.out.print(StatusFormatter.logs(cluster));

        step(ok ? "DEMO FINISHED: all checks passed" : "DEMO FINISHED: some checks FAILED");
        return ok;
    }

    private boolean check(String what, boolean result) {
        System.out.println((result ? "   [PASS] " : "   [FAIL] ") + what);
        return result;
    }
}
