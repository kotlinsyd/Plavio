package com.pulsestream.app.data

import android.content.Context
import com.pulsestream.app.BuildConfig
import io.sentry.android.core.SentryAndroid

/**
 * Sentry crash reporting (owner's order 2026-10-23; Sentry is not Google —
 * Firebase Crashlytics was removed under the no-Google order). Errors only:
 * no tracing, no profiling, no session replay, so the free plan's quota is
 * spent on crashes alone. The DSN is Sentry's client ingest key — public
 * by design, it can only SEND events to this project.
 *
 * ON by default; the Settings "Crash reports" toggle turns it off, which
 * closes the SDK for the rest of the process and prevents any init at the
 * next launch. Every call is failure-proof: a Sentry problem must never
 * break app startup or settings.
 */
object CrashReports {

    fun initIfEnabled(context: Context, settings: SettingsStore) {
        if (!settings.sentryEnabled) return
        init(context)
    }

    /** Applies a toggle change immediately (init now, or close until relaunch). */
    fun apply(context: Context, enabled: Boolean) {
        runCatching {
            if (enabled) init(context) else io.sentry.Sentry.close()
        }
    }

    private fun init(context: Context) {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) return
        runCatching {
            SentryAndroid.init(context) { options ->
                options.dsn = dsn
                options.isEnableUncaughtExceptionHandler = true
                // The native (NDK) crash handler stays OFF — Java-side
                // reporting only. In v0.12.23 the native part initialized
                // with the SDK at startup and the app died on open before
                // any report could be written or sent (the Sentry project
                // stayed empty) — a native init failure leaves no trace.
                options.isEnableNdk = false
                // Crashes only — keep the free plan's quota for errors.
                options.tracesSampleRate = 0.0
                options.isEnableAutoSessionTracking = false
                options.isAttachScreenshot = false
                options.isAttachViewHierarchy = false
            }
        }
    }
}
