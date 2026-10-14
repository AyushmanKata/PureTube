package com.puretube.app

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Local-only persistence for watch history and subscriptions (no Google account). */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("puretube", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadHistory(): List<HistoryEntry> = load("history", ListSerializer(HistoryEntry.serializer()))
    fun loadSubscriptions(): List<ChannelRef> = load("subs", ListSerializer(ChannelRef.serializer()))

    fun saveHistory(list: List<HistoryEntry>) =
        save("history", ListSerializer(HistoryEntry.serializer()), list.take(MAX_HISTORY))

    fun saveSubscriptions(list: List<ChannelRef>) =
        save("subs", ListSerializer(ChannelRef.serializer()), list)

    private fun <T> load(key: String, ser: kotlinx.serialization.KSerializer<List<T>>): List<T> =
        runCatching { prefs.getString(key, null)?.let { json.decodeFromString(ser, it) } }
            .getOrNull().orEmpty()

    private fun <T> save(key: String, ser: kotlinx.serialization.KSerializer<List<T>>, value: List<T>) {
        prefs.edit().putString(key, json.encodeToString(ser, value)).apply()
    }

    private companion object {
        const val MAX_HISTORY = 500
    }
}
