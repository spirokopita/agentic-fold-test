package de.rwth_aachen.phyphox;

import android.app.Application;
import android.app.Activity;
import android.os.Bundle;

/** Embedded phyphox 1.2.1, with NERV presentation. Experiment code remains upstream. */
public class App extends Application {
    public PhyphoxExperiment experiment = null;
    private static String hostPackage;
    public static String hostPackage() { return hostPackage; }
    @Override public void onCreate() {
        super.onCreate();
        hostPackage = getPackageName();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, Bundle state) {}
            public void onActivityStarted(Activity a) {}
            public void onActivityResumed(Activity a) {
                if (a.getClass().getName().startsWith("de.rwth_aachen.phyphox."))
                    a.getWindow().getDecorView().post(() -> NervSkin.apply(a));
            }
            public void onActivityPaused(Activity a) {}
            public void onActivityStopped(Activity a) {}
            public void onActivitySaveInstanceState(Activity a, Bundle state) {}
            public void onActivityDestroyed(Activity a) {}
        });
    }
}