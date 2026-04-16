[![Community Project header](https://github.com/newrelic/opensource-website/raw/master/src/images/categories/Community_Project.png)](https://opensource.newrelic.com/oss-category/#community-project)

# New Relic Video Agent for Android


The New Relic Video Agent for Android contains multiple modules necessary to instrument video players and send data to New Relic.

## Modules

There are three modules available:

### NewRelicVideoCore

Contains all the base classes necessary to create trackers and send data to New Relic. It depends on the New Relic Agent.

### NRExoPlayerTracker

The video tracker for ExoPlayer2 player. It depends on NewRelicVideoCore.

### NRIMATracker

The video tracker for Google IMA Ads library. It depends on NewRelicVideoCore.

## Installation

### Prerequisites

Install the [New Relic Android Agent](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/install-android-apps-gradle-android-studio), and any other needed dependency, like ExoPlayer or Google IMA.

### Install automatically using JitPack

Add the following line inside your root build.gradle:

```
allprojects {
    repositories {
        ...
        
        // Add this line at the end of your repositories
        maven { url 'https://jitpack.io' }
    }
}
```

And inside your app's build.gradle, add the following dependencies:

```
dependencies {
    ...

    // Add this to install the NewRelicVideoCore (required)
    implementation 'com.github.newrelic.video-agent-android:NewRelicVideoCore:v4.0.3'
    
    // Add this to install the ExoPlayer tracker
    implementation 'com.github.newrelic.video-agent-android:NRExoPlayerTracker:v4.0.3'
    
    // Add this to install the Google IMA library tracker
    implementation 'com.github.newrelic.video-agent-android:NRIMATracker:v4.0.3'
}
```

To install an specific version, replace the `master-SNAPSHOT` by a version tag.

### Install manually using AAR files

1. Clone this repo.
2. Open it with Android Studio.
3. Click on **View > Tool Windows > Gradle** to open the gradle tool window.
4. In there, unfold **NRVideoProject > Tasks > build** and double-click on **assemble**. This will generate the AAR libraries inside the module's folder **build > outputs > aar**.
5. In your project, click on **File > New > New Module**,  **Import .JAR/.AAR Package** and then **Next**.
6. Enter the location of the generated AAR file then click **Finish**.
7. Repeat steps 5 and 6 with all the AAR files you want to include.
8. In you app module's build.gradle file, add the following:

```
dependencies {
	...
	implementation project(":NewRelicVideoCore")
	implementation project(":NRExoPlayerTracker")
	implementation project(':NRIMATracker')
}
```

### Install manually using source code

1. Clone this repo.
2. In your project, click **File > New > Import Module**.
3. Enter the location of the library module directory (located in the repo you just cloned) then click **Finish**.
4. Repeat steps 2 and 3 with all the modules you want to include.
5. In you app module's build.gradle file, add the following:

```
dependencies {
	...
	implementation project(":NewRelicVideoCore")
	implementation project(":NRExoPlayerTracker")
	implementation project(':NRIMATracker')
}
```
## Usage

To start the video agent with ExoPlayer tracker only:

<details>
<summary>Java</summary>
<p>

```Java
// Step 1: Initialize the NRVideo at main activity. i.e MainActivity.java
NRVideoConfiguration config = new NRVideoConfiguration.Builder("application-token")
        .autoDetectPlatform(getApplicationContext())
        .withHarvestCycle(5*60) //This is in seconds, for ondemand video, please use minimum 5 minutes
        .build();
NRVideo.newBuilder(getApplicationContext()).withConfiguration(config).build();

//Step 2: Initialize the player(it could have n number of players in your application). i.e VideoPlayer.java
player = new ExoPlayer.Builder(this).build();
NRVideoPlayerConfiguration playerConfiguration = new NRVideoPlayerConfiguration("test-player", player, true, customAttr);
Integer trackerId = NRVideo.addPlayer(playerConfiguration);

Integer trackerId = NewRelicVideoAgent.getInstance().start(new NRTrackerExoPlayer(player));

// Step 4(Optional): Destroy the player on your player activity close. i.e VideoPlayer.java
// New Relic auto detects the player close, but its advisable to release tracker for a better control.
@Override
void onDestroy() {
    NRVideo.releaseTracker(trackerId);
    player.stop();
}
```

</p>
</details>

To enable Quality of Experience (QoE) aggregate metrics:

<details>
<summary>Java</summary>
<p>

```Java
// Initialize with QoE enabled
NRVideoConfiguration config = new NRVideoConfiguration.Builder("application-token")
        .autoDetectPlatform(getApplicationContext())
        .withHarvestCycle(5*60) // 5 minutes for on-demand content
        .enableQoeAggregate(true) // Enable QoE KPIs
        .withQoeAggregateIntervalMultiplier(1) // Send QoE every harvest cycle (default)
        .build();
NRVideo.newBuilder(getApplicationContext()).withConfiguration(config).build();

// QoE events (QOE_AGGREGATE) will now be generated containing:
// - startupTime, peakBitrate, averageBitrate, totalPlaytime
// - totalRebufferingTime, rebufferingRatio
// - hadStartupError, hadPlaybackError

// Query QoE data in NRDB:
// SELECT * FROM VideoAction WHERE actionName = 'QOE_AGGREGATE' SINCE 1 hour ago
```

</p>
</details>

To mask sensitive data using obfuscation rules:

<details>
<summary>Java</summary>
<p>

```java
import com.newrelic.videoagent.core.ObfuscationRule;
import java.util.Arrays;
import java.util.List;

// Define rules — each rule is a regex pattern and a replacement string.
// Rules are applied in order on every string attribute of every outgoing event.
List<ObfuscationRule> obfuscationRules = Arrays.asList(
    // Mask account IDs:  "account-83729"  →  "ACCOUNT_ID"
    new ObfuscationRule("account-\\d+", "ACCOUNT_ID"),

    // Mask auth tokens:  "token=abc123xyz"  →  "token=REDACTED"
    new ObfuscationRule("token=[^&\"]+", "token=REDACTED"),

    // Mask user path segments:  "/users/john_doe"  →  "/users/USER_ID"
    new ObfuscationRule("/users/[^\"/]+", "/users/USER_ID")
);

NRVideoConfiguration config = new NRVideoConfiguration.Builder("application-token")
        .autoDetectPlatform(getApplicationContext())
        .withHarvestCycle(5 * 60)
        .withObfuscationRules(obfuscationRules)
        .build();

NRVideo.newBuilder(getApplicationContext()).withConfiguration(config).build();
```

</p>
</details>

To start the video agent with ExoPlayer and IMA trackers:

<details>
<summary>Java</summary>
<p>

```Java

//Step 2: Initialize the player(it could have n number of players in your application). i.e VideoPlayer.java
//... more details on configuring the ads loader
player = new SimpleExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();

NRVideoPlayerConfiguration playerConfiguration = new NRVideoPlayerConfiguration("test-player-something-else", player, true, null);
Integer trackerId = NRVideo.addPlayer(playerConfiguration);
// Get the ads tracker
adTracker = (NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId);

//While building ads loader pass the listeners
ImaAdsLoader.Builder builder = new ImaAdsLoader.Builder(this);
builder.setAdErrorListener(adTracker.getAdErrorListener());
builder.setAdEventListener(adTracker.getAdEventListener());
```

</p>
</details>

### Configuration Fields
**`NRVideoConfiguration.java`**

- **`applicationToken`**  
  Unique identifier for your application\. Used for authentication and region detection\. Must be a non\-empty string\.

- **`harvestCycleSeconds`**  
  Interval \(in seconds\) between regular data harvests\. Controls how often data is sent to the New Relic\. Typical values range from 300 to 600 seconds\.

- **`liveHarvestCycleSeconds`**  
  Interval \(in seconds\) for live stream data harvests\. Used for real\-time or near\-real\-time data transmission\. Valid range is 30 to 60 seconds\.

- **`enableQoeAggregate`**
  Enables Quality of Experience \(QoE\) aggregate metrics collection and reporting\. When enabled, the agent generates `QOE_AGGREGATE` events containing KPIs such as startup time, average bitrate, rebuffering metrics, and playback failures\. **Default: `false`** \(QoE is disabled by default and must be explicitly enabled\)\.

- **`qoeAggregateIntervalMultiplier`**
  Controls the frequency of QoE aggregate event generation as a multiplier of the harvest cycle\. For example:
  - `1` = QoE generated every harvest cycle \(cycles 1, 2, 3, 4\.\.\.\)
  - `2` = QoE generated every other harvest cycle \(cycles 1, 3, 5, 7\.\.\.\)
  - `3` = QoE generated every third harvest cycle \(cycles 1, 4, 7, 10\.\.\.\)
  **Default: `1`** \(QoE generated every harvest cycle when enabled\)\. Only applies when `enableQoeAggregate` is `true`\.

- **`obfuscationRules`**
  A list of `ObfuscationRule` objects applied to every string attribute of every outgoing event immediately before HTTP transmission\. Each rule is a compiled regex pattern and a plain\-string replacement\. Rules run in the order they are declared — the output of one rule feeds into the next\. **Default: empty list** \(no obfuscation\)\.

### Quality of Experience (QoE) Metrics

When `enableQoeAggregate` is enabled, the agent generates `QOE_AGGREGATE` events containing the following KPIs:

| Attribute | Type | Description |
|-----------|------|-------------|
| `startupTime` | Long (ms) | Time from `CONTENT_REQUEST` to `CONTENT_START`, minus ad time and pause time |
| `peakBitrate` | Long (bps) | Highest observed bitrate during the session |
| `averageBitrate` | Long (bps) | Time-weighted average bitrate during active playback (excludes pause, buffer, seek time) |
| `totalPlaytime` | Long (ms) | Total content playtime excluding pause, buffer, and seek; computed in real-time at harvest |
| `totalRebufferingTime` | Long (ms) | Total duration of all rebuffering events (excludes initial buffer) |
| `rebufferingRatio` | Double (%) | Percentage of playtime spent rebuffering: `(totalRebufferingTime / totalPlaytime) * 100` |
| `hadStartupError` | Boolean | `true` if an error occurred before `CONTENT_START` |
| `hadPlaybackError` | Boolean | `true` if an error occurred after `CONTENT_START` |

#### How QoE Works

**Architecture**:

1. **Early Registration** - QoE provider registers at `CONTENT_REQUEST` (not `CONTENT_START`) to capture startup failures even if content never starts
2. **Harvest-Time Generation** - QoE events are generated at harvest time based on real-time metrics, not buffered with regular events
3. **Dirty Check** - QoE events are only sent when KPI values have changed since the last send, reducing unnecessary data transmission
4. **Independent Send** - QoE sends independently on qualified harvest cycles regardless of whether other VideoAction events are present
5. **Bitrate Timer** - Automatically pauses during non-play states (pause, buffer, seek) for accurate time-weighted average bitrate calculation
6. **Final QoE** - Built eagerly at `CONTENT_END` while tracker state is still valid, ensuring no data loss at session end

**Example Log Output:**
```
D/NRVideoTracker: QOE provider registered at CONTENT_REQUEST
D/NRVideoTracker: QOE_AGGREGATE generated for harvest cycle 1 (KPIs changed)
D/HarvestManager: QOE_AGGREGATE injected into harvest batch (cycle 1)
D/NRVideoTracker: QOE_AGGREGATE skipped for harvest cycle 2 (no KPI changes)
```

### Obfuscation Rules

Obfuscation rules let you mask sensitive data — user IDs, auth tokens, account numbers, PII in URLs — before events are transmitted to New Relic. Rules are applied at send time, so no sensitive data is written to the in-memory buffer, SQLite crash-recovery storage, or the dead-letter retry queue.

#### How it works

1. You define a list of `ObfuscationRule` objects, each with a regex pattern and a replacement string.
2. Pass the list to `.withObfuscationRules()` on the config builder.
3. Just before each HTTP transmission, `ObfuscationEngine` iterates every string attribute of every event in the batch and applies the rules in order.
4. Only string values are processed — integers, longs, booleans, and nulls are passed through unchanged.
5. The original event objects are never mutated, so failed batches can be retried cleanly without double-obfuscation.

#### `ObfuscationRule` constructor

```java
new ObfuscationRule(String pattern, String replacement)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `pattern` | `String` | A Java regex pattern string. Compiled eagerly — an invalid pattern throws `IllegalArgumentException` at construction time. |
| `replacement` | `String` | The string to substitute for each match. Use `""` to delete matches. `$` and `\` are treated as plain characters, not back-references. |

#### Common patterns

| What to mask | Pattern | Replacement |
|---|---|---|
| Numeric account IDs | `account-\\d+` | `ACCOUNT_ID` |
| Auth / bearer tokens | `token=[^&\"]+` | `token=REDACTED` |
| User path segments | `/users/[^\"/]+` | `/users/USER_ID` |
| Email addresses | `[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}` | `EMAIL_REDACTED` |
| UUIDs | `[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}` | `UUID_REDACTED` |

#### Rule ordering

Rules are applied left-to-right. The output of rule N becomes the input of rule N+1. Order matters when one rule's replacement could match a later rule's pattern.

```java
// Rule 1 turns "/john" into "/USER", then Rule 2 sees "/USER_profile" and masks it.
new ObfuscationRule("john", "USER"),
new ObfuscationRule("USER_profile", "PROFILE")
// Result: "/john_profile" → "/USER_profile" → "/PROFILE"
```

#### Edge cases

| Situation | Behaviour |
|-----------|-----------|
| No rules configured | No-op — zero overhead, original list returned as-is |
| Pattern matches nothing | Value is passed through unchanged |
| Empty replacement `""` | Matched content is deleted |
| `$` or `\` in replacement | Treated as plain characters (not regex back-references) |
| Integer / Long / Boolean value | Skipped — only `String` values are processed |
| `null` value | Skipped — no NullPointerException |
| Invalid regex pattern | `IllegalArgumentException` thrown at `new ObfuscationRule(...)` construction — fails fast at startup, not silently at harvest time |
| `null` replacement | `IllegalArgumentException` thrown at construction |
| HTTP send fails → dead-letter retry | Original (unobfuscated) events are retried; obfuscation is re-applied correctly on the retry pass |

**`NRVideoPlayerConfiguration.java`**

- **`playerName`**  
  Unique identifier for the video player\. Used to distinguish between multiple players in the same application\. Must be a non\-empty string\.
- **`player`**  
  The video player instance to be tracked\. Must implement the `Player` interface from ExoPlayer\.
- **`isAdEnabled`**  
  Indicates whether the video player has Ad compatibility\. Set to `true` for player having ads loader capability else `false`.

## Documentation

To generate the javadocs, open the project in Android Studio and then go to `Tools > Generate JavaDoc...`, select `Whole Project`, then select the `Output directory` and click `OK`.

For more detail on the Events and Data Model generated by Video Agent for Android and for advanced concepts such as creating custom trackers, reference the [Advanced Topics](advanced.md) manual.

## Examples

The `app` folder contains a usage example that shows the basics of video tracking using ExoPlayer.

## Testing

The `Test` folder contains the test apps.

## Support

New Relic has open-sourced this project. This project is provided AS-IS WITHOUT WARRANTY OR DEDICATED SUPPORT. Issues and contributions should be reported to the project here on GitHub.

We encourage you to bring your experiences and questions to the [Explorers Hub](https://discuss.newrelic.com) where our community members collaborate on solutions and new ideas.

## Contributing

We encourage your contributions to improve New Relic Video Agent! Keep in mind when you submit your pull request, you'll need to sign the CLA via the click-through using CLA-Assistant. You only have to sign the CLA one time per project. If you have any questions, or to execute our corporate CLA, required if your contribution is on behalf of a company, please drop us an email at opensource@newrelic.com.

**A note about vulnerabilities**

As noted in our [security policy](../../security/policy), New Relic is committed to the privacy and security of our customers and their data. We believe that providing coordinated disclosure by security researchers and engaging with the security community are important means to achieve our security goals.

If you believe you have found a security vulnerability in this project or any of New Relic's products or websites, we welcome and greatly appreciate you reporting it to New Relic through [HackerOne](https://hackerone.com/newrelic).

## License

New Relic Video Agent is licensed under the [Apache 2.0](http://apache.org/licenses/LICENSE-2.0.txt) License.
 
