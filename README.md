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

## Pricing

Important: Ingesting video telemetry data via this video agent requires a subscription to an Advanced Compute. Contact your New Relic account representative for more details on pricing and entitlement.

## License

New Relic Video Agent is licensed under the [Apache 2.0](http://apache.org/licenses/LICENSE-2.0.txt) License.
 




