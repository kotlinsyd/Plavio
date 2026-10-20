package com.pulsestream.app.data.update

import java.net.URI
import org.json.JSONObject

/** The same mobile-only contract is consumed by the bundled website. */
internal object MobileReleaseManifest {
    fun parse(json: JSONObject): UpdateInfo {
        val version = json.optString("version").removePrefix("v")
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Invalid stable mobile version" }
        require(json.optString("platform", "mobile") == "mobile") { "Not a mobile release" }
        require(json.optString("channel", "stable") == "stable") { "Not a stable release" }
        val url = json.optString("apkUrl")
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "gitlab.com" && uri.userInfo == null &&
            uri.port == -1 && uri.query == null && uri.fragment == null) { "Invalid mobile APK origin" }
        val expectedPath = "/api/v4/projects/87396069/packages/generic/plavio/$version/Plavio-v$version.apk"
        require(uri.rawPath == expectedPath) { "APK does not match stable mobile version" }
        return UpdateInfo(version, json.optString("notes").take(16_000), url, json.optBoolean("major", false))
    }
}
