package com.newrelic.videoagent.core.utils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logs fill thresholds and dropped events for one bounded queue. Logging only: it never
 * changes what the queue stores or evicts.
 *
 * INFO at 85% and WARNING at 90% are logged once per crossing and re-armed when the fill level
 * falls back below the threshold. ERROR is logged on every drop with a cumulative drop count.
 */
public class BufferDropLogger {
    private static final double INFO_THRESHOLD = 0.85;
    private static final double WARNING_THRESHOLD = 0.9;

    private final String name;
    private final int capacity;
    private final AtomicLong droppedCount;
    private final AtomicBoolean infoLogged = new AtomicBoolean(false);
    private final AtomicBoolean warningLogged = new AtomicBoolean(false);

    public BufferDropLogger(String name, int capacity) {
        this(name, capacity, new AtomicLong(0));
    }

    /**
     * @param droppedCount Cumulative counter, shared when several queues belong to one buffer.
     */
    public BufferDropLogger(String name, int capacity, AtomicLong droppedCount) {
        this.name = name;
        this.capacity = capacity;
        this.droppedCount = droppedCount;
    }

    /**
     * Check the current fill level: logs on a new threshold crossing and re-arms below it.
     */
    public void checkFill(int size) {
        double fill = (double) size / capacity;
        if (fill >= INFO_THRESHOLD) {
            if (infoLogged.compareAndSet(false, true)) {
                NRLog.i(name + " buffer 85% full (" + size + " of " + capacity + " events)");
            }
        } else {
            infoLogged.set(false);
        }
        if (fill >= WARNING_THRESHOLD) {
            if (warningLogged.compareAndSet(false, true)) {
                NRLog.w(name + " buffer 90% full (" + size + " of " + capacity + " events)");
            }
        } else {
            warningLogged.set(false);
        }
    }

    /**
     * Record one evicted event. Logged on every drop.
     */
    public void recordDrop() {
        long total = droppedCount.incrementAndGet();
        NRLog.e(name + " buffer full (capacity " + capacity + "), oldest event dropped. "
                + "Total dropped this session: " + total);
    }
}
