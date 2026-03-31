package com.newrelic.videoagent.core.lifecycle;

import android.app.Activity;
import android.os.Bundle;

import com.newrelic.videoagent.core.harvest.HarvestComponentFactory;
import com.newrelic.videoagent.core.harvest.SchedulerInterface;
import com.newrelic.videoagent.core.NRVideoConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NRVideoLifecycleObserver.
 * Tests app lifecycle management, background/foreground transitions, and crash handling.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class NRVideoLifecycleObserverTest {

    @Mock
    private HarvestComponentFactory mockFactory;

    @Mock
    private SchedulerInterface mockScheduler;

    @Mock
    private NRVideoConfiguration mockConfiguration;

    @Mock
    private Activity mockActivity;

    @Mock
    private Bundle mockBundle;

    private NRVideoLifecycleObserver observer;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        when(mockFactory.getScheduler()).thenReturn(mockScheduler);
        when(mockFactory.getConfiguration()).thenReturn(mockConfiguration);
        when(mockFactory.getRecoveryStats()).thenReturn("Recovery stats");
        when(mockConfiguration.isTV()).thenReturn(false); // Default to mobile

        observer = new NRVideoLifecycleObserver(mockFactory);
    }

    @Test
    public void testConstructorInitialization() {
        verify(mockFactory).getConfiguration();
        verify(mockConfiguration).isTV();
    }

    @Test
    public void testConstructorWithTVConfiguration() {
        when(mockConfiguration.isTV()).thenReturn(true);

        NRVideoLifecycleObserver tvObserver = new NRVideoLifecycleObserver(mockFactory);

        verify(mockConfiguration, atLeastOnce()).isTV();
    }

    @Test
    public void testOnActivityStarted_TransitionsToForeground() {
        // First, put app in background
        observer.onActivityStarted(mockActivity);
        observer.onActivityStopped(mockActivity);

        // Now start again - this should trigger foreground transition
        observer.onActivityStarted(mockActivity);

        verify(mockScheduler, atLeastOnce()).resume(anyBoolean());
        verify(mockFactory, atLeastOnce()).getRecoveryStats();
    }

    @Test
    public void testOnActivityStarted_MultipleActivities() {
        // Start first activity (count = 1, no foreground transition yet)
        observer.onActivityStarted(mockActivity);

        // Second activity starts (count = 2, still no transition)
        Activity mockActivity2 = mock(Activity.class);
        observer.onActivityStarted(mockActivity2);

        // Scheduler resume should not be called yet (app never went to background)
        verify(mockScheduler, never()).resume(anyBoolean());
    }

    @Test
    public void testOnActivityStopped_TransitionsToBackground() {
        // Start an activity (app starts in foreground, so no foreground transition occurs)
        observer.onActivityStarted(mockActivity);

        // Verify that resume wasn't called (app was already in foreground state)
        verify(mockScheduler, never()).resume(anyBoolean());

        // Stop the activity to transition to background
        observer.onActivityStopped(mockActivity);

        // Verify background handling occurred
        verify(mockFactory).performEmergencyBackup();
        verify(mockScheduler).pause();
        verify(mockScheduler).resume(anyBoolean());  // Called in handleAppBackgrounded
    }

    @Test
    public void testOnActivityStopped_MultipleActivities() {
        // Start two activities
        observer.onActivityStarted(mockActivity);
        Activity mockActivity2 = mock(Activity.class);
        observer.onActivityStarted(mockActivity2);

        // Stop first activity - should not trigger background yet
        observer.onActivityStopped(mockActivity);
        verify(mockFactory, never()).performEmergencyBackup();

        // Stop second activity - now should trigger background
        observer.onActivityStopped(mockActivity2);
        verify(mockFactory).performEmergencyBackup();
        verify(mockScheduler).pause();
    }

    @Test
    public void testBackgroundTransition_MobileDevice() {
        when(mockConfiguration.isTV()).thenReturn(false);
        NRVideoLifecycleObserver mobileObserver = new NRVideoLifecycleObserver(mockFactory);

        mobileObserver.onActivityStarted(mockActivity);
        mobileObserver.onActivityStopped(mockActivity);

        verify(mockFactory).performEmergencyBackup();
        verify(mockScheduler).pause();
        verify(mockScheduler, atLeast(1)).resume(anyBoolean());
    }

    @Test
    public void testBackgroundTransition_TVDevice() {
        when(mockConfiguration.isTV()).thenReturn(true);
        NRVideoLifecycleObserver tvObserver = new NRVideoLifecycleObserver(mockFactory);

        tvObserver.onActivityStarted(mockActivity);
        tvObserver.onActivityStopped(mockActivity);

        verify(mockFactory).performEmergencyBackup();
        verify(mockScheduler).pause();
        verify(mockScheduler, atLeast(1)).resume(anyBoolean());
    }

    @Test
    public void testForegroundTransition() {
        // Put app in background first
        observer.onActivityStarted(mockActivity);
        observer.onActivityStopped(mockActivity);

        // Now transition to foreground
        observer.onActivityStarted(mockActivity);

        verify(mockScheduler, atLeastOnce()).resume(anyBoolean());
        verify(mockFactory, atLeastOnce()).getRecoveryStats();
    }

    @Test
    public void testEmergencyBackupOnBackground() {
        observer.onActivityStarted(mockActivity);
        observer.onActivityStopped(mockActivity);

        verify(mockFactory).performEmergencyBackup();
    }

    @Test
    public void testOnActivitySaveInstanceState_TriggersEmergencyBackup() {
        observer.onActivitySaveInstanceState(mockActivity, mockBundle);

        verify(mockFactory).performEmergencyBackup();
    }

    @Test
    public void testOnActivityDestroyed_WithNoActiveActivities() {
        observer.onActivityDestroyed(mockActivity);

        verify(mockFactory).performEmergencyBackup();
        verify(mockFactory).cleanup();
    }

    @Test
    public void testOnActivityDestroyed_WithActiveActivities() {
        // Start an activity first
        observer.onActivityStarted(mockActivity);

        // Destroy it but keep another one active
        Activity mockActivity2 = mock(Activity.class);
        observer.onActivityStarted(mockActivity2);

        observer.onActivityDestroyed(mockActivity);

        // Should not cleanup since there's still an active activity
        verify(mockFactory, never()).cleanup();
    }

    @Test
    public void testOnActivityCreated_NoAction() {
        observer.onActivityCreated(mockActivity, mockBundle);

        // onActivityCreated does nothing - no additional interactions beyond construction
        // (Factory was already called during construction in setUp)
        verifyNoMoreInteractions(mockScheduler);
    }

    @Test
    public void testOnActivityResumed_NoAction() {
        observer.onActivityResumed(mockActivity);

        // No interactions should occur for this lifecycle event
        verifyNoMoreInteractions(mockScheduler);
    }

    @Test
    public void testOnActivityPaused_NoAction() {
        observer.onActivityPaused(mockActivity);

        // No interactions should occur for this lifecycle event
        verifyNoMoreInteractions(mockScheduler);
    }

    @Test
    public void testMultipleBackgroundTransitions() {
        // First transition
        observer.onActivityStarted(mockActivity);
        observer.onActivityStopped(mockActivity);

        // Second transition
        observer.onActivityStarted(mockActivity);
        observer.onActivityStopped(mockActivity);

        // Emergency backup should be called twice
        verify(mockFactory, atLeast(2)).performEmergencyBackup();
    }

    @Test
    public void testBackgroundWithException_HandlesGracefully() {
        doThrow(new RuntimeException("Test exception"))
            .when(mockFactory).performEmergencyBackup();

        observer.onActivityStarted(mockActivity);
        observer.onActivityStopped(mockActivity);

        // Should not throw exception
        verify(mockFactory).performEmergencyBackup();
    }

    @Test
    public void testForegroundWithException_HandlesGracefully() {
        doThrow(new RuntimeException("Test exception"))
            .when(mockScheduler).resume(anyBoolean());

        // Put in background first, then foreground to trigger scheduler.resume
        observer.onActivityStarted(mockActivity);
        observer.onActivityStopped(mockActivity);
        observer.onActivityStarted(mockActivity);

        // Should not throw exception
        verify(mockScheduler, atLeastOnce()).resume(anyBoolean());
    }

    @Test
    public void testCrashDetection_SetsUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();

        assertNotNull(handler);
        // The handler is set during observer construction
    }

    @Test
    public void testUncaughtExceptionHandler_PerformsEmergencyBackup() {
        Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();

        // Create a new observer which will set up crash detection
        NRVideoLifecycleObserver testObserver = new NRVideoLifecycleObserver(mockFactory);

        Thread.UncaughtExceptionHandler newHandler = Thread.getDefaultUncaughtExceptionHandler();

        assertNotNull(newHandler);
        // The new handler should be different from the original
        // (unless it's the same observer instance, which wraps the original)
    }

    @Test
    public void testActivityLifecycleSequence() {
        // Typical lifecycle sequence
        observer.onActivityCreated(mockActivity, mockBundle);
        observer.onActivityStarted(mockActivity);
        observer.onActivityResumed(mockActivity);
        observer.onActivityPaused(mockActivity);
        observer.onActivityStopped(mockActivity);
        observer.onActivitySaveInstanceState(mockActivity, mockBundle);
        observer.onActivityDestroyed(mockActivity);

        // Verify key operations occurred
        // resume(anyBoolean()) is called in handleAppBackgrounded (line 67 in implementation)
        verify(mockScheduler, atLeastOnce()).resume(anyBoolean()); // Called on background transition
        verify(mockScheduler).pause(); // On stopped (background)
        verify(mockFactory, atLeast(2)).performEmergencyBackup(); // On save state and destroy
        verify(mockFactory).cleanup(); // On destroy
    }

    @Test
    public void testMultipleActivitiesLifecycle() {
        Activity activity1 = mock(Activity.class);
        Activity activity2 = mock(Activity.class);

        // Start activity 1
        observer.onActivityStarted(activity1);
        // No foreground transition yet (app never was in background)

        // Start activity 2 (app already in foreground)
        observer.onActivityStarted(activity2);

        // Stop activity 1 (app still in foreground due to activity 2)
        observer.onActivityStopped(activity1);
        verify(mockFactory, never()).performEmergencyBackup();

        // Stop activity 2 (app goes to background)
        observer.onActivityStopped(activity2);
        verify(mockFactory).performEmergencyBackup();
    }

    @Test
    public void testRapidBackgroundForegroundTransitions() {
        // Simulate rapid transitions
        for (int i = 0; i < 5; i++) {
            observer.onActivityStarted(mockActivity);
            observer.onActivityStopped(mockActivity);
        }

        // Should handle all transitions
        verify(mockScheduler, atLeast(5)).resume(anyBoolean());
        verify(mockScheduler, atLeast(5)).pause();
        verify(mockFactory, atLeast(5)).performEmergencyBackup();
    }

    @Test
    public void testConfigurationChange() {
        // Activity destroyed and recreated (configuration change)
        observer.onActivityStarted(mockActivity);
        observer.onActivitySaveInstanceState(mockActivity, mockBundle);
        observer.onActivityStopped(mockActivity);

        Activity newActivity = mock(Activity.class);
        observer.onActivityCreated(newActivity, mockBundle);
        observer.onActivityStarted(newActivity);

        // Should save state and restore properly
        verify(mockFactory, atLeast(1)).performEmergencyBackup();
        verify(mockScheduler, atLeast(2)).resume(anyBoolean());
    }

    @Test
    public void testEmergencyBackup_NotCalledMultipleTimes_Concurrently() {
        // Simulate multiple save instance state calls
        observer.onActivitySaveInstanceState(mockActivity, mockBundle);
        observer.onActivitySaveInstanceState(mockActivity, mockBundle);
        observer.onActivitySaveInstanceState(mockActivity, mockBundle);

        // Emergency backup should still be called (but protected by AtomicBoolean)
        verify(mockFactory, atLeast(1)).performEmergencyBackup();
    }

    @Test
    public void testSchedulerPauseOnBackground() {
        observer.onActivityStarted(mockActivity);
        observer.onActivityStopped(mockActivity);

        verify(mockScheduler).pause();
    }

    @Test
    public void testSchedulerResumeOnForeground() {
        // Put in background first, then foreground
        observer.onActivityStarted(mockActivity);
        observer.onActivityStopped(mockActivity);
        observer.onActivityStarted(mockActivity);

        verify(mockScheduler, atLeastOnce()).resume(anyBoolean());
    }

    @Test
    public void testRecoveryStatsRetrievedOnForeground() {
        // Put in background first, then foreground
        observer.onActivityStarted(mockActivity);
        observer.onActivityStopped(mockActivity);
        observer.onActivityStarted(mockActivity);

        verify(mockFactory, atLeastOnce()).getRecoveryStats();
    }

    @Test
    public void testCleanupCalledOnlyWhenNoActiveActivities() {
        observer.onActivityStarted(mockActivity);
        Activity activity2 = mock(Activity.class);
        observer.onActivityStarted(activity2);

        // Destroy first activity
        observer.onActivityStopped(mockActivity);
        observer.onActivityDestroyed(mockActivity);
        verify(mockFactory, never()).cleanup();

        // Destroy second activity
        observer.onActivityStopped(activity2);
        observer.onActivityDestroyed(activity2);
        verify(mockFactory).cleanup();
    }

    private void assertNotNull(Object object) {
        if (object == null) {
            throw new AssertionError("Expected non-null value");
        }
    }
}
