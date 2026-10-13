/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.net;

/**
 * Base class of every message sent between nodes (Unit 1: abstract class and
 * inheritance). Subclasses override dispatch() and describe(), which is
 * runtime polymorphism (Unit 2).
 */
public abstract class Message {
    private final int from;
    private final int to;
    private final long term;

    protected Message(int from, int to, long term) {
        this.from = from;
        this.to = to;
        this.term = term;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    public long getTerm() {
        return term;
    }

    public abstract MessageType getType();

    /** Calls the correct handler method of the receiver. */
    public abstract void dispatch(MessageHandler handler);

    /** Human readable one-line description (used for message tracing). */
    public abstract String describe();

    @Override
    public String toString() {
        return describe();
    }
}
