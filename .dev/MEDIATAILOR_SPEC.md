# MediaTailor Tracker - Technical Specification

## Document Information

**Version:** 1.0 (POC)
**Date:** 2025-11-25
**Author:** New Relic Video Agent Team
**Status:** Draft

---

## 1. Overview

### 1.1 Purpose
This specification defines the technical implementation of AWS MediaTailor ad tracking integration for the New Relic Video Agent Android SDK. The goal is to track Server-Side Ad Insertion (SSAI) ads served through MediaTailor and emit ad lifecycle events.

### 1.2 Scope
This POC implementation covers:
- MediaTailor session initialization
- Ad schedule fetching and parsing
- Real-time ad break detection based on player position
- Ad lifecycle event emission with logging
- Integration with ExoPlayer

**Out of Scope (for POC):**
- Full integration with NRVideo event system
- Sending events to New Relic platform
- Multiple player support (beyond ExoPlayer)
- Production-grade error handling and retry logic
- Performance optimizations

### 1.3 Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        Sample App                            │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│               MediaTailor Tracker Flow                       │
│                                                               │
│  ┌──────────────────┐  1. Initialize Session                │
│  │ SessionManager   │─────────────────────────┐              │
│  └──────────────────┘                         │              │
│           │                                    ▼              │
│           │ 2. Fetch Tracking Data   ┌──────────────┐        │
│           └──────────────────────────▶│ HTTP Client  │        │
│                                       └──────────────┘        │
│           │                                    │              │
│           ▼                                    │              │
│  ┌──────────────────┐                         │              │
│  │   AdsMapper      │◀────────────────────────┘              │
│  └──────────────────┘                                        │
│           │                                                   │
│           │ 3. Mapped Ad Schedule                            │
│           ▼                                                   │
│  ┌──────────────────────┐                                    │
│  │ AdPlaybackTracker    │◀───── ExoPlayer Position           │
│  │  - nextAdBreak       │                                    │
│  │  - playingAdBreak    │                                    │
│  └──────────────────────┘                                    │
│           │                                                   │
│           │ 4. State Changes                                 │
│           ▼                                                   │
│  ┌──────────────────┐                                        │
│  │  EventEmitter    │                                        │
│  │  - AdBreakStarted│────────────▶ Android Logcat            │
│  │  - AdStarted     │                                        │
│  │  - AdFinished    │                                        │
│  │  - AdBreakFinished│                                       │
│  └──────────────────┘                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. MediaTailor API Integration

### 2.1 Session Initialization API

**Endpoint:** `POST https://{session-prefix}.mediatailor.{region}.amazonaws.com/v1/session/{origin-id}/{playback-config}/`

**Example:**
```
POST https://2d271f758c9940e882092aed7e4451c4.mediatailor.ap-southeast-2.amazonaws.com/v1/session/54ad5836d26bc9938d9793caa9fe55e611da7d60/my-playback-config/
```

**Request Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "adsParams": {},
  "reportingMode": "client"
}
```

**Response (200 OK):**
```json
{
  "manifestUrl": "https://..../master.m3u8",
  "trackingUrl": "https://..../v1/tracking/..."
}
```

**Error Handling:**
- 400 Bad Request → Invalid parameters
- 403 Forbidden → Invalid credentials
- 500 Server Error → Retry with exponential backoff

### 2.2 Tracking Data API

**Endpoint:** `GET {trackingUrl}?t={timestamp}`

**Example:**
```
GET https://...tracking-url...?t=1700000000000
```

**Response (200 OK):**
```json
{
  "avails": [
    {
      "availId": "avail-1",
      "startTimeInSeconds": 30.0,
      "durationInSeconds": 60.0,
      "duration": "PT1M",
      "adMarkerDuration": 60.0,
      "ads": [
        {
          "adId": "ad-1",
          "startTimeInSeconds": 30.0,
          "durationInSeconds": 30.0,
          "duration": "PT30S",
          "trackingEvents": [
            {
              "eventId": "event-1",
              "startTimeInSeconds": 30.0,
              "durationInSeconds": 0.0,
              "eventType": "impression",
              "beaconUrls": ["https://..."]
            }
          ]
        }
      ]
    }
  ]
}
```

**Polling Timing:**
- Initial fetch: 500ms after session initialization
- Refresh (future): Every 30s or on manifest update

---

## 3. Data Models

### 3.1 Data Transfer Objects (DTOs)

#### MediaTailorSession
Maps session initialization response.

```java
public class MediaTailorSession {
    private final String manifestUrl;
    private final String trackingUrl;

    // Constructor, getters, toString()
}
```

#### MediaTailorTrackingData
Root tracking response container.

```java
public class MediaTailorTrackingData {
    private final List<Avail> avails;

    // Constructor, getters
}
```

#### Avail
Represents an ad break (avail) from MediaTailor API.

```java
public class Avail {
    private final String availId;
    private final double startTimeInSeconds;
    private final double durationInSeconds;
    private final String duration;           // ISO 8601 format (PT1M30S)
    private final double adMarkerDuration;
    private final List<Ad> ads;

    // Constructor, getters
}
```

#### Ad
Represents an individual ad within an avail.

```java
public class Ad {
    private final String adId;
    private final double startTimeInSeconds;
    private final double durationInSeconds;
    private final String duration;           // ISO 8601 format (PT30S)
    private final List<TrackingEvent> trackingEvents;

    // Constructor, getters
}
```

#### TrackingEvent
Represents a tracking beacon event.

```java
public class TrackingEvent {
    private final String eventId;
    private final double startTimeInSeconds;
    private final double durationInSeconds;
    private final String eventType;          // impression, start, firstQuartile, etc.
    private final List<String> beaconUrls;

    // Constructor, getters
}
```

### 3.2 Domain Models

#### MediaTailorAdBreak
Domain representation of an ad break with computed properties.

```java
public class MediaTailorAdBreak {
    private final String id;
    private final List<MediaTailorLinearAd> ads;
    private final double scheduleTime;       // Start time in seconds
    private final double duration;
    private final String formattedDuration;
    private final double adMarkerDuration;

    // Computed properties
    public double getEndTime() {
        return scheduleTime + duration;
    }

    public boolean isInRange(double playerTime) {
        return playerTime >= scheduleTime && playerTime < getEndTime();
    }

    // Constructor, getters, toString()
}
```

#### MediaTailorLinearAd
Domain representation of a linear ad.

```java
public class MediaTailorLinearAd {
    private final String id;
    private final double scheduleTime;
    private final double duration;
    private final String formattedDuration;
    private final List<MediaTailorTrackingEvent> trackingEvents;

    // Computed properties
    public double getEndTime() {
        return scheduleTime + duration;
    }

    public boolean isInRange(double playerTime) {
        return playerTime >= scheduleTime && playerTime < getEndTime();
    }

    // Constructor, getters, toString()
}
```

#### MediaTailorTrackingEvent
Domain representation of tracking event.

```java
public class MediaTailorTrackingEvent {
    private final String id;
    private final double scheduleTime;
    private final double duration;
    private final String eventType;
    private final List<String> beaconUrls;

    // Constructor, getters, toString()
}
```

#### PlayingAdBreak
State object representing currently playing ad break.

```java
public class PlayingAdBreak {
    private final MediaTailorAdBreak adBreak;
    private final int adIndex;                 // 0-based index

    public MediaTailorLinearAd getAd() {
        return adBreak.getAds().get(adIndex);
    }

    // Constructor, getters, equals(), hashCode(), toString()
}
```

---

## 4. Component Specifications

### 4.1 MediaTailorSessionManager

**Purpose:** Manages MediaTailor session initialization and tracking data fetching.

**Dependencies:**
- `OptimizedHttpClient` (for HTTP calls)
- `DefaultAdsMapper` (for data transformation)

**Key Methods:**

```java
public class MediaTailorSessionManager {
    private final OptimizedHttpClient httpClient;
    private final DefaultAdsMapper adsMapper;

    public MediaTailorSessionManager() {
        this.httpClient = new OptimizedHttpClient();
        this.adsMapper = new DefaultAdsMapper();
    }

    /**
     * Initialize MediaTailor session.
     * @param sessionEndpoint Full session URL
     * @return Session with manifest and tracking URLs
     * @throws IOException on network error
     */
    public MediaTailorSession initializeSession(String sessionEndpoint) throws IOException;

    /**
     * Fetch tracking data from MediaTailor.
     * @param trackingUrl Tracking URL from session
     * @return List of mapped ad breaks
     * @throws IOException on network error
     */
    public List<MediaTailorAdBreak> fetchTrackingData(String trackingUrl) throws IOException;
}
```

**Implementation Notes:**
- Use `HttpURLConnection` via `OptimizedHttpClient`
- Parse JSON using `org.json.JSONObject` and `org.json.JSONArray`
- Add 500ms delay before fetching tracking data (as per MediaTailor requirement)
- Append timestamp query parameter to tracking URL to prevent caching
- Log all HTTP requests/responses for debugging

**Error Handling:**
- Catch `IOException` and log error
- Catch `JSONException` for parsing errors
- Return empty list on error (for POC; future: throw custom exception)

### 4.2 DefaultAdsMapper

**Purpose:** Transform MediaTailor API response DTOs to domain models.

**Key Methods:**

```java
public class DefaultAdsMapper {
    /**
     * Map API avails to domain ad breaks.
     * @param avails List of avails from API
     * @return List of mapped ad breaks
     */
    public List<MediaTailorAdBreak> mapAdBreaks(List<Avail> avails);
}
```

**Mapping Logic:**
```
For each Avail:
  Create MediaTailorAdBreak
  Set id = availId
  Set scheduleTime = startTimeInSeconds
  Set duration = durationInSeconds
  Set formattedDuration = duration
  Set adMarkerDuration = adMarkerDuration

  For each Ad in avail:
    Create MediaTailorLinearAd
    Set id = adId
    Set scheduleTime = startTimeInSeconds
    Set duration = durationInSeconds
    Set formattedDuration = duration

    For each TrackingEvent in ad:
      Create MediaTailorTrackingEvent
      Copy all fields

    Add trackingEvents to ad

  Add ads to adBreak

Return list of adBreaks
```

### 4.3 MediaTailorAdPlaybackTracker

**Purpose:** Monitor player position and detect ad break/ad playback.

**Dependencies:**
- `ExoPlayer` (player instance)
- `List<MediaTailorAdBreak>` (ad schedule)

**State Variables:**
```java
private int currentAdBreakIndex = 0;
private int currentAdIndex = 0;
private MediaTailorAdBreak nextAdBreak = null;
private PlayingAdBreak playingAdBreak = null;
```

**Key Methods:**

```java
public class MediaTailorAdPlaybackTracker {
    private final ExoPlayer player;
    private final List<MediaTailorAdBreak> adBreaks;
    private final Handler handler;

    public MediaTailorAdPlaybackTracker(ExoPlayer player, List<MediaTailorAdBreak> adBreaks);

    /**
     * Start tracking ad playback.
     */
    public void start();

    /**
     * Stop tracking and clean up.
     */
    public void stop();

    /**
     * Core tracking logic - called periodically.
     */
    private void trackAdBreaks();

    /**
     * Get the next upcoming ad break.
     * @return Next ad break or null if none
     */
    public MediaTailorAdBreak getNextAdBreak();

    /**
     * Get currently playing ad break.
     * @return Playing ad break or null if not in ad
     */
    public PlayingAdBreak getPlayingAdBreak();
}
```

**Tracking Algorithm:**

```
trackAdBreaks():
  1. Get player current time in seconds
  2. If adBreaks is empty, return

  3. Advance currentAdBreakIndex:
     While currentAdBreakIndex < last index AND
           playerTime >= adBreaks[currentAdBreakIndex].endTime:
       Increment currentAdBreakIndex
       Reset currentAdIndex = 0

  4. Get current ad break

  5. Update nextAdBreak:
     If playerTime < adBreak.scheduleTime:
       nextAdBreak = current adBreak
     Else if next adBreak exists:
       nextAdBreak = next adBreak
     Else:
       nextAdBreak = null

  6. Check if in ad break range:
     If playerTime NOT in [scheduleTime, endTime):
       playingAdBreak = null
       return

  7. Advance currentAdIndex:
     Get ads list from current ad break
     While currentAdIndex < last ad index AND
           playerTime >= ads[currentAdIndex].endTime:
       Increment currentAdIndex

  8. Get current ad

  9. Check if in ad range:
     If playerTime in [ad.scheduleTime, ad.endTime):
       playingAdBreak = new PlayingAdBreak(adBreak, currentAdIndex)
```

**Polling Frequency:** 100ms (10 updates per second)

**Performance Considerations:**
- Minimal computation per poll (O(1) with index tracking)
- No memory allocation in hot path
- Use Android Handler for main thread posting

### 4.4 MediaTailorEventEmitter

**Purpose:** Detect state changes and emit ad lifecycle events.

**Dependencies:**
- `MediaTailorAdPlaybackTracker` (state source)

**Key Methods:**

```java
public class MediaTailorEventEmitter {
    private final MediaTailorAdPlaybackTracker tracker;
    private PlayingAdBreak previousPlayingAdBreak = null;
    private final Handler handler;

    public MediaTailorEventEmitter(MediaTailorAdPlaybackTracker tracker);

    /**
     * Start emitting events.
     */
    public void start();

    /**
     * Stop emitting events.
     */
    public void stop();

    /**
     * Check for state changes and emit events.
     */
    private void checkAndEmitEvents();

    /**
     * Emit a single event.
     * @param event Event to emit
     */
    private void emitEvent(MediaTailorEvent event);
}
```

**Event Emission Logic:**

```
checkAndEmitEvents():
  Get current playingAdBreak from tracker

  Case 1: previousPlayingAdBreak == null && playingAdBreak != null
    → Emit AdBreakStarted(playingAdBreak.adBreak)
    → Emit AdStarted(playingAdBreak.ad, playingAdBreak.adIndex)

  Case 2: previousPlayingAdBreak != null && playingAdBreak != null
          AND different adBreak IDs
    → Emit AdFinished(previousPlayingAdBreak.ad)
    → Emit AdBreakFinished(previousPlayingAdBreak.adBreak)
    → Emit AdBreakStarted(playingAdBreak.adBreak)
    → Emit AdStarted(playingAdBreak.ad, playingAdBreak.adIndex)

  Case 3: playingAdBreak != null && different ad IDs (same break)
    → If previousPlayingAdBreak.ad exists: Emit AdFinished(previousPlayingAdBreak.ad)
    → Emit AdStarted(playingAdBreak.ad, playingAdBreak.adIndex)

  Case 4: previousPlayingAdBreak != null && playingAdBreak == null
    → Emit AdFinished(previousPlayingAdBreak.ad)
    → Emit AdBreakFinished(previousPlayingAdBreak.adBreak)

  Update previousPlayingAdBreak = playingAdBreak
```

**Event Output (POC):**
```java
private void emitEvent(MediaTailorEvent event) {
    Log.d("MediaTailor", event.toString());
    // Log detailed JSON or formatted output
}
```

**Polling Frequency:** 50ms (faster than tracker for responsive event emission)

---

## 5. Event Definitions

### 5.1 MediaTailorEvent Hierarchy

```java
public abstract class MediaTailorEvent {
    public abstract String getEventType();

    public static class AdBreakStarted extends MediaTailorEvent {
        private final MediaTailorAdBreak adBreak;

        @Override
        public String getEventType() { return "AD_BREAK_STARTED"; }
    }

    public static class AdBreakFinished extends MediaTailorEvent {
        private final MediaTailorAdBreak adBreak;

        @Override
        public String getEventType() { return "AD_BREAK_FINISHED"; }
    }

    public static class AdStarted extends MediaTailorEvent {
        private final MediaTailorLinearAd ad;
        private final int indexInQueue;

        @Override
        public String getEventType() { return "AD_STARTED"; }
    }

    public static class AdFinished extends MediaTailorEvent {
        private final MediaTailorLinearAd ad;

        @Override
        public String getEventType() { return "AD_FINISHED"; }
    }
}
```

### 5.2 Event Sequence Example

```
Content Playing → Ad Break Detection
├─ AdBreakStarted { adBreakId: "avail-1", scheduleTime: 30.0, duration: 60.0 }
├─ AdStarted { adId: "ad-1", indexInQueue: 0, scheduleTime: 30.0, duration: 30.0 }
│
Ad 1 Playing (30s - 60s)
│
├─ AdFinished { adId: "ad-1" }
├─ AdStarted { adId: "ad-2", indexInQueue: 1, scheduleTime: 60.0, duration: 30.0 }
│
Ad 2 Playing (60s - 90s)
│
├─ AdFinished { adId: "ad-2" }
├─ AdBreakFinished { adBreakId: "avail-1" }
│
Content Resumed → Next Ad Break
```

---

## 6. Integration with ExoPlayer

### 6.1 Player Time Access

```java
ExoPlayer player = ...;

// Get current position in milliseconds
long currentPositionMs = player.getCurrentPosition();

// Convert to seconds for ad schedule comparison
double currentTimeSeconds = currentPositionMs / 1000.0;
```

### 6.2 Player Lifecycle Integration

```java
// Initialize
MediaTailorSessionManager sessionManager = new MediaTailorSessionManager();
MediaTailorSession session = sessionManager.initializeSession(sessionUrl);
List<MediaTailorAdBreak> adBreaks = sessionManager.fetchTrackingData(session.getTrackingUrl());

// Start tracking
MediaTailorAdPlaybackTracker tracker = new MediaTailorAdPlaybackTracker(player, adBreaks);
tracker.start();

MediaTailorEventEmitter emitter = new MediaTailorEventEmitter(tracker);
emitter.start();

// Play MediaTailor manifest
player.setMediaItem(MediaItem.fromUri(session.getManifestUrl()));
player.prepare();
player.play();

// Clean up
emitter.stop();
tracker.stop();
```

---

## 7. Error Handling & Edge Cases

### 7.1 Network Errors
- Session initialization fails → Log error, return null
- Tracking data fetch fails → Log error, return empty list
- Future: Implement retry with exponential backoff

### 7.2 Parsing Errors
- Invalid JSON → Log error, return empty/null
- Missing required fields → Use default values or skip entry
- Future: Validate schema with JSON schema library

### 7.3 Playback Edge Cases

**Seeking During Ad:**
- If seeking past ad → Emit AdFinished + AdBreakFinished if needed
- If seeking into ad → Emit AdBreakStarted + AdStarted
- Index tracking handles this automatically

**Replaying Content:**
- Reset `currentAdBreakIndex` and `currentAdIndex` to 0
- Clear state: `nextAdBreak = null`, `playingAdBreak = null`
- Future: Add `reset()` method

**Pausing During Ad:**
- State remains unchanged (still in ad)
- No special handling needed
- Tracking continues on resume

**Stream Without Ads:**
- Empty avails list → No events emitted
- Tracker gracefully handles empty list

---

## 8. Performance Considerations

### 8.1 Memory Usage
- Ad schedule stored in memory (typically < 100 KB for 2-hour content)
- No leaks: clean up handlers on stop()
- Minimal object allocation in hot path

### 8.2 CPU Usage
- Polling frequency: 100ms (tracker), 50ms (emitter)
- Lightweight comparisons only (no heavy computation)
- Android Handler ensures main thread execution

### 8.3 Network Usage
- One-time session initialization (< 1 KB)
- One-time tracking data fetch (typically < 50 KB)
- Future: Periodic refresh (every 30s, configurable)

---

## 9. Testing Strategy

### 9.1 Unit Tests
- Test data mapping (DTOs → domain models)
- Test ad detection logic with mock schedules
- Test event emission logic with state transitions

### 9.2 Integration Tests
- Test with real MediaTailor session URL
- Verify HTTP calls and response parsing
- Test with ExoPlayer and sample HLS manifest

### 9.3 Manual Testing
- Play MediaTailor stream in sample app
- Monitor Logcat for events
- Verify timing accuracy (±100ms acceptable)
- Test edge cases (seeking, pausing, replaying)

---

## 10. Future Enhancements

### 10.1 Full Event Integration
- Send events through `NRVideo.recordEvent()`
- Map MediaTailor events to `VideoAdAction` types
- Include all standard ad attributes (adPartner, adCreativeId, etc.)

### 10.2 Beacon Tracking
- Fire tracking beacons at appropriate times
- Use `MediaTailorTrackingEvent.beaconUrls`
- Respect event timing (impression, start, quartiles, complete)

### 10.3 Dynamic Ad Schedule Updates
- Poll tracking URL periodically (every 30s)
- Handle schedule changes mid-stream
- Merge new avails with existing schedule

### 10.4 Multi-Player Support
- Abstract player interface
- Implementations for ExoPlayer, MediaPlayer, custom players
- Plugin architecture

### 10.5 Production Readiness
- Comprehensive error handling
- Retry logic with exponential backoff
- Monitoring and metrics
- Unit test coverage > 80%
- Performance profiling

---

## 11. References

### 11.1 MediaTailor Documentation
- [AWS MediaTailor Developer Guide](https://docs.aws.amazon.com/mediatailor/)
- [Client-side Reporting](https://docs.aws.amazon.com/mediatailor/latest/ug/ad-reporting-client-side.html)
- [Session Initialization API](https://docs.aws.amazon.com/mediatailor/latest/apireference/session.html)

### 11.2 ExoPlayer Documentation
- [ExoPlayer Developer Guide](https://developer.android.com/guide/topics/media/exoplayer)
- [Player.Listener](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Player.Listener.html)

### 11.3 Internal Documentation
- `DATAMODEL.md` - New Relic video event data model
- `CLAUDE.md` - Project guidelines and architecture

---

## 12. Glossary

- **Avail:** MediaTailor term for an ad break (ad availability window)
- **SSAI:** Server-Side Ad Insertion (ads stitched into HLS manifest)
- **DTO:** Data Transfer Object (API response models)
- **Domain Model:** Business logic representation of data
- **Beacon:** HTTP request fired to track ad event (impression, quartile, etc.)
- **Playhead:** Current playback position in seconds

---

## Appendix A: Example Log Output

```
D/MediaTailor: Session initialized: manifestUrl=https://...master.m3u8, trackingUrl=https://...tracking
D/MediaTailor: Tracking data fetched: 3 ad breaks, 7 total ads
D/MediaTailor: AdBreakStarted { id=avail-1, scheduleTime=30.0, duration=60.0, adCount=2 }
D/MediaTailor: AdStarted { id=ad-1, indexInQueue=0, scheduleTime=30.0, duration=30.0 }
D/MediaTailor: PlayerTime=45.2s, PlayingAd { id=ad-1, adIndex=0, adBreakId=avail-1 }
D/MediaTailor: AdFinished { id=ad-1, duration=30.0 }
D/MediaTailor: AdStarted { id=ad-2, indexInQueue=1, scheduleTime=60.0, duration=30.0 }
D/MediaTailor: PlayerTime=75.8s, PlayingAd { id=ad-2, adIndex=1, adBreakId=avail-1 }
D/MediaTailor: AdFinished { id=ad-2, duration=30.0 }
D/MediaTailor: AdBreakFinished { id=avail-1, totalDuration=60.0 }
D/MediaTailor: PlayerTime=95.0s, Content playing
```

---

**End of Specification**
