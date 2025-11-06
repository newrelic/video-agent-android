package com.newrelic.videoagent.core.harvest;

public interface SizeEstimator {
    int estimate(Object obj);
}

