package com.pulsestream.app.data

import android.content.Context

/**
 * Recent search queries, shown under the search field when it's empty.
 * Tiny SharedPreferences-backed store, most-recent-first, max 12 entries.
 */
object SearchHistory {
    private const val PREFS = "pulse_search"
    private const val KEY_HISTORY = "history"
    private const val MAX_ENTRIES = 12

    fun get(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null) ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }.take(MAX_ENTRIES)
    }

    fun add(context: Context, query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val updated = (listOf(q) + get(context).filter { !it.equals(q, ignoreCase = true) })
            .take(MAX_ENTRIES)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, updated.joinToString("\n"))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }
}
