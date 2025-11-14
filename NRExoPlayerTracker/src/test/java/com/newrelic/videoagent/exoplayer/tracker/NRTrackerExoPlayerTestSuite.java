package com.newrelic.videoagent.exoplayer.tracker;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Test suite for NRTrackerExoPlayer frame drop aggregation functionality.
 *
 * This suite focuses on core functionality testing:
 * - Basic aggregation logic
 * - "Last track always" pattern
 * - Configuration management
 * - Event routing and state management
 * - Basic concurrency verification
 *
 * Usage:
 * - Run test suite: ./gradlew test --tests "NRTrackerExoPlayerTestSuite"
 * - CI/CD integration: Include this suite in automated testing
 * - Coverage reports: Generates comprehensive coverage of aggregation features
 */
@RunWith(Suite.class)
@SuiteClasses({
        NRTrackerExoPlayerFrameDropAggregationTest.class
})
public class NRTrackerExoPlayerTestSuite {
    // This class remains empty, used only as a holder for the above annotations

    // Test execution:
    // NRTrackerExoPlayerFrameDropAggregationTest - Comprehensive core functionality testing
}
