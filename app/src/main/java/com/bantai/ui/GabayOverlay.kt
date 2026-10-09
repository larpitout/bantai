package com.bantai.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bantai.Bantai
import com.bantai.R
import com.bantai.data.GuardianPreferences
import com.bantai.model.ScreenContext
import com.bantai.pipeline.GabaySource
import com.bantai.rules.GabayIntent
import com.bantai.rules.GabayRanker
import com.bantai.rules.PromptBuilder
import com.bantai.service.BantaiAccessibilityService
import com.bantai.service.Screen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Gabay: chathead (shield) → tanong ni Nanay → si Gemma ang pipili ng button → bibilugan ito + text + boses.
 * Hindi pumipindot si Bantai; si Nanay pa rin ang pipindot.
 * Usapan: tinatandaan ang layunin ni Nanay at kusang itinuturo ang susunod na hakbang pagkapindot niya,
 * hanggang makarating o sabihin niyang "okay na".
 * Lahat ng window ay TYPE_ACCESSIBILITY_OVERLAY mula sa accessibility service. Tawagin sa main thread.
 */
object GabayOverlay {

    private const val TAG = "BantaiGabay"

    private var service: BantaiAccessibilityService? = null
    private var bubble: View? = null
    private var panel: View? = null
    private var highlight: View? = null
    private var card: View? = null
    private var highlightShownAt = 0L
    private var highlightSignature: Pair<String, Set<String>>? = null
    private var job: Job? = null
    private val main = Handler(Looper.getMainLooper())

    /** Layunin ni Nanay sa kasalukuyang usapan (hal. "go to facebook"); null kapag walang usapan. */
    private var goal: String? = null

    /** Tawag/video/message sa isang tao (may alam na daan); null kapag ibang klase ng tanong. */
    private var task: GabayIntent? = null

    /** Naghihintay ng pangalan ("Sino po ang tatawagan?") para sa gawaing ito. */
    private var askingNameFor: GabayIntent? = null
    private var steps = 0
    private var scrolls = 0
    private var emptyReads = 0
    private const val MAX_STEPS = 8
    private const val MAX_SCROLLS = 6

    private val wm get() = service!!.getSystemService(WindowManager::class.java)

    fun showBubble(svc: BantaiAccessibilityService) {
        service = svc
        if (bubble != null) return
        val size = svc.dp(64)
        val view = ImageView(svc).apply {
            setImageResource(R.drawable.ic_shield)
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(svc.color(R.color.bantai_primary))
                setStroke(svc.dp(3), Color.WHITE)
            }
            elevation = svc.dp(8).toFloat()
            contentDescription = svc.t(R.string.gabay_bubble_desc)
        }
        val params = overlayParams(size, size, touchable = true).apply {
            gravity = Gravity.TOP or Gravity.START
            x = svc.resources.displayMetrics.widthPixels - size - svc.dp(8)
            y = svc.resources.displayMetrics.heightPixels / 3
        }
        view.setOnTouchListener(DragToMove(params) { togglePanel() })
        wm.addView(view, params)
        bubble = view
    }

    fun hideAll() {
        job?.cancel()

        listOf(bubble, panel, highlight, card).forEach { it?.let(::removeSafely) }
        bubble = null; panel = null; highlight = null; card = null
        service = null
    }

    /** Pumindot si Nanay o nagbago ang screen: tapos ang hakbang, ituro ang susunod kung may usapan pa. */
    fun onScreenChanged() {
        // Hindi pa nakikita ni Nanay ang bilog kung kalalabas lang; huwag agad alisin.
        if (highlight == null || SystemClock.uptimeMillis() - highlightShownAt < 1_000) return
        val svc = service ?: return
        // Ang orasan at widget sa Home ay nagpapadala rin ng event: ituloy lang kapag talagang nagbago ang screen.
        val now = signature(svc.readButtons())
        if (now == highlightSignature) return
        clearGuide()
        if (goal != null) step()
    }

    /** App + mga button, walang orasan: para malaman kung talagang lumipat ang screen. */
    private fun signature(screen: Screen) =
        screen.app to screen.buttons.map { it.label }.filterNot { CLOCK.matches(it) }.toSet()

    private fun togglePanel() {
        if (panel != null) return closePanel()
        goal = null // pinindot ni Nanay ang chathead: bagong usapan
        task = null
        askingNameFor = null
        openPanel(service?.t(R.string.gabay_panel_title) ?: return)
    }

    /** Panel na nagtatanong ng [prompt] (sinasabi rin nang malakas), tapos nakikinig. */
    private fun openPanel(prompt: String) {
        closePanel()
        clearGuide()
        val svc = service ?: return
        val status = TextView(svc).apply {
            setTextColor(svc.color(R.color.bantai_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, svc.dp(8), 0, 0)
        }
        val items = mutableListOf<View>(title(svc, prompt), status)
        items += option(svc, svc.t(R.string.gabay_speak), primary = true) { listen(status) }
        for ((labelRes, question) in OPTIONS) items += option(svc, svc.t(labelRes)) { startGoal(question) }
        items += option(svc, svc.t(R.string.gabay_close)) { closePanel() }
        val box = cardView(svc, items)
        val params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, touchable = true)
            .apply { gravity = Gravity.BOTTOM }
        wm.addView(box, params)
        panel = box

        // Two-way: magtatanong si Bantay, tapos makikinig pagkatapos niyang magsalita (para hindi niya marinig ang sarili).
        Bantai.speaker(svc).speak(prompt) {
            main.post { if (panel === box) listen(status) }
        }
    }

    private fun listen(status: TextView) {
        val svc = service ?: return
        Bantai.speaker(svc).stop()
        status.text = svc.t(R.string.gabay_listening)
        VoiceActivity.pending = VoiceActivity.Request(
            onPartial = { status.text = "\"$it\"" },
            onText = { text ->
                Log.e(TAG, "heard=$text")
                val waiting = askingNameFor
                when {
                    waiting != null -> {
                        askingNameFor = null
                        val name = text.trim().split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }
                        startTask(waiting.copy(person = name, needsTrustedContact = false), "${waiting.action} $name")
                    }
                    STOP_WORDS.containsMatchIn(text.lowercase()) -> endSession()
                    else -> startGoal(text)
                }
            },
            onFail = { error ->
                Log.e(TAG, "listen failed error=$error")
                status.text = svc.t(R.string.gabay_not_heard)
            },
        )
        svc.startActivity(
            Intent(svc, VoiceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
    }

    private fun closePanel() {
        VoiceActivity.pending = null
        panel?.let(::removeSafely)
        panel = null
    }

    /** Para sa pag-test gamit ang adb, walang boses (debug build lang ang tumatawag). */
    fun debugStart(goal: String) = startGoal(goal)

    private fun startGoal(text: String) {
        val parsed = GabayIntent.parse(text)
        Log.e(TAG, "intent=$parsed")
        val svc = service ?: return
        if (parsed.needsTrustedContact) {
            // "apo ko": ang Trusted Contact sa setup; kung wala, itanong ang pangalan.
            val saved = GuardianPreferences(svc).apoName.trim()
            if (saved.isEmpty()) {
                askingNameFor = parsed
                openPanel(svc.t(R.string.gabay_ask_name))
                return
            }
            return startTask(parsed.copy(person = saved, needsTrustedContact = false), text)
        }
        startTask(parsed, text)
    }

    private fun startTask(parsed: GabayIntent, text: String) {
        task = parsed.takeIf { it.person != null && it.action in PERSON_ACTIONS }
        goal = text
        steps = 0
        scrolls = 0
        emptyReads = 0
        step()
    }

    private fun endSession() {
        val svc = service ?: return
        goal = null
        task = null
        closePanel()
        clearGuide()
        Bantai.speaker(svc).speak(svc.t(R.string.gabay_bye))
    }

    /** Isang hakbang ng usapan: basahin ang screen, tapos ituro ang susunod na pipindutin. */
    private fun step() {
        val svc = service ?: return
        val g = goal ?: return
        closePanel()
        clearGuide()
        showCard(svc.t(R.string.gabay_thinking), offerCallApo = false)
        job?.cancel()
        job = Bantai.scope.launch {
            delay(700) // hayaang mawala muna ang panel at ang VoiceActivity bago basahin ang screen
            val screen = readSettled(svc)
            val (app, buttons) = screen.app to screen.buttons
            // Hindi pa nababasa ang screen (hal. nagpapalit pa ng app): subukan ulit, huwag agad ituro ang Home.
            if (app.isEmpty() || buttons.isEmpty()) {
                if (++emptyReads <= 3) { delay(800); step() } else showCard(svc.t(R.string.gabay_cannot_see), offerCallApo = true)
                return@launch
            }
            emptyReads = 0

            // Tawag/video/message sa isang tao: may alam na daan, hindi na hula.
            task?.let { t ->
                if (++steps > MAX_STEPS) {
                    clearGuide()
                    openPanel(svc.t(R.string.gabay_anything_else))
                } else {
                    guidePersonTask(svc, t, screen)
                }
                return@launch
            }

            // Nakarating na (hal. bukas na ang Facebook) o masyado nang mahaba: tanungin kung may iba pa.
            val reached = reachedApp(svc, app, g)
            if (reached != null || ++steps > MAX_STEPS) {
                clearGuide()
                val done = reached?.let { svc.t(R.string.gabay_reached, it) }.orEmpty()
                openPanel("$done ${svc.t(R.string.gabay_anything_else)}".trim())
                return@launch
            }

            // Parehong paglilinis ng label gaya ng nasa prompt, para mahanap ang button na pinili ni Gemma.
            val byLabel = LinkedHashMap<String, Rect>()
            for (b in buttons) PromptBuilder.gabayLabels(listOf(b.label)).firstOrNull()?.let { byLabel.putIfAbsent(it, b.bounds) }
            val labels = byLabel.keys.toList()

            // Pagbukas ng app na naka-install pero wala sa screen (nasa folder o ibang pahina): sa listahan ng lahat ng apps.
            val wantedApp = installedAppIn(svc, g)
            if (wantedApp != null && app == launcherPackage(svc)) {
                clearGuide()
                guideToApp(svc, wantedApp, byLabel, screen.scrollable)
                return@launch
            }
            val candidates = GabayRanker.candidates(g, labels)

            // Walang tugma pero may maisi-scroll pa: baka nasa ibaba lang.
            // (Hindi kapag app ang hinahanap: sa Home/listahan ng apps iyon, hindi sa loob ng ibang app.)
            if (wantedApp == null && candidates === labels && app != launcherPackage(svc) && screen.scrollable && scrolls < MAX_SCROLLS) {
                scrolls++
                clearGuide()
                showScrollHint(svc, labels.toSet())
                return@launch
            }

            // Walang kahit anong tugma sa screen ng isang app: ituro muna ang Home, doon nagsisimula ang lahat.
            if (candidates === labels && app != launcherPackage(svc)) {
                Log.e(TAG, "q=$g app=$app buttons=${labels.size} -> HOME")
                clearGuide()
                showHighlight(homeArea(svc), svc.t(R.string.gabay_go_home))
                Bantai.speaker(svc).speak(svc.t(R.string.gabay_go_home))
                return@launch
            }

            val apoName = GuardianPreferences(svc).apoName
            val result = Bantai.gabayPipeline(svc).guide(g, ScreenContext(app, candidates), apoName)
            val target = result.steps.firstOrNull()?.let { QUOTED.find(it)?.groupValues?.get(1) }?.let(byLabel::get)
            Log.e(TAG, "q=$g app=$app buttons=${labels.size} offered=${candidates.size} source=${result.source} step=${result.steps.firstOrNull()} found=${target != null}")

            clearGuide()
            // Lumipat na si Nanay sa ibang app habang nag-iisip si Gemma: luma na ang sagot; susunod na hakbang na lang.
            if (svc.readButtons().app != app) return@launch
            if (result.source == GabaySource.LLM && target != null) {
                showHighlight(target, result.steps.first())
                Bantai.speaker(svc).speak(result.steps.first())
            } else {
                goal = null
                showCard(result.spokenText, result.offerCallApo)
                Bantai.speaker(svc).speak(result.spokenText)
            }
        }
    }

    /**
     * Nasa Home o listahan ng apps, at alam kung aling app ang hinahanap:
     * nakikita → bilugan; may "Search for apps" → ituro ito at ang ita-type; wala pa → ituro ang pag-swipe pataas.
     * Walang "click" kapag nag-swipe o nag-type, kaya binabantayan ang screen hanggang magbago.
     */
    private suspend fun guideToApp(svc: BantaiAccessibilityService, appName: String, byLabel: Map<String, Rect>, scrollable: Boolean) {
        val visible = byLabel.entries.firstOrNull { isApp(it.key, appName) }
        val inAppList = byLabel.keys.any { it.contains("search", true) && it.contains("app", true) }
        Log.e(TAG, "app=$appName visible=${visible != null} inAppList=$inAppList labels=${byLabel.keys.take(40)}")
        when {
            visible != null -> {
                val text = svc.t(R.string.gabay_tap, appName)
                showHighlight(visible.value, text)
                Bantai.speaker(svc).speak(text)
                // pipindutin ni Nanay → onScreenChanged
            }
            // Nasa listahan na ng apps pero hindi pa kita: scroll lang (hindi pinapa-type si Nanay).
            inAppList && scrollable && scrolls < MAX_SCROLLS -> {
                scrolls++
                showScrollHint(svc, byLabel.keys)
            }
            inAppList -> {
                goal = null
                val text = svc.t(R.string.gabay_cannot_find, appName)
                showCard(text, offerCallApo = true)
                Bantai.speaker(svc).speak(text)
            }
            else -> {
                val text = svc.t(R.string.gabay_open_drawer)
                showHighlight(drawerArea(svc), text, swipeUp = true)
                Bantai.speaker(svc).speak(text)
                waitForChange(byLabel.keys)
            }
        }
    }

    /** Walang "click" kapag nag-swipe o nag-scroll si Nanay: bantayan ang screen hanggang magbago, saka ang susunod na hakbang. */
    private suspend fun waitForChange(before: Set<String>) {
        val svc = service ?: return
        val g = goal
        repeat(20) {
            delay(1_000)
            if (goal != g) return
            // Parehong paglilinis ng label gaya ng [before] (galing sa byLabel), kung hindi laging "nagbago".
            val now = readSettled(svc).buttons.mapNotNull { PromptBuilder.gabayLabels(listOf(it.label)).firstOrNull() }
                .filterNot { CLOCK.matches(it) }.toSet()
            if (now != before.filterNot { CLOCK.matches(it) }.toSet()) {
                clearGuide()
                step()
                return
            }
        }
    }

    /**
     * Binabasa ang screen hanggang hindi na nagbabago (hal. naglo-load pa ang listahan ng apps pagbukas).
     * Kapag binasa habang gumagalaw pa, mukhang wala ang app at napapa-scroll si Nanay nang walang dahilan.
     */
    private suspend fun readSettled(svc: BantaiAccessibilityService): Screen {
        var last = svc.readButtons()
        repeat(4) {
            delay(400)
            val now = svc.readButtons()
            if (now.app == last.app && now.buttons.map { it.label } == last.buttons.map { it.label }) return now
            last = now
        }
        return last
    }

    /** "Mag-scroll po pababa", tapos hintayin na magbago ang screen bago ang susunod na hakbang. */
    private suspend fun showScrollHint(svc: BantaiAccessibilityService, before: Set<String>) {
        val text = svc.t(R.string.gabay_scroll)
        Log.e(TAG, "q=$goal -> SCROLL $scrolls")
        showHighlight(drawerArea(svc), text, swipeUp = true)
        Bantai.speaker(svc).speak(text)
        waitForChange(before)
    }

    /** "Messenger, 7 new notifications" ay ang Messenger pa rin (may badge ang pangalan sa accessibility). */
    private fun isApp(label: String, appName: String): Boolean {
        val l = label.trim().lowercase()
        val n = appName.lowercase()
        return l == n || l.startsWith("$n,") || l.startsWith("$n ")
    }

    /** Pangalan ng naka-install na app na binanggit ni Nanay (hal. "go to facebook" → "Facebook"). */
    private fun installedAppIn(ctx: Context, goal: String): String? {
        val wanted = WORDS.findAll(goal.lowercase()).map { it.value }.filter { it.length > 2 && it !in ACTION_WORDS }.toSet()
        if (wanted.isEmpty()) return null
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return ctx.packageManager.queryIntentActivities(launcher, 0)
            .map { it.loadLabel(ctx.packageManager).toString() }
            .filter { label -> WORDS.findAll(label.lowercase()).any { it.value in wanted } }
            .minByOrNull { it.length } // "Facebook" bago "Facebook Lite"
    }

    /** Gitna ng screen, kung saan nagsisimula ang pag-swipe pataas para sa listahan ng lahat ng apps. */
    private fun drawerArea(ctx: Context): Rect {
        val w = ctx.resources.displayMetrics.widthPixels
        val h = ctx.resources.displayMetrics.heightPixels
        return Rect(w / 2 - ctx.dp(60), h * 6 / 10, w / 2 + ctx.dp(60), h * 6 / 10 + ctx.dp(120))
    }

    /**
     * Isang hakbang ng "tawagan / i-video call / i-message si X":
     * nasa tawag na → tapos; nasa chat ni X → ang call button; nakikita si X → siya; Phone app → Contacts;
     * may maisi-scroll → scroll; wala pa sa tamang app → papunta roon (Home → listahan ng apps).
     */
    private suspend fun guidePersonTask(svc: BantaiAccessibilityService, t: GabayIntent, screen: Screen) {
        val person = t.person ?: return
        clearGuide()
        val buttons = screen.buttons
        val target = targetAppLabel(svc, t)
        val inTarget = appLabel(svc, screen.app)?.equals(target, ignoreCase = true) == true
        fun find(words: List<String>, exclude: List<String> = emptyList()) = buttons.firstOrNull { b ->
            val l = b.label.lowercase()
            words.any { it in l } && exclude.none { it in l }
        }
        val firstName = person.lowercase().substringBefore(' ')
        val personButton = buttons.firstOrNull { firstName in it.label.lowercase() }
        Log.e(TAG, "task=${t.action} person=$person target=$target inTarget=$inTarget app=${screen.app} personVisible=${personButton != null}")

        // Nasa tawag na.
        if ((t.action == GabayIntent.Action.CALL || t.action == GabayIntent.Action.VIDEO_CALL) && find(END_CALL) != null) {
            task = null; goal = null
            val text = svc.t(R.string.gabay_calling, person)
            showCard(text, offerCallApo = false)
            Bantai.speaker(svc).speak(text)
            return
        }

        if (!inTarget) {
            // Papunta sa tamang app, gaya ng "Go to Messenger".
            if (screen.app == launcherPackage(svc)) {
                val byLabel = LinkedHashMap<String, Rect>()
                for (b in buttons) PromptBuilder.gabayLabels(listOf(b.label)).firstOrNull()?.let { byLabel.putIfAbsent(it, b.bounds) }
                guideToApp(svc, target, byLabel, screen.scrollable)
            } else {
                val text = svc.t(R.string.gabay_go_home)
                showHighlight(homeArea(svc), text)
                Bantai.speaker(svc).speak(text)
            }
            return
        }

        val callButton = when (t.action) {
            GabayIntent.Action.VIDEO_CALL -> find(listOf("video"))
            GabayIntent.Action.CALL -> find(listOf("audio call", "voice call", "call", "tawag"), exclude = listOf("video", "end", "log", "history"))
            else -> null
        }
        when {
            // Nasa chat/contact na ni X at may call button: iyon ang pipindutin.
            personButton != null && callButton != null -> say(svc, callButton.bounds, svc.t(R.string.gabay_tap, callButton.label))
            // Message: bukas na ang chat ni X.
            t.action == GabayIntent.Action.MESSAGE && personButton != null && find(listOf("message", "aa", "type")) != null -> {
                task = null; goal = null
                val text = svc.t(R.string.gabay_chat_open, person)
                showCard(text, offerCallApo = false)
                Bantai.speaker(svc).speak(text)
            }
            personButton != null -> say(svc, personButton.bounds, svc.t(R.string.gabay_tap, person))
            find(listOf("contacts", "contact", "people")) != null ->
                find(listOf("contacts", "contact", "people"))!!.let { say(svc, it.bounds, svc.t(R.string.gabay_tap, it.label)) }
            screen.scrollable && scrolls < MAX_SCROLLS -> {
                scrolls++
                showScrollHint(svc, buttons.mapNotNull { PromptBuilder.gabayLabels(listOf(it.label)).firstOrNull() }.toSet())
            }
            else -> {
                task = null; goal = null
                val text = svc.t(R.string.gabay_cannot_find_person, person)
                showCard(text, offerCallApo = true)
                Bantai.speaker(svc).speak(text)
            }
        }
    }

    private fun say(svc: BantaiAccessibilityService, rect: Rect, text: String) {
        showHighlight(rect, text)
        Bantai.speaker(svc).speak(text)
    }

    /** Kung saang app gagawin: ang binanggit ni Nanay, o ang karaniwan (tawag → Phone, video/message → Messenger). */
    private fun targetAppLabel(ctx: Context, t: GabayIntent): String {
        val wanted = t.app ?: if (t.action == GabayIntent.Action.CALL) "phone" else "messenger"
        return installedAppIn(ctx, "open $wanted") ?: wanted.replaceFirstChar(Char::uppercase)
    }

    private fun appLabel(ctx: Context, pkg: String): String? = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    /** Pangalan ng app kung ito na ang hinahanap ni Nanay (hal. "go to facebook" at bukas ang Facebook). */
    private fun reachedApp(ctx: Context, app: String, goal: String): String? {
        if (app.isEmpty() || app == launcherPackage(ctx)) return null
        val name = runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(app, 0)).toString()
        }.getOrNull() ?: return null
        val wanted = WORDS.findAll(goal.lowercase()).map { it.value }.filter { it.length > 2 && it !in ACTION_WORDS }.toSet()
        return name.takeIf { WORDS.findAll(it.lowercase()).any { w -> w.value in wanted } }
    }

    private fun launcherPackage(ctx: Context): String? =
        ctx.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )
            ?.activityInfo?.packageName

    /** Gitna-ibaba ng screen: ang Home button o ang swipe bar. Wala ito sa accessibility tree ng app. */
    private fun homeArea(ctx: Context): Rect {
        val w = ctx.resources.displayMetrics.widthPixels
        val h = ctx.resources.displayMetrics.heightPixels
        return Rect(w / 2 - ctx.dp(70), h - ctx.dp(40), w / 2 + ctx.dp(70), h - ctx.dp(4))
    }

    /** Bilog sa paligid ng button + text. Hindi nito sinasalo ang pindot, kaya ang button mismo ang mapipindot ni Nanay. */
    private fun showHighlight(target: Rect, text: String, swipeUp: Boolean = false) {
        val svc = service ?: return
        val ring = RingView(svc, target, text, swipeUp)
        val params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, touchable = false)
        wm.addView(ring, params)
        highlight = ring
        highlightShownAt = SystemClock.uptimeMillis()
        highlightSignature = signature(svc.readButtons())
    }

    /** Text lang (habang nag-iisip, o kapag walang maituro), may "Tawagan si Apo" kung kailangan. */
    private fun showCard(text: String, offerCallApo: Boolean) {
        val svc = service ?: return
        card?.let(::removeSafely)
        val items = mutableListOf<View>(title(svc, text))
        val apoPhone = GuardianPreferences(svc).apoPhone
        if (offerCallApo && apoPhone.isNotBlank()) {
            items += option(svc, svc.t(R.string.btn_call_apo), primary = true) {
                clearGuide()
                svc.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$apoPhone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        if (offerCallApo) items += option(svc, svc.t(R.string.btn_dismiss)) { clearGuide() }
        val box = cardView(svc, items)
        val params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, touchable = offerCallApo)
            .apply { gravity = Gravity.BOTTOM }
        wm.addView(box, params)
        card = box
    }

    private fun clearGuide() {
        highlight?.let(::removeSafely)
        card?.let(::removeSafely)
        highlight = null
        card = null
        service?.let { Bantai.speaker(it).stop() }
    }

    private fun removeSafely(view: View) {
        runCatching { (view as? RingView)?.stop(); wm.removeView(view) }
    }

    private fun overlayParams(w: Int, h: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    )

    /** Puting card na may asul na border; may panlabas na padding para hindi dumikit sa gilid ng screen. */
    private fun cardView(ctx: Context, children: List<View>): View {
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = ctx.dp(18)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = ctx.dp(20).toFloat()
                setStroke(ctx.dp(3), ctx.color(R.color.bantai_primary))
            }
            elevation = ctx.dp(12).toFloat()
            children.forEach(::addView)
        }
        return FrameLayout(ctx).apply {
            val m = ctx.dp(12)
            setPadding(m, m, m, m)
            addView(inner)
        }
    }

    private fun title(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = text
        setTextColor(ctx.color(R.color.bantai_text))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun option(ctx: Context, text: String, primary: Boolean = false, onClick: () -> Unit) = TextView(ctx).apply {
        this.text = text
        gravity = Gravity.CENTER
        minHeight = ctx.dp(56)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(if (primary) Color.WHITE else ctx.color(R.color.bantai_text))
        setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(10) }
        setOnClickListener { onClick() }
    }

    /** Kumikislap na bilog sa paligid ng button, at text sa itaas o ibaba (malayo sa button). */
    @SuppressLint("ViewConstructor")
    private class RingView(
        ctx: Context,
        private val target: Rect,
        private val text: String,
        /** Swipe/scroll: arrow na umaakyat sa halip na bilog, para hindi mapagkamalang button ang nasa ilalim. */
        private val swipeUp: Boolean,
    ) : View(ctx) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ctx.dp(6).toFloat()
            strokeCap = Paint.Cap.ROUND
            color = ctx.color(R.color.caution_border)
        }
        private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.color(R.color.bantai_primary) }
        private val label = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = ctx.sp(20f)
            isFakeBoldText = true
        }
        private val loc = IntArray(2)
        private val pulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { invalidate() }
            start()
        }

        fun stop() = pulse.cancel()

        override fun onDraw(canvas: Canvas) {
            getLocationOnScreen(loc)
            canvas.save()
            canvas.translate(-loc[0].toFloat(), -loc[1].toFloat()) // screen coords ang bounds ng button
            val t = pulse.animatedValue as Float
            if (swipeUp) {
                // Arrow mula ibaba pataas, umaakyat habang kumikislap.
                val x = target.exactCenterX()
                val lift = context.dp(60) * t
                val bottom = target.bottom + context.dp(80) - lift
                val top = target.top - lift
                val head = context.dp(28).toFloat()
                canvas.drawLine(x, bottom, x, top, ring)
                canvas.drawLine(x, top, x - head, top + head, ring)
                canvas.drawLine(x, top, x + head, top + head, ring)
            } else {
                val grow = context.dp(10) + context.dp(8) * t
                val r = RectF(target).apply { inset(-grow, -grow) }
                canvas.drawRoundRect(r, context.dp(18).toFloat(), context.dp(18).toFloat(), ring)
            }
            canvas.restore()

            // Text: sa ibaba kung nasa itaas ang button, at baligtad.
            val pad = context.dp(16)
            val layout = android.text.StaticLayout.Builder
                .obtain(text, 0, text.length, label, width - pad * 4).build()
            val boxH = layout.height + pad * 2
            val buttonTopOnView = target.top - loc[1]
            val top = if (buttonTopOnView > height / 2) pad * 3 else height - boxH - pad * 5
            val box = RectF(pad.toFloat(), top.toFloat(), (width - pad).toFloat(), (top + boxH).toFloat())
            canvas.drawRoundRect(box, pad.toFloat(), pad.toFloat(), bubble)
            canvas.save()
            canvas.translate(pad * 2f, top + pad.toFloat())
            layout.draw(canvas)
            canvas.restore()
        }
    }

    /** I-drag para ilipat ang chathead; maikling pindot = buksan ang panel. */
    private class DragToMove(private val params: WindowManager.LayoutParams, private val onTap: () -> Unit) : View.OnTouchListener {
        private var startX = 0; private var startY = 0; private var downX = 0f; private var downY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = params.x; startY = params.y; downX = e.rawX; downY = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - downX).toInt()
                    params.y = startY + (e.rawY - downY).toInt()
                    v.context.getSystemService(WindowManager::class.java).updateViewLayout(v, params)
                }
                MotionEvent.ACTION_UP -> if (abs(e.rawX - downX) < v.context.dp(10) && abs(e.rawY - downY) < v.context.dp(10)) onTap()
            }
            return true
        }
    }

    private val QUOTED = Regex("\"([^\"]+)\"")
    private val WORDS = Regex("[a-z0-9]+")
    private val PERSON_ACTIONS = setOf(GabayIntent.Action.CALL, GabayIntent.Action.VIDEO_CALL, GabayIntent.Action.MESSAGE)
    private val END_CALL = listOf("end call", "hang up", "end", "ibaba")
    private val CLOCK = Regex("\\d{1,2}:\\d{2}.*")
    private val STOP_WORDS = Regex("\\b(ok|okay|okey|tama na|salamat|thank|thanks|stop|done|enough|wala na)\\b")

    // Mga salitang gawain, hindi pangalan ng app: ang "call" ay hindi ibig sabihing nasa Phone app na ang dulo.
    private val ACTION_WORDS = setOf(
        "call", "phone", "tawag", "tawagan", "dial", "message", "messages", "mensahe", "chat", "text",
        "search", "hanap", "hanapin", "open", "the", "and", "app", "punta", "pumunta", "buksan", "gusto",
        "please", "paano", "how", "send", "picture", "photo", "video", "grandson", "granddaughter", "apo",
    )

    // Mga handang tanong. Ang tanong ay English dahil English ang prompt at karamihan ng button labels.
    private val OPTIONS = listOf(
        R.string.gabay_opt_message to "Start a new message or chat",
        R.string.gabay_opt_video to "Make a video call",
        R.string.gabay_opt_photo to "Send a photo or picture",
        R.string.gabay_opt_search to "Search for a person",
    )

    private fun Context.t(id: Int, vararg args: Any): String = Bantai.localized(this).getString(id, *args)
    private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun Context.sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
    private fun Context.color(res: Int) = ContextCompat.getColor(this, res)
}
