/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.core;

import java.util.Map;

/**
 * The application that sits on top of Raft. Every committed log entry is
 * applied, in order, to the state machine of every node (Unit 1: interface).
 */
public interface StateMachine {
    /** Applies one committed command and returns a short result text. */
    String apply(String command);

    /** Forgets everything (used when a crashed node restarts). */
    void reset();

    /** @return a copy of the current state, for display and for tests */
    Map<String, String> snapshot();
}
