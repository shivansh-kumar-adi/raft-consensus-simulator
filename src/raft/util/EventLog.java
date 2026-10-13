/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe event log shared by every node and the network.
 * The GUI registers a listener (observer pattern) to show events live,
 * while the console UI can simply echo them to System.out.
 */
public class EventLog {
    private static final int MAX_EVENTS = 1000;
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Deque<String> buffer = new ArrayDeque<>();
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean consoleEcho = false;

    public void log(String message) {
        String line = "[" + LocalTime.now().format(FORMAT) + "] " + message;
        synchronized (this) {
            buffer.addLast(line);
            if (buffer.size() > MAX_EVENTS) {
                buffer.removeFirst();
            }
        }
        if (consoleEcho) {
            System.out.println(line);
        }
        for (Consumer<String> listener : listeners) {
            listener.accept(line);
        }
    }

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void setConsoleEcho(boolean echo) {
        this.consoleEcho = echo;
    }

    /** @return the most recent n events (oldest first) */
    public synchronized List<String> recent(int n) {
        List<String> all = new ArrayList<>(buffer);
        int from = Math.max(0, all.size() - n);
        return new ArrayList<>(all.subList(from, all.size()));
    }
}
