package com.newrelic.videoagent;

import android.net.Uri;
import com.newrelic.videoagent.basetrackers.AdsTracker;
import com.newrelic.videoagent.basetrackers.ContentsTracker;
import com.newrelic.videoagent.jni.swig.TrackerCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class NewRelicVideoAgent {

    static {
        System.loadLibrary("Core");
    }

    public static class TrackerContainer {

        public enum TRACKER_TYPE {
            CONTENTS, ADS, UNKNOWN
        }

        public TrackerCore tracker;
        public TRACKER_TYPE type;
        public Long trackerPartner;

        public TrackerContainer(TrackerCore tracker, Long trackerPartner) {
            this.tracker = tracker;

            if (tracker instanceof ContentsTracker) {
                this.type = TRACKER_TYPE.CONTENTS;
            }
            else if (tracker instanceof AdsTracker) {
                this.type = TRACKER_TYPE.ADS;
            }
            else {
                this.type = TRACKER_TYPE.UNKNOWN;
            }

            this.trackerPartner = trackerPartner;
        }

        public TrackerContainer(TrackerCore tracker) {
            this(tracker, 0L);
        }
    }

    private static HashMap<Long, TrackerContainer> trackersTable = new HashMap<>();

    public static native void initJNIEnv();

    public static Long start(Object player, Uri videoUri, Class c) {

        initJNIEnv();

        try {
            TrackerBuilder trackerBuilder = (TrackerBuilder) c.newInstance();
            trackerBuilder.startWithPlayer(player, videoUri);
            ContentsTracker contentsTracker = trackerBuilder.contents();
            AdsTracker adsTracker = trackerBuilder.ads();
            Long trackerID = createTracker(contentsTracker, adsTracker);

            List<Uri> playlist = new ArrayList<>();
            playlist.add(videoUri);
            initializeTracker(contentsTracker, adsTracker, playlist);

            return trackerID;
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public static Long start(Object player, List<Uri> playlist, Class c) {

        initJNIEnv();

        try {
            TrackerBuilder trackerBuilder = (TrackerBuilder) c.newInstance();
            trackerBuilder.startWithPlayer(player, playlist);
            ContentsTracker contentsTracker = trackerBuilder.contents();
            AdsTracker adsTracker = trackerBuilder.ads();
            Long trackerID = createTracker(contentsTracker, adsTracker);

            initializeTracker(contentsTracker, adsTracker, playlist);

            return trackerID;
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public static Long start(Object player, Class c) {

        initJNIEnv();

        try {
            TrackerBuilder trackerBuilder = (TrackerBuilder) c.newInstance();
            trackerBuilder.startWithPlayer(player);
            ContentsTracker contentsTracker = trackerBuilder.contents();
            AdsTracker adsTracker = trackerBuilder.ads();
            Long trackerID = createTracker(contentsTracker, adsTracker);

            initializeTracker(contentsTracker, adsTracker, null);

            return trackerID;
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public static Long startWithTracker(ContentsTracker contentsTracker, List<Uri> playlist) {
        NRLog.d("Starting Video Agent with ContentsTracker");

        initJNIEnv();

        Long trackerID = createTracker(contentsTracker, null);

        initializeTracker(contentsTracker, null, playlist);

        return trackerID;
    }

    public static Long startWithTracker(ContentsTracker contentsTracker, AdsTracker adsTracker, List<Uri> playlist) {
        NRLog.d("Starting Video Agent with ContentsTracker and AdsTracker");

        initJNIEnv();

        Long trackerID = createTracker(contentsTracker, adsTracker);

        initializeTracker(contentsTracker, adsTracker, playlist);

        return trackerID;
    }

    public static void releaseTracker(Long trackerID) {
        TrackerContainer tc = getTrackerContainer(trackerID);

        if (tc != null) {
            Long partnerID = tc.trackerPartner;
            TrackerContainer tcPartner = getTrackerContainer(partnerID);

            if (tcPartner  != null) {
                tcPartner.tracker = null;
                tcPartner.trackerPartner = 0L;
                trackersTable.remove(partnerID);
            }

            tc.tracker = null;
            tc.trackerPartner = 0L;
            trackersTable.remove(trackerID);
        }
    }

    public static ContentsTracker getContentsTracker(Long trackerID) {
        if (trackersTable.containsKey(trackerID)) {
            return (ContentsTracker)trackersTable.get(trackerID).tracker;
        }
        else {
            return null;
        }
    }

    // The tracker ID we return when creating is the ContentsTracker, so we need to reference it and then get the partner
    public static AdsTracker getAdsTracker(Long trackerID) {
        if (trackersTable.containsKey(trackerID)) {
            Long adTrackerID = trackersTable.get(trackerID).trackerPartner;
            if (trackersTable.containsKey(adTrackerID)) {
                AdsTracker adsTracker = (AdsTracker)trackersTable.get(adTrackerID).tracker;
                return adsTracker;
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }

    public static HashMap<Long, TrackerContainer> getTrackersTable() {
        return trackersTable;
    }

    private static TrackerContainer getTrackerContainer(Long trackerID) {
        if (trackersTable.containsKey(trackerID)) {
            return trackersTable.get(trackerID);
        }
        else {
            return null;
        }
    }

    private static Long createTracker(ContentsTracker contentsTracker, AdsTracker adsTracker) {

        TrackerContainer contents = new TrackerContainer(contentsTracker);
        trackersTable.put(contentsTracker.getCppPointer(), contents);

        if (adsTracker != null) {
            TrackerContainer ads = new TrackerContainer(adsTracker, contentsTracker.getCppPointer());
            trackersTable.put(adsTracker.getCppPointer(), ads);

            contents.trackerPartner = adsTracker.getCppPointer();
        }

        return contentsTracker.getCppPointer();
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
