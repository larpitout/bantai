package com.bantai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Mga na-flag na mensahe para makita ni Apo sa setup screen. Huling [MAX] lang, nasa phone lang. */
object ScamHistory {

    private const val PREFS = "bantai_scam_history"
    private const val KEY = "items"
    private const val MAX = 20

    data class Item(val time: Long, val sender: String, val message: String, val reason: String)

    fun add(context: Context, sender: String, message: String, reason: String) {
        val items = JSONArray().put(
            JSONObject().put("t", System.currentTimeMillis()).put("s", sender).put("m", message.take(160)).put("r", reason)
        )
        val old = read(context)
        for (i in 0 until minOf(old.length(), MAX - 1)) items.put(old.get(i))
        prefs(context).edit().putString(KEY, items.toString()).apply()
    }

    fun list(context: Context): List<Item> {
        val arr = read(context)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Item(o.getLong("t"), o.optString("s"), o.optString("m"), o.optString("r"))
        }
    }

    private fun read(context: Context) = runCatching { JSONArray(prefs(context).getString(KEY, "[]")) }.getOrDefault(JSONArray())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
