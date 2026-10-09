package com.bantai.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bantai.Bantai
import com.bantai.R
import com.bantai.data.GuardianPreferences
import com.bantai.data.ScamHistory
import com.bantai.pipeline.VerdictSource
import com.bantai.rules.LinkChecker
import com.bantai.rules.RuleFilter
import com.bantai.util.PermissionHelper
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Pangunahing screen ni Apo, may header at bottom navbar (disenyo: Dashboard ng bantai_ui).
 * Mga tab: Home (status, huling scan), Babala (insights + audit), Suriin (i-paste at suriin),
 * Settings (setup). Binubuksan ng Welcome pagkatapos ng onboarding, at ng "Posibleng scam" na notification.
 */
class DashboardActivity : AppCompatActivity() {

    private enum class Tab { HOME, ALERTS, CHECK, SETTINGS }

    private lateinit var content: LinearLayout
    private lateinit var statusPill: TextView
    private lateinit var navItems: Map<Tab, Pair<ImageView, TextView>>
    private var tab = Tab.HOME
    private var checkJob: Job? = null

    /** Babalang binuksan sa Alerts tab (audit); null = listahan. */
    private var selected: ScamHistory.Item? = null

    // Iisang wika sa buong app (pareho ng babala).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Bantai.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bantai.warmUp(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(24))
        }
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F8FE"))
            addView(header())
            addView(ScrollView(context).apply {
                addView(content)
                layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            })
            addView(bottomNav())
        }
        // Edge-to-edge (Android 15+): huwag matakpan ng status at navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(screen) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        setContentView(screen)

        // Back sa audit: balik sa listahan, hindi labas ng app.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selected != null) { selected = null; render() } else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---------- Header: logo · BANTAI · status ----------

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(12))
        addView(FrameLayout(context).apply {
            background = rounded(Color.WHITE, 14, BORDER)
            clipToOutline = true
            addView(ImageView(context).apply {
                setImageResource(R.drawable.bantai_logo)
                scaleType = ImageView.ScaleType.CENTER_CROP
            })
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        addView(text("BANTAI", 21f, bold = true, color = INK).apply {
            letterSpacing = 0.2f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) }
        })
        statusPill = text("", 13f, bold = true, color = GREEN).apply {
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(-2, -2)
        }
        addView(statusPill)
    }

    private fun updateHeader() {
        val on = GuardianPreferences(this).isProtectionEnabled
        statusPill.text = getString(if (on) R.string.status_protected else R.string.status_paused)
        statusPill.setTextColor(if (on) GREEN else MUTED)
        statusPill.background = rounded(
            Color.parseColor(if (on) "#ECFDF5" else "#F1F5F9"), 999,
            if (on) Color.parseColor("#A7F3D0") else BORDER,
        )
    }

    // ---------- Bottom navbar ----------

    private fun bottomNav(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat()
            setPadding(0, dp(6), 0, dp(6))
        }
        fun item(icon: Int, label: Int, onClick: () -> Unit): Pair<ImageView, TextView> {
            val img = ImageView(this).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(MUTED)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            }
            val txt = text(getString(label), 13f, bold = true, color = MUTED).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) }
            }
            bar.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(56)
                isClickable = true
                contentDescription = getString(label)
                setOnClickListener { onClick() }
                addView(img)
                addView(txt)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            return img to txt
        }
        navItems = mapOf(
            Tab.HOME to item(R.drawable.ic_nav_home, R.string.nav_home) { switchTo(Tab.HOME) },
            Tab.ALERTS to item(R.drawable.ic_nav_list, R.string.nav_alerts) { switchTo(Tab.ALERTS) },
            Tab.CHECK to item(R.drawable.ic_nav_search, R.string.nav_check) { switchTo(Tab.CHECK) },
            // Tab din ang Settings: hindi nawawala ang navbar.
            Tab.SETTINGS to item(R.drawable.ic_nav_tune, R.string.nav_settings) { switchTo(Tab.SETTINGS) },
        )
        return bar
    }

    private fun switchTo(t: Tab) {
        tab = t
        selected = null
        render()
    }

    private fun render() {
        updateHeader()
        navItems.forEach { (t, v) ->
            val c = if (t == tab) BLUE else MUTED
            v.first.imageTintList = ColorStateList.valueOf(c)
            v.second.setTextColor(c)
        }
        content.removeAllViews()
        when (tab) {
            Tab.HOME -> renderHome()
            Tab.ALERTS -> renderAlerts()
            Tab.CHECK -> renderCheck()
            Tab.SETTINGS -> renderSettings()
        }
    }

    // ---------- Home ----------

    private fun renderHome() {
        val prefs = GuardianPreferences(this)
        val items = ScamHistory.list(this)
        val on = prefs.isProtectionEnabled

        content.addView(card {
            addView(label(getString(R.string.card_status)))
            addView(text(getString(if (on) R.string.status_active else R.string.status_paused), 26f, bold = true, color = if (on) GREEN else MUTED))
            addView(text(Bantai.modelName()?.let { getString(R.string.status_ai_model, it) } ?: getString(R.string.status_rules_only), 14f, color = MUTED))
        })

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            addView(stat(ScamHistory.scannedToday(this@DashboardActivity), getString(R.string.dashboard_scanned_today)))
            addView(stat(items.count { ScamHistory.isToday(it.time) }, getString(R.string.dashboard_flagged_today)))
            addView(stat(items.size, getString(R.string.dashboard_flagged_total)))
        })

        content.addView(card {
            addView(label(getString(R.string.card_latest_scan)))
            val last = items.firstOrNull()
            if (last == null) {
                addView(text(getString(R.string.history_empty), 16f, color = MUTED))
            } else {
                addView(row(getString(R.string.field_verdict), verdictChip(true)))
                addView(row(getString(R.string.field_from), text(last.sender, 15f, bold = true, color = INK)))
                addView(row(getString(R.string.field_reason), text(last.reason, 15f, color = INK)))
                addView(row(getString(R.string.field_kind), chip(last.kind, ROSE_BG, ROSE)))
            }
        })

        content.addView(button(getString(R.string.btn_test_alert), RED) {
            val loc = Bantai.localized(this)
            val (reason, action) = RuleFilter.instantWarningRes(RuleFilter.score(SAMPLE_SCAM))
            ScamAlertOverlay.show(this, loc.getString(reason), loc.getString(action), message = SAMPLE_SCAM)
        })
    }

    // ---------- Babala: insights + audit ----------

    private fun renderAlerts() {
        selected?.let { return renderAudit(it) }
        val items = ScamHistory.list(this)
        content.addView(card {
            addView(label(getString(R.string.dashboard_insights)))
            addView(text(insights(items).joinToString("\n\n") { "• $it" }, 16f, color = INK))
        })
        if (items.isNotEmpty()) {
            content.addView(card {
                addView(label(getString(R.string.dashboard_by_kind)))
                items.groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value }.forEach {
                    addView(text("${it.value}×  ${it.key}", 16f, color = INK))
                }
            })
        }
        content.addView(sectionTitle(getString(R.string.dashboard_history)))
        if (items.isEmpty()) content.addView(card { addView(text(getString(R.string.history_empty), 16f, color = MUTED)) })
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        // Isang row bawat mensahe; pindutin para makita ang buong audit.
        for (item in items) {
            content.addView(card {
                isClickable = true
                setOnClickListener { selected = item; render() }
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(text(item.sender, 16f, bold = true, color = INK).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                    addView(text(fmt.format(Date(item.time)), 12f, color = MUTED).apply { layoutParams = LinearLayout.LayoutParams(-2, -2) })
                })
                addView(text(item.message, 15f, color = MUTED).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, dp(8))
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(chip(item.kind, ROSE_BG, ROSE))
                    addView(chip(riskLabel(item.score), AMBER_BG, AMBER).apply { (layoutParams as LinearLayout.LayoutParams).marginStart = dp(6) })
                    if (item.aiModel != null) {
                        addView(chip(getString(R.string.chip_ai), Color.parseColor("#EFF6FF"), BLUE).apply { (layoutParams as LinearLayout.LayoutParams).marginStart = dp(6) })
                    }
                })
            })
        }
    }

    /** Buong audit ng isang babala. */
    private fun renderAudit(item: ScamHistory.Item) {
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        content.addView(text("← " + getString(R.string.btn_back), 16f, bold = true, color = BLUE).apply {
            setPadding(dp(4), dp(10), 0, dp(4))
            isClickable = true
            setOnClickListener { selected = null; render() }
        })
        content.addView(card {
            addView(label(getString(R.string.card_message)))
            addView(text("\"${item.message}\"", 17f, color = INK))
            addView(text("${item.sender}  ·  ${fmt.format(Date(item.time))}", 13f, color = MUTED).apply { setPadding(0, dp(8), 0, 0) })
        })
        content.addView(card {
            addView(label(getString(R.string.card_audit)))
            addView(row(getString(R.string.field_verdict), verdictChip(true)))
            addView(row(getString(R.string.field_risk), chip(riskLabel(item.score), AMBER_BG, AMBER)))
            addView(row(getString(R.string.field_kind), chip(item.kind, ROSE_BG, ROSE)))
            addView(row(getString(R.string.field_decided_by), text(
                item.aiModel?.let { getString(R.string.decided_by_ai_model, it) } ?: getString(R.string.decided_by_rules), 15f, bold = true,
                color = if (item.aiModel != null) BLUE else INK,
            )))
            addView(row(getString(R.string.field_reason), text(item.reason, 15f, color = INK)))
            if (item.action.isNotBlank()) addView(row(getString(R.string.field_action), text(item.action, 15f, bold = true, color = RED)))
            if (item.signals.isNotEmpty()) addView(row(getString(R.string.field_signals), text(item.signals.joinToString("\n") { "• $it" }, 15f, color = INK)))
        })
    }

    // ---------- Suriin: i-paste ang text o link ----------

    private fun renderCheck() {
        val input = EditText(this).apply {
            hint = getString(R.string.check_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            gravity = Gravity.TOP
            background = rounded(Color.WHITE, 16, BORDER)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val result = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(card {
            addView(label(getString(R.string.check_title)))
            addView(text(getString(R.string.check_desc), 15f, color = MUTED).apply { setPadding(0, 0, 0, dp(10)) })
            addView(input)
        })
        content.addView(button(getString(R.string.btn_check), BLUE) {
            val msg = input.text.toString().trim()
            if (msg.isNotEmpty()) analyze(msg, result)
        })
        content.addView(result)
    }

    /** Rules at link check agad; saka ang AI sa phone (kung meron) bilang pangalawang tingin. */
    private fun analyze(message: String, out: LinearLayout) {
        val loc = Bantai.localized(this)
        val rule = RuleFilter.score(message)
        val link = LinkChecker.check(message)
        val initialReason: String
        val initialAction: String
        val initialIsScam: Boolean

        when {
            link?.kind == LinkChecker.Kind.FAKE_BRAND -> {
                initialReason = loc.getString(R.string.warning_link_fake_brand, LinkChecker.displayBrand(link.brand!!))
                initialAction = loc.getString(R.string.warning_link_action)
                initialIsScam = true
            }
            link != null -> {
                initialReason = loc.getString(R.string.warning_link_risky_ending)
                initialAction = loc.getString(R.string.warning_link_action)
                initialIsScam = true
            }
            rule.score > 0 -> {
                val (rRes, aRes) = RuleFilter.instantWarningRes(rule)
                initialReason = loc.getString(rRes)
                initialAction = loc.getString(aRes)
                initialIsScam = true
            }
            else -> {
                initialReason = loc.getString(R.string.warning_safe_reason)
                initialAction = loc.getString(R.string.warning_safe_action)
                initialIsScam = false
            }
        }

        fun show(isScam: Boolean, reasonText: String, actionText: String, note: String?) {
            out.removeAllViews()
            out.addView(card {
                addView(row(getString(R.string.field_verdict), verdictChip(isScam)))
                addView(row(getString(R.string.field_reason), text(reasonText, 15f, color = INK)))
                addView(row(getString(R.string.field_action), text(actionText, 15f, bold = true, color = if (isScam) RED else INK)))
                if (rule.signals.isNotEmpty()) {
                    addView(row(getString(R.string.field_signals), text(rule.signals.joinToString(", "), 14f, color = MUTED)))
                }
                note?.let { addView(text(it, 14f, bold = true, color = BLUE).apply { setPadding(0, dp(8), 0, 0) }) }
            })
        }

        val hasAi = Bantai.modelName() != null
        show(initialIsScam, initialReason, initialAction, if (hasAi) getString(R.string.check_ai_thinking) else null)
        if (!hasAi) return

        // AI sa phone: may allowAiDowngrade para sa Check tab upang marinig ang totoong desisyon ng AI.
        checkJob?.cancel()
        checkJob = Bantai.scope.launch {
            Bantai.scamPipeline(this@DashboardActivity, allowAiDowngrade = true).check(message).collect { c ->
                if (!c.isFinal) return@collect
                val ai = c.source == VerdictSource.LLM
                // Kung pekeng brand link (phishing), laging scam; kung hindi, sundin ang hatol ng AI
                val isScam = if (link?.kind == LinkChecker.Kind.FAKE_BRAND) true else c.verdict.isScam
                val finalReason = if (ai && c.verdict.reason.isNotBlank()) {
                    c.verdict.reason
                } else if (!isScam) {
                    loc.getString(R.string.warning_safe_reason)
                } else {
                    initialReason
                }
                val finalAction = if (ai && c.verdict.action.isNotBlank()) {
                    c.verdict.action
                } else if (!isScam) {
                    loc.getString(R.string.warning_safe_action)
                } else {
                    initialAction
                }
                show(isScam, finalReason, finalAction, getString(if (ai) R.string.check_ai_done else R.string.check_ai_unavailable))
            }
        }
    }

    // ---------- Settings (tab, hindi hiwalay na screen) ----------

    private fun renderSettings() {
        val prefs = GuardianPreferences(this)

        // Proteksyon
        content.addView(card {
            addView(label(getString(R.string.card_protection)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(text(getString(if (prefs.isProtectionEnabled) R.string.status_active else R.string.status_paused), 18f, bold = true,
                    color = if (prefs.isProtectionEnabled) GREEN else MUTED).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                addView(androidx.appcompat.widget.SwitchCompat(context).apply {
                    isChecked = prefs.isProtectionEnabled
                    setOnCheckedChangeListener { _, on -> prefs.isProtectionEnabled = on; render() }
                })
            })
        })

        // Mga permission: pindutin para buksan ang tamang settings ng phone
        content.addView(card {
            addView(label(getString(R.string.card_permissions)))
            val callOk = android.os.Build.VERSION.SDK_INT >= 29 &&
                getSystemService(android.app.role.RoleManager::class.java).isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)
            addView(permRow(R.string.setup_perm_notification_title, PermissionHelper.isNotificationAccessGranted(this@DashboardActivity)) {
                startActivity(PermissionHelper.getNotificationAccessSettingsIntent())
            })
            addView(permRow(R.string.setup_perm_accessibility_title, PermissionHelper.isAccessibilityServiceEnabled(this@DashboardActivity)) {
                startActivity(PermissionHelper.getAccessibilitySettingsIntent())
            })
            addView(permRow(R.string.setup_perm_overlay_title, ScamAlertOverlay.canShow(this@DashboardActivity)) {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            })
            addView(permRow(R.string.setup_perm_battery_title, PermissionHelper.isIgnoringBatteryOptimizations(this@DashboardActivity)) {
                startActivity(PermissionHelper.getBatteryOptimizationIntent(this@DashboardActivity))
            })
            addView(permRow(R.string.setup_perm_call_title, callOk) {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val roles = getSystemService(android.app.role.RoleManager::class.java)
                    @Suppress("DEPRECATION")
                    startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING), 2)
                }
            })
        })
    }

    /** Isang permission: pangalan at status; pindutin para buksan ang settings ng phone. */
    private fun permRow(title: Int, granted: Boolean, open: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(52)
        isClickable = true
        setOnClickListener { open() }
        addView(text(getString(title), 16f, bold = true, color = INK).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        addView(chip(getString(if (granted) R.string.perm_on else R.string.perm_off),
            if (granted) Color.parseColor("#ECFDF5") else ROSE_BG, if (granted) GREEN else ROSE))
    }

    // ---------- Insights ----------

    /** Binibilang mula sa mga na-flag sa phone (pinakamadalas na uri, hindi kilalang numero, 7 araw, payo). */
    private fun insights(items: List<ScamHistory.Item>): List<String> {
        if (items.isEmpty()) return listOf(getString(R.string.insight_none))
        val out = mutableListOf<String>()
        val top = items.groupingBy { it.kind }.eachCount().maxByOrNull { it.value }!!
        out += getString(R.string.insight_top_kind, top.key, top.value)
        val unknown = items.count { it.sender.any(Char::isDigit) }
        if (unknown > 0) out += getString(R.string.insight_unknown_numbers, unknown, items.size)
        out += getString(R.string.insight_week, items.count { System.currentTimeMillis() - it.time < 7L * 24 * 60 * 60 * 1000 })
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

    // ---------- Mga piraso ng UI ----------

    private fun riskLabel(score: Int) = getString(
        when { score >= 3 -> R.string.risk_high; score == 2 -> R.string.risk_medium; else -> R.string.risk_low }
    )

    private fun card(build: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = rounded(Color.WHITE, 22, BORDER)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        build()
    }

    private fun label(value: String) = text(value.uppercase(), 12f, bold = true, color = MUTED).apply {
        letterSpacing = 0.15f
        setPadding(0, 0, 0, dp(8))
    }

    private fun sectionTitle(value: String) = text(value, 18f, bold = true, color = INK).apply {
        setPadding(dp(4), dp(20), 0, 0)
    }

    private fun row(name: String, value: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(5), 0, dp(5))
        addView(text(name.uppercase(), 12f, bold = true, color = MUTED).apply {
            layoutParams = LinearLayout.LayoutParams(dp(84), -2)
        })
        value.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        addView(value)
    }

    private fun verdictChip(scam: Boolean) = LinearLayout(this).apply {
        addView(chip(getString(if (scam) R.string.verdict_scam else R.string.verdict_safe), if (scam) RED else GREEN, Color.WHITE))
    }

    private fun chip(value: String, bg: Int, fg: Int) = text(value, 13f, bold = true, color = fg).apply {
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = rounded(bg, 10, bg)
        layoutParams = LinearLayout.LayoutParams(-2, -2)
    }

    private fun stat(value: Int, label: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(14), dp(8), dp(14))
        background = rounded(Color.WHITE, 20, BORDER)
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
        addView(text(value.toString(), 26f, bold = true, color = BLUE).apply { gravity = Gravity.CENTER })
        addView(text(label, 12f, color = MUTED).apply { gravity = Gravity.CENTER })
    }

    private fun button(value: String, color: Int, onClick: () -> Unit) = text(value.uppercase(), 16f, bold = true, color = Color.WHITE).apply {
        letterSpacing = 0.12f
        gravity = Gravity.CENTER
        minHeight = dp(56)
        background = rounded(color, 22, color)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        setOnClickListener { onClick() }
    }

    private fun text(value: String, sp: Float, bold: Boolean = false, color: Int) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val BLUE get() = ContextCompat.getColor(this, R.color.bantai_primary)

    private companion object {
        val INK = Color.parseColor("#0F172A")
        val MUTED = Color.parseColor("#64748B")
        val BORDER = Color.parseColor("#DCE6F4")
        val GREEN = Color.parseColor("#059669")
        val RED = Color.parseColor("#DC2626")
        val ROSE = Color.parseColor("#BE123C")
        val ROSE_BG = Color.parseColor("#FFF1F2")
        val AMBER = Color.parseColor("#B45309")
        val AMBER_BG = Color.parseColor("#FFFBEB")
        const val SAMPLE_SCAM = "Ma si Junjun to bagong number ko padala ka 5k sa gcash emergency lang"
    }
}
