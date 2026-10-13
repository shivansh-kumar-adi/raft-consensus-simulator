/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.net;

import java.util.List;
import raft.core.LogEntry;

/**
 * Sent by the leader. With no entries it is a heartbeat; with entries it
 * replicates the log.
 */
public class AppendEntries extends Message {
    private final int prevLogIndex;
    private final long prevLogTerm;
    private final List<LogEntry<String>> entries;
    private final int leaderCommit;

    public AppendEntries(int from, int to, long term, int prevLogIndex, long prevLogTerm,
                         List<LogEntry<String>> entries, int leaderCommit) {
        super(from, to, term);
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries;
        this.leaderCommit = leaderCommit;
    }

    public int getLeaderId() {
        return getFrom();
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public long getPrevLogTerm() {
        return prevLogTerm;
    }

    public List<LogEntry<String>> getEntries() {
        return entries;
    }

    public int getLeaderCommit() {
        return leaderCommit;
    }

    @Override
    public MessageType getType() {
        return MessageType.APPEND_ENTRIES;
    }

    @Override
    public void dispatch(MessageHandler handler) {
        handler.onAppendEntries(this);
    }

    @Override
    public String describe() {
        String what = entries.isEmpty() ? "heartbeat" : entries.size() + " entries";
        return "AppendEntries N" + getFrom() + "->N" + getTo() + " (term " + getTerm()
                + ", " + what + ", prev " + prevLogIndex + ", commit " + leaderCommit + ")";
    }
}
