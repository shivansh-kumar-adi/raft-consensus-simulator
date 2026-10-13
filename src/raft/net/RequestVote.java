/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.net;

/** Sent by a candidate to ask for a vote. */
public class RequestVote extends Message {
    private final int lastLogIndex;
    private final long lastLogTerm;

    public RequestVote(int from, int to, long term, int lastLogIndex, long lastLogTerm) {
        super(from, to, term);
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    public int getCandidateId() {
        return getFrom();
    }

    public int getLastLogIndex() {
        return lastLogIndex;
    }

    public long getLastLogTerm() {
        return lastLogTerm;
    }

    @Override
    public MessageType getType() {
        return MessageType.REQUEST_VOTE;
    }

    @Override
    public void dispatch(MessageHandler handler) {
        handler.onRequestVote(this);
    }

    @Override
    public String describe() {
        return "RequestVote N" + getFrom() + "->N" + getTo() + " (term " + getTerm()
                + ", lastLog " + lastLogIndex + "/t" + lastLogTerm + ")";
    }
}
