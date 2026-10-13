/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.net;

/**
 * Implemented by RaftNode. Each message calls the matching method
 * ("double dispatch"), so the node needs no if/else on message types.
 */
public interface MessageHandler {
    void onRequestVote(RequestVote message);

    void onVoteReply(VoteReply message);

    void onAppendEntries(AppendEntries message);

    void onAppendReply(AppendReply message);
}
