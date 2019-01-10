package com.newrelic.videoagent;

import android.net.Uri;

import com.google.android.exoplayer2.*;
import com.newrelic.videoagent.basetrackers.AdsTracker;
import com.newrelic.videoagent.basetrackers.ContentsTracker;
import com.newrelic.videoagent.basetrackers.ExoPlayer2.ExoPlayer2ContentsTracker;

import java.util.ArrayList;
import java.util.List;

public class NewRelicVideoAgent {

    static {
        System.loadLibrary("Core");
    }

    public static native void initJNIEnv();

    private static ContentsTracker tracker;
    private static AdsTracker adsTracker;

    public static void startWithPlayer(SimpleExoPlayer player, Uri videoUri) {
        NRLog.d("Starting Video Agent with player and one video");

        initJNIEnv();

        tracker = new ExoPlayer2ContentsTracker(player);

        List<Uri> playlist = new ArrayList<>();
        playlist.add(videoUri);

        initializeTracker(playlist);
    }

    public static void startWithPlayer(SimpleExoPlayer player, List<Uri> playlist) {
        NRLog.d("Starting Video Agent with player and a playlist");

        initJNIEnv();

        tracker = new ExoPlayer2ContentsTracker(player);

        initializeTracker(playlist);
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
            ((ExoPlayer2ContentsTracker) tracker).setSrc(playlist);
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
}
