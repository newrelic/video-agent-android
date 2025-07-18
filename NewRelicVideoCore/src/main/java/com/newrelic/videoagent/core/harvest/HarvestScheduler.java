package com.newrelic.videoagent.core.harvest;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class HarvestScheduler {
    private final ScheduledExecutorService scheduler;
    private final Runnable harvestTask;
    private final int intervalSeconds;

    public HarvestScheduler(Runnable harvestTask, int intervalSeconds) {
        this.harvestTask = harvestTask;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("NewRelicVideoAgent-HarvestThread"); // More descriptive thread name
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(harvestTask, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
