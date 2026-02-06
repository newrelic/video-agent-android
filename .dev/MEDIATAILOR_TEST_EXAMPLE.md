# MediaTailor Tracker - Test Example

## Step 1 Testing Guide

### Overview
All network operations now run on background threads. The API uses callbacks to return results on the main thread.

---

## Test Code Example

```java
import com.newrelic.videoagent.core.mediatailor.MediaTailorSessionManager;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorSession;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorAdBreak;
import android.util.Log;

public class MediaTailorTest {
    private static final String TAG = "MediaTailorTest";

    // MediaTailor session URL
    private static final String SESSION_URL =
        "https://2d271f758c9940e882092aed7e4451c4.mediatailor.ap-southeast-2.amazonaws.com" +
        "/v1/session/54ad5836d26bc9938d9793caa9fe55e611da7d60/my-playback-config/";

    private MediaTailorSessionManager sessionManager;

    public void testMediaTailorIntegration() {
        // Initialize session manager
        sessionManager = new MediaTailorSessionManager();

        Log.d(TAG, "Starting MediaTailor session initialization...");

        // Step 1: Initialize session (runs on background thread)
        sessionManager.initializeSessionAsync(SESSION_URL, session -> {
            // This callback runs on main thread
            if (session != null) {
                Log.d(TAG, "✅ Session initialized successfully!");
                Log.d(TAG, "Manifest URL: " + session.getManifestUrl());
                Log.d(TAG, "Tracking URL: " + session.getTrackingUrl());

                // Step 2: Fetch tracking data (runs on background thread with 500ms delay)
                fetchTrackingData(session.getTrackingUrl());
            } else {
                Log.e(TAG, "❌ Session initialization failed!");
            }
        });
    }

    private void fetchTrackingData(String trackingUrl) {
        Log.d(TAG, "Starting tracking data fetch...");

        sessionManager.fetchTrackingDataAsync(trackingUrl, adBreaks -> {
            // This callback runs on main thread
            Log.d(TAG, "✅ Received " + adBreaks.size() + " ad breaks");

            // Log each ad break
            for (int i = 0; i < adBreaks.size(); i++) {
                MediaTailorAdBreak adBreak = adBreaks.get(i);
                Log.d(TAG, "─────────────────────────────────────");
                Log.d(TAG, "Ad Break #" + (i + 1));
                Log.d(TAG, "  ID: " + adBreak.getId());
                Log.d(TAG, "  Schedule Time: " + adBreak.getScheduleTime() + "s");
                Log.d(TAG, "  Duration: " + adBreak.getDuration() + "s");
                Log.d(TAG, "  End Time: " + adBreak.getEndTime() + "s");
                Log.d(TAG, "  Number of Ads: " + adBreak.getAds().size());

                // Log each ad in the break
                for (int j = 0; j < adBreak.getAds().size(); j++) {
                    var ad = adBreak.getAds().get(j);
                    Log.d(TAG, "    Ad #" + (j + 1) + ": " + ad.getId());
                    Log.d(TAG, "      Schedule Time: " + ad.getScheduleTime() + "s");
                    Log.d(TAG, "      Duration: " + ad.getDuration() + "s");
                    Log.d(TAG, "      Tracking Events: " + ad.getTrackingEvents().size());
                }
            }
            Log.d(TAG, "─────────────────────────────────────");
            Log.d(TAG, "✅ Step 1 Complete - Session & Tracking Data Working!");
        });
    }

    public void cleanup() {
        if (sessionManager != null) {
            sessionManager.shutdown();
            Log.d(TAG, "Session manager cleaned up");
        }
    }
}
```

---

## Integration in Activity/Fragment

```java
public class VideoPlayerActivity extends AppCompatActivity {
    private MediaTailorSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        // Initialize MediaTailor tracker
        initializeMediaTailor();
    }

    private void initializeMediaTailor() {
        sessionManager = new MediaTailorSessionManager();

        String sessionUrl = "https://2d271f758c9940e882092aed7e4451c4.mediatailor.ap-southeast-2.amazonaws.com" +
                           "/v1/session/54ad5836d26bc9938d9793caa9fe55e611da7d60/my-playback-config/";

        // Initialize session on background thread
        sessionManager.initializeSessionAsync(sessionUrl, session -> {
            if (session != null) {
                // Update UI on main thread
                Log.d("VideoPlayer", "MediaTailor session ready");

                // Load manifest in player
                loadVideoManifest(session.getManifestUrl());

                // Fetch ad schedule
                sessionManager.fetchTrackingDataAsync(session.getTrackingUrl(), adBreaks -> {
                    Log.d("VideoPlayer", "Ad schedule loaded: " + adBreaks.size() + " breaks");
                    // Store ad breaks for tracking (Step 2)
                    // this.adBreaks = adBreaks;
                });
            }
        });
    }

    private void loadVideoManifest(String manifestUrl) {
        // Load HLS manifest in ExoPlayer
        // player.setMediaItem(MediaItem.fromUri(manifestUrl));
        // player.prepare();
        // player.play();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
    }
}
```

---

## Expected Logcat Output

Filter logs with: `adb logcat -s MediaTailor.Session MediaTailor.Mapper MediaTailorTest`

```
D/MediaTailor.Session: MediaTailorSessionManager initialized with background executor
D/MediaTailorTest: Starting MediaTailor session initialization...
D/MediaTailor.Session: Scheduling session initialization on background thread: https://...
D/MediaTailor.Session: Initializing MediaTailor session: https://...
D/MediaTailor.Session: Request body: {"adsParams":{},"reportingMode":"client"}
D/MediaTailor.Session: Response code: 200
D/MediaTailor.Session: Response body: {"manifestUrl":"https://...","trackingUrl":"https://..."}
D/MediaTailor.Session: Session initialized successfully: MediaTailorSession{manifestUrl='...', trackingUrl='...'}
D/MediaTailorTest: ✅ Session initialized successfully!
D/MediaTailorTest: Manifest URL: https://...master.m3u8
D/MediaTailorTest: Tracking URL: https://...tracking...
D/MediaTailorTest: Starting tracking data fetch...
D/MediaTailor.Session: Scheduling tracking data fetch with 500ms delay on background thread
D/MediaTailor.Session: Fetching tracking data from: https://...
D/MediaTailor.Session: Request URL with timestamp: https://...?t=1700000000000
D/MediaTailor.Session: Response code: 200
D/MediaTailor.Session: Response body length: 2456 characters
D/MediaTailor.Mapper: Mapping 3 avails to ad breaks
D/MediaTailor.Mapper: Mapped ad break: MediaTailorAdBreak{id='avail-1', scheduleTime=30.0, ...}
D/MediaTailor.Mapper: Successfully mapped 3 ad breaks
D/MediaTailor.Session: Successfully fetched and mapped 3 ad breaks
D/MediaTailorTest: ✅ Received 3 ad breaks
D/MediaTailorTest: ─────────────────────────────────────
D/MediaTailorTest: Ad Break #1
D/MediaTailorTest:   ID: avail-1
D/MediaTailorTest:   Schedule Time: 30.0s
D/MediaTailorTest:   Duration: 60.0s
D/MediaTailorTest:   End Time: 90.0s
D/MediaTailorTest:   Number of Ads: 2
D/MediaTailorTest:     Ad #1: ad-1
D/MediaTailorTest:       Schedule Time: 30.0s
D/MediaTailorTest:       Duration: 30.0s
D/MediaTailorTest:       Tracking Events: 5
D/MediaTailorTest: ─────────────────────────────────────
D/MediaTailorTest: ✅ Step 1 Complete - Session & Tracking Data Working!
```

---

## Key Changes (Network on Background Thread Fix)

### Before (Crashed):
```java
// ❌ Network call on main thread
MediaTailorSession session = sessionManager.initializeSession(url);
```

### After (Fixed):
```java
// ✅ Network call on background thread with callback
sessionManager.initializeSessionAsync(url, session -> {
    // Callback runs on main thread - safe to update UI
    if (session != null) {
        updateUI(session);
    }
});
```

---

## Testing Checklist

- [ ] Session initialization succeeds (check manifestUrl and trackingUrl in logs)
- [ ] Tracking data fetch happens after 500ms delay
- [ ] Ad breaks are parsed correctly (check count and IDs)
- [ ] Ads within each break are parsed (check schedule times)
- [ ] Tracking events are included for each ad
- [ ] No NetworkOnMainThreadException crashes
- [ ] Callbacks execute on main thread (safe for UI updates)
- [ ] Cleanup (shutdown) works without errors

---

## Troubleshooting

### If session initialization fails:
- Check internet connectivity
- Verify MediaTailor URL is correct and accessible
- Check Logcat for HTTP response code and error messages
- Ensure INTERNET permission in AndroidManifest.xml:
  ```xml
  <uses-permission android:name="android.permission.INTERNET" />
  ```

### If tracking data is empty:
- Check that session was initialized successfully first
- Verify trackingUrl is valid
- Check Logcat for JSON parsing errors
- Ensure 500ms delay is completed before request

### If app still crashes:
- Verify you're using `initializeSessionAsync` (not `initializeSessionSync`)
- Ensure all network calls go through the async methods
- Check thread names in stack trace

---

## Next Steps

Once Step 1 is validated:
- ✅ Session initialization working
- ✅ Tracking data fetch working
- ✅ Ad schedule parsed and mapped

**Ready for Step 2:** Ad break tracking with player time monitoring
