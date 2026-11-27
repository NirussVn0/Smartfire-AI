package com.example.service.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Displays a high-urgency floating emergency overlay card over other apps when
 * SYSTEM_ALERT_WINDOW is granted and a fire alarm triggers while in background.
 */
object EmergencyOverlayPopupManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentOverlayView: View? = null
    private var currentPulseAnimator: ObjectAnimator? = null
    private var currentWindowManager: WindowManager? = null

    fun canDrawOverlay(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun showOverlay(
        context: Context,
        deviceId: String,
        deviceName: String,
        gasRaw: Int,
        tempC: Float?,
        hasFlame: Boolean,
        onEvacuate: () -> Unit,
        onSilence: () -> Unit
    ) {
        val appContext = context.applicationContext ?: context
        runOnMainThread {
            if (!canDrawOverlay(appContext)) return@runOnMainThread

            val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@runOnMainThread

            dismissOverlayInternal(windowManager)

            val density = appContext.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()

            val container = FrameLayout(appContext).apply {
                setPadding(dp(12), dp(28), dp(12), dp(8))
            }

            val cardBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(3), Color.parseColor("#EF4444"))
            }

            val card = LinearLayout(appContext).apply {
                orientation = LinearLayout.VERTICAL
                background = cardBackground
                setPadding(dp(14), dp(12), dp(14), dp(12))
                elevation = dp(12).toFloat()
            }

            // Header Row: Pulsing Alert Indicator + Bold "BÁO CHÁY KHẨN CẤP"
            val headerRow = LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val badgeBackground = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#DC2626"))
            }
            val pulseDot = View(appContext).apply {
                background = badgeBackground
            }
            val dotParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                marginEnd = dp(8)
            }
            headerRow.addView(pulseDot, dotParams)

            val headerTitle = TextView(appContext).apply {
                text = "BÁO CHÁY KHẨN CẤP"
                setTextColor(Color.parseColor("#DC2626"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            headerRow.addView(
                headerTitle,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            card.addView(
                headerRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            // Device Name
            val deviceTitle = TextView(appContext).apply {
                text = deviceName.ifBlank { deviceId }
                setTextColor(Color.parseColor("#0F172A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val deviceParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            card.addView(deviceTitle, deviceParams)

            // Concise 1-line Telemetry Strip
            val tempText = tempC?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A"
            val flameText = if (hasFlame) "CÓ" else "Không"
            val telemetryBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#FEF2F2"))
                setStroke(dp(1), Color.parseColor("#FECACA"))
            }
            val telemetryText = TextView(appContext).apply {
                text = "Nhiệt: $tempText · Khói: $gasRaw ADC · Lửa: $flameText"
                setTextColor(Color.parseColor("#991B1B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                background = telemetryBg
                setPadding(dp(10), dp(6), dp(10), dp(6))
            }
            val telemetryParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            card.addView(telemetryText, telemetryParams)

            // Action Buttons Row
            val buttonRow = LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val buttonRowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }

            val evacuateBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#0F766E"))
            }
            val evacuateButton = Button(appContext).apply {
                text = "Mở & Thoát Hiểm"
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(typeface, Typeface.BOLD)
                background = evacuateBg
                minimumHeight = dp(42)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener {
                    dismissOverlayInternal(windowManager)
                    onEvacuate()
                }
            }
            val evacuateParams = LinearLayout.LayoutParams(0, dp(42), 1.3f).apply {
                marginEnd = dp(8)
            }
            buttonRow.addView(evacuateButton, evacuateParams)

            val silenceBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#EF4444"))
            }
            val silenceButton = Button(appContext).apply {
                text = "Im 30s"
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(typeface, Typeface.BOLD)
                background = silenceBg
                minimumHeight = dp(42)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener {
                    dismissOverlayInternal(windowManager)
                    onSilence()
                }
            }
            val silenceParams = LinearLayout.LayoutParams(0, dp(42), 0.9f)
            buttonRow.addView(silenceButton, silenceParams)

            card.addView(buttonRow, buttonRowParams)

            container.addView(
                card,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            var windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                @Suppress("DEPRECATION")
                windowFlags = windowFlags or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            }

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                windowFlags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }

            val animator = ObjectAnimator.ofFloat(pulseDot, View.ALPHA, 1.0f, 0.25f).apply {
                duration = 380L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
            }

            try {
                windowManager.addView(container, layoutParams)
                currentOverlayView = container
                currentWindowManager = windowManager
                currentPulseAnimator = animator
                animator.start()
            } catch (_: WindowManager.BadTokenException) {
                animator.cancel()
            } catch (_: SecurityException) {
                animator.cancel()
            } catch (_: Throwable) {
                animator.cancel()
            }
        }
    }

    fun dismissOverlay(context: Context) {
        val appContext = context.applicationContext ?: context
        runOnMainThread {
            val wm = (appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?: currentWindowManager
            dismissOverlayInternal(wm)
        }
    }

    private fun dismissOverlayInternal(windowManager: WindowManager?) {
        currentPulseAnimator?.cancel()
        currentPulseAnimator = null

        val viewToRemove = currentOverlayView
        currentOverlayView = null
        val targetWm = windowManager ?: currentWindowManager
        currentWindowManager = null

        if (viewToRemove != null && targetWm != null) {
            try {
                if (viewToRemove.isAttachedToWindow) {
                    targetWm.removeViewImmediate(viewToRemove)
                }
            } catch (_: Throwable) {
                try {
                    targetWm.removeView(viewToRemove)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
