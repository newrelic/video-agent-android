package com.newrelic.videoagent.core.model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for NRTrackerState.
 */
public class NRTrackerStateTest {

    private NRTrackerState state;

    @Before
    public void setUp() {
        state = new NRTrackerState();
    }

    @Test
    public void testConstructorInitializesAllFieldsFalse() {
        NRTrackerState newState = new NRTrackerState();

        assertFalse(newState.isPlayerReady);
        assertFalse(newState.isRequested);
        assertFalse(newState.isStarted);
        assertFalse(newState.isPlaying);
        assertFalse(newState.isPaused);
        assertFalse(newState.isSeeking);
        assertFalse(newState.isBuffering);
        assertFalse(newState.isAd);
        assertFalse(newState.isAdBreak);
        assertNotNull(newState.chrono);
        assertEquals(Long.valueOf(0L), newState.accumulatedVideoWatchTime);
    }

    @Test
    public void testGoPlayerReadyFromInitialState() {
        assertTrue(state.goPlayerReady());
        assertTrue(state.isPlayerReady);
    }

    @Test
    public void testGoPlayerReadyWhenAlreadyReady() {
        state.goPlayerReady();

        assertFalse(state.goPlayerReady());
        assertTrue(state.isPlayerReady);
    }

    @Test
    public void testGoRequestFromInitialState() {
        assertTrue(state.goRequest());
        assertTrue(state.isRequested);
    }

    @Test
    public void testGoRequestWhenAlreadyRequested() {
        state.goRequest();

        assertFalse(state.goRequest());
        assertTrue(state.isRequested);
    }

    @Test
    public void testGoStartRequiresRequest() {
        assertFalse(state.goStart());
        assertFalse(state.isStarted);
        assertFalse(state.isPlaying);
    }

    @Test
    public void testGoStartAfterRequest() {
        state.goRequest();

        assertTrue(state.goStart());
        assertTrue(state.isStarted);
        assertTrue(state.isPlaying);
    }

    @Test
    public void testGoStartWhenAlreadyStarted() {
        state.goRequest();
        state.goStart();

        assertFalse(state.goStart());
    }

    @Test
    public void testGoEndFromInitialState() {
        assertFalse(state.goEnd());
    }

    @Test
    public void testGoEndAfterRequest() {
        state.goRequest();

        assertTrue(state.goEnd());
        assertFalse(state.isRequested);
        assertFalse(state.isStarted);
        assertFalse(state.isPlaying);
        assertFalse(state.isPaused);
        assertFalse(state.isSeeking);
        assertFalse(state.isBuffering);
    }

    @Test
    public void testGoEndResetsMultipleStates() {
        state.goRequest();
        state.goStart();
        state.goPause();

        assertTrue(state.goEnd());
        assertFalse(state.isRequested);
        assertFalse(state.isStarted);
        assertFalse(state.isPlaying);
        assertFalse(state.isPaused);
    }

    @Test
    public void testGoPauseRequiresStarted() {
        assertFalse(state.goPause());
        assertFalse(state.isPaused);
    }

    @Test
    public void testGoPauseAfterStart() {
        state.goRequest();
        state.goStart();

        assertTrue(state.goPause());
        assertTrue(state.isPaused);
        assertFalse(state.isPlaying);
    }

    @Test
    public void testGoPauseWhenAlreadyPaused() {
        state.goRequest();
        state.goStart();
        state.goPause();

        assertFalse(state.goPause());
    }

    @Test
    public void testGoResumeRequiresPaused() {
        assertFalse(state.goResume());
    }

    @Test
    public void testGoResumeAfterPause() {
        state.goRequest();
        state.goStart();
        state.goPause();

        assertTrue(state.goResume());
        assertFalse(state.isPaused);
        assertTrue(state.isPlaying);
    }

    @Test
    public void testGoResumeWhenNotPaused() {
        state.goRequest();
        state.goStart();

        assertFalse(state.goResume());
    }

    @Test
    public void testGoBufferStartRequiresRequested() {
        assertFalse(state.goBufferStart());
        assertFalse(state.isBuffering);
    }

    @Test
    public void testGoBufferStartAfterRequest() {
        state.goRequest();

        assertTrue(state.goBufferStart());
        assertTrue(state.isBuffering);
        assertFalse(state.isPlaying);
    }

    @Test
    public void testGoBufferStartWhenAlreadyBuffering() {
        state.goRequest();
        state.goBufferStart();

        assertFalse(state.goBufferStart());
    }

    @Test
    public void testGoBufferEndRequiresBuffering() {
        assertFalse(state.goBufferEnd());
    }

    @Test
    public void testGoBufferEndAfterBufferStart() {
        state.goRequest();
        state.goBufferStart();

        assertTrue(state.goBufferEnd());
        assertFalse(state.isBuffering);
        assertTrue(state.isPlaying);
    }

    @Test
    public void testGoSeekStartRequiresStarted() {
        assertFalse(state.goSeekStart());
        assertFalse(state.isSeeking);
    }

    @Test
    public void testGoSeekStartAfterStart() {
        state.goRequest();
        state.goStart();

        assertTrue(state.goSeekStart());
        assertTrue(state.isSeeking);
        assertFalse(state.isPlaying);
    }

    @Test
    public void testGoSeekStartWhenAlreadySeeking() {
        state.goRequest();
        state.goStart();
        state.goSeekStart();

        assertFalse(state.goSeekStart());
    }

    @Test
    public void testGoSeekEndRequiresSeeking() {
        assertFalse(state.goSeekEnd());
    }

    @Test
    public void testGoSeekEndAfterSeekStart() {
        state.goRequest();
        state.goStart();
        state.goSeekStart();

        assertTrue(state.goSeekEnd());
        assertFalse(state.isSeeking);
        assertTrue(state.isPlaying);
    }

    @Test
    public void testGoAdBreakStartFromInitialState() {
        assertTrue(state.goAdBreakStart());
        assertTrue(state.isAdBreak);
    }

    @Test
    public void testGoAdBreakStartWhenAlreadyInAdBreak() {
        state.goAdBreakStart();

        assertFalse(state.goAdBreakStart());
    }

    @Test
    public void testGoAdBreakEndRequiresAdBreak() {
        assertFalse(state.goAdBreakEnd());
    }

    @Test
    public void testGoAdBreakEndAfterAdBreakStart() {
        state.goAdBreakStart();

        assertTrue(state.goAdBreakEnd());
        assertFalse(state.isAdBreak);
        assertFalse(state.isRequested);
    }

    @Test
    public void testResetClearsAllStates() {
        state.goPlayerReady();
        state.goRequest();
        state.goStart();

        state.reset();

        assertFalse(state.isPlayerReady);
        assertFalse(state.isRequested);
        assertFalse(state.isStarted);
        assertFalse(state.isPlaying);
        assertFalse(state.isPaused);
        assertFalse(state.isSeeking);
        assertFalse(state.isBuffering);
        assertFalse(state.isAd);
        assertFalse(state.isAdBreak);
        assertNotNull(state.chrono);
        assertEquals(Long.valueOf(0L), state.accumulatedVideoWatchTime);
    }

    @Test
    public void testCompleteVideoPlaybackFlow() {
        assertTrue(state.goPlayerReady());
        assertTrue(state.goRequest());
        assertTrue(state.goStart());
        assertTrue(state.goPause());
        assertTrue(state.goResume());
        assertTrue(state.goEnd());
    }

    @Test
    public void testBufferingDuringPlayback() {
        state.goRequest();
        state.goStart();

        assertTrue(state.goBufferStart());
        assertTrue(state.isBuffering);
        assertFalse(state.isPlaying);

        assertTrue(state.goBufferEnd());
        assertFalse(state.isBuffering);
        assertTrue(state.isPlaying);
    }

    @Test
    public void testSeekingDuringPlayback() {
        state.goRequest();
        state.goStart();

        assertTrue(state.goSeekStart());
        assertTrue(state.isSeeking);
        assertFalse(state.isPlaying);

        assertTrue(state.goSeekEnd());
        assertFalse(state.isSeeking);
        assertTrue(state.isPlaying);
    }

    @Test
    public void testAdBreakFlow() {
        state.goPlayerReady();
        state.goRequest();

        assertTrue(state.goAdBreakStart());
        assertTrue(state.isAdBreak);

        assertTrue(state.goAdBreakEnd());
        assertFalse(state.isAdBreak);
        assertFalse(state.isRequested);
    }

    @Test
    public void testMultiplePauseResumeCycles() {
        state.goRequest();
        state.goStart();

        assertTrue(state.goPause());
        assertTrue(state.goResume());
        assertTrue(state.goPause());
        assertTrue(state.goResume());

        assertTrue(state.isPlaying);
        assertFalse(state.isPaused);
    }

    @Test
    public void testCannotStartWithoutRequest() {
        state.goPlayerReady();

        assertFalse(state.goStart());
        assertFalse(state.isStarted);
    }

    @Test
    public void testCannotPauseWithoutStart() {
        state.goRequest();

        assertFalse(state.goPause());
        assertFalse(state.isPaused);
    }

    @Test
    public void testCannotSeekWithoutStart() {
        state.goRequest();

        assertFalse(state.goSeekStart());
        assertFalse(state.isSeeking);
    }

    @Test
    public void testChronoIsInitializedOnConstruction() {
        assertNotNull(state.chrono);
        assertEquals(0, state.chrono.getDeltaTime());
    }

    @Test
    public void testChronoIsResetOnReset() {
        state.chrono.start();

        state.reset();

        assertNotNull(state.chrono);
        assertEquals(0, state.chrono.getDeltaTime());
    }

    @Test
    public void testAccumulatedVideoWatchTimeInitializedToZero() {
        assertEquals(Long.valueOf(0L), state.accumulatedVideoWatchTime);
    }

    @Test
    public void testAccumulatedVideoWatchTimeResetToZero() {
        state.accumulatedVideoWatchTime = 12345L;

        state.reset();

        assertEquals(Long.valueOf(0L), state.accumulatedVideoWatchTime);
    }

    @Test
    public void testStateTransitionsAreIdempotent() {
        state.goRequest();
        state.goStart();

        assertTrue(state.isStarted);
        assertFalse(state.goStart());
        assertTrue(state.isStarted);
    }

    @Test
    public void testEndClearsAllPlaybackStates() {
        state.goRequest();
        state.goStart();
        state.goSeekStart();
        state.goBufferStart();

        state.goEnd();

        assertFalse(state.isRequested);
        assertFalse(state.isStarted);
        assertFalse(state.isPlaying);
        assertFalse(state.isSeeking);
        assertFalse(state.isBuffering);
    }

    @Test
    public void testMultipleResets() {
        state.goPlayerReady();
        state.reset();
        state.goRequest();
        state.reset();
        state.goStart();
        state.reset();

        assertFalse(state.isPlayerReady);
        assertFalse(state.isRequested);
        assertFalse(state.isStarted);
    }

    @Test
    public void testComplexPlaybackScenario() {
        state.goPlayerReady();
        state.goRequest();
        state.goBufferStart();
        state.goBufferEnd();
        state.goStart();
        state.goSeekStart();
        state.goSeekEnd();
        state.goPause();
        state.goResume();
        state.goEnd();

        assertFalse(state.isRequested);
        assertFalse(state.isStarted);
        assertFalse(state.isPlaying);
    }
}
