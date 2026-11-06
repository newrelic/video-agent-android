package com.newrelic.videoagent.core.tracker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The per-ad timeSince values must not carry over from the previous ad break.
 */
@RunWith(RobolectricTestRunner.class)
public class NRVideoTrackerAdTimeSinceTest {

    private static class Sent {
        final String action;
        final Map<String, Object> attributes;

        Sent(String action, Map<String, Object> attributes) {
            this.action = action;
            this.attributes = new HashMap<>(attributes);
        }
    }

    /** Captures the fully built attributes (timeSince applied) instead of recording the event. */
    private static class CapturingTracker extends NRVideoTracker {
        final List<Sent> sent = new ArrayList<>();

        @Override
        public boolean preSend(String action, Map<String, Object> attributes) {
            sent.add(new Sent(action, attributes));
            return false;
        }

        Sent last(String action) {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (sent.get(i).action.equals(action)) return sent.get(i);
            }
            throw new AssertionError("no " + action + " sent: " + sent.size());
        }
    }

    private CapturingTracker ad;
    private NRVideoTracker content;

    @Before
    public void setUp() {
        ad = new CapturingTracker();
        ad.getState().isAd = true;
        content = new NRVideoTracker();
        ad.linkedTracker = content;
    }

    @After
    public void tearDown() {
        ad.dispose();
        content.dispose();
    }

    private void playOneAdBreak() throws InterruptedException {
        ad.sendAdBreakStart();
        ad.sendRequest();
        ad.sendStart();
        Thread.sleep(20);
        ad.sendAdQuartile();
        ad.sendEnd();
        ad.sendAdBreakEnd();
    }

    @Test
    public void secondBreakStartsWithoutThePreviousAdTimers() throws InterruptedException {
        playOneAdBreak();
        assertTrue("sanity: first AD_END has the timers",
                ad.last("AD_END").attributes.containsKey("timeSinceAdStarted"));

        ad.sendAdBreakStart();

        Map<String, Object> second = ad.last("AD_BREAK_START").attributes;
        assertFalse(second.containsKey("timeSinceAdRequested"));
        assertFalse(second.containsKey("timeSinceAdStarted"));
        assertFalse(second.containsKey("timeSinceLastAdHeartbeat"));
        assertFalse(second.containsKey("timeSinceLastAdQuartile"));
    }

    @Test
    public void adTimersWorkAgainInTheNewBreak() throws InterruptedException {
        playOneAdBreak();
        playOneAdBreak();

        Map<String, Object> end = ad.last("AD_END").attributes;
        assertTrue(end.containsKey("timeSinceAdRequested"));
        assertTrue(end.containsKey("timeSinceAdStarted"));
    }
}
