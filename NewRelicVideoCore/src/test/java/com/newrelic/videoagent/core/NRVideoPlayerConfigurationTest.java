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

    @Test
    public void testConstructorWithAllParameters() {
        Map<String, Object> customAttrs = new HashMap<>();
        customAttrs.put("key1", "value1");

        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer",
            mockPlayer,
            true,
            customAttrs
        );

        assertNotNull(config);
        assertEquals("TestPlayer", config.getPlayerName());
        assertEquals(mockPlayer, config.getPlayer());
        assertTrue(config.isAdEnabled());
        assertEquals(customAttrs, config.getCustomAttributes());
    }

    @Test
    public void testConstructorWithAdDisabled() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer",
            mockPlayer,
            false,
            null
        );

        assertNotNull(config);
        assertFalse(config.isAdEnabled());
    }

    @Test
    public void testGetPlayerName() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "MyPlayer",
            mockPlayer,
            false,
            null
        );

        assertEquals("MyPlayer", config.getPlayerName());
    }

    @Test
    public void testGetPlayer() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer",
            mockPlayer,
            false,
            null
        );

        assertSame(mockPlayer, config.getPlayer());
    }

    @Test
    public void testGetCustomAttributes() {
        Map<String, Object> customAttrs = new HashMap<>();
        customAttrs.put("attr1", "value1");
        customAttrs.put("attr2", 42);

        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer",
            mockPlayer,
            false,
            customAttrs
        );

        Map<String, Object> result = config.getCustomAttributes();
        assertEquals(customAttrs, result);
        assertEquals("value1", result.get("attr1"));
        assertEquals(42, result.get("attr2"));
    }

    @Test
    public void testIsAdEnabled() {
        NRVideoPlayerConfiguration configWithAds = new NRVideoPlayerConfiguration(
            "TestPlayer",
            mockPlayer,
            true,
            null
        );

        NRVideoPlayerConfiguration configWithoutAds = new NRVideoPlayerConfiguration(
            "TestPlayer",
            mockPlayer,
            false,
            null
        );

        assertTrue(configWithAds.isAdEnabled());
        assertFalse(configWithoutAds.isAdEnabled());
    }

    @Test
    public void testConstructorWithNullPlayerName() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            null,
            mockPlayer,
            false,
            null
        );

        assertNull(config.getPlayerName());
    }

    @Test
    public void testConstructorWithNullPlayer() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer",
            null,
            false,
            null
        );

        assertNull(config.getPlayer());
    }

    @Test
    public void testConstructorWithNullCustomAttributes() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer",
            mockPlayer,
            false,
            null
        );

        assertNull(config.getCustomAttributes());
    }

    @Test
    public void testConstructorWithEmptyCustomAttributes() {
        Map<String, Object> emptyAttrs = new HashMap<>();

        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer",
            mockPlayer,
            false,
            emptyAttrs
        );

        assertNotNull(config.getCustomAttributes());
        assertTrue(config.getCustomAttributes().isEmpty());
    }

    @Test
    public void testConstructorWithMultipleCustomAttributes() {
        Map<String, Object> customAttrs = new HashMap<>();
        customAttrs.put("stringAttr", "test");
        customAttrs.put("intAttr", 123);
        customAttrs.put("boolAttr", true);
        customAttrs.put("doubleAttr", 3.14);

        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer",
            mockPlayer,
            true,
            customAttrs
        );

        Map<String, Object> result = config.getCustomAttributes();
        assertEquals(4, result.size());
        assertEquals("test", result.get("stringAttr"));
        assertEquals(123, result.get("intAttr"));
        assertEquals(true, result.get("boolAttr"));
        assertEquals(3.14, result.get("doubleAttr"));
    }

    @Test
    public void testPlayerNameWithSpecialCharacters() {
        String specialName = "Player@#$%^&*()_+-=[]{}|;':\",./<>?";

        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            specialName,
            mockPlayer,
            false,
            null
        );

        assertEquals(specialName, config.getPlayerName());
    }

    @Test
    public void testPlayerNameWithEmptyString() {
        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "",
            mockPlayer,
            false,
            null
        );

        assertEquals("", config.getPlayerName());
    }

    @Test
    public void testMultipleInstancesWithSamePlayer() {
        NRVideoPlayerConfiguration config1 = new NRVideoPlayerConfiguration(
            "Player1",
            mockPlayer,
            true,
            null
        );

        NRVideoPlayerConfiguration config2 = new NRVideoPlayerConfiguration(
            "Player2",
            mockPlayer,
            false,
            null
        );

        assertSame(config1.getPlayer(), config2.getPlayer());
        assertNotEquals(config1.getPlayerName(), config2.getPlayerName());
    }

    @Test
    public void testCustomAttributesImmutability() {
        Map<String, Object> originalAttrs = new HashMap<>();
        originalAttrs.put("key", "value");

        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "TestPlayer",
            mockPlayer,
            false,
            originalAttrs
        );

        Map<String, Object> retrievedAttrs = config.getCustomAttributes();
        assertSame(originalAttrs, retrievedAttrs);
    }

    @Test
    public void testAllFieldsAreAccessible() {
        Map<String, Object> customAttrs = new HashMap<>();
        customAttrs.put("test", "value");

        NRVideoPlayerConfiguration config = new NRVideoPlayerConfiguration(
            "CompletePlayer",
            mockPlayer,
            true,
            customAttrs
        );

        assertNotNull(config.getPlayerName());
        assertNotNull(config.getPlayer());
        assertNotNull(config.getCustomAttributes());
        assertTrue(config.isAdEnabled());
    }
}
