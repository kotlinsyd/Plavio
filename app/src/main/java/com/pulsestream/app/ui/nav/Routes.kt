package com.pulsestream.app.ui.nav

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val LIVE = "live"
    const val SEARCH = "search"
    const val WATCHLIST = "watchlist"
    const val ADDONS = "addons"
    const val HOME_CATALOGS = "home_catalogs"
    const val ACCOUNT = "account"
    const val PLAYBACK_SETTINGS = "playback_settings"
    const val APPEARANCE_SETTINGS = "appearance_settings"
    const val ABOUT = "about"
    const val LICENSES = "licenses"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val DETAIL = "detail/{addonId}/{type}/{metaId}"
    const val CATALOG = "catalog/{addonId}/{type}/{catalogId}"

    fun detail(addonId: String, type: String, metaId: String): String =
        "detail/${enc(addonId)}/${enc(type)}/${enc(metaId)}"

    fun catalog(addonId: String, type: String, catalogId: String): String =
        "catalog/${enc(addonId)}/${enc(type)}/${enc(catalogId)}"

    private fun enc(s: String): String =
        Base64.encodeToString(s.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun dec(s: String): String =
        String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
}
