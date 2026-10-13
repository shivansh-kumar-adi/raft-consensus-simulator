/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.core;

/** All timing parameters of the simulation in one place (times in milliseconds). */
public class RaftConfig {
    public final int electionTimeoutMin;
    public final int electionTimeoutMax;
    public final int heartbeatInterval;
    public final int tickMs;
    public final int minNetworkDelay;
    public final int maxNetworkDelay;
    public final int maxEntriesPerMessage;

    public RaftConfig(int electionTimeoutMin, int electionTimeoutMax, int heartbeatInterval,
                      int tickMs, int minNetworkDelay, int maxNetworkDelay, int maxEntriesPerMessage) {
        this.electionTimeoutMin = electionTimeoutMin;
        this.electionTimeoutMax = electionTimeoutMax;
        this.heartbeatInterval = heartbeatInterval;
        this.tickMs = tickMs;
        this.minNetworkDelay = minNetworkDelay;
        this.maxNetworkDelay = maxNetworkDelay;
        this.maxEntriesPerMessage = maxEntriesPerMessage;
    }

    /** Slow timings so a human can follow what happens (default). */
    public static RaftConfig demo() {
        return new RaftConfig(1500, 3000, 500, 10, 40, 150, 10);
    }

    /** Fast timings used by the automated tests. */
    public static RaftConfig fast() {
        return new RaftConfig(150, 300, 50, 5, 1, 8, 10);
    }
}
