package com.bantai.ui

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bantai.Bantai
import com.bantai.R
import com.bantai.data.GuardianPreferences

/**
 * Welcome screen (unang bukas lang). Disenyo: "Welcome" ng bantai_ui.
 *
 * Kapag tapos na ang setup, diretso na sa [SetupActivity].
 */
class WelcomeActivity : AppCompatActivity() {

    private val animators = mutableListOf<Animator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (GuardianPreferences(this).isOnboardingCompleted) {
            openSetup()
            return
        }

        setContentView(R.layout.activity_welcome)
        Bantai.warmUp(this)

        drawBehindSystemBars()

        // XML na clipToOutline ay API 31+ pa; minSdk natin ay 26.
        findViewById<View>(R.id.logoFrame).clipToOutline = true

        findViewById<View>(R.id.btnGetStarted).setOnClickListener { openSetup() }
    }

    override fun onStart() {
        super.onStart()
        if (findViewById<View>(R.id.ringPing) != null) startAnimations()
    }

    override fun onStop() {
        animators.forEach { it.cancel() }
        animators.clear()
        super.onStop()
    }

    private fun openSetup() {
        startActivity(Intent(this, SetupActivity::class.java))
        finish()
    }

    /** Abot hanggang status bar at navigation bar ang gradient. */
    private fun drawBehindSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val content = findViewById<View>(R.id.welcomeContent)
        val baseTop = content.paddingTop
        val baseBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = baseTop + bars.top, bottom = baseBottom + bars.bottom)
            insets
        }
    }

    private fun startAnimations() {
        // Umaalong singsing palabas (katumbas ng "animate-ping")
        animators += ObjectAnimator.ofPropertyValuesHolder(
            findViewById<View>(R.id.ringPing),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.8f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.8f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.6f, 0f)
        ).apply {
            duration = 1600
            interpolator = DecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }

        animators += pulse(findViewById(R.id.ringPulse), 0.5f, 1000)
        animators += pulse(findViewById(R.id.glowTop), 0.5f, 1500)
        animators += pulse(findViewById(R.id.ivLogo), 0.85f, 2000)

        animators.forEach { it.start() }
    }

    private fun pulse(view: View, minAlpha: Float, halfCycleMs: Long): Animator =
        ObjectAnimator.ofFloat(view, View.ALPHA, 1f, minAlpha).apply {
            duration = halfCycleMs
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
}
