package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.MainActivity
import com.example.R
import com.example.model.formatIntervalLabel
import com.example.model.formatTimeRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingBubbleService : Service() {

    companion object {
        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FloatingBubbleService::class.java)
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var bubbleContainer: LinearLayout? = null
    private var moonIcon: ImageView? = null
    private var popupCard: LinearLayout? = null
    private var timerText: TextView? = null
    private var countBadge: TextView? = null
    private var popupUrlText: TextView? = null
    private var popupStatusText: TextView? = null
    private var popupIntervalVal: TextView? = null
    private var pauseResumeBtn: Button? = null
    private var bubbleStatusIndicator: View? = null
    private var popupActivityIndicator: LinearLayout? = null
    private var popupActivityDot: View? = null
    private var popupActivityText: TextView? = null
    private var popupProgressBar: ProgressBar? = null
    private val intervalButtons = mutableMapOf<Int, TextView>()

    private lateinit var layoutParams: WindowManager.LayoutParams
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var isExpanded = false
    private var redBlinkJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingView()
        AutoRefreshManager.setFloatingWidgetActive(true)
        observeState()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingView() {
        val wm = windowManager ?: return

        rootView = FrameLayout(this)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dpToPx(70)
            y = dpToPx(180)
        }

        // Mini Compact Bubble (46dp x 46dp)
        bubbleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val size = dpToPx(48)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            background = createBubbleBackground(Color.parseColor("#34A853"))
            elevation = dpToPx(8).toFloat()

            // Mini status indicator dot at the top of the bubble
            bubbleStatusIndicator = View(context).apply {
                val dotSize = dpToPx(5)
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    bottomMargin = dpToPx(1)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#34A853"))
                }
            }
            addView(bubbleStatusIndicator)

            // Rotating Moon in the center of the mini floating icon
            moonIcon = ImageView(context).apply {
                setImageResource(R.drawable.ic_moon)
                layoutParams = LinearLayout.LayoutParams(dpToPx(22), dpToPx(22))
            }
            addView(moonIcon)
            startMoonRotation()

            timerText = TextView(context).apply {
                text = "2m"
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(timerText)
        }

        // Popup Quick Controls Card
        popupCard = createPopupCard()
        popupCard?.visibility = View.GONE

        rootView?.addView(popupCard)
        rootView?.addView(bubbleContainer)

        setupDragAndClick(bubbleContainer!!)

        wm.addView(rootView, layoutParams)
    }

    private fun startMoonRotation(durationMs: Long = 3500L) {
        val rotateAnim = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = durationMs
            repeatCount = Animation.INFINITE
            interpolator = LinearInterpolator()
        }
        moonIcon?.startAnimation(rotateAnim)
    }

    private fun createBubbleBackground(borderColor: Int, fillColor: Int = Color.parseColor("#161B22")): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            val strokeWidth = if (borderColor == Color.parseColor("#FF1744") || borderColor == Color.RED) dpToPx(3) else dpToPx(2)
            setStroke(strokeWidth, borderColor)
        }
    }

    private fun createPopupCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14).toFloat()
                setColor(Color.parseColor("#1C2128"))
                setStroke(dpToPx(1), Color.parseColor("#444C56"))
            }
            elevation = dpToPx(10).toFloat()
            layoutParams = FrameLayout.LayoutParams(dpToPx(220), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(52)
            }

            popupStatusText = TextView(context).apply {
                text = "Suru Auto: Active"
                setTextColor(Color.parseColor("#8AB4F8"))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(popupStatusText)

            popupUrlText = TextView(context).apply {
                text = "https://..."
                setTextColor(Color.WHITE)
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dpToPx(2), 0, dpToPx(4))
            }
            addView(popupUrlText)

            countBadge = TextView(context).apply {
                text = "Reloads: 0"
                setTextColor(Color.parseColor("#34A853"))
                textSize = 10f
            }
            addView(countBadge)

            // Visual Status Indicator Pill: Waiting to refresh vs Actively loading
            popupActivityIndicator = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpToPx(5)
                    bottomMargin = dpToPx(2)
                }
                layoutParams = params
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    setColor(Color.parseColor("#0E2A1A"))
                    setStroke(dpToPx(1), Color.parseColor("#238636"))
                }
            }

            popupActivityDot = View(context).apply {
                val size = dpToPx(7)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(6)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#34A853"))
                }
            }
            popupActivityIndicator?.addView(popupActivityDot)

            popupActivityText = TextView(context).apply {
                text = "⏳ Waiting to refresh"
                setTextColor(Color.parseColor("#34A853"))
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            popupActivityIndicator?.addView(popupActivityText)
            addView(popupActivityIndicator)

            popupProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = false
                max = 100
                progress = 100
                visibility = View.GONE
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(3)).apply {
                    bottomMargin = dpToPx(4)
                }
                layoutParams = params
            }
            addView(popupProgressBar)

            // 2m, 5m, 10m, 15m buttons inside floating popup
            val intervalRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(6), 0, dpToPx(4))
            }
            val intervalLabel = TextView(context).apply {
                text = "Interval: "
                setTextColor(Color.parseColor("#8B949E"))
                textSize = 10f
            }
            intervalRow.addView(intervalLabel)

            val minuteOptions = listOf(120 to "2m", 300 to "5m", 600 to "10m", 900 to "15m")
            intervalButtons.clear()
            minuteOptions.forEach { (seconds, label) ->
                val btn = TextView(context).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(4).toFloat()
                        setColor(if (AutoRefreshManager.state.value.intervalSeconds == seconds) Color.parseColor("#1F6FEB") else Color.parseColor("#30363D"))
                    }
                    setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3))
                    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dpToPx(4)
                    }
                    layoutParams = params
                    setOnClickListener {
                        AutoRefreshManager.setInterval(seconds)
                    }
                }
                intervalButtons[seconds] = btn
                intervalRow.addView(btn)
            }
            addView(intervalRow)

            // Button row 1
            val row1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(6), 0, 0)
            }

            pauseResumeBtn = Button(context).apply {
                text = "Pause"
                textSize = 10f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(6).toFloat()
                    setColor(Color.parseColor("#238636"))
                }
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply {
                    marginEnd = dpToPx(4)
                }
                setOnClickListener {
                    AutoRefreshManager.toggleRunning()
                }
            }
            row1.addView(pauseResumeBtn)

            val reloadBtn = Button(context).apply {
                text = "Reload"
                textSize = 10f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(6).toFloat()
                    setColor(Color.parseColor("#1F6FEB"))
                }
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f)
                setOnClickListener {
                    AutoRefreshManager.triggerReload()
                }
            }
            row1.addView(reloadBtn)
            addView(row1)

            // Button row 2: Open App & Close Widget
            val row2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(4), 0, 0)
            }

            val openAppBtn = Button(context).apply {
                text = "Open App"
                textSize = 10f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(6).toFloat()
                    setColor(Color.parseColor("#30363D"))
                }
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(30), 1f).apply {
                    marginEnd = dpToPx(4)
                }
                setOnClickListener {
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(appIntent)
                    togglePopup(false)
                }
            }
            row2.addView(openAppBtn)

            val closeWidgetBtn = Button(context).apply {
                text = "Close"
                textSize = 10f
                setTextColor(Color.parseColor("#FF7B72"))
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(6).toFloat()
                    setColor(Color.parseColor("#21262D"))
                }
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(30), 1f)
                setOnClickListener {
                    stopSelf()
                }
            }
            row2.addView(closeWidgetBtn)
            addView(row2)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndClick(bubble: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isClick = false
                    }
                    layoutParams.x = initialX + deltaX
                    layoutParams.y = initialY + deltaY
                    windowManager?.updateViewLayout(rootView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        togglePopup(!isExpanded)
                    } else {
                        // Snap smoothly to nearest screen edge (left or right)
                        val screenWidth = resources.displayMetrics.widthPixels
                        val targetX = if (layoutParams.x + bubble.width / 2 > screenWidth / 2) {
                            screenWidth - bubble.width - dpToPx(8)
                        } else {
                            dpToPx(8)
                        }
                        layoutParams.x = targetX
                        windowManager?.updateViewLayout(rootView, layoutParams)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun togglePopup(expand: Boolean) {
        isExpanded = expand
        popupCard?.visibility = if (expand) View.VISIBLE else View.GONE

        // Adjust popup position if bubble is near right edge so it doesn't clip
        val screenWidth = resources.displayMetrics.widthPixels
        val popupWidth = dpToPx(220)
        val params = popupCard?.layoutParams as? FrameLayout.LayoutParams
        if (params != null) {
            if (layoutParams.x + popupWidth > screenWidth) {
                params.gravity = Gravity.TOP or Gravity.END
                params.rightMargin = 0
                params.leftMargin = 0
            } else {
                params.gravity = Gravity.TOP or Gravity.START
                params.rightMargin = 0
                params.leftMargin = 0
            }
            popupCard?.layoutParams = params
        }
    }

    private fun setDotColor(view: View?, color: Int) {
        view?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun setPillStyle(layout: LinearLayout?, bgColor: Int, strokeColor: Int) {
        layout?.background = GradientDrawable().apply {
            cornerRadius = dpToPx(8).toFloat()
            setColor(bgColor)
            setStroke(dpToPx(1), strokeColor)
        }
    }

    private fun startRedBlinkEffect() {
        if (redBlinkJob?.isActive == true) return
        moonIcon?.setColorFilter(Color.parseColor("#FF1744"))
        setDotColor(bubbleStatusIndicator, Color.parseColor("#FF1744"))
        redBlinkJob = scope.launch {
            var toggle = false
            while (isActive) {
                toggle = !toggle
                val strokeColor = if (toggle) Color.parseColor("#FF1744") else Color.parseColor("#B71C1C")
                val fillColor = if (toggle) Color.parseColor("#660000") else Color.parseColor("#2B0000")
                bubbleContainer?.background = createBubbleBackground(strokeColor, fillColor)
                timerText?.text = "RELOAD"
                timerText?.setTextColor(if (toggle) Color.WHITE else Color.parseColor("#FFCDD2"))
                delay(200L) // Fast blink every 200ms
            }
        }
    }

    private fun stopRedBlinkEffect() {
        redBlinkJob?.cancel()
        redBlinkJob = null
        moonIcon?.clearColorFilter()
        timerText?.setTextColor(Color.WHITE)
    }

    private fun observeState() {
        scope.launch {
            AutoRefreshManager.state.collectLatest { state ->
                // Update interval buttons highlight
                intervalButtons.forEach { (sec, btn) ->
                    val isSelected = (state.intervalSeconds == sec)
                    btn.background = GradientDrawable().apply {
                        cornerRadius = dpToPx(4).toFloat()
                        setColor(if (isSelected) Color.parseColor("#1F6FEB") else Color.parseColor("#30363D"))
                    }
                }

                if (state.isReloadBlinking) {
                    startRedBlinkEffect()
                    popupStatusText?.text = "⚡ RELOADING NOW... ⚡"
                    popupStatusText?.setTextColor(Color.parseColor("#FF1744"))

                    // Visual indicator: Page reload blink
                    setPillStyle(popupActivityIndicator, Color.parseColor("#330A0A"), Color.parseColor("#FF1744"))
                    setDotColor(popupActivityDot, Color.parseColor("#FF1744"))
                    popupActivityText?.text = "⚡ Refreshing Page..."
                    popupActivityText?.setTextColor(Color.parseColor("#FF1744"))
                    popupProgressBar?.visibility = View.VISIBLE
                    popupProgressBar?.isIndeterminate = true
                } else {
                    stopRedBlinkEffect()

                    if (state.isLoading) {
                        // Actively Loading state
                        val cyan = Color.parseColor("#00E5FF")
                        bubbleContainer?.background = createBubbleBackground(cyan)
                        setDotColor(bubbleStatusIndicator, cyan)
                        timerText?.text = if (state.pageProgress in 1..99) "${state.pageProgress}%" else "LOAD"
                        timerText?.setTextColor(cyan)

                        popupStatusText?.text = "Suru Auto: Loading (${state.pageProgress}%)"
                        popupStatusText?.setTextColor(cyan)

                        setPillStyle(popupActivityIndicator, Color.parseColor("#05232A"), cyan)
                        setDotColor(popupActivityDot, cyan)
                        popupActivityText?.text = "⚡ Actively Loading (${state.pageProgress}%)"
                        popupActivityText?.setTextColor(cyan)

                        popupProgressBar?.visibility = View.VISIBLE
                        popupProgressBar?.isIndeterminate = false
                        popupProgressBar?.progress = state.pageProgress
                    } else if (state.isRunning && !state.isPaused) {
                        // Waiting to Refresh state
                        val green = Color.parseColor("#34A853")
                        bubbleContainer?.background = createBubbleBackground(green)
                        setDotColor(bubbleStatusIndicator, green)
                        timerText?.text = formatTimeRemaining(state.remainingSeconds)
                        timerText?.setTextColor(Color.WHITE)

                        popupStatusText?.text = "Suru Auto: Active"
                        popupStatusText?.setTextColor(green)

                        setPillStyle(popupActivityIndicator, Color.parseColor("#0E2A1A"), Color.parseColor("#238636"))
                        setDotColor(popupActivityDot, green)
                        popupActivityText?.text = "⏳ Waiting to refresh in ${formatTimeRemaining(state.remainingSeconds)}"
                        popupActivityText?.setTextColor(green)

                        popupProgressBar?.visibility = View.GONE
                    } else if (state.isPaused) {
                        // Paused state
                        val amber = Color.parseColor("#FBBC04")
                        bubbleContainer?.background = createBubbleBackground(amber)
                        setDotColor(bubbleStatusIndicator, amber)
                        timerText?.text = "PAUSE"
                        timerText?.setTextColor(amber)

                        popupStatusText?.text = "Suru Auto: Paused"
                        popupStatusText?.setTextColor(amber)

                        setPillStyle(popupActivityIndicator, Color.parseColor("#2D2305"), amber)
                        setDotColor(popupActivityDot, amber)
                        popupActivityText?.text = "⏸ Auto-Refresh Paused"
                        popupActivityText?.setTextColor(amber)

                        popupProgressBar?.visibility = View.GONE
                    } else {
                        // Stopped / Inactive state
                        val gray = Color.parseColor("#8B949E")
                        bubbleContainer?.background = createBubbleBackground(gray)
                        setDotColor(bubbleStatusIndicator, gray)
                        timerText?.text = "OFF"
                        timerText?.setTextColor(gray)

                        popupStatusText?.text = "Suru Auto: Stopped"
                        popupStatusText?.setTextColor(gray)

                        setPillStyle(popupActivityIndicator, Color.parseColor("#1C2128"), Color.parseColor("#444C56"))
                        setDotColor(popupActivityDot, gray)
                        popupActivityText?.text = "⏹ Auto-Refresh Inactive"
                        popupActivityText?.setTextColor(gray)

                        popupProgressBar?.visibility = View.GONE
                    }
                }

                popupUrlText?.text = state.url
                countBadge?.text = "Reloads: ${state.totalRefreshes}"
                pauseResumeBtn?.text = if (state.isRunning && !state.isPaused) "Pause" else "Resume"
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (AutoRefreshManager.state.value.isRunning && Settings.canDrawOverlays(applicationContext)) {
            FloatingBubbleService.start(applicationContext)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRedBlinkEffect()
        scope.cancel()
        AutoRefreshManager.setFloatingWidgetActive(false)
        if (rootView != null && windowManager != null) {
            try {
                windowManager?.removeView(rootView)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
