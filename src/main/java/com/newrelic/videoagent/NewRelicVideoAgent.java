package com.newrelic.videoagent;

import android.net.Uri;

import com.google.android.exoplayer2.*;
import com.newrelic.videoagent.tracker.ContentsTracker;
import com.newrelic.videoagent.tracker.ExoPlayer2.ExoPlayer2ContentsTracker;

import java.util.ArrayList;
import java.util.List;

public class NewRelicVideoAgent {

    static {
        System.loadLibrary("Core");
    }

    public static native void initJNIEnv();

    private static ContentsTracker tracker;

    // TODO: ads tracker

    public static void startWithPlayer(SimpleExoPlayer player, Uri videoUri) {
        NRLog.d("Starting Video Agent with player");

        initJNIEnv();

        tracker = new ExoPlayer2ContentsTracker(player);

        if (videoUri != null) {
            List<Uri> playlist = new ArrayList<>();
            playlist.add(videoUri);
            ((ExoPlayer2ContentsTracker) tracker).setSrc(playlist);
        }

        tracker.reset();
        tracker.setup();
    }

    public static void startWithPlayer(SimpleExoPlayer player, List<Uri> playlist) {
        NRLog.d("Starting Video Agent with player");

        initJNIEnv();

        tracker = new ExoPlayer2ContentsTracker(player);

        if (playlist != null && playlist.size() > 0) {
            ((ExoPlayer2ContentsTracker) tracker).setSrc(playlist);
        }

        tracker.reset();
        tracker.setup();
    }

    public static ContentsTracker getTracker() {
        return tracker;
    }
}
