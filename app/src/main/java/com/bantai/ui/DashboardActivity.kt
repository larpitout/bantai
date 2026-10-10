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
import android.view.ViewOutlineProvider
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
import com.bantai.util.ContactHelper
import com.bantai.util.PermissionHelper
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Pangunahing screen ni Apo, may header at lumulutang na bottom navbar (disenyo: Dashboard ng bantai_ui).
 * Mga tab: Home (status, huling scan), Babala (insights + audit), Suriin (i-paste at suriin),
 * Settings (setup). Binubuksan ng Welcome pagkatapos ng onboarding, at ng "Posibleng scam" na notification.
 */
class DashboardActivity : AppCompatActivity() {

    private enum class Tab { HOME, ALERTS, CHECK, SETTINGS }

    private lateinit var content: LinearLayout
    private lateinit var statusPill: TextView
    private lateinit var navItems: Map<Tab, NavItem>
    private var tab = Tab.HOME
    private var checkJob: Job? = null

    /** Babalang binuksan sa Alerts tab (audit); null = listahan. */
    private var selected: ScamHistory.Item? = null
    /** Filter ng kasaysayan: null = lahat, false = SCAM, true = SUSPICIOUS (kakilala). */
    private var filter: Boolean? = null

    // Iisang wika sa buong app (pareho ng babala).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Bantai.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bantai.warmUp(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(104))
        }
        // BlurTarget: ang nilalamang puwedeng i-blur ng floating navbar.
        val target = BlurTarget(this).apply {
            setBackgroundColor(Color.parseColor("#F4F8FE"))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(header())
                addView(ScrollView(context).apply {
                    addView(content)
                    layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
                })
            })
        }
        val screen = FrameLayout(this).apply {
            addView(target)
            addView(bottomNav(target))
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

    // ---------- Floating bottom navbar ----------

    private class NavItem(val icon: ImageView, val title: TextView, val box: View)

    private fun bottomNav(target: BlurTarget): View {
        // Mga tab sa loob ng iisang floating bubble.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        fun item(icon: Int, label: Int, onClick: () -> Unit): NavItem {
            val img = ImageView(this).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(MUTED)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            }
            val txt = text(getString(label), 13f, bold = true, color = MUTED).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) }
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(56)
                isClickable = true
                contentDescription = getString(label)
                setOnClickListener { onClick() }
                // Capsule din ang ripple: i-clip sa parehong bubble shape ng active tab.
                background = rounded(Color.TRANSPARENT, 28, Color.TRANSPARENT)
                clipToOutline = true
                with(obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))) {
                    foreground = getDrawable(0)
                    recycle()
                }
                addView(img)
                addView(txt)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
            }
            bar.addView(box)
            return NavItem(img, txt, box)
        }
        navItems = mapOf(
            Tab.HOME to item(R.drawable.ic_nav_home, R.string.nav_home) { switchTo(Tab.HOME) },
            Tab.ALERTS to item(R.drawable.ic_nav_list, R.string.nav_alerts) { switchTo(Tab.ALERTS) },
            Tab.CHECK to item(R.drawable.ic_nav_search, R.string.nav_check) { switchTo(Tab.CHECK) },
            // Tab din ang Settings: hindi nawawala ang navbar.
            Tab.SETTINGS to item(R.drawable.ic_nav_tune, R.string.nav_settings) { switchTo(Tab.SETTINGS) },
        )
        // Isang bubble na lumulutang: full capsule shape, totoong blur ng content sa likod, may tint at anino.
        return BlurView(this).apply {
            addView(bar)
            background = rounded(
                Color.argb(100, 255, 255, 255), 36,
                Color.argb(120, 0xDC, 0xE6, 0xF4),
            )
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = dp(12).toFloat()
            setupWith(target).setBlurRadius(25f)
            layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                bottomMargin = dp(16)
            }
        }
    }

    private fun switchTo(t: Tab) {
        tab = t
        selected = null
        render()
    }

    private fun render() {
        updateHeader()
        navItems.forEach { (t, v) ->
            val on = t == tab
            val c = if (on) BLUE else MUTED
            v.icon.imageTintList = ColorStateList.valueOf(c)
            v.title.setTextColor(c)
            v.box.background = rounded(
                if (on) Color.argb(190, 0xE6, 0xF0, 0xFC) else Color.TRANSPARENT,
                28,
                if (on) ContextCompat.getColor(this, R.color.bantai_pale_border) else Color.TRANSPARENT,
            )
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
                addView(row(getString(R.string.field_verdict), verdictChip(true, last.suspicious)))
                addView(row(getString(R.string.field_from), text(last.sender, 15f, bold = true, color = INK)))
                addView(row(getString(R.string.field_reason), text(last.reason, 15f, color = INK)))
                addView(row(getString(R.string.field_kind), chip(last.kind, if (last.suspicious) AMBER_BG else ROSE_BG, if (last.suspicious) AMBER else ROSE)))
            }
        })
    }

    // ---------- Babala: insights + audit ----------

    private fun renderAlerts() {
        selected?.let { return renderAudit(it) }
        val items = ScamHistory.list(this)
        if (items.isEmpty()) {
            content.addView(card {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(24), dp(32), dp(24), dp(32))
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_check_circle)
                    imageTintList = ColorStateList.valueOf(GREEN)
                    layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
                })
                addView(text(getString(R.string.history_empty), 19f, bold = true, color = INK).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(14), 0, dp(6))
                })
                addView(text(getString(R.string.insight_none), 15f, color = MUTED).apply { gravity = Gravity.CENTER })
            })
            return
        }

        // Insights: mga bilang muna, saka ang payo sa sariling kahon.
        val lines = insights(items)
        content.addView(card {
            addView(label(getString(R.string.dashboard_insights)))
            lines.dropLast(1).forEach { addView(bullet(it)) }
            addView(text(lines.last(), 15f, bold = true, color = BLUE).apply {
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(
                    ContextCompat.getColor(context, R.color.bantai_pale), 14,
                    ContextCompat.getColor(context, R.color.bantai_pale_border),
                )
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
            })
        })

        val kinds = items.groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value }
        content.addView(card {
            addView(label(getString(R.string.dashboard_by_kind)))
            kinds.forEach { (kind, count) -> addView(kindBar(kind, count, kinds.first().value)) }
        })

        content.addView(sectionTitle(getString(R.string.dashboard_history)))
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(10), 0, 0)
            listOf(null to R.string.filter_all, false to R.string.verdict_scam, true to R.string.verdict_suspicious).forEach { (value, name) ->
                val on = filter == value
                addView(chip(getString(name), if (on) BLUE else SLATE_BG, if (on) Color.WHITE else MUTED).apply {
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8)
                    isClickable = true
                    setOnClickListener { filter = value; render() }
                })
            }
        })
        val fmt = DateFormat.getTimeInstance(DateFormat.SHORT, locale)
        // Nakagrupo ayon sa araw; isang row bawat mensahe, pindutin para makita ang buong audit.
        var day: String? = null
        for (item in items.filter { filter == null || it.suspicious == filter }) {
            val itemDay = dayLabel(item.time)
            if (itemDay != day) {
                day = itemDay
                content.addView(label(itemDay).apply { setPadding(dp(4), dp(16), 0, 0) })
            }
            content.addView(alertRow(item, fmt.format(Date(item.time))))
        }
    }

    private fun alertRow(item: ScamHistory.Item, time: String) = card {
        orientation = LinearLayout.HORIZONTAL
        isClickable = true
        setOnClickListener { selected = item; render() }
        val (riskBg, riskFg) = if (item.suspicious) AMBER_BG to AMBER else riskColors(item.score)
        addView(text("!", 20f, bold = true, color = riskFg).apply {
            gravity = Gravity.CENTER
            background = rounded(riskBg, 999, riskBg)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) }
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(text(item.sender, 16f, bold = true, color = INK).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                addView(text(time, 12f, color = MUTED).apply {
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) }
                })
            })
            addView(text(item.kind, 13f, bold = true, color = if (item.suspicious) AMBER else ROSE).apply { setPadding(0, dp(2), 0, 0) })
            addView(text(item.message, 15f, color = MUTED).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, dp(10))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(chip(if (item.suspicious) getString(R.string.verdict_suspicious) else riskLabel(item.score), riskBg, riskFg))
                if (item.aiModel != null) {
                    addView(chip(getString(R.string.chip_ai), Color.parseColor("#EFF6FF"), BLUE).apply { (layoutParams as LinearLayout.LayoutParams).marginStart = dp(6) })
                }
            })
        })
    }

    /** Buong audit ng isang babala. */
    private fun renderAudit(item: ScamHistory.Item) {
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        content.addView(text("← " + getString(R.string.btn_back), 16f, bold = true, color = BLUE).apply {
            setPadding(dp(4), dp(10), 0, dp(4))
            isClickable = true
            setOnClickListener { selected = null; render() }
        })
        val accent = if (item.suspicious) AMBER else RED
        content.addView(card(if (item.suspicious) AMBER_BG else ROSE_BG, if (item.suspicious) AMBER_BORDER else ROSE_BORDER) {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(chip(getString(if (item.suspicious) R.string.verdict_suspicious else R.string.verdict_scam), accent, Color.WHITE))
                addView(chip(riskLabel(item.score), Color.WHITE, riskColors(item.score).second).apply { (layoutParams as LinearLayout.LayoutParams).marginStart = dp(6) })
            })
            addView(text(item.kind, 21f, bold = true, color = INK).apply { setPadding(0, dp(10), 0, dp(2)) })
            addView(text("${item.sender}  ·  ${fmt.format(Date(item.time))}", 14f, color = MUTED))
        })
        content.addView(card {
            addView(label(getString(R.string.card_message)))
            addView(text(item.message, 17f, color = INK).apply {
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(SLATE_BG, 14, SLATE_BG)
            })
        })
        if (item.action.isNotBlank()) {
            content.addView(card {
                addView(label(getString(R.string.field_action)))
                addView(text(item.action, 17f, bold = true, color = accent))
            })
        }
        item.callNumber?.let { number ->
            content.addView(button(getString(R.string.btn_call_contact, item.sender), BLUE) {
                runCatching { startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", number, null))) }
            })
        }
        content.addView(card {
            addView(label(getString(R.string.why_flagged)))
            addView(text(item.reason, 16f, color = INK))
            item.signals.forEach {
                addView(chip(it, AMBER_BG, AMBER).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8) })
            }
            addView(View(context).apply {
                setBackgroundColor(BORDER)
                layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(8) }
            })
            addView(row(getString(R.string.field_decided_by), text(
                item.aiModel?.let { getString(R.string.decided_by_ai_model, it) } ?: getString(R.string.decided_by_rules), 15f, bold = true,
                color = if (item.aiModel != null) BLUE else INK,
            )))
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
        val hasAi = Bantai.modelName() != null
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
            rule.score >= 2 -> {
                val (rRes, aRes) = RuleFilter.instantWarningRes(rule)
                initialReason = loc.getString(rRes)
                initialAction = loc.getString(aRes)
                initialIsScam = true
            }
            rule.score == 1 && !hasAi -> {
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

        show(initialIsScam, initialReason, initialAction, null)
        if (!hasAi) return

        // AI sa phone: may allowAiDowngrade para sa Check tab upang marinig ang totoong desisyon ng AI.
        checkJob?.cancel()
        checkJob = Bantai.scope.launch {
            Bantai.scamPipeline(this@DashboardActivity, allowAiDowngrade = true).check(message).collect { c ->
                if (!c.isFinal) return@collect
                val ai = c.source == VerdictSource.LLM
                // Kung pekeng brand link (phishing), laging scam; kung hindi, sundin ang hatol ng AI
                var isScam = if (link?.kind == LinkChecker.Kind.FAKE_BRAND) true else c.verdict.isScam
                if (isScam && link?.kind != LinkChecker.Kind.FAKE_BRAND && c.verdict.action.isNotBlank() &&
                    (c.verdict.action.contains("no action", ignoreCase = true) || c.verdict.action.contains("wala", ignoreCase = true))) {
                    isScam = false
                }
                val ruleWarning = if (rule.score > 0) RuleFilter.instantWarningRes(rule) else null
                val finalReason = if (ai && c.verdict.reason.isNotBlank()) {
                    c.verdict.reason
                } else if (!isScam) {
                    loc.getString(R.string.warning_safe_reason)
                } else if (link?.kind == LinkChecker.Kind.FAKE_BRAND) {
                    loc.getString(R.string.warning_link_fake_brand, LinkChecker.displayBrand(link.brand!!))
                } else if (link != null) {
                    loc.getString(R.string.warning_link_risky_ending)
                } else if (ruleWarning != null) {
                    loc.getString(ruleWarning.first)
                } else {
                    initialReason
                }
                val finalAction = if (ai && c.verdict.action.isNotBlank()) {
                    c.verdict.action
                } else if (!isScam) {
                    loc.getString(R.string.warning_safe_action)
                } else if (link != null) {
                    loc.getString(R.string.warning_link_action)
                } else if (ruleWarning != null) {
                    loc.getString(ruleWarning.second)
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
            addView(permRow(R.string.setup_perm_contacts_title, ContactHelper.hasPermission(this@DashboardActivity)) {
                PermissionHelper.requestContacts(this@DashboardActivity)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHelper.onContactsResult(this, requestCode)
        render()
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

    /** (background, text) ng chip ayon sa taas ng panganib. */
    private fun riskColors(score: Int) = when {
        score >= 3 -> RED_BG to RED
        score == 2 -> AMBER_BG to AMBER
        else -> SLATE_BG to MUTED
    }

    private fun dayLabel(time: Long): String = when {
        ScamHistory.isToday(time) -> getString(R.string.day_today)
        ScamHistory.isToday(time + 24L * 60 * 60 * 1000) -> getString(R.string.day_yesterday)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(time))
    }

    private fun bullet(value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(5), 0, dp(5))
        addView(View(context).apply {
            background = rounded(BLUE, 999, BLUE)
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { topMargin = dp(8); marginEnd = dp(12) }
        })
        addView(text(value, 16f, color = INK).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
    }

    /** Uri ng scam, bilang, at bar na katumbas ng dami nito kumpara sa pinakamadalas. */
    private fun kindBar(kind: String, count: Int, max: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(text(kind, 16f, color = INK).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            addView(text(count.toString(), 16f, bold = true, color = ROSE).apply {
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) }
            })
        })
        addView(LinearLayout(context).apply {
            weightSum = max.toFloat()
            background = rounded(SLATE_BG, 999, SLATE_BG)
            layoutParams = LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(6) }
            addView(View(context).apply {
                background = rounded(ROSE, 999, ROSE)
                layoutParams = LinearLayout.LayoutParams(0, -1, count.toFloat())
            })
        })
    }

    private fun card(fill: Int = Color.WHITE, stroke: Int = BORDER, build: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = rounded(fill, 22, stroke)
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

    private fun verdictChip(scam: Boolean, suspicious: Boolean = false) = LinearLayout(this).apply {
        addView(
            if (suspicious) chip(getString(R.string.verdict_suspicious), AMBER, Color.WHITE)
            else chip(getString(if (scam) R.string.verdict_scam else R.string.verdict_safe), if (scam) RED else GREEN, Color.WHITE)
        )
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

    // Wika ng app (pareho ng mga string), hindi ng phone.
    private val locale get() = resources.configuration.locales[0]

    private companion object {
        val INK = Color.parseColor("#0F172A")
        val MUTED = Color.parseColor("#64748B")
        val SLATE_BG = Color.parseColor("#F1F5F9")
        val BORDER = Color.parseColor("#DCE6F4")
        val GREEN = Color.parseColor("#059669")
        val RED = Color.parseColor("#DC2626")
        val RED_BG = Color.parseColor("#FEF2F2")
        val ROSE = Color.parseColor("#BE123C")
        val ROSE_BG = Color.parseColor("#FFF1F2")
        val ROSE_BORDER = Color.parseColor("#FECDD3")
        val AMBER = Color.parseColor("#B45309")
        val AMBER_BG = Color.parseColor("#FFFBEB")
        val AMBER_BORDER = Color.parseColor("#FDE68A")
    }
}
