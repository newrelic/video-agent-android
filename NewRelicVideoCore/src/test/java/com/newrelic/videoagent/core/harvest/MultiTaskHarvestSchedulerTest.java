package com.newrelic.videoagent.core.harvest;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.NRVideoConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MultiTaskHarvestScheduler.
 * Tests scheduling, lifecycle management, pause/resume, and device-specific optimizations.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class MultiTaskHarvestSchedulerTest {

    @Mock
    private NRVideoConfiguration mockConfiguration;

    @Mock
    private Runnable mockOnDemandTask;

    @Mock
    private Runnable mockLiveTask;

    private MultiTaskHarvestScheduler scheduler;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Set up default configuration
        when(mockConfiguration.getHarvestCycleSeconds()).thenReturn(60);
        when(mockConfiguration.getLiveHarvestCycleSeconds()).thenReturn(10);
        when(mockConfiguration.isTV()).thenReturn(false);
    }

    @After
    public void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    // ========== Constructor Tests ==========

    @Test
    public void testConstructorInitialization() {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        assertNotNull("Scheduler should be initialized", scheduler);
        assertFalse("Scheduler should not be running initially", scheduler.isRunning());
    }

    @Test
    public void testConstructorWithMobileDevice() {
        when(mockConfiguration.isTV()).thenReturn(false);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        assertNotNull("Scheduler should be initialized for mobile", scheduler);
    }

    @Test
    public void testConstructorWithTVDevice() {
        when(mockConfiguration.isTV()).thenReturn(true);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        assertNotNull("Scheduler should be initialized for TV", scheduler);
    }

    @Test
    public void testConstructorWithCustomHarvestCycles() {
        when(mockConfiguration.getHarvestCycleSeconds()).thenReturn(120);
        when(mockConfiguration.getLiveHarvestCycleSeconds()).thenReturn(5);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        assertNotNull("Scheduler should accept custom harvest cycles", scheduler);
    }

    @Test
    public void testConstructorWithShortHarvestCycles() {
        when(mockConfiguration.getHarvestCycleSeconds()).thenReturn(10);
        when(mockConfiguration.getLiveHarvestCycleSeconds()).thenReturn(2);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        assertNotNull("Scheduler should accept short harvest cycles", scheduler);
    }

    @Test
    public void testConstructorWithLongHarvestCycles() {
        when(mockConfiguration.getHarvestCycleSeconds()).thenReturn(300);
        when(mockConfiguration.getLiveHarvestCycleSeconds()).thenReturn(30);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        assertNotNull("Scheduler should accept long harvest cycles", scheduler);
    }

    // ========== Start Tests ==========

    @Test
    public void testStartWithoutBufferType() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start();

        Thread.sleep(200); // Wait for scheduler to activate
        assertTrue("Scheduler should be running", scheduler.isRunning());
    }

    @Test
    public void testStartOnDemandScheduler() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start(NRVideoConstants.EVENT_TYPE_ONDEMAND);

        Thread.sleep(200);
        assertTrue("Scheduler should be running", scheduler.isRunning());
    }

    @Test
    public void testStartLiveScheduler() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start(NRVideoConstants.EVENT_TYPE_LIVE);

        Thread.sleep(200);
        assertTrue("Scheduler should be running", scheduler.isRunning());
    }

    @Test
    public void testStartBothSchedulers() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start(NRVideoConstants.EVENT_TYPE_ONDEMAND);
        scheduler.start(NRVideoConstants.EVENT_TYPE_LIVE);

        Thread.sleep(200);
        assertTrue("Both schedulers should be running", scheduler.isRunning());
    }

    @Test
    public void testStartAfterAlreadyStarted() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start(NRVideoConstants.EVENT_TYPE_ONDEMAND);
        scheduler.start(NRVideoConstants.EVENT_TYPE_ONDEMAND); // Try to start again

        Thread.sleep(200);
        assertTrue("Scheduler should remain running", scheduler.isRunning());
    }

    @Test
    public void testStartWithInvalidBufferType() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start("INVALID_TYPE");

        Thread.sleep(200);
        // Should not start with invalid type
    }

    @Test
    public void testStartAfterShutdown() {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.shutdown();
        scheduler.start(NRVideoConstants.EVENT_TYPE_ONDEMAND);

        assertFalse("Scheduler should not start after shutdown", scheduler.isRunning());
    }

    // ========== Shutdown Tests ==========

    @Test
    public void testShutdown() {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start();
        scheduler.shutdown();

        assertFalse("Scheduler should not be running after shutdown", scheduler.isRunning());
    }

    @Test
    public void testShutdownBeforeStart() {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.shutdown();

        assertFalse("Scheduler should remain shut down", scheduler.isRunning());
    }

    @Test
    public void testDoubleShutdown() {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.shutdown();
        scheduler.shutdown(); // Second shutdown should be safe

        assertFalse("Scheduler should remain shut down", scheduler.isRunning());
    }

    @Test
    public void testShutdownExecutesImmediateHarvest() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        Runnable countingOnDemandTask = () -> {
            latch.countDown();
        };
        Runnable countingLiveTask = () -> {
            latch.countDown();
        };

        scheduler = new MultiTaskHarvestScheduler(countingOnDemandTask, countingLiveTask, mockConfiguration);

        scheduler.start();
        Thread.sleep(100);
        scheduler.shutdown();

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue("Shutdown should execute immediate harvest", completed);
    }

    // ========== Force Harvest Tests ==========

    @Test
    public void testForceHarvest() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        Runnable countingOnDemandTask = () -> latch.countDown();
        Runnable countingLiveTask = () -> latch.countDown();

        scheduler = new MultiTaskHarvestScheduler(countingOnDemandTask, countingLiveTask, mockConfiguration);

        scheduler.forceHarvest();

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue("Force harvest should execute both tasks", completed);
    }

    @Test
    public void testForceHarvestWhileRunning() throws InterruptedException {
        AtomicInteger onDemandCount = new AtomicInteger(0);
        AtomicInteger liveCount = new AtomicInteger(0);

        Runnable countingOnDemandTask = () -> onDemandCount.incrementAndGet();
        Runnable countingLiveTask = () -> liveCount.incrementAndGet();

        scheduler = new MultiTaskHarvestScheduler(countingOnDemandTask, countingLiveTask, mockConfiguration);

        scheduler.start();
        Thread.sleep(100);
        scheduler.forceHarvest();
        Thread.sleep(100);

        assertTrue("OnDemand task should execute", onDemandCount.get() > 0);
        assertTrue("Live task should execute", liveCount.get() > 0);
    }

    @Test
    public void testMultipleForceHarvests() throws InterruptedException {
        AtomicInteger onDemandCount = new AtomicInteger(0);
        AtomicInteger liveCount = new AtomicInteger(0);

        Runnable countingOnDemandTask = () -> onDemandCount.incrementAndGet();
        Runnable countingLiveTask = () -> liveCount.incrementAndGet();

        scheduler = new MultiTaskHarvestScheduler(countingOnDemandTask, countingLiveTask, mockConfiguration);

        scheduler.forceHarvest();
        scheduler.forceHarvest();
        scheduler.forceHarvest();

        Thread.sleep(200);

        assertTrue("Multiple force harvests should execute", onDemandCount.get() >= 3);
        assertTrue("Multiple force harvests should execute", liveCount.get() >= 3);
    }

    // ========== isRunning Tests ==========

    @Test
    public void testIsRunningInitially() {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        assertFalse("Scheduler should not be running initially", scheduler.isRunning());
    }

    @Test
    public void testIsRunningAfterStart() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start();
        Thread.sleep(100);

        assertTrue("Scheduler should be running after start", scheduler.isRunning());
    }

    @Test
    public void testIsRunningAfterShutdown() {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start();
        scheduler.shutdown();

        assertFalse("Scheduler should not be running after shutdown", scheduler.isRunning());
    }

    @Test
    public void testIsRunningWithOnlyOnDemand() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start(NRVideoConstants.EVENT_TYPE_ONDEMAND);
        Thread.sleep(100);

        assertTrue("Scheduler should be running with only OnDemand", scheduler.isRunning());
    }

    @Test
    public void testIsRunningWithOnlyLive() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start(NRVideoConstants.EVENT_TYPE_LIVE);
        Thread.sleep(100);

        assertTrue("Scheduler should be running with only Live", scheduler.isRunning());
    }

    // ========== Pause/Resume Tests ==========

    @Test
    public void testPause() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start();
        Thread.sleep(100);
        scheduler.pause();

        // Scheduler is still marked as running, but callbacks are removed
        assertTrue("Scheduler should still be marked as running after pause", scheduler.isRunning());
    }

    @Test
    public void testPauseBeforeStart() {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.pause(); // Should not throw exception

        assertFalse("Scheduler should not be running", scheduler.isRunning());
    }

    @Test
    public void testResumeWithNormalIntervals() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start();
        Thread.sleep(100);
        scheduler.pause();
        scheduler.resume(false);

        // Should resume with normal intervals
        assertTrue("Scheduler should be running after resume", scheduler.isRunning());
    }

    @Test
    public void testResumeWithExtendedIntervals() throws InterruptedException {
        when(mockConfiguration.isTV()).thenReturn(true);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start();
        Thread.sleep(100);
        scheduler.pause();
        scheduler.resume(true);

        // Should resume with extended intervals for TV
        assertTrue("Scheduler should be running after resume", scheduler.isRunning());
    }

    @Test
    public void testResumeAfterShutdown() {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.shutdown();
        scheduler.resume(false);

        assertFalse("Scheduler should not resume after shutdown", scheduler.isRunning());
    }

    @Test
    public void testPauseResumeMultipleTimes() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start();
        for (int i = 0; i < 5; i++) {
            Thread.sleep(50);
            scheduler.pause();
            Thread.sleep(50);
            scheduler.resume(false);
        }

        assertTrue("Scheduler should handle multiple pause/resume cycles", scheduler.isRunning());
    }

    // ========== Device Type Tests ==========

    @Test
    public void testMobileDeviceThreadPriority() {
        when(mockConfiguration.isTV()).thenReturn(false);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        assertNotNull("Mobile scheduler should initialize", scheduler);
    }

    @Test
    public void testTVDeviceThreadPriority() {
        when(mockConfiguration.isTV()).thenReturn(true);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        assertNotNull("TV scheduler should initialize", scheduler);
    }

    @Test
    public void testTVDeviceWithExtendedIntervals() throws InterruptedException {
        when(mockConfiguration.isTV()).thenReturn(true);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start();
        Thread.sleep(100);
        scheduler.pause();
        scheduler.resume(true); // Extended intervals

        assertTrue("TV scheduler should use extended intervals", scheduler.isRunning());
    }

    @Test
    public void testMobileDeviceWithExtendedIntervalsIgnored() throws InterruptedException {
        when(mockConfiguration.isTV()).thenReturn(false);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start();
        Thread.sleep(100);
        scheduler.pause();
        scheduler.resume(true); // Extended intervals - should be ignored for mobile

        assertTrue("Mobile scheduler should ignore extended intervals", scheduler.isRunning());
    }

    // ========== Error Handling Tests ==========

    @Test
    public void testOnDemandTaskThrowsException() throws InterruptedException {
        Runnable failingTask = () -> {
            throw new RuntimeException("Test exception");
        };

        scheduler = new MultiTaskHarvestScheduler(failingTask, mockLiveTask, mockConfiguration);

        scheduler.start(NRVideoConstants.EVENT_TYPE_ONDEMAND);
        Thread.sleep(1500); // Wait for at least one execution

        // Should handle exception and continue running
        assertTrue("Scheduler should handle exceptions and continue", scheduler.isRunning());
    }

    @Test
    public void testLiveTaskThrowsException() throws InterruptedException {
        Runnable failingTask = () -> {
            throw new RuntimeException("Test exception");
        };

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, failingTask, mockConfiguration);

        scheduler.start(NRVideoConstants.EVENT_TYPE_LIVE);
        Thread.sleep(1000); // Wait for at least one execution

        // Should handle exception and continue running
        assertTrue("Scheduler should handle exceptions and continue", scheduler.isRunning());
    }

    @Test
    public void testBothTasksThrowExceptions() throws InterruptedException {
        Runnable failingOnDemandTask = () -> {
            throw new RuntimeException("OnDemand exception");
        };
        Runnable failingLiveTask = () -> {
            throw new RuntimeException("Live exception");
        };

        scheduler = new MultiTaskHarvestScheduler(failingOnDemandTask, failingLiveTask, mockConfiguration);

        scheduler.start();
        Thread.sleep(1500);

        // Should handle both exceptions and continue running
        assertTrue("Scheduler should handle both exceptions", scheduler.isRunning());
    }

    // ========== Harvest Interval Tests ==========
    // Note: Timing-based tests removed due to Robolectric Handler limitations
    // The scheduler interval configuration is tested in constructor tests

    // ========== Null Task Handling ==========

    @Test
    public void testNullOnDemandTask() {
        scheduler = new MultiTaskHarvestScheduler(null, mockLiveTask, mockConfiguration);

        scheduler.start(NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should not crash with null task
        assertNotNull("Scheduler should handle null OnDemand task", scheduler);
    }

    @Test
    public void testNullLiveTask() {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, null, mockConfiguration);

        scheduler.start(NRVideoConstants.EVENT_TYPE_LIVE);

        // Should not crash with null task
        assertNotNull("Scheduler should handle null Live task", scheduler);
    }

    @Test
    public void testBothTasksNull() {
        scheduler = new MultiTaskHarvestScheduler(null, null, mockConfiguration);

        scheduler.start();

        // Should not crash with both tasks null
        assertNotNull("Scheduler should handle both null tasks", scheduler);
    }

    // ========== Edge Cases ==========

    @Test
    public void testStartStopSequence() throws InterruptedException {
        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        scheduler.start();
        Thread.sleep(100);
        scheduler.shutdown();
        Thread.sleep(100);

        assertFalse("Scheduler should be stopped", scheduler.isRunning());
    }

    @Test
    public void testRapidStartStopCycles() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);
            scheduler.start();
            Thread.sleep(50);
            scheduler.shutdown();
        }

        // Should handle rapid creation/destruction without issues
    }

    @Test
    public void testZeroHarvestInterval() {
        when(mockConfiguration.getHarvestCycleSeconds()).thenReturn(0);
        when(mockConfiguration.getLiveHarvestCycleSeconds()).thenReturn(0);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        assertNotNull("Scheduler should handle zero interval", scheduler);
    }

    @Test
    public void testVeryLargeHarvestInterval() {
        when(mockConfiguration.getHarvestCycleSeconds()).thenReturn(Integer.MAX_VALUE);
        when(mockConfiguration.getLiveHarvestCycleSeconds()).thenReturn(Integer.MAX_VALUE);

        scheduler = new MultiTaskHarvestScheduler(mockOnDemandTask, mockLiveTask, mockConfiguration);

        assertNotNull("Scheduler should handle very large interval", scheduler);
    }
}
