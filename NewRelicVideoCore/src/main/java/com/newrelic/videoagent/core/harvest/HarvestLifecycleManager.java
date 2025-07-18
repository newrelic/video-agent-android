package com.newrelic.videoagent.core.harvest;

import android.app.Application;
import android.app.Activity;
import android.os.Bundle;

public class HarvestLifecycleManager {
    private final HarvestManager harvestManager;
    private final int harvestCycleSeconds;
    private final Object lock = new Object();
    private int activityCount = 0;
    private boolean schedulerRunning = false;
    private Application.ActivityLifecycleCallbacks lifecycleCallbacks;

    public HarvestLifecycleManager(Application application, HarvestManager harvestManager, int harvestCycleSeconds) {
        this.harvestManager = harvestManager;
        this.harvestCycleSeconds = harvestCycleSeconds;
        registerLifecycleCallbacks(application);
    }

    private void registerLifecycleCallbacks(Application application) {
        lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity activity) {
                synchronized (lock) {
                    activityCount++;
                    if (activityCount == 1 && !schedulerRunning) {
                        harvestManager.startScheduler(harvestCycleSeconds);
                        schedulerRunning = true;
                    }
                }
            }
            @Override
            public void onActivityStopped(Activity activity) {
                synchronized (lock) {
                    activityCount--;
                    if (activityCount == 0 && schedulerRunning) {
                        harvestManager.shutdownScheduler();
                        schedulerRunning = false;
                    }
                }
            }
            public void onActivityResumed(Activity activity) {}
            public void onActivityPaused(Activity activity) {}
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            public void onActivityDestroyed(Activity activity) {}
        };
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
    }

    public void unregisterLifecycleCallbacks(Application application) {
        if (lifecycleCallbacks != null) {
            application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
            lifecycleCallbacks = null;
        }
    }
}
