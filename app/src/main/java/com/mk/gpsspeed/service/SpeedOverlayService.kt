package com.mk.gpsspeed.service

import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.mk.gpsspeed.MainActivity
import com.mk.gpsspeed.data.TrackingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SpeedOverlayService : android.app.Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var speedView: TextView
    private lateinit var params: WindowManager.LayoutParams
    private var lastTapTimestamp = 0L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
        TrackingRepository.updateOverlayVisible(true)
        serviceScope.launch {
            TrackingRepository.uiState.collectLatest { state ->
                updateText(state.currentSpeedKmh.roundToInt())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        TrackingRepository.updateOverlayVisible(false)
        if (::speedView.isInitialized) {
            windowManager.removeView(speedView)
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay() {
        val density = resources.displayMetrics.density
        val radius = (18f * density)
        val stroke = (2f * density).toInt()

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(0xE6000000.toInt())
            setStroke(stroke, 0xFF00E676.toInt())
        }

        speedView = TextView(this).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setShadowLayer(10f, 0f, 0f, 0xCC000000.toInt())
            backgroundTintList = null
            setBackground(background)
            setPadding((14f * density).toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            text = "0\nkm/h"
            setOnClickListener {
                handleOverlayTap()
            }
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 180
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        speedView.setOnTouchListener { _: View, event: MotionEvent ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(speedView, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(speedView, params)
    }

    private fun updateText(speedKmh: Int) {
        speedView.text = "$speedKmh\nkm/h"
    }

    private fun handleOverlayTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTimestamp <= 400L) {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(launchIntent)
            lastTapTimestamp = 0L
        } else {
            lastTapTimestamp = now
        }
    }
}
