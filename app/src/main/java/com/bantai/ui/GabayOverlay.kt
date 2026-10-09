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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bantai.Bantai
import com.bantai.R
import com.bantai.data.GuardianPreferences
import com.bantai.model.ScreenContext
import com.bantai.pipeline.GabaySource
import com.bantai.rules.GabayRanker
import com.bantai.rules.PromptBuilder
import com.bantai.service.BantaiAccessibilityService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Gabay: chathead (🛡️) → tanong ni Nanay → si Gemma ang pipili ng button → bibilugan ito + text + boses.
 * Hindi pumipindot si Bantai; si Nanay pa rin ang pipindot. Isang hakbang bawat tanong.
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
    private var job: Job? = null
    private val main = Handler(Looper.getMainLooper())

    /** Huling tanong, para sa "Susunod na hakbang" pagkatapos pumindot ni Nanay. */
    private var lastQuestion: String? = null

    private val wm get() = service!!.getSystemService(WindowManager::class.java)

    fun showBubble(svc: BantaiAccessibilityService) {
        service = svc
        if (bubble != null) return
        val size = svc.dp(64)
        val view = TextView(svc).apply {
            text = "🛡️"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(svc.color(R.color.bantai_primary))
                setStroke(svc.dp(3), Color.WHITE)
            }
            elevation = svc.dp(8).toFloat()
            contentDescription = svc.getString(R.string.gabay_bubble_desc)
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

    /** Pumindot si Nanay o nagbago ang screen: alisin ang bilog; ang susunod na hakbang ay nasa panel na. */
    fun onScreenChanged() {
        // Hindi pa nakikita ni Nanay ang bilog kung kalalabas lang; huwag agad alisin.
        if (highlight == null || SystemClock.uptimeMillis() - highlightShownAt < 1_000) return
        clearGuide()
    }

    private fun togglePanel() {
        if (panel != null) return closePanel()
        clearGuide()
        val svc = service ?: return
        val status = TextView(svc).apply {
            setTextColor(svc.color(R.color.bantai_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, svc.dp(8), 0, 0)
        }
        val items = mutableListOf<View>(title(svc, svc.getString(R.string.gabay_panel_title)), status)
        items += option(svc, svc.getString(R.string.gabay_speak), primary = true) { listen(status) }
        lastQuestion?.let { q -> items += option(svc, svc.getString(R.string.gabay_next_step)) { ask(q) } }
        for ((labelRes, question) in OPTIONS) items += option(svc, svc.getString(labelRes)) { ask(question) }
        items += option(svc, svc.getString(R.string.gabay_close)) { closePanel() }
        val box = cardView(svc, items)
        val params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, touchable = true)
            .apply { gravity = Gravity.BOTTOM }
        wm.addView(box, params)
        panel = box

        // Two-way: magtatanong si Bantay, tapos makikinig pagkatapos niyang magsalita (para hindi niya marinig ang sarili).
        Bantai.speaker(svc).speak(svc.getString(R.string.gabay_panel_title)) {
            main.post { if (panel === box) listen(status) }
        }
    }

    private fun listen(status: TextView) {
        val svc = service ?: return
        Bantai.speaker(svc).stop()
        status.text = svc.getString(R.string.gabay_listening)
        VoiceActivity.pending = VoiceActivity.Request(
            onPartial = { status.text = "🎤 \"$it\"" },
            onText = { text ->
                Log.i(TAG, "heard chars=${text.length}")
                ask(text)
            },
            onFail = { error ->
                Log.e(TAG, "listen failed error=$error")
                status.text = svc.getString(R.string.gabay_not_heard)
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

    private fun ask(question: String) {
        val svc = service ?: return
        closePanel()
        clearGuide()
        lastQuestion = question
        showCard(svc.getString(R.string.gabay_thinking), offerCallApo = false)
        job?.cancel()
        job = Bantai.scope.launch {
            delay(700) // hayaang mawala muna ang panel at ang VoiceActivity bago basahin ang screen
            val (app, buttons) = svc.readButtons()
            // Parehong paglilinis ng label gaya ng nasa prompt, para mahanap ang button na pinili ni Gemma.
            val byLabel = LinkedHashMap<String, Rect>()
            for (b in buttons) PromptBuilder.gabayLabels(listOf(b.label)).firstOrNull()?.let { byLabel.putIfAbsent(it, b.bounds) }

            val apoName = GuardianPreferences(svc).apoName
            val result = Bantai.gabayPipeline(svc).guide(question, ScreenContext(app, GabayRanker.rank(question, byLabel.keys.toList())), apoName)
            val target = result.steps.firstOrNull()?.let { QUOTED.find(it)?.groupValues?.get(1) }?.let(byLabel::get)
            Log.e(TAG, "q=$question app=$app buttons=${byLabel.size} source=${result.source} step=${result.steps.firstOrNull()} found=${target != null}")

            clearGuide()
            // Lumipat na si Nanay sa ibang app habang nag-iisip si Gemma: luma na ang sagot, huwag ituro.
            if (svc.readButtons().first != app) return@launch
            if (result.source == GabaySource.LLM && target != null) {
                showHighlight(target, result.steps.first())
            } else {
                showCard(result.spokenText, result.offerCallApo)
            }
            Bantai.speaker(svc).speak(if (target != null) result.steps.first() else result.spokenText)
        }
    }

    /** Bilog sa paligid ng button + text. Hindi nito sinasalo ang pindot, kaya ang button mismo ang mapipindot ni Nanay. */
    private fun showHighlight(target: Rect, text: String) {
        val svc = service ?: return
        val ring = RingView(svc, target, text)
        val params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, touchable = false)
        wm.addView(ring, params)
        highlight = ring
        highlightShownAt = SystemClock.uptimeMillis()
    }

    /** Text lang (habang nag-iisip, o kapag walang maituro), may "Tawagan si Apo" kung kailangan. */
    private fun showCard(text: String, offerCallApo: Boolean) {
        val svc = service ?: return
        card?.let(::removeSafely)
        val items = mutableListOf<View>(title(svc, text))
        val apoPhone = GuardianPreferences(svc).apoPhone
        if (offerCallApo && apoPhone.isNotBlank()) {
            items += option(svc, svc.getString(R.string.btn_call_apo), primary = true) {
                clearGuide()
                svc.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$apoPhone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        if (offerCallApo) items += option(svc, svc.getString(R.string.btn_dismiss)) { clearGuide() }
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
    private class RingView(ctx: Context, private val target: Rect, private val text: String) : View(ctx) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ctx.dp(6).toFloat()
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
            val grow = context.dp(10) + context.dp(8) * (pulse.animatedValue as Float)
            val r = RectF(target).apply { inset(-grow, -grow) }
            canvas.drawRoundRect(r, context.dp(18).toFloat(), context.dp(18).toFloat(), ring)
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

    // Mga handang tanong. Ang tanong ay English dahil English ang prompt at karamihan ng button labels.
    private val OPTIONS = listOf(
        R.string.gabay_opt_message to "Start a new message or chat",
        R.string.gabay_opt_video to "Make a video call",
        R.string.gabay_opt_photo to "Send a photo or picture",
        R.string.gabay_opt_search to "Search for a person",
    )

    private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun Context.sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
    private fun Context.color(res: Int) = ContextCompat.getColor(this, res)
}
