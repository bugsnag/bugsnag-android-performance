package com.bugsnag.android.performance.minimalapp;

import android.app.Application;
import com.bugsnag.android.performance.BugsnagPerformance;

public class CustomApp extends Application {
    @Override
    public void onCreate() {
        BugsnagPerformance.start(this);
    }
}
