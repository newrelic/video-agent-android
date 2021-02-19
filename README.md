# New Relic Video Agent for Android


The New Relic Video Agent for Android contains multiple modules necessary to instrument video players and send data to New Relic.

## Modules

There are two modules available:

### NewRelicVideoCore

Contains all the base classes necessary to create trackers and send data to New Relic. It depends on the New Relic Agent.

### NRExoPlayerTracker

The video tracker for ExoPlayer2 player. It depends on NewRelicVideoCore.

## Build & Setup

### Install manually using AAR files

With this method the dependencies are not automatically installed, you have to manually install the [New Relic Android Agent](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/install-android-apps-gradle-android-studio) first.

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
}
```

### Install manually using source code

With this method the dependencies are not automatically installed, you have to manually install the [New Relic Android Agent](https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/install-android-apps-gradle-android-studio) first.

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
}
```

## Examples

The `app` folder contains a usage example that shows the basics of video tracking using ExoPlayer.