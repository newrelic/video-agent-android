package com.newrelic.videoagent;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.newrelic.videoagent.jni.swig.TrackerCore;
import com.newrelic.videoagent.tracker.AdsTracker;
import com.newrelic.videoagent.tracker.ContentsTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    static {
        System.loadLibrary("Core");
    }

    public static native void initJNIEnv();

    class TestContentsTracker extends ContentsTracker {

        /*
        public TestContentsTracker() {
           super();
        }
        */

        @Override
        public void setup() {
            super.setup();
        }

        @Override
        public void reset() {
            super.reset();
        }
    }

    class TestAdsTracker extends AdsTracker {

        /*
        public TestAdsTracker() {
            super();
        }
        */

        @Override
        public void setup() {
            super.setup();
        }

        @Override
        public void reset() {
            super.reset();
        }
    }

    @Before
    public void setUp() {
        initJNIEnv();
    }

    @Test
    public void testContentsTracker() {
        TestContentsTracker tracker = new TestContentsTracker();
        testTracker(tracker);
    }

    @Test
    public void adsContentsTracker() {
        TestAdsTracker tracker = new TestAdsTracker();
        testTracker(tracker);
    }

    private void testTracker(TrackerCore tracker) {
        // TODO
        tracker.setup();
        tracker.reset();
    }
}
