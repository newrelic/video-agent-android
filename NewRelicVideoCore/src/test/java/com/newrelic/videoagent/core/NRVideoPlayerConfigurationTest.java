package com.newrelic.videoagent.core;

import androidx.media3.exoplayer.ExoPlayer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for NRVideoPlayerConfiguration.
 */
public class NRVideoPlayerConfigurationTest {

    @Mock
    private ExoPlayer mockPlayer;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ── Current API ───────────────────────────────────────────────────────────

    @Test
    public void testConstructorWithAdConfig() {
        Map<String, Object> customAttrs = new HashMap<>();
        customAttrs.put("key1", "value1");

        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer, NRAdConfig.csai(), customAttrs);

        assertNotNull(config);
        assertEquals("TestPlayer", config.getPlayerName());
        assertEquals(mockPlayer, config.getPlayer());
        assertNotNull(config.getAdConfig());
        assertEquals(NRAdConfig.Type.CSAI, config.getAdConfig().type);
        assertEquals(customAttrs, config.getCustomAttributes());
    }

    @Test
    public void testConstructorWithNoAds() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer, (NRAdConfig) null, null);

        assertNotNull(config);
        assertNull(config.getAdConfig());
    }

    @Test
    public void testMediaTailorAdConfig() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer, NRAdConfig.mediaTailor(), null);

        assertNotNull(config.getAdConfig());
        assertEquals(NRAdConfig.Type.SSAI_MT, config.getAdConfig().type);
        assertNull(config.getAdConfig().segmentPrefix);
        assertNull(config.getAdConfig().trackingUrl);
    }

    @Test
    public void testMediaTailorAdConfigWithSegmentPrefix() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer, NRAdConfig.mediaTailor("/tm/"), null);

        assertEquals(NRAdConfig.Type.SSAI_MT, config.getAdConfig().type);
        assertEquals("/tm/", config.getAdConfig().segmentPrefix);
        assertNull(config.getAdConfig().trackingUrl);
    }

    @Test
    public void testMediaTailorAdConfigWithTrackingUrl() {
        String url = "https://mediatailor.us-east-1.amazonaws.com/v1/tracking/abc/123";
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer, NRAdConfig.mediaTailor(null, url), null);

        assertEquals(NRAdConfig.Type.SSAI_MT, config.getAdConfig().type);
        assertNull(config.getAdConfig().segmentPrefix);
        assertEquals(url, config.getAdConfig().trackingUrl);
    }

    @Test
    public void testGetCustomAttributes() {
        Map<String, Object> customAttrs = new HashMap<>();
        customAttrs.put("attr1", "value1");
        customAttrs.put("attr2", 42);

        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer, (NRAdConfig) null, customAttrs);

        assertEquals("value1", config.getCustomAttributes().get("attr1"));
        assertEquals(42, config.getCustomAttributes().get("attr2"));
    }

    @Test
    public void testNullCustomAttributes() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer, (NRAdConfig) null, null);

        assertNull(config.getCustomAttributes());
    }

    @Test
    public void testAllFieldsAreAccessible() {
        Map<String, Object> customAttrs = new HashMap<>();
        customAttrs.put("test", "value");

        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "CompletePlayer", mockPlayer,
            NRAdConfig.mediaTailor("/ads/", "https://tracking.example.com"),
            customAttrs);

        assertNotNull(config.getPlayerName());
        assertNotNull(config.getPlayer());
        assertNotNull(config.getCustomAttributes());
        assertEquals(NRAdConfig.Type.SSAI_MT, config.getAdConfig().type);
        assertEquals("/ads/", config.getAdConfig().segmentPrefix);
        assertEquals("https://tracking.example.com", config.getAdConfig().trackingUrl);
    }

    // ── Backward compatibility (deprecated API) ───────────────────────────────

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedBooleanConstructorTrue() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer, true, null);

        assertNotNull(config.getAdConfig());
        assertEquals(NRAdConfig.Type.CSAI, config.getAdConfig().type);
        assertTrue(config.isAdEnabled());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedBooleanConstructorFalse() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer, false, null);

        assertNull(config.getAdConfig());
        assertFalse(config.isAdEnabled());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedAdTrackerTypeIma() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer,
            NRVideoPlayerConfiguration.AdTrackerType.IMA, null);

        assertNotNull(config.getAdConfig());
        assertEquals(NRAdConfig.Type.CSAI, config.getAdConfig().type);
        assertEquals(NRVideoPlayerConfiguration.AdTrackerType.IMA, config.getAdTrackerType());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedAdTrackerTypeMediaTailor() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer,
            NRVideoPlayerConfiguration.AdTrackerType.MEDIA_TAILOR, null);

        assertNotNull(config.getAdConfig());
        assertEquals(NRAdConfig.Type.SSAI_MT, config.getAdConfig().type);
        assertEquals(NRVideoPlayerConfiguration.AdTrackerType.MEDIA_TAILOR, config.getAdTrackerType());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedAdTrackerTypeNone() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer", mockPlayer,
            NRVideoPlayerConfiguration.AdTrackerType.NONE, null);

        assertNull(config.getAdConfig());
        assertEquals(NRVideoPlayerConfiguration.AdTrackerType.NONE, config.getAdTrackerType());
        assertFalse(config.isAdEnabled());
    }
}
