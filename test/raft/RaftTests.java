/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import raft.core.NodeSnapshot;
import raft.core.NodeState;
import raft.core.RaftConfig;
import raft.core.RaftNode;
import raft.exception.NodeCrashedException;
import raft.exception.NotLeaderException;
import raft.sim.Cluster;
import raft.storage.FileStorage;
import raft.storage.MemoryStorage;
import raft.storage.Storage;
import raft.util.EventLog;

/**
 * Automated tests (plain Java, no JUnit needed).
 * Run: ./run-tests.sh   (or: java -cp out raft.RaftTests)
 */
public class RaftTests {
    private static int passed = 0;
    private static int failed = 0;

    // election safety: at most one leader per term, checked continuously
    private static volatile boolean sampling;
    private static final Map<Long, Integer> leaderOfTerm = new HashMap<>();
    private static volatile String safetyViolation = null;

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  [PASS] " + name);
        } else {
            failed++;
            System.out.println("  [FAIL] " + name);
        }
    }

    private static Cluster newCluster(int size, Storage storage) {
        EventLog events = new EventLog();
        Cluster cluster = new Cluster(size, RaftConfig.fast(), storage, events);
        cluster.start();
        startSampler(cluster);
        return cluster;
    }

    private static void startSampler(Cluster cluster) {
        sampling = true;
        leaderOfTerm.clear();
        Thread t = new Thread(() -> {
            while (sampling) {
                for (NodeSnapshot s : cluster.snapshots()) {
                    if (!s.crashed && s.state == NodeState.LEADER) {
                        Integer known = leaderOfTerm.putIfAbsent(s.term, s.id);
                        if (known != null && known != s.id) {
                            safetyViolation = "two leaders in term " + s.term + ": N" + known + " and N" + s.id;
                        }
                    }
                }
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void finish(Cluster cluster) {
        sampling = false;
        check("election safety (never two leaders in one term)", safetyViolation == null);
        safetyViolation = null;
        cluster.stop();
    }

    // ------------------------------------------------------------------

    static void testElection() throws Exception {
        System.out.println("Test 1: leader election");
        Cluster c = newCluster(5, new MemoryStorage());
        check("a leader is elected", c.waitForLeader(5000));
        Thread.sleep(500);
        int leaders = 0;
        for (NodeSnapshot s : c.snapshots()) {
            if (s.state == NodeState.LEADER) {
                leaders++;
            }
        }
        check("exactly one leader after stabilising", leaders == 1);
        finish(c);
    }

    static void testReplication() throws Exception {
        System.out.println("Test 2: log replication");
        Cluster c = newCluster(5, new MemoryStorage());
        c.waitForLeader(5000);
        for (int i = 1; i <= 10; i++) {
            c.submit("SET k" + i + " v" + i);
        }
        check("all nodes converge", c.waitForConvergence(5000));
        NodeSnapshot s = c.getNode(3).snapshot();
        check("state machine has all 10 keys", s.stateMachine.size() == 10);
        check("a value is correct", "v7".equals(s.stateMachine.get("k7")));
        finish(c);
    }

    static void testLeaderCrashAndRecovery() throws Exception {
        System.out.println("Test 3: leader crash, re-election, recovery");
        Cluster c = newCluster(5, new MemoryStorage());
        c.waitForLeader(5000);
        c.submit("SET a 1");
        c.waitForConvergence(5000);
        int oldLeader = c.crashLeader();
        check("a new leader appears", c.waitUntil(() -> c.findLeader() != null, 5000));
        check("new leader is another node", c.findLeader().getId() != oldLeader);
        c.submit("SET b 2");
        check("remaining nodes converge", c.waitForConvergence(5000));
        c.recover(oldLeader);
        check("recovered node catches up", c.waitForConvergence(5000));
        NodeSnapshot s = c.getNode(oldLeader).snapshot();
        check("recovered node has both keys", s.stateMachine.size() == 2);
        finish(c);
    }

    static void testMinorityPartition() throws Exception {
        System.out.println("Test 4: minority partition cannot commit, entry is discarded after heal");
        Cluster c = newCluster(5, new MemoryStorage());
        c.waitForLeader(5000);
        c.submit("SET base 1");
        c.waitForConvergence(5000);
        RaftNode leader = c.findLeader();
        int buddy = leader.getId() % 5 + 1;
        Set<Integer> minority = new HashSet<>();
        minority.add(leader.getId());
        minority.add(buddy);
        Set<Integer> majority = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            if (!minority.contains(i)) {
                majority.add(i);
            }
        }
        List<Set<Integer>> groups = new ArrayList<>();
        groups.add(minority);
        groups.add(majority);
        c.partition(groups);
        try {
            leader.submit("SET lost 1");
        } catch (NotLeaderException | NodeCrashedException e) {
            // acceptable if it stepped down already
        }
        check("majority elects a new leader",
                c.waitUntil(() -> c.findLeader() != null && majority.contains(c.findLeader().getId()), 5000));
        c.submit("SET won 1");
        Thread.sleep(500);
        check("isolated leader did not commit the lost entry",
                !leader.snapshot().stateMachine.containsKey("lost"));
        c.heal();
        check("cluster converges after heal", c.waitForConvergence(8000));
        check("lost entry is gone everywhere", !c.findLeader().snapshot().stateMachine.containsKey("lost"));
        check("majority entry survived", "1".equals(c.getNode(leader.getId()).snapshot().stateMachine.get("won")));
        finish(c);
    }

    static void testNoQuorum() throws Exception {
        System.out.println("Test 5: no majority -> no leader; works again after recovery");
        Cluster c = newCluster(5, new MemoryStorage());
        c.waitForLeader(5000);
        int a = c.crashLeader();
        c.waitUntil(() -> c.findLeader() != null, 5000);
        int b = c.crashLeader();
        c.waitUntil(() -> c.findLeader() != null, 5000);
        int d = c.crashLeader();
        Thread.sleep(1200);
        check("3 of 5 crashed: no leader can exist", c.findLeader() == null);
        c.recover(a);
        check("leader returns once a majority is alive", c.waitForLeader(8000));
        c.recover(b);
        c.recover(d);
        c.submit("SET done 1");
        check("cluster fully works again", c.waitForConvergence(8000));
        finish(c);
    }

    static void testMessageLoss() throws Exception {
        System.out.println("Test 6: 20% random message loss");
        Cluster c = newCluster(5, new MemoryStorage());
        c.getNetwork().setDropRate(0.2);
        check("leader elected despite loss", c.waitForLeader(8000));
        int accepted = 0;
        for (int i = 0; i < 8; i++) {
            try {
                c.submit("SET m" + i + " " + i);
                accepted++;
            } catch (NotLeaderException e) {
                Thread.sleep(200);
            }
            Thread.sleep(60);
        }
        check("some commands were accepted", accepted > 0);
        c.getNetwork().setDropRate(0.0);
        check("cluster converges once the network is fine", c.waitForConvergence(10000));
        finish(c);
    }

    static void testPersistence() throws Exception {
        System.out.println("Test 7: crashed follower reloads term and log from disk");
        Storage storage = new FileStorage("target-test-data");
        storage.clear();
        Cluster c = newCluster(3, storage);
        c.waitForLeader(5000);
        c.submit("SET p 1");
        c.waitForConvergence(5000);
        int follower = c.findLeader().getId() % 3 + 1;
        NodeSnapshot before = c.getNode(follower).snapshot();
        c.crash(follower);
        c.recover(follower);
        NodeSnapshot after = c.getNode(follower).snapshot();
        check("log survived the crash", after.log.size() == before.log.size() && after.log.size() >= 2);
        check("term survived the crash", after.term >= before.term);
        check("state machine rebuilt after catch-up", c.waitForConvergence(5000));
        storage.clear();
        finish(c);
    }

    static void testSingleNode() throws Exception {
        System.out.println("Test 8: single node cluster");
        Cluster c = newCluster(1, new MemoryStorage());
        check("single node becomes leader", c.waitForLeader(3000));
        c.submit("SET solo 1");
        check("single node commits by itself", c.waitForConvergence(3000));
        finish(c);
    }

    public static void main(String[] args) throws Exception {
        testElection();
        testReplication();
        testLeaderCrashAndRecovery();
        testMinorityPartition();
        testNoQuorum();
        testMessageLoss();
        testPersistence();
        testSingleNode();
        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        new java.io.File("target-test-data").delete();
        System.exit(failed == 0 ? 0 : 1);
    }
}
