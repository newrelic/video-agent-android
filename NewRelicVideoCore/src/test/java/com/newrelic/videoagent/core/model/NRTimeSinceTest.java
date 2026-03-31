package com.newrelic.videoagent.core.model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for NRTimeSince.
 */
public class NRTimeSinceTest {

    private NRTimeSince timeSince;

    @Before
    public void setUp() {
        timeSince = new NRTimeSince("VIDEO_START", "timeSinceVideoStart", "VIDEO_.*");
    }

    @Test
    public void testConstructor() {
        NRTimeSince ts = new NRTimeSince("ACTION", "attrName", "FILTER");

        assertNotNull(ts);
        assertEquals("attrName", ts.getAttribute());
    }

    @Test
    public void testIsActionWithMatchingAction() {
        assertTrue(timeSince.isAction("VIDEO_START"));
    }

    @Test
    public void testIsActionWithNonMatchingAction() {
        assertFalse(timeSince.isAction("VIDEO_END"));
        assertFalse(timeSince.isAction("VIDEO_PAUSE"));
        assertFalse(timeSince.isAction("AUDIO_START"));
    }

    @Test
    public void testIsActionWithEmptyString() {
        assertFalse(timeSince.isAction(""));
    }

    @Test
    public void testIsActionWithNull() {
        assertFalse(timeSince.isAction(null));
    }

    @Test
    public void testIsMatchWithMatchingPattern() {
        assertTrue(timeSince.isMatch("VIDEO_START"));
        assertTrue(timeSince.isMatch("VIDEO_END"));
        assertTrue(timeSince.isMatch("VIDEO_PAUSE"));
        assertTrue(timeSince.isMatch("VIDEO_ERROR"));
    }

    @Test
    public void testIsMatchWithNonMatchingPattern() {
        assertFalse(timeSince.isMatch("AUDIO_START"));
        assertFalse(timeSince.isMatch("CONTENT_START"));
        assertFalse(timeSince.isMatch("PLAYER_READY"));
    }

    @Test
    public void testIsMatchWithExactFilter() {
        NRTimeSince exactFilter = new NRTimeSince("VIDEO_START", "attr", "VIDEO_END");

        assertTrue(exactFilter.isMatch("VIDEO_END"));
        assertFalse(exactFilter.isMatch("VIDEO_START"));
        assertFalse(exactFilter.isMatch("VIDEO_PAUSE"));
    }

    @Test
    public void testIsMatchWithComplexRegex() {
        NRTimeSince complexRegex = new NRTimeSince("START", "attr", "(VIDEO|AUDIO)_(START|END)");

        assertTrue(complexRegex.isMatch("VIDEO_START"));
        assertTrue(complexRegex.isMatch("VIDEO_END"));
        assertTrue(complexRegex.isMatch("AUDIO_START"));
        assertTrue(complexRegex.isMatch("AUDIO_END"));
        assertFalse(complexRegex.isMatch("VIDEO_PAUSE"));
        assertFalse(complexRegex.isMatch("CONTENT_START"));
    }

    @Test
    public void testIsMatchWithWildcard() {
        NRTimeSince wildcard = new NRTimeSince("START", "attr", ".*");

        assertTrue(wildcard.isMatch("VIDEO_START"));
        assertTrue(wildcard.isMatch("AUDIO_START"));
        assertTrue(wildcard.isMatch("ANY_ACTION"));
        assertTrue(wildcard.isMatch(""));
    }

    @Test
    public void testTimeSinceBeforeNow() {
        Long result = timeSince.timeSince();

        assertNull(result);
    }

    @Test
    public void testTimeSinceAfterNow() throws InterruptedException {
        timeSince.now();

        Thread.sleep(10);

        Long result = timeSince.timeSince();

        assertNotNull(result);
        assertTrue(result >= 10);
        assertTrue(result < 100);
    }

    @Test
    public void testTimeSinceImmediatelyAfterNow() {
        timeSince.now();

        Long result = timeSince.timeSince();

        assertNotNull(result);
        assertTrue(result >= 0);
    }

    @Test
    public void testTimeSinceIncreasesOverTime() throws InterruptedException {
        timeSince.now();

        Thread.sleep(10);
        Long first = timeSince.timeSince();

        Thread.sleep(10);
        Long second = timeSince.timeSince();

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(second > first);
    }

    @Test
    public void testNowResetsTimestamp() throws InterruptedException {
        timeSince.now();
        Thread.sleep(20);

        Long firstTime = timeSince.timeSince();

        timeSince.now();
        Thread.sleep(5);

        Long secondTime = timeSince.timeSince();

        assertNotNull(firstTime);
        assertNotNull(secondTime);
        assertTrue(secondTime < firstTime);
    }

    @Test
    public void testMultipleNowCalls() throws InterruptedException {
        timeSince.now();
        Thread.sleep(10);
        timeSince.now();
        Thread.sleep(10);
        timeSince.now();

        Long result = timeSince.timeSince();

        assertNotNull(result);
        assertTrue(result >= 0);
        assertTrue(result < 20);
    }

    @Test
    public void testGetAttribute() {
        assertEquals("timeSinceVideoStart", timeSince.getAttribute());
    }

    @Test
    public void testGetAttributeWithDifferentNames() {
        NRTimeSince ts1 = new NRTimeSince("A", "attr1", ".*");
        NRTimeSince ts2 = new NRTimeSince("B", "attr2", ".*");
        NRTimeSince ts3 = new NRTimeSince("C", "timeSinceLoad", ".*");

        assertEquals("attr1", ts1.getAttribute());
        assertEquals("attr2", ts2.getAttribute());
        assertEquals("timeSinceLoad", ts3.getAttribute());
    }

    @Test
    public void testIsActionCaseSensitive() {
        assertTrue(timeSince.isAction("VIDEO_START"));
        assertFalse(timeSince.isAction("video_start"));
        assertFalse(timeSince.isAction("Video_Start"));
    }

    @Test
    public void testIsMatchCaseSensitive() {
        assertTrue(timeSince.isMatch("VIDEO_START"));
        assertFalse(timeSince.isMatch("video_start"));
    }

    @Test
    public void testConstructorWithEmptyStrings() {
        NRTimeSince ts = new NRTimeSince("", "", "");

        assertNotNull(ts);
        assertEquals("", ts.getAttribute());
        assertTrue(ts.isAction(""));
    }

    @Test
    public void testTimeSinceWithVeryShortDelay() throws InterruptedException {
        timeSince.now();
        Thread.sleep(1);

        Long result = timeSince.timeSince();

        assertNotNull(result);
        assertTrue(result >= 0);
    }

    @Test
    public void testIsMatchWithSpecialRegexCharacters() {
        NRTimeSince dotFilter = new NRTimeSince("A", "attr", "VIDEO.START");

        assertTrue(dotFilter.isMatch("VIDEO_START"));
        assertTrue(dotFilter.isMatch("VIDEOXSTART"));
        assertFalse(dotFilter.isMatch("VIDEOSTART"));
    }

    @Test
    public void testIsMatchWithAnchoredPattern() {
        NRTimeSince anchored = new NRTimeSince("A", "attr", "^VIDEO_START$");

        assertTrue(anchored.isMatch("VIDEO_START"));
        assertFalse(anchored.isMatch("PREFIX_VIDEO_START"));
        assertFalse(anchored.isMatch("VIDEO_START_SUFFIX"));
    }

    @Test
    public void testMultipleInstancesIndependent() throws InterruptedException {
        NRTimeSince ts1 = new NRTimeSince("VIDEO_START", "attr1", ".*");
        NRTimeSince ts2 = new NRTimeSince("VIDEO_END", "attr2", ".*");

        ts1.now();
        Thread.sleep(10);
        ts2.now();
        Thread.sleep(10);

        Long time1 = ts1.timeSince();
        Long time2 = ts2.timeSince();

        assertNotNull(time1);
        assertNotNull(time2);
        assertTrue(time1 > time2);
    }
}
