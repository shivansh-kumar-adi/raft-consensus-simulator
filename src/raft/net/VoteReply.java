/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.net;

/** Answer to a RequestVote. */
public class VoteReply extends Message {
    private final boolean granted;

    public VoteReply(int from, int to, long term, boolean granted) {
        super(from, to, term);
        this.granted = granted;
    }

    public boolean isGranted() {
        return granted;
    }

    @Override
    public MessageType getType() {
        return MessageType.VOTE_REPLY;
    }

    @Override
    public void dispatch(MessageHandler handler) {
        handler.onVoteReply(this);
    }

    @Override
    public String describe() {
        return "VoteReply N" + getFrom() + "->N" + getTo() + " (term " + getTerm()
                + ", " + (granted ? "GRANTED" : "DENIED") + ")";
    }
}
