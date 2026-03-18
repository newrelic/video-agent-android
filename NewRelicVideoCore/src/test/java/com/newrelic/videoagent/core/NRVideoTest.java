package com.newrelic.videoagent.core;

import android.app.Application;
import android.content.Context;

import androidx.media3.exoplayer.ExoPlayer;

import com.newrelic.videoagent.core.tracker.NRTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for NRVideo singleton.
 * Tests initialization, builder pattern, player management, and static API methods.
 */
@RunWith(RobolectricTestRunner.class)
public class NRVideoTest {

    @Mock
    private ExoPlayer mockPlayer;

    @Mock
    private NRVideoConfiguration mockConfig;

    private Context context;
    private NRVideoConfiguration testConfig;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication();

        // Reset singleton instance before each test
        resetNRVideoSingleton();

        // Create a valid test configuration
        testConfig = new NRVideoConfiguration.Builder("test-app-token-1234567890")
            .build();
    }

    @After
    public void tearDown() throws Exception {
        // Clean up singleton after each test
        resetNRVideoSingleton();
    }

    /**
     * Helper method to reset the NRVideo singleton using reflection
     */
    private void resetNRVideoSingleton() throws Exception {
        Field instanceField = NRVideo.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    // ========== Singleton Tests ==========

    @Test
    public void testGetInstanceBeforeInitialization() {
        NRVideo instance = NRVideo.getInstance();
        assertNull("getInstance should return null before initialization", instance);
    }

    @Test
    public void testIsInitializedBeforeInitialization() {
        assertFalse("isInitialized should return false before initialization",
                   NRVideo.isInitialized());
    }

    @Test
    public void testIsInitializedAfterInitialization() {
        NRVideo.newBuilder(context)
            .withConfiguration(testConfig)
            .build();

        assertTrue("isInitialized should return true after initialization",
                  NRVideo.isInitialized());
    }

    @Test
    public void testGetInstanceAfterInitialization() {
        NRVideo.newBuilder(context)
            .withConfiguration(testConfig)
            .build();

        NRVideo instance = NRVideo.getInstance();
        assertNotNull("getInstance should return instance after initialization", instance);
    }

    @Test
    public void testGetInstanceReturnsSameInstance() {
        NRVideo.newBuilder(context)
            .withConfiguration(testConfig)
            .build();

        NRVideo instance1 = NRVideo.getInstance();
        NRVideo instance2 = NRVideo.getInstance();

        assertSame("getInstance should always return the same instance", instance1, instance2);
    }

    // ========== Builder Tests ==========

    @Test
    public void testBuilderCreation() {
        NRVideo.Builder builder = NRVideo.newBuilder(context);
        assertNotNull("Builder should be created", builder);
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutConfiguration() {
        NRVideo.newBuilder(context).build();
    }

    @Test
    public void testBuildWithConfiguration() {
        NRVideo instance = NRVideo.newBuilder(context)
            .withConfiguration(testConfig)
            .build();

        assertNotNull("Build should return instance", instance);
        assertTrue("Should be initialized", NRVideo.isInitialized());
    }

    @Test(expected = RuntimeException.class)
    public void testMultipleInitializationAttempts() {
        NRVideo.newBuilder(context)
            .withConfiguration(testConfig)
            .build();

        // Second initialization attempt should throw
        NRVideo.newBuilder(context)
            .withConfiguration(testConfig)
            .build();
    }

    @Test
    public void testBuilderChaining() {
        NRVideo.Builder builder = NRVideo.newBuilder(context);
        NRVideo.Builder result = builder.withConfiguration(testConfig);

        assertSame("Builder methods should return same instance for chaining", builder, result);
    }

    @Test
    public void testBuildWithApplicationContext() {
        Application app = RuntimeEnvironment.getApplication();

        NRVideo instance = NRVideo.newBuilder(app)
            .withConfiguration(testConfig)
            .build();

        assertNotNull("Should work with Application context", instance);
    }

    @Test(expected = IllegalStateException.class)
    public void testAddPlayerBeforeInitialization() {
        NRVideoPlayerConfiguration playerConfig = new NRVideoPlayerConfiguration(
            "TestPlayer",
            mockPlayer,
            false,
            null
        );

        NRVideo.addPlayer(playerConfig);
    }

}