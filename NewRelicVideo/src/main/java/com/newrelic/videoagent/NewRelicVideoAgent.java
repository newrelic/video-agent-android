package com.newrelic.videoagent;

import android.net.Uri;
import com.newrelic.videoagent.basetrackers.AdsTracker;
import com.newrelic.videoagent.basetrackers.ContentsTracker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class NewRelicVideoAgent {

    static {
        System.loadLibrary("Core");
    }

    private static class TrackerContainer {
        public ContentsTracker contentsTracker;
        public AdsTracker adsTracker;
        public boolean timerIsActive;

        public TrackerContainer(ContentsTracker contentsTracker, AdsTracker adsTracker) {
            this.contentsTracker = contentsTracker;
            this.adsTracker = adsTracker;
            this.timerIsActive = true;
        }
    }

    private static HashMap<Integer, TrackerContainer> trackersTable = new HashMap<>();
    private static Integer lastTrackerID = 0;

    public static native void initJNIEnv();

    public static Integer start(Object player, Uri videoUri, Class c) {

        initJNIEnv();

        try {
            TrackerBuilder trackerBuilder = (TrackerBuilder) c.newInstance();
            trackerBuilder.startWithPlayer(player, videoUri);
            ContentsTracker contentsTracker = trackerBuilder.contents();
            Integer trackerID = createTracker(contentsTracker, null);

            List<Uri> playlist = new ArrayList<>();
            playlist.add(videoUri);
            initializeTracker(contentsTracker, null, playlist);

            return trackerID;
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static Integer start(Object player, List<Uri> playlist, Class c) {

        initJNIEnv();

        try {
            TrackerBuilder trackerBuilder = (TrackerBuilder) c.newInstance();
            trackerBuilder.startWithPlayer(player, playlist);
            ContentsTracker contentsTracker = trackerBuilder.contents();
            Integer trackerID = createTracker(contentsTracker, null);

            initializeTracker(contentsTracker, null, playlist);

            return trackerID;
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static Integer start(Object player, Class c) {

        initJNIEnv();

        try {
            TrackerBuilder trackerBuilder = (TrackerBuilder) c.newInstance();
            trackerBuilder.startWithPlayer(player);
            ContentsTracker contentsTracker = trackerBuilder.contents();
            Integer trackerID = createTracker(contentsTracker, null);

            initializeTracker(contentsTracker, null, null);

            return trackerID;
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static Integer startWithTracker(ContentsTracker contentsTracker, List<Uri> playlist) {
        NRLog.d("Starting Video Agent with ContentsTracker");

        initJNIEnv();

        Integer trackerID = createTracker(contentsTracker, null);

        initializeTracker(contentsTracker, null, playlist);

        return trackerID;
    }

    public static Integer startWithTracker(ContentsTracker contentsTracker, AdsTracker adsTracker, List<Uri> playlist) {
        NRLog.d("Starting Video Agent with ContentsTracker and AdsTracker");

        initJNIEnv();

        Integer trackerID = createTracker(contentsTracker, adsTracker);

        initializeTracker(contentsTracker, adsTracker, playlist);

        return trackerID;
    }

    public static void releaseTracker(Integer trackerID) {
        TrackerContainer tc = getTrackerContainer(trackerID);
        if (tc != null) {
            tc.contentsTracker = null;
            tc.adsTracker = null;
        }
        destroyTracker(trackerID);
    }

    public static ContentsTracker getContentsTracker(Integer trackerID) {
        if (trackersTable.containsKey(trackerID)) {
            return trackersTable.get(trackerID).contentsTracker;
        }
        else {
            return null;
        }
    }

    public static AdsTracker getAdsTracker(Integer trackerID) {
        if (trackersTable.containsKey(trackerID)) {
            return trackersTable.get(trackerID).adsTracker;
        }
        else {
            return null;
        }
    }

    private static TrackerContainer getTrackerContainer(Integer trackerID) {
        if (trackersTable.containsKey(trackerID)) {
            return trackersTable.get(trackerID);
        }
        else {
            return null;
        }
    }

    private static Integer createTracker(ContentsTracker contentsTracker, AdsTracker adsTracker) {
        lastTrackerID ++;
        trackersTable.put(lastTrackerID, new TrackerContainer(contentsTracker, adsTracker));
        return lastTrackerID;
    }

    private static Boolean destroyTracker(Integer trackerID) {
        if (trackersTable.containsKey(trackerID)) {
            trackersTable.remove(trackerID);
            return true;
        }
        else {
            return false;
        }
    }

    private static void initializeTracker(ContentsTracker contentsTracker, AdsTracker adsTracker, List<Uri> playlist) {

        if (contentsTracker != null) {
            if (playlist != null && playlist.size() > 0) {
                contentsTracker.setSrc(playlist);
            }
            contentsTracker.reset();
            contentsTracker.setup();
        }

        if (adsTracker != null) {
            adsTracker.reset();
            adsTracker.setup();
        }
    }
}
