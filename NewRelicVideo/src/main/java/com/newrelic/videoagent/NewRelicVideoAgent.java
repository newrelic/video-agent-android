package com.newrelic.videoagent;

import android.net.Uri;
import com.newrelic.videoagent.basetrackers.AdsTracker;
import com.newrelic.videoagent.basetrackers.ContentsTracker;
import java.util.ArrayList;
import java.util.List;

public class NewRelicVideoAgent {

    static {
        System.loadLibrary("Core");
    }

    public static native void initJNIEnv();

    private static ContentsTracker tracker;
    private static AdsTracker adsTracker;

    public static void start(Object player, Uri videoUri, Class c) {

        initJNIEnv();

        try {
            TrackerBuilder trackerBuilder = (TrackerBuilder) c.newInstance();
            trackerBuilder.startWithPlayer(player, videoUri);
            tracker = trackerBuilder.contents();

            List<Uri> playlist = new ArrayList<>();
            playlist.add(videoUri);
            initializeTracker(playlist);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void start(Object player, List<Uri> playlist, Class c) {

        initJNIEnv();

        try {
            TrackerBuilder trackerBuilder = (TrackerBuilder) c.newInstance();
            trackerBuilder.startWithPlayer(player, playlist);
            tracker = trackerBuilder.contents();

            initializeTracker(playlist);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void start(Object player, Class c) {

        initJNIEnv();

        try {
            TrackerBuilder trackerBuilder = (TrackerBuilder) c.newInstance();
            trackerBuilder.startWithPlayer(player);
            tracker = trackerBuilder.contents();

            initializeTracker(null);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void startWithTracker(ContentsTracker contentsTracker, List<Uri> playlist) {
        NRLog.d("Starting Video Agent with ContentsTracker");

        initJNIEnv();

        tracker = contentsTracker;

        initializeTracker(playlist);
    }

    public static void startWithTracker(ContentsTracker tracker1, AdsTracker tracker2, List<Uri> playlist) {
        NRLog.d("Starting Video Agent with ContentsTracker and AdsTracker");

        initJNIEnv();

        tracker = tracker1;
        adsTracker = tracker2;

        initializeTracker(playlist);
    }

    private static void initializeTracker(List<Uri> playlist) {
        if (playlist != null && playlist.size() > 0) {
            tracker.setSrc(playlist);
        }

        tracker.reset();
        tracker.setup();

        if (adsTracker != null) {
            adsTracker.reset();
            adsTracker.setup();
        }
    }

    public static ContentsTracker getTracker() {
        return tracker;
    }
    public static AdsTracker getAdsTracker() {
        return adsTracker;
    }
    public static void setTracker(ContentsTracker obj) {
        tracker = obj;
    }
    public static void setAdsTracker(AdsTracker obj) {
        adsTracker = obj;
    }
    public static void release() {
        setAdsTracker(null);
        setTracker(null);
    }
}
