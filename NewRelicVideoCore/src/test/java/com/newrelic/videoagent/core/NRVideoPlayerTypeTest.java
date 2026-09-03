package com.newrelic.videoagent.core;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Tests for the playerType-driven path in NRVideo.addPlayer / createTrackerForType.
 */
@RunWith(RobolectricTestRunner.class)
public class NRVideoPlayerTypeTest {

    private Context context;
    private NRVideoConfiguration config;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        config  = new NRVideoConfiguration.Builder("test-token-1234567890").build();
        resetSingleton();
        NRVideo.newBuilder(context).withConfiguration(config).build();
    }

    @After
    public void tearDown() throws Exception {
        resetSingleton();
    }

    private void resetSingleton() throws Exception {
        Field f = NRVideo.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    // ── PLAYER_TYPE_* constants ───────────────────────────────────────────────

    @Test
    public void playerTypeExoConstant_isExo() {
        assertEquals("exo", NRVideoPlayerConfiguration.PLAYER_TYPE_EXO);
    }

    @Test
    public void playerTypeTheoConstant_isTheo() {
        assertEquals("theo", NRVideoPlayerConfiguration.PLAYER_TYPE_THEO);
    }

    // ── Unknown playerType → IllegalArgumentException ─────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void addPlayer_unknownPlayerType_throwsIllegalArgument() {
        NRVideoPlayerConfiguration cfg = new NRVideoPlayerConfiguration(
                "test", new Object(), "bitmovin", null, null);
        NRVideo.addPlayer(cfg);
    }

    @Test
    public void addPlayer_unknownPlayerType_errorMessageContainsType() {
        NRVideoPlayerConfiguration cfg = new NRVideoPlayerConfiguration(
                "test", new Object(), "bitmovin", null, null);
        try {
            NRVideo.addPlayer(cfg);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("bitmovin"));
        }
    }

    // ── Missing tracker module → clear error (ClassNotFoundException path) ────

    @Test
    public void addPlayer_exoType_missingModule_throwsIllegalState() {
        // NRExoPlayerTracker is not on the Core test classpath — ClassNotFoundException expected
        NRVideoPlayerConfiguration cfg = new NRVideoPlayerConfiguration(
                "test", new Object(), NRVideoPlayerConfiguration.PLAYER_TYPE_EXO, null, null);
        try {
            NRVideo.addPlayer(cfg);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Tracker class not found"));
            assertTrue(e.getMessage().contains("NRTrackerExoPlayer"));
        }
    }

    @Test
    public void addPlayer_theoType_missingModule_throwsIllegalState() {
        NRVideoPlayerConfiguration cfg = new NRVideoPlayerConfiguration(
                "test", new Object(), NRVideoPlayerConfiguration.PLAYER_TYPE_THEO, null, null);
        try {
            NRVideo.addPlayer(cfg);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Tracker class not found"));
            assertTrue(e.getMessage().contains("NRTrackerTHEOPlayer"));
        }
    }

    // ── Legacy (null playerType) → defaults to ExoPlayer path ────────────────

    @Test
    public void addPlayer_legacyNullType_defaultsToExoPath() {
        // Null playerType → legacy path → attempts NRTrackerExoPlayer (not on classpath here)
        NRVideoPlayerConfiguration cfg = new NRVideoPlayerConfiguration(
                "test", new Object(), (NRAdConfig) null, null);
        assertNull(cfg.getPlayerType());
        try {
            NRVideo.addPlayer(cfg);
            fail("Expected IllegalStateException from missing ExoPlayer module");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("NRTrackerExoPlayer"));
        }
    }

    // ── NRVideoPlayerConfiguration constructor delegation ─────────────────────

    @Test
    public void legacyConstructor_playerTypeIsNull() {
        NRVideoPlayerConfiguration cfg = new NRVideoPlayerConfiguration(
                "p", new Object(), (NRAdConfig) null, null);
        assertNull(cfg.getPlayerType());
    }

    @Test
    public void configDrivenConstructor_playerTypeIsPreserved() {
        NRVideoPlayerConfiguration cfg = new NRVideoPlayerConfiguration(
                "p", new Object(), NRVideoPlayerConfiguration.PLAYER_TYPE_THEO, null, null);
        assertEquals(NRVideoPlayerConfiguration.PLAYER_TYPE_THEO, cfg.getPlayerType());
    }
}
