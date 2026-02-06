# MediaTailor Tracker Implementation - Task List

## Overview
This document tracks the implementation of AWS MediaTailor ad tracking integration for the NewRelic Video Agent Android SDK.

**Implementation Approach:** Step-by-step with validation checkpoints
**Location:** NewRelicVideoCore package
**Integration:** ExoPlayer player
**Network:** OptimizedHttpClient
**Validation:** Android Logcat logging (POC)

---

## STEP 1: Session Initialization & Data Fetching

### 1.1 Create Package Structure
- [ ] Create package: `com.newrelic.videoagent.core.mediatailor`
- [ ] Create sub-package: `com.newrelic.videoagent.core.mediatailor.model`

### 1.2 Create Data Transfer Objects (DTOs)

#### MediaTailorSession.java
- [ ] Create class in `mediatailor/model/`
- [ ] Add fields:
  - [ ] `String manifestUrl`
  - [ ] `String trackingUrl`
- [ ] Add constructor
- [ ] Add getters
- [ ] Add toString() for debugging

#### MediaTailorTrackingData.java
- [ ] Create class in `mediatailor/model/`
- [ ] Add field: `List<Avail> avails`
- [ ] Add getters

#### Avail.java (nested in MediaTailorTrackingData or separate)
- [ ] Create class in `mediatailor/model/`
- [ ] Add fields:
  - [ ] `String availId`
  - [ ] `double startTimeInSeconds`
  - [ ] `double durationInSeconds`
  - [ ] `String duration` (formatted)
  - [ ] `double adMarkerDuration`
  - [ ] `List<Ad> ads`
- [ ] Add getters

#### Ad.java (nested in Avail or separate)
- [ ] Create class in `mediatailor/model/`
- [ ] Add fields:
  - [ ] `String adId`
  - [ ] `double startTimeInSeconds`
  - [ ] `double durationInSeconds`
  - [ ] `String duration` (formatted)
  - [ ] `List<TrackingEvent> trackingEvents`
- [ ] Add getters

#### TrackingEvent.java (nested in Ad or separate)
- [ ] Create class in `mediatailor/model/`
- [ ] Add fields:
  - [ ] `String eventId`
  - [ ] `double startTimeInSeconds`
  - [ ] `double durationInSeconds`
  - [ ] `String eventType`
  - [ ] `List<String> beaconUrls`
- [ ] Add getters

### 1.3 Create Domain Models

#### MediaTailorAdBreak.java
- [ ] Create class in `mediatailor/model/`
- [ ] Add fields:
  - [ ] `String id`
  - [ ] `List<MediaTailorLinearAd> ads`
  - [ ] `double scheduleTime`
  - [ ] `double duration`
  - [ ] `String formattedDuration`
  - [ ] `double adMarkerDuration`
- [ ] Add computed properties:
  - [ ] `double getEndTime()` → scheduleTime + duration
  - [ ] `Range getStartToEndTime()` → scheduleTime..endTime
- [ ] Add constructor, getters, toString()

#### MediaTailorLinearAd.java
- [ ] Create class in `mediatailor/model/`
- [ ] Add fields:
  - [ ] `String id`
  - [ ] `double scheduleTime`
  - [ ] `double duration`
  - [ ] `String formattedDuration`
  - [ ] `List<MediaTailorTrackingEvent> trackingEvents`
- [ ] Add computed properties:
  - [ ] `double getEndTime()` → scheduleTime + duration
  - [ ] `Range getStartToEndTime()` → scheduleTime..endTime
- [ ] Add constructor, getters, toString()

#### MediaTailorTrackingEvent.java
- [ ] Create class in `mediatailor/model/`
- [ ] Add fields:
  - [ ] `String id`
  - [ ] `double scheduleTime`
  - [ ] `double duration`
  - [ ] `String eventType`
  - [ ] `List<String> beaconUrls`
- [ ] Add constructor, getters, toString()

### 1.4 Create Mapper

#### DefaultAdsMapper.java
- [ ] Create class in `mediatailor/`
- [ ] Implement method: `List<MediaTailorAdBreak> mapAdBreaks(List<Avail> avails)`
- [ ] Map logic:
  - [ ] Iterate through avails
  - [ ] For each avail, map to MediaTailorAdBreak
  - [ ] For each ad in avail, map to MediaTailorLinearAd
  - [ ] For each trackingEvent in ad, map to MediaTailorTrackingEvent
- [ ] Add error handling for null/empty data

### 1.5 Create Session Manager

#### MediaTailorSessionManager.java
- [ ] Create class in `mediatailor/`
- [ ] Add private field: `OptimizedHttpClient httpClient`
- [ ] Add private field: `DefaultAdsMapper adsMapper`
- [ ] Add constructor
- [ ] Implement `MediaTailorSession initializeSession(String sessionEndpoint)`:
  - [ ] Create POST request body with `adsParams: {}` and `reportingMode: "client"`
  - [ ] Use httpClient to POST to sessionEndpoint
  - [ ] Parse JSON response to extract manifestUrl and trackingUrl
  - [ ] Return MediaTailorSession object
  - [ ] Add error handling and logging
- [ ] Implement `List<MediaTailorAdBreak> fetchTrackingData(String trackingUrl)`:
  - [ ] Wait 500ms (Thread.sleep or Handler.postDelayed)
  - [ ] Append timestamp query param: `?t={currentTimeMillis}`
  - [ ] Use httpClient to GET from trackingUrl
  - [ ] Parse JSON response to MediaTailorTrackingData
  - [ ] Use adsMapper to map avails to MediaTailorAdBreak list
  - [ ] Return mapped list
  - [ ] Add error handling and logging
- [ ] Add helper method for JSON parsing (or use org.json.JSONObject)

### 1.6 Testing Step 1
- [ ] Create test session URL constant
- [ ] Initialize MediaTailorSessionManager
- [ ] Call initializeSession() with test URL
- [ ] Log manifestUrl and trackingUrl
- [ ] Call fetchTrackingData() with trackingUrl
- [ ] Log ad schedule (number of ad breaks, ad counts, timings)
- [ ] Verify data structure is correct
- [ ] **CHECKPOINT: User validates Step 1 before proceeding**

---

## STEP 2: Ad Break Tracking

### 2.1 Create Playback State Model

#### PlayingAdBreak.java
- [ ] Create class in `mediatailor/model/`
- [ ] Add fields:
  - [ ] `MediaTailorAdBreak adBreak`
  - [ ] `int adIndex`
- [ ] Add computed property:
  - [ ] `MediaTailorLinearAd getAd()` → adBreak.ads[adIndex]
- [ ] Add constructor, getters, toString()

### 2.2 Create Ad Playback Tracker

#### MediaTailorAdPlaybackTracker.java
- [ ] Create class in `mediatailor/`
- [ ] Add private fields:
  - [ ] `ExoPlayer player`
  - [ ] `List<MediaTailorAdBreak> adBreaks`
  - [ ] `int currentAdBreakIndex = 0`
  - [ ] `int currentAdIndex = 0`
  - [ ] `Handler handler`
  - [ ] `Runnable trackingRunnable`
- [ ] Add state fields (observable/mutable):
  - [ ] `MediaTailorAdBreak nextAdBreak` (nullable)
  - [ ] `PlayingAdBreak playingAdBreak` (nullable)
- [ ] Add constructor: `MediaTailorAdPlaybackTracker(ExoPlayer player, List<MediaTailorAdBreak> adBreaks)`
- [ ] Implement `start()`:
  - [ ] Initialize Handler
  - [ ] Create trackingRunnable that calls trackAdBreaks() and re-posts itself (100ms delay)
  - [ ] Post initial runnable
- [ ] Implement `stop()`:
  - [ ] Remove callbacks from handler
- [ ] Implement `trackAdBreaks()`:
  - [ ] Get current player time: `player.getCurrentPosition() / 1000.0` (convert to seconds)
  - [ ] Check if adBreaks is empty → return early
  - [ ] Advance currentAdBreakIndex while player time >= current adBreak.endTime
  - [ ] Get current ad break
  - [ ] Update nextAdBreak state:
    - [ ] If player time < adBreak.scheduleTime → nextAdBreak = current
    - [ ] Else if next ad break exists → nextAdBreak = next
    - [ ] Else → nextAdBreak = null
  - [ ] Check if player time is within current ad break range:
    - [ ] If NO → playingAdBreak = null, return
    - [ ] If YES → continue
  - [ ] Advance currentAdIndex while player time >= current ad.endTime
  - [ ] Get current ad
  - [ ] Check if player time is within current ad range:
    - [ ] If YES → update playingAdBreak with new PlayingAdBreak(adBreak, adIndex)
- [ ] Add getters for nextAdBreak and playingAdBreak
- [ ] Add logging for state changes

### 2.3 Testing Step 2
- [ ] Create mock/test ad schedule with 2-3 ad breaks
- [ ] Initialize MediaTailorAdPlaybackTracker with ExoPlayer
- [ ] Call start()
- [ ] Play MediaTailor HLS stream
- [ ] Monitor Logcat for:
  - [ ] nextAdBreak updates (before ad break starts)
  - [ ] playingAdBreak updates (during ad break)
  - [ ] Correct ad break index and ad index
- [ ] Test edge cases:
  - [ ] Seeking past ad break
  - [ ] Replaying content
  - [ ] Pausing during ad
- [ ] Verify timing accuracy (±100ms acceptable)
- [ ] **CHECKPOINT: User validates Step 2 before proceeding**

---

## STEP 3: Event Emitter

### 3.1 Create Event Models

#### MediaTailorEvent.java (Sealed class pattern)
- [ ] Create abstract base class in `mediatailor/model/`
- [ ] Create nested classes:
  - [ ] `AdBreakStarted extends MediaTailorEvent`:
    - [ ] Field: `MediaTailorAdBreak adBreak`
  - [ ] `AdBreakFinished extends MediaTailorEvent`:
    - [ ] Field: `MediaTailorAdBreak adBreak`
  - [ ] `AdStarted extends MediaTailorEvent`:
    - [ ] Field: `MediaTailorLinearAd ad`
    - [ ] Field: `int indexInQueue`
  - [ ] `AdFinished extends MediaTailorEvent`:
    - [ ] Field: `MediaTailorLinearAd ad`
- [ ] Add constructors, getters, toString() for each

### 3.2 Create Event Emitter

#### MediaTailorEventEmitter.java
- [ ] Create class in `mediatailor/`
- [ ] Add private fields:
  - [ ] `MediaTailorAdPlaybackTracker tracker`
  - [ ] `PlayingAdBreak previousPlayingAdBreak` (nullable)
  - [ ] `Handler handler`
  - [ ] `Runnable eventCheckRunnable`
- [ ] Add constructor: `MediaTailorEventEmitter(MediaTailorAdPlaybackTracker tracker)`
- [ ] Implement `start()`:
  - [ ] Initialize Handler
  - [ ] Create eventCheckRunnable that calls checkAndEmitEvents() and re-posts itself (50ms delay)
  - [ ] Post initial runnable
- [ ] Implement `stop()`:
  - [ ] Remove callbacks from handler
- [ ] Implement `checkAndEmitEvents()`:
  - [ ] Get current playingAdBreak from tracker
  - [ ] Compare with previousPlayingAdBreak
  - [ ] Event logic:
    - [ ] Case: previousPlayingAdBreak == null && playingAdBreak != null
      - [ ] Emit AdBreakStarted(playingAdBreak.adBreak)
      - [ ] Emit AdStarted(playingAdBreak.ad, playingAdBreak.adIndex)
    - [ ] Case: previousPlayingAdBreak != null && playingAdBreak != null && different ad break IDs
      - [ ] Emit AdFinished(previousPlayingAdBreak.ad)
      - [ ] Emit AdBreakFinished(previousPlayingAdBreak.adBreak)
      - [ ] Emit AdBreakStarted(playingAdBreak.adBreak)
      - [ ] Emit AdStarted(playingAdBreak.ad, playingAdBreak.adIndex)
    - [ ] Case: playingAdBreak != null && different ad IDs (same break)
      - [ ] If previousPlayingAdBreak.ad != null: Emit AdFinished(previousPlayingAdBreak.ad)
      - [ ] Emit AdStarted(playingAdBreak.ad, playingAdBreak.adIndex)
    - [ ] Case: previousPlayingAdBreak != null && playingAdBreak == null
      - [ ] Emit AdFinished(previousPlayingAdBreak.ad)
      - [ ] Emit AdBreakFinished(previousPlayingAdBreak.adBreak)
  - [ ] Update previousPlayingAdBreak = playingAdBreak
- [ ] Implement `emitEvent(MediaTailorEvent event)`:
  - [ ] Use Log.d("MediaTailor", event.toString())
  - [ ] Log detailed event data (ad ID, ad break ID, timing, index, etc.)

### 3.3 Testing Step 3
- [ ] Initialize full flow: SessionManager → AdPlaybackTracker → EventEmitter
- [ ] Play MediaTailor HLS stream
- [ ] Monitor Logcat for event sequence
- [ ] Verify events:
  - [ ] AdBreakStarted appears before first ad
  - [ ] AdStarted appears for each ad with correct index
  - [ ] AdFinished appears after each ad
  - [ ] AdBreakFinished appears after last ad in break
  - [ ] Transitions between ad breaks are correct
- [ ] Test edge cases:
  - [ ] Single ad in break
  - [ ] Multiple ads in break
  - [ ] Seeking during ad break
  - [ ] Stream with no ads
- [ ] Verify timing accuracy
- [ ] **CHECKPOINT: User validates Step 3 - POC complete**

---

## STEP 4: Integration & Configuration (Optional)

### 4.1 Update Configuration

#### NRVideoPlayerConfiguration.java
- [ ] Add optional field: `private String mediaTailorSessionUrl`
- [ ] Update Builder:
  - [ ] Add `Builder mediaTailorSessionUrl(String url)` method
  - [ ] Update build() to set field
- [ ] Add getter: `String getMediaTailorSessionUrl()`

### 4.2 Integration Point
- [ ] Decide where to initialize MediaTailor tracker (in sample app or as library feature)
- [ ] If library feature:
  - [ ] Update NRTrackerExoPlayer or create wrapper
  - [ ] Auto-initialize if mediaTailorSessionUrl is provided
  - [ ] Manage lifecycle (start/stop with player)

---

## Documentation Updates

### MEDIATAILOR_SPEC.md
- [ ] Complete technical specification
- [ ] Add architecture diagrams
- [ ] Document API contracts
- [ ] Add state machine diagrams
- [ ] Document error handling

### DATAMODEL.md (Future - after POC validation)
- [ ] Document MediaTailor-specific attributes
- [ ] Add example events
- [ ] Update attribute tables

---

## Final Validation

### End-to-End Testing
- [ ] Test with real MediaTailor session URL: `https://2d271f758c9940e882092aed7e4451c4.mediatailor.ap-southeast-2.amazonaws.com/v1/session/54ad5836d26bc9938d9793caa9fe55e611da7d60/my-playback-config/`
- [ ] Verify session initialization
- [ ] Verify tracking data fetch
- [ ] Verify ad detection during playback
- [ ] Verify complete event lifecycle
- [ ] Monitor for memory leaks
- [ ] Monitor for performance issues
- [ ] Collect Logcat output for review

### User Acceptance
- [ ] Demo to stakeholder
- [ ] Review logged data
- [ ] Confirm accuracy of ad tracking
- [ ] Discuss next steps (full integration vs refinements)

---

## Progress Summary

**Step 1:** ⬜ Not Started
**Step 2:** ⬜ Not Started
**Step 3:** ⬜ Not Started
**Integration:** ⬜ Not Started
**Final Validation:** ⬜ Not Started

---

## Notes & Issues

### Issues Encountered
- (Track any blockers or issues here)

### Decisions Made
- Implementation within NewRelicVideoCore (not separate module)
- Using OptimizedHttpClient for network calls
- Logging only for POC validation
- ExoPlayer integration via direct player instance

### Future Enhancements
- Full event integration with NRVideo event system
- Send ad events to New Relic platform
- Support for other player types
- Background thread optimization
- Caching of tracking data
- Error recovery and retry logic
