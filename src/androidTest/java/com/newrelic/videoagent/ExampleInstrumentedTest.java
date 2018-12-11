package com.newrelic.videoagent;

import android.support.test.runner.AndroidJUnit4;

import com.newrelic.videoagent.jni.swig.CoreTrackerState;
import com.newrelic.videoagent.jni.swig.TrackerCore;
import com.newrelic.videoagent.jni.swig.ValueHolder;
import com.newrelic.videoagent.tracker.AdsTracker;
import com.newrelic.videoagent.tracker.ContentsTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

class TestContentsTracker extends ContentsTracker {

    @Override
    public void setup() {
        super.setup();
    }

    @Override
    public void reset() {
        super.reset();
    }

    @Override
    public Object getPlayerName() {
        return "FakePlayer";
    }

    @Override
    public Object getPlayerVersion() {
        return "X.Y";
    }

    @Override
    public Object getTrackerName() {
        return "TestContentsTracker";
    }

    @Override
    public Object getTrackerVersion() {
        return "Y.Z";
    }
}

class TestAdsTracker extends AdsTracker {

    @Override
    public void setup() {
        super.setup();
    }

    @Override
    public void reset() {
        super.reset();
    }

    @Override
    public Object getPlayerName() {
        return "FakePlayer";
    }

    @Override
    public Object getPlayerVersion() {
        return "X.Y";
    }

    @Override
    public Object getTrackerName() {
        return "TestAdsTracker";
    }

    @Override
    public Object getTrackerVersion() {
        return "Y.Z";
    }
}

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    static {
        System.loadLibrary("Core");
    }

    public static native void initJNIEnv();

    @Before
    public void setUp() {
        initJNIEnv();
    }

    @After
    public void tearDown() {

    }

    @Test
    public void testContentsTracker() {
        TestContentsTracker tracker = new TestContentsTracker();
        runTrackerTest(tracker);
    }

    @Test
    public void adsContentsTracker() {
        TestAdsTracker tracker = new TestAdsTracker();
        runTrackerTest(tracker);
    }

    private void runTrackerTest(TrackerCore tracker) {

        assertTrue("Invalid tracker class", tracker instanceof ContentsTracker || tracker instanceof AdsTracker);

        tracker.reset();
        tracker.setup();

        assertTrue("State not Stopped", tracker.state() == CoreTrackerState.CoreTrackerStateStopped);

        tracker.updateAttribute("option", new ValueHolder(123));
        tracker.updateAttribute("option", new ValueHolder(123), "TEST_ACTION");

        assertTrue("Error setting unknown timestamp", !tracker.setTimestamp(1000.0, "xxxxx"));
        assertTrue("Error setting timeSinceTrackerReady", tracker.setTimestamp(1000.0, "timeSinceTrackerReady"));
        assertTrue("Error setting timeSinceLastRenditionChange", tracker.setTimestamp(1000.0, "timeSinceLastRenditionChange"));
        assertTrue("Error setting timeSinceRequested", tracker.setTimestamp(1000.0, "timeSinceRequested"));

        if (tracker instanceof AdsTracker) {
            assertTrue("Error setting timeSinceLastAdHeartbeat", tracker.setTimestamp(1000.0, "timeSinceLastAdHeartbeat"));
            assertTrue("Error setting timeSinceAdStarted", tracker.setTimestamp(1000.0, "timeSinceAdStarted"));
            assertTrue("Error setting timeSinceAdPaused", tracker.setTimestamp(1000.0, "timeSinceAdPaused"));
            assertTrue("Error setting timeSinceAdBufferBegin", tracker.setTimestamp(1000.0, "timeSinceAdBufferBegin"));
            assertTrue("Error setting timeSinceAdSeekBegin", tracker.setTimestamp(1000.0, "timeSinceAdSeekBegin"));
            assertTrue("Error setting timeSinceAdBreakBegin", tracker.setTimestamp(1000.0, "timeSinceAdBreakBegin"));
            assertTrue("Error setting timeSinceLastAdQuartile", tracker.setTimestamp(1000.0, "timeSinceLastAdQuartile"));
        }
        else {
            assertTrue("Error setting timeSinceStarted", tracker.setTimestamp(1000.0, "timeSinceStarted"));
            assertTrue("Error setting timeSincePaused", tracker.setTimestamp(1000.0, "timeSincePaused"));
            assertTrue("Error setting timeSinceBufferBegin", tracker.setTimestamp(1000.0, "timeSinceBufferBegin"));
            assertTrue("Error setting timeSinceSeekBegin", tracker.setTimestamp(1000.0, "timeSinceSeekBegin"));
            assertTrue("Error setting timeSinceLastAd", tracker.setTimestamp(1000.0, "timeSinceLastAd"));
            assertTrue("Error setting timeSinceLastHeartbeat", tracker.setTimestamp(1000.0, "timeSinceLastHeartbeat"));
        }

        assertTrue("State not Stopped", tracker.state() == CoreTrackerState.CoreTrackerStateStopped);

        tracker.sendRequest();
        assertTrue("State not Starting", tracker.state() == CoreTrackerState.CoreTrackerStateStarting);

        tracker.sendStart();
        assertTrue("State not Playing", tracker.state() == CoreTrackerState.CoreTrackerStatePlaying);

        tracker.sendPause();
        assertTrue("State not Paused", tracker.state() == CoreTrackerState.CoreTrackerStatePaused);

        tracker.sendResume();
        assertTrue("State not Paying", tracker.state() == CoreTrackerState.CoreTrackerStatePlaying);

        tracker.sendBufferStart();
        assertTrue("State not Buffering",tracker.state() == CoreTrackerState.CoreTrackerStateBuffering);

        tracker.sendPause();
        assertTrue("State not Buffering", tracker.state() == CoreTrackerState.CoreTrackerStateBuffering);

        tracker.sendBufferEnd();
        assertTrue("State not Playing", tracker.state() == CoreTrackerState.CoreTrackerStatePlaying);

        tracker.sendSeekStart();
        assertTrue("State not Seeking",tracker.state() == CoreTrackerState.CoreTrackerStateSeeking);

        tracker.sendSeekEnd();
        assertTrue("State not Playing",tracker.state() == CoreTrackerState.CoreTrackerStatePlaying);

        tracker.sendHeartbeat();
        assertTrue("State not Playing", tracker.state() == CoreTrackerState.CoreTrackerStatePlaying);

        tracker.sendError("Test error message");
        assertTrue("State not Playing", tracker.state() == CoreTrackerState.CoreTrackerStatePlaying);

        tracker.sendRenditionChange();
        assertTrue("State not Playing", tracker.state() == CoreTrackerState.CoreTrackerStatePlaying);
    }
}
