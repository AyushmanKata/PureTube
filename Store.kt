package com.puretube.app

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Local-only persistence for history, subscriptions and recent searches (no Google account). */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("puretube", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadHistory(): List<HistoryEntry> = load("history", ListSerializer(HistoryEntry.serializer()))
    fun loadSubscriptions(): List<ChannelRef> = load("subs", ListSerializer(ChannelRef.serializer()))
    fun loadRecent(): List<String> = load("recent", ListSerializer(String.serializer()))

    fun saveHistory(list: List<HistoryEntry>) =
        save("history", ListSerializer(HistoryEntry.serializer()), list.take(MAX_HISTORY))

    fun saveSubscriptions(list: List<ChannelRef>) =
        save("subs", ListSerializer(ChannelRef.serializer()), list)

    fun saveRecent(list: List<String>) =
        save("recent", ListSerializer(String.serializer()), list.take(MAX_RECENT))

    private fun <T> load(key: String, ser: KSerializer<List<T>>): List<T> =
        runCatching { prefs.getString(key, null)?.let { json.decodeFromString(ser, it) } }
            .getOrNull().orEmpty()

    private fun <T> save(key: String, ser: KSerializer<List<T>>, value: List<T>) {
        prefs.edit().putString(key, json.encodeToString(ser, value)).apply()
    }

    private companion object {
        const val MAX_HISTORY = 500
        const val MAX_RECENT = 20
    }
}
