package com.pulsestream.app.data

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Optional telemetry is off until chosen on this installation; never exported or synced. */
object TelemetryConsent {
    private const val PREFS = "plavio_privacy"
    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean("optional_telemetry", false)

    fun apply(context: Context) {
        val value = enabled(context)
        // Optional SDK availability must never determine whether the app starts.
        // Firebase's provider initializes the default app before Application.onCreate.
        if (com.google.firebase.FirebaseApp.getApps(context).none {
                it.name == com.google.firebase.FirebaseApp.DEFAULT_APP_NAME }) return
        runCatching { FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(value) }
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(value)
            if (!value) FirebaseCrashlytics.getInstance().deleteUnsentReports()
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("optional_telemetry", value).commit()) { "Couldn't save privacy choice" }
        apply(context)
    }
}
