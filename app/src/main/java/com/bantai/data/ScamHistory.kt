package com.bantai.data

import android.content.Context
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mga na-flag na mensahe at bilang ng nasuri, para sa dashboard ni Apo. Nasa phone lang.
 * Huling [MAX] na na-flag lang ang itinatabi.
 */
object ScamHistory {

    private const val PREFS = "bantai_scam_history"
    private const val KEY = "items"
    private const val KEY_SCANNED_TOTAL = "scanned_total"
    private const val KEY_SCANNED_DAY = "scanned_day"
    private const val KEY_SCANNED_TODAY = "scanned_today"
    private const val MAX = 50
    private const val MAX_SAFE = 20

    /** [kind] ay ang uri ng scam sa wika ng babala (hal. "Pekeng link"). */
    data class Item(
        val time: Long,
        val sender: String,
        val message: String,
        val reason: String,
        val kind: String,
        val score: Int,
        val action: String = "",
        val signals: List<String> = emptyList(),
        /** Pangalan ng AI model kung siya ang nagpasya; null kapag rules. */
        val aiModel: String? = null,
        /** Kakilalang humihingi ng pera (SUSPICIOUS), hindi SCAM. */
        val suspicious: Boolean = false,
        /** Numero ng kakilala, para sa "Tawagan". */
        val callNumber: String? = null,
        /** May senyales pero ligtas ayon sa huling hatol ("checked and cleared"). */
        val safe: Boolean = false,
    ) {
        /** Hatol para sa filter ng Alerts: SAFE > SUSPICIOUS > SCAM (isa lang totoo). */
        val level: com.bantai.model.Verdict
            get() = if (safe) com.bantai.model.Verdict.SAFE
                else if (suspicious) com.bantai.model.Verdict.SUSPICIOUS
                else com.bantai.model.Verdict.SCAM
    }

    fun add(
        context: Context, sender: String, message: String, reason: String, kind: String, score: Int,
        action: String = "", signals: List<String> = emptyList(), aiModel: String? = null,
        suspicious: Boolean = false, callNumber: String? = null, safe: Boolean = false,
    ) {
        val items = JSONArray().put(
            JSONObject().put("t", System.currentTimeMillis()).put("s", sender).put("m", message.take(160))
                .put("r", reason).put("k", kind).put("c", score)
                .put("a", action).put("g", JSONArray(signals)).put("ai", aiModel ?: "")
                .put("v", suspicious).put("p", callNumber ?: "").put("sf", safe)
        )
        val old = read(context)
        for (i in 0 until minOf(old.length(), MAX - 1)) items.put(old.get(i))
        // Safe ("checked and cleared") items: huling [MAX_SAFE] lang — hindi bumubuga ang kasaysayan.
        var safeKept = 0
        val trimmed = JSONArray()
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            if (o.optBoolean("sf") && ++safeKept > MAX_SAFE) continue
            trimmed.put(o)
        }
        prefs(context).edit().putString(KEY, trimmed.toString()).apply()
    }

    fun list(context: Context): List<Item> {
        val arr = read(context)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val g = o.optJSONArray("g")
            Item(
                o.getLong("t"), o.optString("s"), o.optString("m"), o.optString("r"), o.optString("k"), o.optInt("c"),
                o.optString("a"), (0 until (g?.length() ?: 0)).map { g!!.getString(it) }, o.optString("ai").ifBlank { null },
                o.optBoolean("v"), o.optString("p").ifBlank { null }, o.optBoolean("sf"),
            )
        }
    }

    /** Bawat mensaheng sinuri ni Bantai (scam man o hindi). */
    fun countScanned(context: Context) {
        val p = prefs(context)
        val today = dayKey(System.currentTimeMillis())
        val todayCount = if (p.getInt(KEY_SCANNED_DAY, -1) == today) p.getInt(KEY_SCANNED_TODAY, 0) else 0
        p.edit()
            .putInt(KEY_SCANNED_TOTAL, p.getInt(KEY_SCANNED_TOTAL, 0) + 1)
            .putInt(KEY_SCANNED_DAY, today)
            .putInt(KEY_SCANNED_TODAY, todayCount + 1)
            .apply()
    }

    fun scannedTotal(context: Context) = prefs(context).getInt(KEY_SCANNED_TOTAL, 0)

    fun scannedToday(context: Context): Int {
        val p = prefs(context)
        return if (p.getInt(KEY_SCANNED_DAY, -1) == dayKey(System.currentTimeMillis())) p.getInt(KEY_SCANNED_TODAY, 0) else 0
    }

    fun isToday(time: Long) = dayKey(time) == dayKey(System.currentTimeMillis())

    private fun dayKey(time: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private fun read(context: Context) =
        runCatching { JSONArray(prefs(context).getString(KEY, "[]")) }.getOrDefault(JSONArray())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
