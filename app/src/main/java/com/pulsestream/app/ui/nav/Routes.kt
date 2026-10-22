package com.pulsestream.app.ui.nav

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LIVE = "live"
    const val SEARCH = "search"
    const val WATCHLIST = "watchlist"
    const val LISTS = "lists"
    const val LIST_DETAIL = "list_detail/{listId}"
    const val ADDONS = "addons"
    const val HOME_CATALOGS = "home_catalogs"
    const val ACCOUNT = "account"
    const val BACKUP = "backup"
    const val PLAYBACK_SETTINGS = "playback_settings"
    const val SUBTITLE_SETTINGS = "subtitle_settings"
    const val APPEARANCE_SETTINGS = "appearance_settings"
    const val STREAMS_SETTINGS = "streams_settings"
    const val INTEGRATIONS = "integrations"
    const val ABOUT = "about"
    const val FEEDBACK = "feedback"
    const val DOWNLOADS = "downloads"
    const val LICENSES = "licenses"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val TRAILER_PLAYER = "trailer_player"
    const val TV_ADDON_SETUP = "tv_addon_setup"
    const val DETAIL = "detail/{addonId}/{type}/{metaId}"
    const val CATALOG = "catalog/{addonId}/{type}/{catalogId}"
    const val ACTOR = "actor/{personId}"

    fun detail(addonId: String, type: String, metaId: String): String =
        "detail/${enc(addonId)}/${enc(type)}/${enc(metaId)}"

    fun catalog(addonId: String, type: String, catalogId: String): String =
        "catalog/${enc(addonId)}/${enc(type)}/${enc(catalogId)}"

    /** List ids are UUIDs (URL-safe), no encoding needed. */
    fun listDetail(listId: String): String = "list_detail/$listId"

    private fun enc(s: String): String =
        Base64.encodeToString(s.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun dec(s: String): String =
        String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
}
