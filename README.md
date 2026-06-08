[![Community Project header](https://github.com/newrelic/opensource-website/raw/master/src/images/categories/Community_Project.png)](https://opensource.newrelic.com/oss-category/#community-project)

# New Relic Video Agent for Android

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

The New Relic Video Agent for Android provides comprehensive video analytics for Android applications using ExoPlayer (Media3). Track video events, monitor playback quality, identify errors, and gain deep insights into user engagement and performance — for both mobile and Android TV.

## Features

- **Automatic Event Detection** — Captures ExoPlayer lifecycle events automatically without manual instrumentation
- **QoE Metrics** — Quality of Experience aggregation for startup time, buffering ratio, bitrate, and playback errors
- **Event Segregation** — Organized event types: `VideoAction`, `VideoAdAction`, `VideoErrorAction`, `VideoCustomAction`
- **IMA Ads Support** — Built-in Google IMA SDK ad tracking via dedicated ad tracker
- **Android TV Support** — Auto-detection of Android TV with optimized harvest cycles
- **Multi-Player Support** — Track multiple simultaneous video players in the same application
- **Easy Integration** — JitPack dependency or manual AAR/source import

## Table of Contents

- [Installation](#installation)
  - [Option 1: JitPack (Recommended)](#option-1-install-via-jitpack-recommended)
  - [Option 2: Manual AAR Files](#option-2-install-manually-using-aar-files)
  - [Option 3: Source Code](#option-3-install-manually-using-source-code)
- [Prerequisites](#prerequisites)
- [Modules](#modules)
- [Usage](#usage)
- [Best Practices](#best-practices)
- [Configuration Options](#configuration-options)
- [API Reference](#api-reference)
- [Data Model](#data-model)
- [Support](#support)
- [Contribute](#contribute)
- [License](#license)

## Installation

### Option 1: Install via JitPack (Recommended)

Add the JitPack repository inside your root `build.gradle`:

```groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependencies inside your app's `build.gradle`:

```groovy
dependencies {
    // Required: Core library
    implementation 'com.github.newrelic.video-agent-android:NewRelicVideoCore:v4.1.0'

    // ExoPlayer (Media3) tracker
    implementation 'com.github.newrelic.video-agent-android:NRExoPlayerTracker:v4.1.0'

    // Google IMA ad tracker (optional — for client-side ad insertion)
    implementation 'com.github.newrelic.video-agent-android:NRIMATracker:v4.1.0'

    // AWS MediaTailor ad tracker (optional — for server-side ad insertion / SSAI)
    implementation 'com.github.newrelic.video-agent-android:NRMediaTailorTracker:v4.1.0'
}
```

> **Note:** Replace `v4.1.0` with the desired [release version](https://github.com/newrelic/video-agent-android/releases).
> `NRIMATracker` and `NRMediaTailorTracker` are mutually exclusive per player — pick one based on whether your stream uses CSAI (IMA) or SSAI (MediaTailor).

### Option 2: Install Manually Using AAR Files

1. Clone this repo.
2. Open it with Android Studio.
3. Click on **View > Tool Windows > Gradle** to open the Gradle tool window.
4. Unfold **NRVideoProject > Tasks > build** and double-click **assemble**. This generates AAR files inside each module's `build/outputs/aar/` directory.
5. In your project, click **File > New > New Module > Import .JAR/.AAR Package** and click **Next**.
6. Select the generated AAR file and click **Finish**.
7. Repeat steps 5–6 for each module you need.
8. Add the dependencies in your app's `build.gradle`:

```groovy
dependencies {
    implementation project(":NewRelicVideoCore")
    implementation project(":NRExoPlayerTracker")
    implementation project(":NRIMATracker")
}
```

### Option 3: Install Manually Using Source Code

1. Clone this repo.
2. In your project, click **File > New > Import Module**.
3. Select the module directory and click **Finish**.
4. Repeat for each module you need.
5. Add the dependencies in your app's `build.gradle`:

```groovy
dependencies {
    implementation project(":NewRelicVideoCore")
    implementation project(":NRExoPlayerTracker")
    implementation project(":NRIMATracker")
}
```

## Prerequisites

Before using the Video Agent, ensure you have:

- **New Relic Account** — Active account with a valid application token
- **New Relic Android Agent** — [Installed and configured](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/install-android-apps-gradle-android-studio) in your project
- **ExoPlayer / Media3** — `androidx.media3:media3-exoplayer:1.2.0` or later
- **Google IMA SDK** (optional) — `androidx.media3:media3-exoplayer-ima:1.2.0` if tracking ads
- **Android minSdk** — API 16 (Android 4.1) or higher

## Modules

The Video Agent is composed of three modules:

| Module | Description | Required |
|--------|-------------|----------|
| **NewRelicVideoCore** | Base classes for tracker management, event generation, and data harvesting. Depends on the New Relic Android Agent. | Yes |
| **NRExoPlayerTracker** | Video tracker for ExoPlayer (Media3). Automatically hooks into player lifecycle events. | Yes (for ExoPlayer) |
| **NRIMATracker** | Ad tracker for the Google IMA SDK (client-side ad insertion / CSAI). Captures ad lifecycle events including quartiles, breaks, and errors. | Optional |
| **NRMediaTailorTracker** | Ad tracker for AWS Elemental MediaTailor (server-side ad insertion / SSAI). Supports DASH and HLS, explicit and implicit session init, live + VOD, with rich VAST metadata. | Optional |

## Usage

### Getting Your Application Token

Before initializing the Video Agent, obtain your application token:

1. Log in to [one.newrelic.com](https://one.newrelic.com)
2. Navigate to the Streaming Video & Ads onboarding flow
3. Copy your `applicationToken`

### Basic Setup — ExoPlayer Only

```java
// Step 1: Initialize NRVideo in your main activity (e.g., MainActivity.java)
NRVideoConfiguration config = new NRVideoConfiguration.Builder("YOUR_APPLICATION_TOKEN")
        .autoDetectPlatform(getApplicationContext())
        .withHarvestCycle(5 * 60) // 300 seconds (5 minutes) — recommended for on-demand video
        .build();

NRVideo.newBuilder(getApplicationContext())
        .withConfiguration(config)
        .build();

// Step 2: Initialize the player and register (e.g., VideoPlayer.java)
ExoPlayer player = new ExoPlayer.Builder(this).build();

Map<String, Object> customAttrs = new HashMap<>();
customAttrs.put("contentTitle", "My Video Title");

NRVideoPlayerConfiguration playerConfig =
        new NRVideoPlayerConfiguration("my-player", player, false, customAttrs);

Integer trackerId = NRVideo.addPlayer(playerConfig);

// Step 3 (Optional): Release the tracker when done
@Override
protected void onDestroy() {
    NRVideo.releaseTracker(trackerId);
    player.release();
    super.onDestroy();
}
```

### Setup with ExoPlayer and AWS MediaTailor (SSAI)

```java
// Step 1: Initialize NRVideo (same as basic setup)

// Step 2: Explicit MediaTailor session init — POST to /v1/session/…, get back
//         a sessionized manifest URL plus a tracking URL.
//   POST  https://<hash>.mediatailor.<region>.amazonaws.com/v1/session/<hash>/<config>/<manifestFile>
//   body  {"reportingMode":"server", "adsParams":{…custom targeting…}}
//   resp  {"manifestUrl":"/v1/dash/…?aws.sessionId=…", "trackingUrl":"/v1/tracking/…"}
// Both paths are relative — prefix them with your MediaTailor host.

String manifestUrl = /* absolute URL from session init */;
String trackingUrl = /* absolute URL from session init */;

ExoPlayer player = new ExoPlayer.Builder(this).build();

// Step 3: Register with MEDIA_TAILOR as the ad tracker type.
NRVideoPlayerConfiguration playerConfig = new NRVideoPlayerConfiguration(
        "mediatailor-player",
        player,
        NRVideoPlayerConfiguration.AdTrackerType.MEDIA_TAILOR,
        /* custom attrs */ null);
Integer trackerId = NRVideo.addPlayer(playerConfig);

// Step 4: Hand ExoPlayer the sessionized manifest URL and start playback.
//         The tracker auto-derives the tracking URL from aws.sessionId in
//         this URI — no extra wiring needed in the normal flow.
player.setMediaItem(MediaItem.fromUri(manifestUrl));
player.prepare();
player.setPlayWhenReady(true);

// Step 5 (Optional): For a "Skip ad" button in your UI, fire AD_SKIP via
// NRTrackerMediaTailor adTracker = (NRTrackerMediaTailor)
//         NewRelicVideoAgent.getInstance().getAdTracker(trackerId);
// adTracker.notifyAdSkipped();
```

What the tracker emits on `VideoAdAction`:
`AD_BREAK_START`, `AD_REQUEST`, `AD_START`, `AD_PAUSE`, `AD_RESUME`,
`AD_SEEK_START`, `AD_SEEK_END`, `AD_BUFFER_START`, `AD_BUFFER_END`,
`AD_QUARTILE` (25/50/75%), `AD_END`, `AD_BREAK_END`, `AD_SKIP`, `AD_ERROR`.
All events carry `trackerName="NRMTracker"`, `adPartner="aws-mediatailor"`,
plus rich VAST metadata (see [DATAMODEL.md](./DATAMODEL.md)).

### Setup with ExoPlayer and IMA Ads

```java
// Step 1: Initialize NRVideo (same as above)

// Step 2: Build the player with IMA ad support
ExoPlayer player = new ExoPlayer.Builder(this)
        .setMediaSourceFactory(mediaSourceFactory)
        .build();

NRVideoPlayerConfiguration playerConfig =
        new NRVideoPlayerConfiguration("my-player", player, true, null);

Integer trackerId = NRVideo.addPlayer(playerConfig);

// Step 3: Wire up the IMA ad tracker
NRTrackerIMA adTracker =
        (NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId);

ImaAdsLoader.Builder builder = new ImaAdsLoader.Builder(this);
builder.setAdErrorListener(adTracker.getAdErrorListener());
builder.setAdEventListener(adTracker.getAdEventListener());

// Step 4 (Optional): Release on destroy
@Override
protected void onDestroy() {
    NRVideo.releaseTracker(trackerId);
    player.release();
    super.onDestroy();
}
```

## Best Practices

### 1. Setting `contentTitle`

The `contentTitle` attribute displays a value if your video metadata contains title information. For best results, explicitly set it during player configuration:

```java
Map<String, Object> customAttrs = new HashMap<>();
customAttrs.put("contentTitle", "My Video Title");

NRVideoPlayerConfiguration playerConfig =
        new NRVideoPlayerConfiguration("my-player", player, false, customAttrs);
```

### 2. Setting `userId`

Set a user identifier to track video analytics per user:

```java
// Set userId globally across all trackers
NRVideo.setUserId("user-12345");
```

### 3. Adding Custom Attributes

Add custom attributes to improve data aggregation and analysis:

```java
Map<String, Object> customAttrs = new HashMap<>();
customAttrs.put("contentTitle", videoMetadata.getTitle());
customAttrs.put("subscriptionTier", "premium");
customAttrs.put("contentProvider", "studio-abc");
customAttrs.put("region", "us-west-2");
customAttrs.put("cdnProvider", "cloudflare");

NRVideoPlayerConfiguration playerConfig =
        new NRVideoPlayerConfiguration("my-player", player, false, customAttrs);
```

You can also set attributes after initialization:

```java
// Set attribute on a specific content tracker
NRVideo.setAttribute(trackerId, "contentSeries", "Season 1");

// Set attribute on ad tracker
NRVideo.setAdAttribute(trackerId, "adCampaign", "spring-promo");

// Set global attribute across all trackers
NRVideo.setGlobalAttribute("appVersion", "2.1.0");
```

**Use these attributes in New Relic queries:**

```sql
-- Analyze by subscription tier
SELECT count(*) FROM VideoAction WHERE actionName = 'CONTENT_START'
FACET subscriptionTier SINCE 1 day ago

-- Monitor by region
SELECT average(contentPlayhead) FROM VideoAction
FACET region SINCE 1 hour ago
```

### 4. Gradual Rollout with Feature Flags

When deploying to production, use feature flags to enable the tracker gradually:

```java
int rolloutPercentage = 5; // Start with 5% of users

boolean shouldEnable = (userId.hashCode() % 100) < rolloutPercentage;

if (shouldEnable) {
    NRVideoConfiguration config = new NRVideoConfiguration.Builder("YOUR_APPLICATION_TOKEN")
            .autoDetectPlatform(getApplicationContext())
            .withHarvestCycle(5 * 60)
            .build();
    NRVideo.newBuilder(getApplicationContext()).withConfiguration(config).build();
}
```

**Recommended Rollout Schedule:**

| Phase | Percentage | Duration | Validation |
|-------|-----------|----------|------------|
| Initial | 5% | 2–3 days | Verify data flowing to New Relic |
| Early | 15% | 3–5 days | Check data quality and performance |
| Expansion | 25% | 5–7 days | Validate across device types |
| Majority | 50% | 1–2 weeks | Monitor at scale |
| Full | 100% | Ongoing | Complete deployment |

## Configuration Options

### NRVideoConfiguration

| Builder Method | Type | Default | Description |
|----------------|------|---------|-------------|
| `Builder(applicationToken)` | `String` | — | **Required.** Your New Relic application token. Used for authentication and region detection. |
| `.autoDetectPlatform(context)` | `Context` | Mobile | Auto-detect Mobile vs. Android TV platform. |
| `.withHarvestCycle(seconds)` | `int` | 300 (Mobile) / 180 (TV) | Interval in seconds between data harvests. For on-demand video, use a minimum of 300 seconds. |
| `.enableLogging()` | — | Disabled | Enable debug logging for development. |
| `.enableQoeAggregate(enabled)` | `boolean` | `false` | Enable Quality of Experience event aggregation (`QOE_AGGREGATE` events). |
| `.withQoeAggregateIntervalMultiplier(multiplier)` | `int` | `1` | Controls how often `QOE_AGGREGATE` events are emitted relative to the harvest cycle. `1` = every harvest cycle, `2` = every other cycle, `3` = every third, etc. The first and last harvest cycles always emit a `QOE_AGGREGATE` event regardless of this value. |
| `.withMemoryOptimization()` | — | Disabled | Optimize for low-memory devices. |

### NRVideoPlayerConfiguration

| Parameter | Type | Description |
|-----------|------|-------------|
| `playerName` | `String` | Unique identifier for the video player. Used to distinguish between multiple players. |
| `player` | `ExoPlayer` | The ExoPlayer instance to track. |
| `isAdEnabled` | `boolean` | Set `true` if the player has an IMA ads loader; `false` otherwise. |
| `customAttributes` | `Map<String, Object>` | Custom attributes to attach to all events from this player. |

### Custom Attribute Limits

Limits for custom attributes added to default mobile events:

- **Attributes:** 128 maximum
- **String attributes:** 4 KB maximum length (empty string values are not accepted)

> **Note:** There are special keywords reserved for default attributes documented in [DATAMODEL.md](./DATAMODEL.md). Please do not use these as custom attribute names, as they will be dropped by the agent.

### Live Stream Configuration

For live streams, the agent automatically uses a shorter harvest cycle (30–60 seconds) for near-real-time data transmission. The `liveHarvestCycleSeconds` is configured internally based on content type.

## API Reference

### `NRVideo` (Primary API)

#### `NRVideo.addPlayer(playerConfig)`
Register a player with the Video Agent. Returns a `trackerId` for future reference.

```java
Integer trackerId = NRVideo.addPlayer(playerConfig);
```

#### `NRVideo.releaseTracker(trackerId)`
Release a tracker when the player is destroyed.

```java
NRVideo.releaseTracker(trackerId);
```

#### `NRVideo.setUserId(userId)`
Set a unique identifier for the current user across all trackers.

```java
NRVideo.setUserId("user-12345");
```

#### `NRVideo.setAttribute(trackerId, key, value)`
Set a custom attribute on a specific content tracker.

```java
NRVideo.setAttribute(trackerId, "contentSeries", "Season 1");
```

#### `NRVideo.setGlobalAttribute(key, value)`
Set a custom attribute across all active trackers.

```java
NRVideo.setGlobalAttribute("appVersion", "2.1.0");
```

#### `NRVideo.recordCustomEvent(attributes)`
Record a custom event across all trackers.

```java
Map<String, Object> attrs = new HashMap<>();
attrs.put("actionName", "VideoBookmarked");
attrs.put("bookmarkPosition", player.getCurrentPosition());
NRVideo.recordCustomEvent(attrs);
```

#### `NRVideo.recordCustomEvent(attributes, trackerId)`
Record a custom event on a specific tracker. Requires an `actionName` key.

```java
Map<String, Object> attrs = new HashMap<>();
attrs.put("actionName", "QualityChanged");
attrs.put("newQuality", "1080p");
NRVideo.recordCustomEvent(attrs, trackerId);
```

### `NRTrackerExoPlayer` (ExoPlayer Tracker)

#### `tracker.setDroppedFrameAggregationEnabled(enabled)`
Enable or disable dropped frame aggregation (5-second window, max 50 events).

```java
NRTrackerExoPlayer tracker =
        (NRTrackerExoPlayer) NewRelicVideoAgent.getInstance().getContentTracker(trackerId);
tracker.setDroppedFrameAggregationEnabled(true);
```

### Example: Complete Integration

```java
// --- MainActivity.java ---
NRVideoConfiguration config = new NRVideoConfiguration.Builder("YOUR_APPLICATION_TOKEN")
        .autoDetectPlatform(getApplicationContext())
        .withHarvestCycle(5 * 60)
        .enableLogging()           // Enable for development
        .enableQoeAggregate(true)  // Enable QoE metrics
        .build();

NRVideo.newBuilder(getApplicationContext())
        .withConfiguration(config)
        .build();

NRVideo.setUserId("user-12345");

// --- VideoPlayer.java ---
ExoPlayer player = new ExoPlayer.Builder(this).build();

Map<String, Object> customAttrs = new HashMap<>();
customAttrs.put("contentTitle", "Big Buck Bunny");
customAttrs.put("contentProvider", "studio-abc");

NRVideoPlayerConfiguration playerConfig =
        new NRVideoPlayerConfiguration("main-player", player, false, customAttrs);

Integer trackerId = NRVideo.addPlayer(playerConfig);

// Enable dropped frame tracking
NRTrackerExoPlayer tracker =
        (NRTrackerExoPlayer) NewRelicVideoAgent.getInstance().getContentTracker(trackerId);
tracker.setDroppedFrameAggregationEnabled(true);

// Cleanup
@Override
protected void onDestroy() {
    NRVideo.releaseTracker(trackerId);
    player.release();
    super.onDestroy();
}
```

## Data Model

The Video Agent captures comprehensive video analytics across four event types:

- **VideoAction** — Playback lifecycle events (request, start, pause, resume, buffer, seek, rendition changes, heartbeats)
- **VideoAdAction** — Ad lifecycle events (request, start, end, quartiles, breaks, clicks)
- **VideoErrorAction** — Error events (playback failures, ad errors, crashes)
- **VideoCustomAction** — Custom events defined by your application

**Full Documentation:** See [DATAMODEL.md](./DATAMODEL.md) for the complete event and attribute reference, and [Advanced Topics](./advanced.md) for creating custom trackers.

## Support

Should you need assistance with New Relic products, you are in good hands with several support channels.

If the issue has been confirmed as a bug or is a feature request, please file a GitHub issue.

### Support Channels

- [New Relic Documentation](https://docs.newrelic.com): Comprehensive guidance for using our platform
- [New Relic Community](https://discuss.newrelic.com): The best place to engage in troubleshooting questions
- [New Relic University](https://learn.newrelic.com): A range of online training for New Relic users of every level
- [New Relic Technical Support](https://support.newrelic.com): 24/7/365 ticketed support. Read more about our [Technical Support Offerings](https://docs.newrelic.com/docs/licenses/license-information/general-usage-licenses/support-plan)

## Contribute

We encourage your contributions to improve the Video Agent for Android! Keep in mind that when you submit your pull request, you'll need to sign the CLA via the click-through using CLA-Assistant. You only have to sign the CLA one time per project.

If you have any questions, or to execute our corporate CLA (which is required if your contribution is on behalf of a company), drop us an email at opensource@newrelic.com.

For more details on how best to contribute, see [CONTRIBUTING.md](./CONTRIBUTING.md).

### A note about vulnerabilities

As noted in our [security policy](../../security/policy), New Relic is committed to the privacy and security of our customers and their data. We believe that providing coordinated disclosure by security researchers and engaging with the security community are important means to achieve our security goals.

If you believe you have found a security vulnerability in this project or any of New Relic's products or websites, we welcome and greatly appreciate you reporting it to New Relic through our [bug bounty program](https://docs.newrelic.com/docs/security/security-privacy/information-security/report-security-vulnerabilities/).

If you would like to contribute to this project, review [these guidelines](./CONTRIBUTING.md).

To all contributors, we thank you! Without your contribution, this project would not be what it is today.

## License

The Video Agent for Android is licensed under the [Apache 2.0](http://apache.org/licenses/LICENSE-2.0.txt) License.