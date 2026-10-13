/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.core;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A tiny key-value store. Supported commands:
 *   SET key value   |   key=value   |   DEL key
 * Anything else is accepted by Raft (it is just a log entry) but ignored here.
 */
public class KeyValueStateMachine implements StateMachine {
    private final Map<String, String> data = new HashMap<>();

    @Override
    public synchronized String apply(String command) {
        String c = command.trim();
        String[] p = c.split("\\s+", 3);
        if (p[0].equalsIgnoreCase("SET") && p.length == 2) {
            c = p[1]; // allow "SET x=5"
        }
        if (p[0].equalsIgnoreCase("SET") && p.length == 3) {
            data.put(p[1], p[2]);
            return "OK";
        }
        if ((p[0].equalsIgnoreCase("DEL") || p[0].equalsIgnoreCase("DELETE")) && p.length >= 2) {
            data.remove(p[1]);
            return "OK";
        }
        int eq = c.indexOf('=');
        if (eq > 0) {
            data.put(c.substring(0, eq).trim(), c.substring(eq + 1).trim());
            return "OK";
        }
        return "IGNORED";
    }

    @Override
    public synchronized void reset() {
        data.clear();
    }

    @Override
    public synchronized Map<String, String> snapshot() {
        return new TreeMap<>(data);
    }
}
