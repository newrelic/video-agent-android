package com.newrelic.videoagent.core.model;

import com.newrelic.videoagent.core.tracker.NRTracker;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;

/**
 * Unit tests for NRTrackerPair.
 */
public class NRTrackerPairTest {

    @Mock
    private NRTracker mockFirstTracker;

    @Mock
    private NRTracker mockSecondTracker;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testConstructorInitializesTrackers() {
        NRTrackerPair pair = new NRTrackerPair(mockFirstTracker, mockSecondTracker);

        assertNotNull(pair);
        assertNotNull(pair.getFirst());
        assertNotNull(pair.getSecond());
    }

    @Test
    public void testGetFirstReturnsCorrectTracker() {
        NRTrackerPair pair = new NRTrackerPair(mockFirstTracker, mockSecondTracker);

        NRTracker result = pair.getFirst();

        assertSame(mockFirstTracker, result);
    }

    @Test
    public void testGetSecondReturnsCorrectTracker() {
        NRTrackerPair pair = new NRTrackerPair(mockFirstTracker, mockSecondTracker);

        NRTracker result = pair.getSecond();

        assertSame(mockSecondTracker, result);
    }

    @Test
    public void testConstructorWithNullFirstTracker() {
        NRTrackerPair pair = new NRTrackerPair(null, mockSecondTracker);

        assertNull(pair.getFirst());
        assertNotNull(pair.getSecond());
        assertSame(mockSecondTracker, pair.getSecond());
    }

    @Test
    public void testConstructorWithNullSecondTracker() {
        NRTrackerPair pair = new NRTrackerPair(mockFirstTracker, null);

        assertNotNull(pair.getFirst());
        assertNull(pair.getSecond());
        assertSame(mockFirstTracker, pair.getFirst());
    }

    @Test
    public void testConstructorWithBothNullTrackers() {
        NRTrackerPair pair = new NRTrackerPair(null, null);

        assertNull(pair.getFirst());
        assertNull(pair.getSecond());
    }

    @Test
    public void testGetFirstIsConsistent() {
        NRTrackerPair pair = new NRTrackerPair(mockFirstTracker, mockSecondTracker);

        NRTracker first1 = pair.getFirst();
        NRTracker first2 = pair.getFirst();

        assertSame(first1, first2);
        assertSame(mockFirstTracker, first1);
    }

    @Test
    public void testGetSecondIsConsistent() {
        NRTrackerPair pair = new NRTrackerPair(mockFirstTracker, mockSecondTracker);

        NRTracker second1 = pair.getSecond();
        NRTracker second2 = pair.getSecond();

        assertSame(second1, second2);
        assertSame(mockSecondTracker, second1);
    }

    @Test
    public void testFirstAndSecondAreDifferent() {
        NRTrackerPair pair = new NRTrackerPair(mockFirstTracker, mockSecondTracker);

        NRTracker first = pair.getFirst();
        NRTracker second = pair.getSecond();

        assertNotSame(first, second);
    }

    @Test
    public void testConstructorWithSameTrackerInstance() {
        NRTrackerPair pair = new NRTrackerPair(mockFirstTracker, mockFirstTracker);

        NRTracker first = pair.getFirst();
        NRTracker second = pair.getSecond();

        assertSame(first, second);
        assertSame(mockFirstTracker, first);
        assertSame(mockFirstTracker, second);
    }

    @Test
    public void testMultiplePairInstances() {
        NRTrackerPair pair1 = new NRTrackerPair(mockFirstTracker, mockSecondTracker);
        NRTrackerPair pair2 = new NRTrackerPair(mockSecondTracker, mockFirstTracker);

        assertNotSame(pair1, pair2);
        assertSame(pair1.getFirst(), pair2.getSecond());
        assertSame(pair1.getSecond(), pair2.getFirst());
    }

    @Test
    public void testPairImmutability() {
        NRTrackerPair pair = new NRTrackerPair(mockFirstTracker, mockSecondTracker);

        NRTracker firstBefore = pair.getFirst();
        NRTracker secondBefore = pair.getSecond();

        // Get trackers multiple times to ensure they remain consistent
        for (int i = 0; i < 10; i++) {
            assertSame(firstBefore, pair.getFirst());
            assertSame(secondBefore, pair.getSecond());
        }
    }
}
