# MediaTailor Tracker - Steps 2 & 3 Testing Guide

## Complete Integration Test

This guide shows how to test the complete MediaTailor tracking flow from session initialization through ad event emission.

---

## Complete Test Example

```java
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.newrelic.videoagent.core.mediatailor.MediaTailorSessionManager;
import com.newrelic.videoagent.core.mediatailor.MediaTailorAdPlaybackTracker;
import com.newrelic.videoagent.core.mediatailor.MediaTailorEventEmitter;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorSession;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorAdBreak;
import android.util.Log;

public class MediaTailorFullTest {
    private static final String TAG = "MediaTailorTest";

    // MediaTailor session URL
    private static final String SESSION_URL =
        "https://2d271f758c9940e882092aed7e4451c4.mediatailor.ap-southeast-2.amazonaws.com" +
        "/v1/session/54ad5836d26bc9938d9793caa9fe55e611da7d60/my-playback-config/";

    private MediaTailorSessionManager sessionManager;
    private MediaTailorAdPlaybackTracker adTracker;
    private MediaTailorEventEmitter eventEmitter;
    private ExoPlayer player;

    /**
     * Complete integration test - all 3 steps.
     */
    public void testCompleteFlow() {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "Starting Complete MediaTailor Integration Test");
        Log.d(TAG, "═══════════════════════════════════════════════════");

        // Initialize ExoPlayer
        initializePlayer();

        // Step 1: Initialize MediaTailor session
        initializeMediaTailor();
    }

    /**
     * Initialize ExoPlayer instance.
     */
    private void initializePlayer() {
        Log.d(TAG, "Initializing ExoPlayer...");
        player = new ExoPlayer.Builder(context).build();
        Log.d(TAG, "✅ ExoPlayer initialized");
    }

    /**
     * Step 1: Initialize MediaTailor session and fetch tracking data.
     */
    private void initializeMediaTailor() {
        sessionManager = new MediaTailorSessionManager();

        Log.d(TAG, "Step 1: Initializing MediaTailor session...");

        sessionManager.initializeSessionAsync(SESSION_URL, session -> {
            if (session != null) {
                Log.d(TAG, "✅ Step 1: Session initialized");
                Log.d(TAG, "  Manifest URL: " + session.getManifestUrl());
                Log.d(TAG, "  Tracking URL: " + session.getTrackingUrl());

                // Load manifest in player
                loadManifest(session);

                // Fetch tracking data
                fetchTrackingData(session.getTrackingUrl());
            } else {
                Log.e(TAG, "❌ Session initialization failed!");
            }
        });
    }

    /**
     * Load MediaTailor manifest in ExoPlayer.
     */
    private void loadManifest(MediaTailorSession session) {
        Log.d(TAG, "Loading manifest in ExoPlayer...");
        MediaItem mediaItem = MediaItem.fromUri(session.getManifestUrl());
        player.setMediaItem(mediaItem);
        player.prepare();
        Log.d(TAG, "✅ Manifest loaded, player prepared");
    }

    /**
     * Fetch tracking data and initialize ad tracking.
     */
    private void fetchTrackingData(String trackingUrl) {
        Log.d(TAG, "Fetching tracking data...");

        sessionManager.fetchTrackingDataAsync(trackingUrl, adBreaks -> {
            Log.d(TAG, "✅ Tracking data received: " + adBreaks.size() + " ad breaks");

            if (!adBreaks.isEmpty()) {
                // Log ad schedule
                logAdSchedule(adBreaks);

                // Step 2 & 3: Initialize tracker and emitter
                initializeTrackerAndEmitter(adBreaks);
            } else {
                Log.w(TAG, "⚠️ No ad breaks in schedule");
            }
        });
    }

    /**
     * Log the complete ad schedule.
     */
    private void logAdSchedule(List<MediaTailorAdBreak> adBreaks) {
        Log.d(TAG, "─────────────────────────────────────────────────");
        Log.d(TAG, "AD SCHEDULE:");
        for (int i = 0; i < adBreaks.size(); i++) {
            MediaTailorAdBreak adBreak = adBreaks.get(i);
            Log.d(TAG, String.format("  Break #%d: %s", i + 1, adBreak.getId()));
            Log.d(TAG, String.format("    Time: %.1f - %.1fs (duration: %.1fs)",
                    adBreak.getScheduleTime(),
                    adBreak.getEndTime(),
                    adBreak.getDuration()));
            Log.d(TAG, String.format("    Ads: %d", adBreak.getAds().size()));

            for (int j = 0; j < adBreak.getAds().size(); j++) {
                var ad = adBreak.getAds().get(j);
                Log.d(TAG, String.format("      Ad #%d: %s (%.1f - %.1fs)",
                        j + 1,
                        ad.getId(),
                        ad.getScheduleTime(),
                        ad.getEndTime()));
            }
        }
        Log.d(TAG, "─────────────────────────────────────────────────");
    }

    /**
     * Step 2 & 3: Initialize tracker and event emitter.
     */
    private void initializeTrackerAndEmitter(List<MediaTailorAdBreak> adBreaks) {
        Log.d(TAG, "Step 2: Initializing MediaTailorAdPlaybackTracker...");

        // Create tracker
        adTracker = new MediaTailorAdPlaybackTracker(player, adBreaks);

        Log.d(TAG, "Step 3: Initializing MediaTailorEventEmitter...");

        // Create event emitter
        eventEmitter = new MediaTailorEventEmitter(adTracker);

        // Start tracking and emitting
        adTracker.start();
        eventEmitter.start();

        Log.d(TAG, "✅ Tracker and emitter started");
        Log.d(TAG, "  Tracking interval: 100ms");
        Log.d(TAG, "  Event check interval: 50ms");
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "🎬 Ready to play! Start video to see ad events.");
        Log.d(TAG, "═══════════════════════════════════════════════════");

        // Start playback
        startPlayback();
    }

    /**
     * Start video playback.
     */
    private void startPlayback() {
        Log.d(TAG, "Starting playback...");
        player.play();
        Log.d(TAG, "✅ Playback started - watch for ad events!");
    }

    /**
     * Clean up resources.
     */
    public void cleanup() {
        Log.d(TAG, "Cleaning up...");

        if (eventEmitter != null) {
            eventEmitter.stop();
        }

        if (adTracker != null) {
            adTracker.stop();
        }

        if (sessionManager != null) {
            sessionManager.shutdown();
        }

        if (player != null) {
            player.release();
        }

        Log.d(TAG, "✅ Cleanup complete");
    }
}
```

---

## Activity Integration Example

```java
public class MediaTailorActivity extends AppCompatActivity {
    private static final String TAG = "MediaTailorActivity";

    private ExoPlayer player;
    private PlayerView playerView;

    private MediaTailorSessionManager sessionManager;
    private MediaTailorAdPlaybackTracker adTracker;
    private MediaTailorEventEmitter eventEmitter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mediatailor);

        playerView = findViewById(R.id.player_view);

        // Initialize player
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        // Initialize MediaTailor
        initializeMediaTailorTracking();
    }

    private void initializeMediaTailorTracking() {
        sessionManager = new MediaTailorSessionManager();

        String sessionUrl = "https://2d271f758c9940e882092aed7e4451c4.mediatailor.ap-southeast-2.amazonaws.com" +
                           "/v1/session/54ad5836d26bc9938d9793caa9fe55e611da7d60/my-playback-config/";

        // Step 1: Session initialization
        sessionManager.initializeSessionAsync(sessionUrl, session -> {
            if (session == null) {
                Log.e(TAG, "Failed to initialize MediaTailor session");
                return;
            }

            Log.d(TAG, "MediaTailor session initialized");

            // Load manifest
            MediaItem mediaItem = MediaItem.fromUri(session.getManifestUrl());
            player.setMediaItem(mediaItem);
            player.prepare();

            // Fetch tracking data
            sessionManager.fetchTrackingDataAsync(session.getTrackingUrl(), adBreaks -> {
                if (adBreaks.isEmpty()) {
                    Log.w(TAG, "No ad breaks in tracking data");
                    return;
                }

                Log.d(TAG, "Tracking data loaded: " + adBreaks.size() + " ad breaks");

                // Step 2: Initialize tracker
                adTracker = new MediaTailorAdPlaybackTracker(player, adBreaks);
                adTracker.start();

                // Step 3: Initialize event emitter
                eventEmitter = new MediaTailorEventEmitter(adTracker);
                eventEmitter.start();

                Log.d(TAG, "MediaTailor tracking active!");

                // Start playback
                player.play();
            });
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cleanup
        if (eventEmitter != null) {
            eventEmitter.stop();
        }
        if (adTracker != null) {
            adTracker.stop();
        }
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (player != null) {
            player.release();
        }
    }
}
```

---

## Expected Logcat Output

Filter with: `adb logcat -s MediaTailor.Session MediaTailor.Mapper MediaTailor.Tracker MediaTailor.Events`

### Session Initialization (Step 1)
```
D/MediaTailor.Session: MediaTailorSessionManager initialized with background executor
D/MediaTailor.Session: Scheduling session initialization on background thread
D/MediaTailor.Session: Response code: 200
D/MediaTailor.Session: Session initialized successfully
D/MediaTailor.Session: Scheduling tracking data fetch with 500ms delay
D/MediaTailor.Mapper: Mapping 3 avails to ad breaks
D/MediaTailor.Mapper: Successfully mapped 3 ad breaks
```

### Tracker Initialization (Step 2)
```
D/MediaTailor.Tracker: MediaTailorAdPlaybackTracker initialized with 3 ad breaks
D/MediaTailor.Tracker: Starting ad playback tracking (interval: 100ms)
D/MediaTailor.Tracker: Next ad break: avail-1 at 30.0s (player: 5.2s)
```

### Event Emission (Step 3)
```
D/MediaTailor.Events: MediaTailorEventEmitter initialized
D/MediaTailor.Events: Starting event emission (interval: 50ms)

[Player reaches 30.0s - first ad break starts]

I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
I/MediaTailor.Events: EVENT: AD_BREAK_STARTED
I/MediaTailor.Events: INFO:  AdBreak[id=avail-1, scheduleTime=30.0s, duration=60.0s, adCount=2]
I/MediaTailor.Events: TIME:  30.1s
I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
I/MediaTailor.Events: EVENT: AD_STARTED
I/MediaTailor.Events: INFO:  Ad[id=ad-1, index=0, scheduleTime=30.0s, duration=30.0s]
I/MediaTailor.Events: TIME:  30.1s
I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

D/MediaTailor.Tracker: Now playing: Ad #1 (ad-1) in break avail-1 (player: 30.1s, ad: 30.0-60.0s)

[Player reaches 60.0s - first ad ends, second ad starts]

I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
I/MediaTailor.Events: EVENT: AD_FINISHED
I/MediaTailor.Events: INFO:  Ad[id=ad-1, duration=30.0s]
I/MediaTailor.Events: TIME:  60.0s
I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
I/MediaTailor.Events: EVENT: AD_STARTED
I/MediaTailor.Events: INFO:  Ad[id=ad-2, index=1, scheduleTime=60.0s, duration=30.0s]
I/MediaTailor.Events: TIME:  60.0s
I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[Player reaches 90.0s - second ad ends, ad break finishes]

I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
I/MediaTailor.Events: EVENT: AD_FINISHED
I/MediaTailor.Events: INFO:  Ad[id=ad-2, duration=30.0s]
I/MediaTailor.Events: TIME:  90.0s
I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
I/MediaTailor.Events: EVENT: AD_BREAK_FINISHED
I/MediaTailor.Events: INFO:  AdBreak[id=avail-1, duration=60.0s, totalAds=2]
I/MediaTailor.Events: TIME:  90.0s
I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

D/MediaTailor.Tracker: Exited ad break: avail-1 (player: 90.2s)
D/MediaTailor.Tracker: Next ad break: avail-2 at 180.0s (player: 90.2s)
```

---

## Testing Checklist

### Step 1: Session Initialization ✓
- [ ] Session URL accepts POST request
- [ ] manifestUrl and trackingUrl received
- [ ] Tracking data fetched after 500ms delay
- [ ] Ad breaks parsed and mapped correctly

### Step 2: Ad Break Tracking ✓
- [ ] Tracker initializes with ad schedule
- [ ] Player position monitored every 100ms
- [ ] nextAdBreak updates correctly
- [ ] playingAdBreak updates when entering ad
- [ ] Correct ad index tracking within break
- [ ] State resets when exiting ad break

### Step 3: Event Emission ✓
- [ ] Events emitted every 50ms check
- [ ] AD_BREAK_STARTED fires on first ad
- [ ] AD_STARTED fires with correct index
- [ ] AD_FINISHED fires at ad end
- [ ] AD_BREAK_FINISHED fires after last ad
- [ ] Transitions between ads work correctly
- [ ] Transitions between ad breaks work correctly

---

## Edge Cases to Test

1. **Seeking during ad:**
   - Seek past ad → AdFinished + AdBreakFinished
   - Seek into middle of ad → AdBreakStarted + AdStarted

2. **Pausing during ad:**
   - State remains unchanged
   - Events resume correctly on play

3. **Multiple ad breaks:**
   - Correct transition between breaks
   - Index resets properly

4. **Stream with no ads:**
   - No crashes
   - No events emitted

---

## Troubleshooting

### Events not firing:
- Check tracker and emitter are started: `adTracker.start()`, `eventEmitter.start()`
- Verify player is actually playing: `player.isPlaying()`
- Check ad schedule has valid times: Review tracking data logs

### Events firing at wrong times:
- Verify ad schedule timing matches actual stream
- Check player position: `player.getCurrentPosition()`
- Compare with ad scheduleTime

### Duplicate events:
- Ensure only one tracker/emitter instance
- Check start() not called multiple times

---

## Success Criteria

✅ **Step 1:** Session initialized, tracking data fetched and mapped
✅ **Step 2:** Player position tracked, ad breaks detected accurately
✅ **Step 3:** Ad lifecycle events emitted in correct sequence

**POC Complete!** All components working together to track MediaTailor SSAI ads.

---

## Next Steps (Future)

- Integrate events with NRVideo event system
- Send events to New Relic platform
- Fire tracking beacons at appropriate times
- Add quartile events (25%, 50%, 75%)
- Support live streams with dynamic ad schedule updates
