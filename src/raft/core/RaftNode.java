/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import raft.exception.NodeCrashedException;
import raft.exception.NotLeaderException;
import raft.exception.StorageException;
import raft.net.AppendEntries;
import raft.net.AppendReply;
import raft.net.Message;
import raft.net.MessageHandler;
import raft.net.Network;
import raft.net.RequestVote;
import raft.net.VoteReply;
import raft.storage.PersistentState;
import raft.storage.Storage;
import raft.util.EventLog;
import raft.util.Util;

/**
 * One Raft server. Every node runs in its own thread (Unit 3: Runnable,
 * multithreading). The thread repeatedly
 *   1. waits a short time for a message in its inbox (BlockingQueue),
 *   2. handles the message (if any),
 *   3. calls tick(): leaders send heartbeats, others check the election timer.
 *
 * All access to the node's fields is done inside "synchronized" methods, so the
 * node thread, the network threads and the user interface never corrupt the
 * state (Unit 3: synchronizing threads).
 */
public class RaftNode implements Runnable, MessageHandler {
    /** Special log entry a new leader appends so older entries can be committed. */
    public static final String NOOP = "NOOP";
    private static final int NO_VOTE = -1;

    private final int id;
    private final List<Integer> peers;
    private final Network network;
    private final Storage storage;
    private final StateMachine stateMachine;
    private final RaftConfig config;
    private final EventLog events;
    private final Random random = new Random();
    private final BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();

    // ---- persistent state (saved through Storage) ----
    private long currentTerm = 0;
    private int votedFor = NO_VOTE;
    private final ReplicatedLog<String> log = new ReplicatedLog<>();

    // ---- volatile state (lost on crash) ----
    private NodeState state = NodeState.FOLLOWER;
    private int leaderId = -1;
    private int commitIndex = 0;
    private int lastApplied = 0;
    private long electionDeadline;
    private long lastHeartbeat;
    private final Set<Integer> votesReceived = new HashSet<>();

    // ---- leader only ----
    private final Map<Integer, Integer> nextIndex = new HashMap<>();
    private final Map<Integer, Integer> matchIndex = new HashMap<>();

    private volatile boolean crashed = false;
    private volatile boolean running = true;

    public RaftNode(int id, List<Integer> peers, Network network, Storage storage,
                    StateMachine stateMachine, RaftConfig config, EventLog events) {
        this.id = id;
        this.peers = new ArrayList<>(peers);
        this.network = network;
        this.storage = storage;
        this.stateMachine = stateMachine;
        this.config = config;
        this.events = events;
        loadPersistentState();
    }

    public int getId() {
        return id;
    }

    public boolean isCrashed() {
        return crashed;
    }

    private int clusterSize() {
        return peers.size() + 1;
    }

    // =====================================================================
    //  Thread main loop
    // =====================================================================

    @Override
    public void run() {
        synchronized (this) {
            resetElectionTimer();
        }
        while (running) {
            try {
                if (crashed) {
                    Thread.sleep(config.tickMs);
                    inbox.clear();
                    continue;
                }
                Message message = inbox.poll(config.tickMs, TimeUnit.MILLISECONDS);
                synchronized (this) {
                    if (crashed) {
                        continue;
                    }
                    if (message != null) {
                        message.dispatch(this); // runtime polymorphism
                    }
                    tick();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Called by the network to put a message into this node's inbox. */
    public void receive(Message message) {
        if (!crashed) {
            inbox.offer(message);
        }
    }

    public void stop() {
        running = false;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (state == NodeState.LEADER) {
            if (now - lastHeartbeat >= config.heartbeatInterval) {
                broadcastAppendEntries();
            }
        } else if (now >= electionDeadline) {
            startElection();
        }
    }

    // =====================================================================
    //  Leader election
    // =====================================================================

    private void resetElectionTimer() {
        int span = config.electionTimeoutMax - config.electionTimeoutMin;
        electionDeadline = System.currentTimeMillis() + config.electionTimeoutMin
                + (span <= 0 ? 0 : random.nextInt(span + 1));
    }

    private void startElection() {
        state = NodeState.CANDIDATE;
        currentTerm++;
        votedFor = id;
        leaderId = -1;
        votesReceived.clear();
        votesReceived.add(id);
        persistState();
        resetElectionTimer();
        events.log("N" + id + " election timeout -> CANDIDATE for term " + currentTerm);
        if (votesReceived.size() >= Util.majority(clusterSize())) {
            becomeLeader(); // single node cluster
            return;
        }
        for (int peer : peers) {
            network.send(new RequestVote(id, peer, currentTerm, log.lastIndex(), log.lastTerm()));
        }
    }

    private void stepDown(long newTerm) {
        boolean wasLeader = state == NodeState.LEADER;
        currentTerm = newTerm;
        votedFor = NO_VOTE;
        if (state != NodeState.FOLLOWER) {
            events.log("N" + id + " steps down to FOLLOWER (saw term " + newTerm + ")"
                    + (wasLeader ? " - no longer leader" : ""));
        }
        state = NodeState.FOLLOWER;
        persistState();
    }

    private void becomeLeader() {
        state = NodeState.LEADER;
        leaderId = id;
        for (int peer : peers) {
            nextIndex.put(peer, log.lastIndex() + 1);
            matchIndex.put(peer, 0);
        }
        events.log("N" + id + " won the election -> LEADER of term " + currentTerm
                + " (votes: " + Util.join(votesReceived, ",") + ")");
        log.append(currentTerm, NOOP);
        persistLog();
        broadcastAppendEntries();
        advanceCommitIndex(); // needed for a single node cluster
    }

    private boolean candidateLogIsUpToDate(long lastTerm, int lastIndex) {
        if (lastTerm != log.lastTerm()) {
            return lastTerm > log.lastTerm();
        }
        return lastIndex >= log.lastIndex();
    }

    @Override
    public void onRequestVote(RequestVote rv) {
        if (rv.getTerm() > currentTerm) {
            stepDown(rv.getTerm());
        }
        boolean grant = false;
        if (rv.getTerm() == currentTerm
                && (votedFor == NO_VOTE || votedFor == rv.getCandidateId())
                && candidateLogIsUpToDate(rv.getLastLogTerm(), rv.getLastLogIndex())) {
            grant = true;
            votedFor = rv.getCandidateId();
            persistState();
            resetElectionTimer();
        }
        events.log("N" + id + " " + (grant ? "votes for" : "denies vote to") + " N"
                + rv.getCandidateId() + " in term " + rv.getTerm());
        network.send(new VoteReply(id, rv.getFrom(), currentTerm, grant));
    }

    @Override
    public void onVoteReply(VoteReply reply) {
        if (reply.getTerm() > currentTerm) {
            stepDown(reply.getTerm());
            return;
        }
        if (state != NodeState.CANDIDATE || reply.getTerm() != currentTerm || !reply.isGranted()) {
            return;
        }
        votesReceived.add(reply.getFrom());
        if (votesReceived.size() >= Util.majority(clusterSize())) {
            becomeLeader();
        }
    }

    // =====================================================================
    //  Log replication
    // =====================================================================

    private void broadcastAppendEntries() {
        lastHeartbeat = System.currentTimeMillis();
        for (int peer : peers) {
            sendAppendEntries(peer);
        }
    }

    private void sendAppendEntries(int peer) {
        int next = nextIndex.get(peer);
        int prevIndex = next - 1;
        long prevTerm = log.termAt(prevIndex);
        List<LogEntry<String>> entries = log.slice(next, config.maxEntriesPerMessage);
        network.send(new AppendEntries(id, peer, currentTerm, prevIndex, prevTerm, entries, commitIndex));
    }

    @Override
    public void onAppendEntries(AppendEntries ae) {
        if (ae.getTerm() < currentTerm) {
            network.send(new AppendReply(id, ae.getFrom(), currentTerm, false, 0)); // stale leader
            return;
        }
        if (ae.getTerm() > currentTerm) {
            stepDown(ae.getTerm());
        }
        if (state != NodeState.FOLLOWER) {
            state = NodeState.FOLLOWER; // a candidate that sees a leader of its own term gives up
            events.log("N" + id + " recognises N" + ae.getLeaderId() + " as leader -> FOLLOWER");
        }
        if (leaderId != ae.getLeaderId()) {
            events.log("N" + id + " follows leader N" + ae.getLeaderId() + " (term " + currentTerm + ")");
        }
        leaderId = ae.getLeaderId();
        resetElectionTimer();

        int prev = ae.getPrevLogIndex();
        if (prev > 0 && (prev > log.lastIndex() || log.termAt(prev) != ae.getPrevLogTerm())) {
            // log consistency check failed -> tell the leader where to retry from
            int hint = Math.min(prev - 1, log.lastIndex());
            network.send(new AppendReply(id, ae.getFrom(), currentTerm, false, hint));
            return;
        }

        boolean changed = false;
        int index = prev;
        for (LogEntry<String> entry : ae.getEntries()) {
            index++;
            if (index <= log.lastIndex()) {
                if (log.termAt(index) != entry.getTerm()) {
                    events.log("N" + id + " log conflict at index " + index
                            + " -> deleting entries from " + index + " onwards");
                    log.truncateFrom(index);
                    log.append(entry.getTerm(), entry.getCommand());
                    changed = true;
                }
            } else {
                log.append(entry.getTerm(), entry.getCommand());
                changed = true;
            }
        }
        if (changed) {
            persistLog();
        }
        int match = prev + ae.getEntries().size();
        if (ae.getLeaderCommit() > commitIndex) {
            commitIndex = Math.min(ae.getLeaderCommit(), match);
            applyCommitted();
        }
        network.send(new AppendReply(id, ae.getFrom(), currentTerm, true, match));
    }

    @Override
    public void onAppendReply(AppendReply reply) {
        if (reply.getTerm() > currentTerm) {
            stepDown(reply.getTerm());
            return;
        }
        if (state != NodeState.LEADER || reply.getTerm() != currentTerm) {
            return;
        }
        int peer = reply.getFrom();
        if (reply.isSuccess()) {
            int match = Math.max(matchIndex.get(peer), reply.getMatchIndex());
            matchIndex.put(peer, match);
            nextIndex.put(peer, match + 1);
            advanceCommitIndex();
            if (match < log.lastIndex()) {
                sendAppendEntries(peer); // follower still behind -> keep sending
            }
        } else {
            int retry = Math.min(nextIndex.get(peer) - 1, reply.getMatchIndex() + 1);
            nextIndex.put(peer, Math.max(matchIndex.get(peer) + 1, Math.max(1, retry)));
            sendAppendEntries(peer);
        }
    }

    /** Leader rule: an entry of the current term replicated on a majority is committed. */
    private void advanceCommitIndex() {
        for (int n = log.lastIndex(); n > commitIndex; n--) {
            if (log.termAt(n) != currentTerm) {
                break;
            }
            int count = 1; // the leader itself
            for (int peer : peers) {
                if (matchIndex.get(peer) >= n) {
                    count++;
                }
            }
            if (count >= Util.majority(clusterSize())) {
                commitIndex = n;
                events.log("N" + id + " (leader) committed up to index " + n
                        + " (replicated on " + count + "/" + clusterSize() + " nodes)");
                applyCommitted();
                break;
            }
        }
    }

    private void applyCommitted() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry<String> entry = log.get(lastApplied);
            if (!NOOP.equals(entry.getCommand())) {
                stateMachine.apply(entry.getCommand());
            }
        }
    }

    /**
     * Client request. Only a live leader accepts it.
     *
     * @return the log index given to the command (it is committed later)
     */
    public synchronized int submit(String command) throws NodeCrashedException, NotLeaderException {
        if (crashed) {
            throw new NodeCrashedException(id);
        }
        if (state != NodeState.LEADER) {
            throw new NotLeaderException("Node " + id + " is not the leader", leaderId);
        }
        LogEntry<String> entry = log.append(currentTerm, command);
        persistLog();
        events.log("N" + id + " (leader) received client command '" + command + "' -> index " + entry.getIndex());
        broadcastAppendEntries();
        advanceCommitIndex(); // needed for a single node cluster
        return entry.getIndex();
    }

    // =====================================================================
    //  Crash and recovery
    // =====================================================================

    public synchronized void crash() {
        if (crashed) {
            return;
        }
        crashed = true;
        inbox.clear();
        events.log("N" + id + " *** CRASHED *** (was " + state.getLabel() + ", term " + currentTerm + ")");
    }

    /** Restarts the node: volatile state is lost, persistent state is reloaded from storage. */
    public synchronized void recover() {
        if (!crashed) {
            return;
        }
        loadPersistentState();
        state = NodeState.FOLLOWER;
        leaderId = -1;
        commitIndex = 0;
        lastApplied = 0;
        votesReceived.clear();
        stateMachine.reset();
        inbox.clear();
        resetElectionTimer();
        crashed = false;
        events.log("N" + id + " *** RECOVERED *** as FOLLOWER (reloaded term " + currentTerm
                + " and " + log.lastIndex() + " log entries from " + storage.name() + ")");
    }

    // =====================================================================
    //  Persistence helpers
    // =====================================================================

    private void persistState() {
        try {
            storage.saveState(id, currentTerm, votedFor);
        } catch (StorageException e) {
            events.log("N" + id + " WARNING: could not save state: " + e.getMessage());
        }
    }

    private void persistLog() {
        try {
            storage.saveLog(id, log.toList());
        } catch (StorageException e) {
            events.log("N" + id + " WARNING: could not save log: " + e.getMessage());
        }
    }

    private void loadPersistentState() {
        try {
            PersistentState saved = storage.loadState(id);
            if (saved != null) {
                currentTerm = saved.currentTerm;
                votedFor = saved.votedFor;
            }
            log.replaceAll(storage.loadLog(id));
        } catch (StorageException e) {
            events.log("N" + id + " WARNING: could not load saved data: " + e.getMessage());
        }
    }

    // =====================================================================
    //  Read-only view for UI and tests
    // =====================================================================

    public synchronized NodeSnapshot snapshot() {
        return new NodeSnapshot(id, state, crashed, currentTerm, votedFor, leaderId,
                commitIndex, lastApplied, log.toList(), stateMachine.snapshot());
    }

    public synchronized boolean isLeader() {
        return !crashed && state == NodeState.LEADER;
    }

    public synchronized long getCurrentTerm() {
        return currentTerm;
    }
}
