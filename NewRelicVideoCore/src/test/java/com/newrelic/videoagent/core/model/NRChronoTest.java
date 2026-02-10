package com.newrelic.videoagent.core.model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for NRChrono.
 */
public class NRChronoTest {

    private NRChrono chrono;

    @Before
    public void setUp() {
        chrono = new NRChrono();
    }

    @Test
    public void testConstructor() {
        NRChrono newChrono = new NRChrono();

        assertNotNull(newChrono);
        assertEquals(0, newChrono.getDeltaTime());
    }

    @Test
    public void testGetDeltaTimeBeforeStart() {
        long delta = chrono.getDeltaTime();

        assertEquals(0, delta);
    }

    @Test
    public void testGetDeltaTimeAfterStart() throws InterruptedException {
        chrono.start();

        Thread.sleep(10);

        long delta = chrono.getDeltaTime();

        assertTrue(delta >= 10);
        assertTrue(delta < 100);
    }

    @Test
    public void testGetDeltaTimeImmediatelyAfterStart() {
        chrono.start();

        long delta = chrono.getDeltaTime();

        assertTrue(delta >= 0);
        assertTrue(delta < 10);
    }

    @Test
    public void testGetDeltaTimeIncreasesOverTime() throws InterruptedException {
        chrono.start();

        Thread.sleep(10);
        long firstDelta = chrono.getDeltaTime();

        Thread.sleep(10);
        long secondDelta = chrono.getDeltaTime();

        assertTrue(secondDelta > firstDelta);
        assertTrue(secondDelta - firstDelta >= 10);
    }

    @Test
    public void testStartResetsTimer() throws InterruptedException {
        chrono.start();
        Thread.sleep(20);

        long firstDelta = chrono.getDeltaTime();

        chrono.start();
        Thread.sleep(5);

        long secondDelta = chrono.getDeltaTime();

        assertTrue(firstDelta >= 20);
        assertTrue(secondDelta < firstDelta);
        assertTrue(secondDelta >= 5);
    }

    @Test
    public void testMultipleStartCalls() throws InterruptedException {
        chrono.start();
        Thread.sleep(10);

        chrono.start();
        Thread.sleep(10);

        chrono.start();
        Thread.sleep(10);

        long delta = chrono.getDeltaTime();

        assertTrue(delta >= 10);
        assertTrue(delta < 20);
    }

    @Test
    public void testGetDeltaTimeIsNonDestructive() throws InterruptedException {
        chrono.start();
        Thread.sleep(10);

        long delta1 = chrono.getDeltaTime();
        long delta2 = chrono.getDeltaTime();
        long delta3 = chrono.getDeltaTime();

        assertTrue(delta1 >= 10);
        assertTrue(delta2 >= delta1);
        assertTrue(delta3 >= delta2);
    }

    @Test
    public void testMultipleInstancesIndependent() throws InterruptedException {
        NRChrono chrono1 = new NRChrono();
        NRChrono chrono2 = new NRChrono();

        chrono1.start();
        Thread.sleep(10);
        chrono2.start();
        Thread.sleep(10);

        long delta1 = chrono1.getDeltaTime();
        long delta2 = chrono2.getDeltaTime();

        assertTrue(delta1 >= 20);
        assertTrue(delta2 >= 10);
        assertTrue(delta1 > delta2);
    }

    @Test
    public void testStartWithoutGetDeltaTime() throws InterruptedException {
        chrono.start();
        Thread.sleep(20);
        chrono.start();

        long delta = chrono.getDeltaTime();

        assertTrue(delta >= 0);
        assertTrue(delta < 10);
    }

    @Test
    public void testGetDeltaTimeAfterLongDelay() throws InterruptedException {
        chrono.start();

        Thread.sleep(100);

        long delta = chrono.getDeltaTime();

        assertTrue(delta >= 100);
        assertTrue(delta < 150);
    }

    @Test
    public void testGetDeltaTimeWithVeryShortDelay() throws InterruptedException {
        chrono.start();

        Thread.sleep(1);

        long delta = chrono.getDeltaTime();

        assertTrue(delta >= 0);
        assertTrue(delta < 10);
    }

    @Test
    public void testStartCanBeCalledMultipleTimes() {
        chrono.start();
        chrono.start();
        chrono.start();

        long delta = chrono.getDeltaTime();

        assertTrue(delta >= 0);
    }

    @Test
    public void testGetDeltaTimeConsistencyAcrossCalls() throws InterruptedException {
        chrono.start();
        Thread.sleep(50);

        long delta1 = chrono.getDeltaTime();
        Thread.sleep(1);
        long delta2 = chrono.getDeltaTime();

        assertTrue(delta2 > delta1);
        assertTrue(delta2 - delta1 < 10);
    }

    @Test
    public void testChronoForMeasuringElapsedTime() throws InterruptedException {
        chrono.start();

        Thread.sleep(15);

        long elapsed = chrono.getDeltaTime();

        assertTrue("Elapsed time should be at least 15ms", elapsed >= 15);
        assertTrue("Elapsed time should be less than 50ms", elapsed < 50);
    }

    @Test
    public void testChronoForMultipleMeasurements() throws InterruptedException {
        chrono.start();

        Thread.sleep(10);
        long measure1 = chrono.getDeltaTime();

        Thread.sleep(10);
        long measure2 = chrono.getDeltaTime();

        Thread.sleep(10);
        long measure3 = chrono.getDeltaTime();

        assertTrue(measure1 >= 10);
        assertTrue(measure2 >= 20);
        assertTrue(measure3 >= 30);
        assertTrue(measure3 > measure2);
        assertTrue(measure2 > measure1);
    }

    @Test
    public void testDeltaTimeIncrementsLinearly() throws InterruptedException {
        chrono.start();

        Thread.sleep(10);
        long time1 = chrono.getDeltaTime();

        Thread.sleep(10);
        long time2 = chrono.getDeltaTime();

        long increment = time2 - time1;
        assertTrue(increment >= 10);
        assertTrue(increment < 20);
    }

    @Test
    public void testChronoResetBehavior() throws InterruptedException {
        chrono.start();
        Thread.sleep(30);
        long firstRun = chrono.getDeltaTime();

        chrono.start();
        Thread.sleep(10);
        long secondRun = chrono.getDeltaTime();

        assertTrue(firstRun >= 30);
        assertTrue(secondRun >= 10);
        assertTrue(secondRun < firstRun);
    }

    @Test
    public void testGetDeltaTimeReturnsZeroAfterConstruction() {
        NRChrono newChrono = new NRChrono();

        assertEquals(0, newChrono.getDeltaTime());
        assertEquals(0, newChrono.getDeltaTime());
        assertEquals(0, newChrono.getDeltaTime());
    }

    @Test
    public void testChronoForTimingOperations() throws InterruptedException {
        chrono.start();

        int sum = 0;
        for (int i = 0; i < 1000; i++) {
            sum += i;
        }

        Thread.sleep(5);

        long operationTime = chrono.getDeltaTime();

        assertTrue(operationTime >= 5);
        assertTrue(sum > 0);
    }
}
