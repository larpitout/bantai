package com.bantai.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bantai.Bantai
import com.bantai.R
import com.bantai.data.GuardianPreferences
import com.bantai.data.ScamHistory
import java.text.DateFormat
import java.util.Date

/**
 * Dashboard para kay Apo: proteksyon, bilang ng nasuri at na-flag, insights, at listahan ng mga babala.
 * Binubuksan ng Welcome screen pagkatapos ng onboarding, at ng "Posibleng scam" na notification.
 */
class DashboardActivity : AppCompatActivity() {

    // Iisang wika sa buong app: Tagalog kung walang pinili (pareho ng babala).
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.bantai.Bantai.localized(newBase))
    }

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad + dp(24), pad, pad)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(color(R.color.bantai_bg))
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        root.removeAllViews()
        val prefs = GuardianPreferences(this)
        val items = ScamHistory.list(this)
        val flaggedToday = items.count { ScamHistory.isToday(it.time) }

        root.addView(text(getString(R.string.dashboard_title), 28f, bold = true, color = R.color.bantai_text))
        root.addView(text(getString(R.string.app_tagline), 15f, color = R.color.bantai_text_muted))

        // Proteksyon
        val on = prefs.isProtectionEnabled
        root.addView(pill(getString(if (on) R.string.dashboard_protection_on else R.string.dashboard_protection_off), on))

        // Mga bilang
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
            addView(stat(ScamHistory.scannedToday(this@DashboardActivity), getString(R.string.dashboard_scanned_today)))
            addView(stat(flaggedToday, getString(R.string.dashboard_flagged_today)))
            addView(stat(items.size, getString(R.string.dashboard_flagged_total)))
        })

        // Insights
        root.addView(section(getString(insightsTitle())))
        root.addView(card(insights(items).joinToString("\n\n") { "• $it" }))

        // Mga uri ng scam
        if (items.isNotEmpty()) {
            root.addView(section(getString(R.string.dashboard_by_kind)))
            val byKind = items.groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value }
            root.addView(card(byKind.joinToString("\n") { "${it.value}×  ${it.key}" }))
        }

        // Kasaysayan (audit)
        root.addView(section(getString(R.string.dashboard_history)))
        if (items.isEmpty()) root.addView(card(getString(R.string.history_empty)))
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        for (item in items) {
            val risk = getString(
                when { item.score >= 3 -> R.string.risk_high; item.score == 2 -> R.string.risk_medium; else -> R.string.risk_low }
            )
            root.addView(card("${fmt.format(Date(item.time))} · ${item.sender}\n\"${item.message}\"\n\n${item.kind} · $risk\n→ ${item.reason}"))
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.dashboard_open_setup)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_btn_primary)
            minHeight = dp(56)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
            setOnClickListener { startActivity(Intent(this@DashboardActivity, SetupActivity::class.java)) }
        })
    }

    /** "AI Insights" lang kapag talagang tumatakbo si Gemma sa phone; kung hindi, "Insights". */
    private fun insightsTitle() =
        if (Bantai.hasRamForAi(this)) R.string.dashboard_ai_insights else R.string.dashboard_insights

    /**
     * Mga obserbasyon mula sa mga na-flag na mensahe sa phone na ito.
     * ponytail: binibilang lang (pinakamadalas na uri, hindi kilalang numero, pinakahuli); walang modelong sumusulat.
     */
    private fun insights(items: List<ScamHistory.Item>): List<String> {
        if (items.isEmpty()) return listOf(getString(R.string.insight_none))
        val out = mutableListOf<String>()
        val top = items.groupingBy { it.kind }.eachCount().maxByOrNull { it.value }!!
        out += getString(R.string.insight_top_kind, top.key, top.value)
        val unknown = items.count { it.sender.any(Char::isDigit) }
        if (unknown > 0) out += getString(R.string.insight_unknown_numbers, unknown, items.size)
        val week = items.count { System.currentTimeMillis() - it.time < 7L * 24 * 60 * 60 * 1000 }
        out += getString(R.string.insight_week, week)
        out += getString(
            when (top.key) {
                getString(R.string.kind_link) -> R.string.tip_link
                getString(R.string.kind_relative) -> R.string.tip_relative
                getString(R.string.kind_account) -> R.string.tip_account
                getString(R.string.kind_prize) -> R.string.tip_prize
                else -> R.string.tip_money
            }
        )
        return out
    }

    private fun section(title: String) = text(title, 18f, bold = true, color = R.color.bantai_text).apply {
        (layoutParams as LinearLayout.LayoutParams).topMargin = dp(24)
    }

    private fun card(body: String) = text(body, 16f, color = R.color.bantai_text).apply {
        val pad = dp(16)
        setPadding(pad, pad, pad, pad)
        setLineSpacing(0f, 1.15f)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), color(R.color.bantai_border))
        }
        (layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
    }

    private fun stat(value: Int, label: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val pad = dp(12)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), color(R.color.bantai_border))
        }
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
        addView(text(value.toString(), 28f, bold = true, color = R.color.bantai_primary).apply { gravity = Gravity.CENTER })
        addView(text(label, 13f, color = R.color.bantai_text_muted).apply { gravity = Gravity.CENTER })
    }

    private fun pill(label: String, on: Boolean) = text(label, 15f, bold = true, color = if (on) R.color.bantai_primary else R.color.caution_text).apply {
        setPadding(dp(14), dp(6), dp(14), dp(6))
        background = GradientDrawable().apply {
            setColor(color(if (on) R.color.bantai_pale else R.color.bantai_bg))
            cornerRadius = dp(999).toFloat()
            setStroke(dp(1), color(if (on) R.color.bantai_pale_border else R.color.caution_border))
        }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) }
    }

    private fun text(value: String, sp: Float, bold: Boolean = false, color: Int) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(res: Int) = ContextCompat.getColor(this, res)
}
