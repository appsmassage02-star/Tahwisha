package com.example;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import com.example.security.AppLockManager;

public class TahwishaApp extends Application implements Application.ActivityLifecycleCallbacks {

    private int startedActivities = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        startedActivities++;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (activity instanceof PinLockActivity || activity instanceof SplashActivity) {
            return;
        }
        if (AppLockManager.isPinEnabled(activity) && !AppLockManager.isSessionUnlocked()) {
            Intent intent = new Intent(activity, PinLockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0 && !activity.isChangingConfigurations()) {
            AppLockManager.lockSession();
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
