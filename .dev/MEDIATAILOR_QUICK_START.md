# MediaTailor Tracker - Quick Start Guide

## 🚀 5-Minute Setup

### 1. Add to Your Activity

```java
import com.newrelic.videoagent.core.mediatailor.*;
import com.newrelic.videoagent.core.mediatailor.model.*;
import com.google.android.exoplayer2.*;

public class VideoActivity extends AppCompatActivity {
    private ExoPlayer player;
    private MediaTailorSessionManager sessionManager;
    private MediaTailorAdPlaybackTracker tracker;
    private MediaTailorEventEmitter emitter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize ExoPlayer
        player = new ExoPlayer.Builder(this).build();

        // Initialize MediaTailor
        setupMediaTailor();
    }

    private void setupMediaTailor() {
        sessionManager = new MediaTailorSessionManager();

        String url = "https://2d271f758c9940e882092aed7e4451c4.mediatailor.ap-southeast-2.amazonaws.com" +
                    "/v1/session/54ad5836d26bc9938d9793caa9fe55e611da7d60/my-playback-config/";

        sessionManager.initializeSessionAsync(url, session -> {
            if (session == null) return;

            player.setMediaItem(MediaItem.fromUri(session.getManifestUrl()));
            player.prepare();

            sessionManager.fetchTrackingDataAsync(session.getTrackingUrl(), adBreaks -> {
                if (adBreaks.isEmpty()) return;

                tracker = new MediaTailorAdPlaybackTracker(player, adBreaks);
                tracker.start();

                emitter = new MediaTailorEventEmitter(tracker);
                emitter.start();

                player.play();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (emitter != null) emitter.stop();
        if (tracker != null) tracker.stop();
        if (sessionManager != null) sessionManager.shutdown();
        if (player != null) player.release();
    }
}
```

### 2. Check Logcat

```bash
adb logcat -s MediaTailor.Events:I
```

### 3. Expected Output

```
I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
I/MediaTailor.Events: EVENT: AD_BREAK_STARTED
I/MediaTailor.Events: INFO:  AdBreak[id=avail-1, scheduleTime=30.0s, ...]
I/MediaTailor.Events: TIME:  30.1s
I/MediaTailor.Events: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 📦 Files Created (12 total)

**Location:** `NewRelicVideoCore/src/main/java/com/newrelic/videoagent/core/mediatailor/`

**Core (4):**
- MediaTailorSessionManager.java
- MediaTailorAdPlaybackTracker.java
- MediaTailorEventEmitter.java
- DefaultAdsMapper.java

**Models (8):**
- MediaTailorSession.java
- MediaTailorTrackingData.java
- Avail.java, Ad.java, TrackingEvent.java
- MediaTailorAdBreak.java
- MediaTailorLinearAd.java
- MediaTailorTrackingEvent.java
- PlayingAdBreak.java
- MediaTailorEvent.java

---

## 🔧 Key Components

| Component | Purpose | Interval |
|-----------|---------|----------|
| SessionManager | HTTP client for MediaTailor API | One-time |
| AdPlaybackTracker | Monitor player position & detect ads | 100ms |
| EventEmitter | Emit ad lifecycle events | 50ms |

---

## 📊 Events Emitted

1. **AD_BREAK_STARTED** - When ad break begins
2. **AD_STARTED** - When individual ad begins (with index)
3. **AD_FINISHED** - When individual ad ends
4. **AD_BREAK_FINISHED** - When ad break ends

---

## 🐛 Troubleshooting

**App crashes on network call?**
- ✅ Fixed! All network ops run on background threads

**No events?**
- Check tracker/emitter started: `.start()`
- Verify player is playing: `player.isPlaying()`

**Events at wrong time?**
- Check ad schedule in logs
- Verify player position matches ad times

**Session fails?**
- Check INTERNET permission in AndroidManifest.xml
- Verify MediaTailor URL is correct
- Check Logcat for HTTP response code

---

## 📚 Documentation

- **MEDIATAILOR_TASKS.md** - Task breakdown
- **MEDIATAILOR_SPEC.md** - Technical spec
- **MEDIATAILOR_TEST_EXAMPLE.md** - Step 1 test
- **MEDIATAILOR_STEP2_STEP3_TEST.md** - Steps 2 & 3 test
- **MEDIATAILOR_IMPLEMENTATION_COMPLETE.md** - Full summary
- **MEDIATAILOR_QUICK_START.md** - This guide

---

## ✅ That's It!

You're now tracking MediaTailor SSAI ads with the New Relic Video Agent! 🎉

Check the logs to see ad events as they happen during playback.
