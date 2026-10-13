# Code Walkthrough (read this to understand the project)

## Suggested reading order
1. `core/NodeState`, `core/LogEntry`, `core/ReplicatedLog` : the simple data classes.
2. `net/Message` and its four subclasses : what nodes say to each other.
3. `net/Network` : how messages travel (delay, loss, partition).
4. **`core/RaftNode`** : the heart of the project. Read it in this order: `run()`, `tick()`,
   `startElection()`, `onRequestVote()`, `onVoteReply()`, `becomeLeader()`, `sendAppendEntries()`,
   `onAppendEntries()`, `onAppendReply()`, `advanceCommitIndex()`, `applyCommitted()`, `crash()`, `recover()`.
5. `sim/Cluster` : builds everything and offers `submit`, `crash`, `partition`.
6. `storage/*` and `ui/*` : persistence and screens.

## The life of a node (RaftNode.run)
Each node is a thread running this loop forever:
```
wait up to 10 ms for a message in the inbox
if a message arrived  -> message.dispatch(this)       // calls onRequestVote / onAppendEntries / ...
tick()                -> leader: send heartbeat if due; others: start election if timer expired
```
Everything inside `synchronized(this)` so the GUI and network threads never see half-updated state.

## Flow 1: leader election
1. Timer expires -> `startElection()`: state=CANDIDATE, `currentTerm++`, vote for self, send `RequestVote` to all.
2. Receivers run `onRequestVote()`: grant the vote only if (a) the term is not old, (b) they have not voted for someone
   else in this term, (c) the candidate's log is at least as up to date (`candidateLogIsUpToDate`).
3. Votes come back as `VoteReply` -> `onVoteReply()` counts them. Majority (`Util.majority`) -> `becomeLeader()`.
4. The new leader appends a `NOOP` entry and immediately sends `AppendEntries` so everybody learns who the leader is.

Why random timeouts? So that normally only one node times out first and wins, instead of everybody splitting the votes.

## Flow 2: log replication and commit
1. Client calls `Cluster.submit("SET x 5")` -> finds the leader -> `RaftNode.submit()` appends to the leader's log.
2. Leader sends `AppendEntries(prevLogIndex, prevLogTerm, entries, leaderCommit)` to each follower.
3. Follower (`onAppendEntries`): checks that its log has an entry at `prevLogIndex` with term `prevLogTerm`.
   * mismatch -> replies failure with a hint; leader goes one step back (`onAppendReply`) and retries.
   * match -> deletes conflicting entries, appends new ones, replies success with `matchIndex`.
4. Leader tracks `matchIndex` per follower. When a majority has index N **and** entry N is from the current term,
   `advanceCommitIndex()` sets `commitIndex = N`.
5. `applyCommitted()` feeds committed commands into the `KeyValueStateMachine`. Followers learn the new
   `commitIndex` from the next heartbeat (`leaderCommit`).

## Flow 3: crash and recovery
* `crash()` sets `crashed=true`; the thread keeps running but ignores everything and the network drops its messages.
* `recover()` reloads term, vote and log from `Storage`, resets volatile state (commitIndex, state machine) and
  restarts as a follower. The leader then sends the missing entries and the state machine is rebuilt.

## Why a minority partition cannot cause damage
An isolated old leader keeps accepting commands, but they never reach a majority, so they are never committed.
The other side elects a new leader with a higher term. After healing, the old leader sees the higher term,
steps down, and its uncommitted entries are deleted because they conflict with the new leader's log.
(See STEP 5 and 6 of `./run.sh --demo`.)

## Key terms
| Term | Meaning |
|---|---|
| term | a logical clock; a new election starts a new term |
| majority / quorum | more than half of the nodes (3 of 5) |
| commit | an entry stored on a majority; it will never be lost |
| heartbeat | an `AppendEntries` with no entries, proving the leader is alive |
| stale leader | an old leader that has not yet noticed a higher term |
| state machine | the application driven by the log (here a key-value map) |

## Likely viva questions
1. **Why Raft instead of Paxos?** Raft is designed to be easier to understand: leader based, clear roles.
2. **What happens if two nodes become candidates at once?** Votes can split, nobody wins, timers expire at different random times and a new term starts.
3. **Can there be two leaders?** Not in the same term (each node votes once per term, a leader needs a majority). An old leader in a lower term may exist briefly.
4. **Why does a leader append a NOOP?** A leader may only commit entries of its own term directly; the NOOP lets it commit older entries safely.
5. **How do you avoid data races?** `synchronized` methods in `RaftNode`, `BlockingQueue` for messages, immutable `NodeSnapshot` for the UI.
6. **Which OOP concepts are used?** Abstract class `Message`, interfaces, polymorphism via `dispatch()`, generics `LogEntry<T>`, custom exceptions, threads.
7. **What does the database store?** `node_state(term, voted_for)` and `raft_log(index, term, command)` per node, so a restarted node keeps its promises.
8. **What is not implemented?** Membership changes and log compaction (snapshots), which are outside "Raft-lite".
