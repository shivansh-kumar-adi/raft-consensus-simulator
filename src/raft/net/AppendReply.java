/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.net;

/**
 * Answer to AppendEntries.
 * On success, matchIndex is the last index the follower now shares with the leader.
 * On failure, matchIndex is a hint telling the leader where to retry from.
 */
public class AppendReply extends Message {
    private final boolean success;
    private final int matchIndex;

    public AppendReply(int from, int to, long term, boolean success, int matchIndex) {
        super(from, to, term);
        this.success = success;
        this.matchIndex = matchIndex;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getMatchIndex() {
        return matchIndex;
    }

    @Override
    public MessageType getType() {
        return MessageType.APPEND_REPLY;
    }

    @Override
    public void dispatch(MessageHandler handler) {
        handler.onAppendReply(this);
    }

    @Override
    public String describe() {
        return "AppendReply N" + getFrom() + "->N" + getTo() + " (term " + getTerm()
                + ", " + (success ? "OK" : "REJECTED") + ", match " + matchIndex + ")";
    }
}
