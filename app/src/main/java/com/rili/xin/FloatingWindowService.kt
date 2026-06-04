package com.rili.xin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import java.time.LocalDateTime

class FloatingWindowService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val handler = Handler(Looper.getMainLooper())
    private var tvTime: TextView? = null
    private var tvDate: TextView? = null
    private var tvLunar: TextView? = null
    private var tvSolarTerm: TextView? = null

    private var isMinimized = false
    private var defaultWidth = 0
    private var defaultHeight = 0
    private var screenWidth = 0
    private var screenHeight = 0

    private var updateRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannelIfNeeded()

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        handler.post {
            try {
                createFloatingWindow()
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "悬浮窗日历服务",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "星历悬浮窗日历前台服务"
                        setShowBadge(false)
                        enableVibration(false)
                        enableLights(false)
                        setSound(null, null)
                    }
                    manager.createNotificationChannel(channel)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("星历")
            .setContentText("悬浮窗日历运行中")
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setStyle(Notification.BigTextStyle()
                .bigText("星历悬浮窗日历正在运行\n点击可返回主界面"))
        }

        return builder.build()
    }

    private fun createFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(applicationContext)
        floatingView = inflater.inflate(R.layout.floating_window, null)

        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = this.display
            if (display != null) {
                val realMetrics = DisplayMetrics()
                display.getRealMetrics(realMetrics)
                screenWidth = realMetrics.widthPixels
                screenHeight = realMetrics.heightPixels
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.getMetrics(metrics)
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }

        defaultWidth = (screenWidth * 0.7).toInt()
        defaultHeight = WindowManager.LayoutParams.WRAP_CONTENT

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            defaultWidth,
            defaultHeight,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 100
        }

        tvTime = floatingView?.findViewById(R.id.fw_time)
        tvDate = floatingView?.findViewById(R.id.fw_date)
        tvLunar = floatingView?.findViewById(R.id.fw_lunar)
        tvSolarTerm = floatingView?.findViewById(R.id.fw_solar_term)

        val btnClose = floatingView?.findViewById<ImageButton>(R.id.fw_close)
        val btnMinimize = floatingView?.findViewById<ImageButton>(R.id.fw_minimize)
        val resizeHandle = floatingView?.findViewById<View>(R.id.fw_resize_handle)
        val header = floatingView?.findViewById<View>(R.id.fw_header)

        btnClose?.setOnClickListener {
            stopSelf()
        }

        btnMinimize?.setOnClickListener {
            toggleMinimize()
        }

        header?.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params!!.x
                        initialY = params!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params!!.x = initialX + (event.rawX - initialTouchX).toInt()
                        params!!.y = initialY + (event.rawY - initialTouchY).toInt()
                        params!!.x = params!!.x.coerceIn(0, screenWidth - 100)
                        params!!.y = params!!.y.coerceIn(0, screenHeight - 100)
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        resizeHandle?.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        defaultWidth = params!!.width
                        defaultHeight = params!!.height
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val newWidth = defaultWidth + (event.rawX - initialTouchX).toInt()
                        val newHeight = defaultHeight + (event.rawY - initialTouchY).toInt()
                        params!!.width = newWidth.coerceIn(250, screenWidth)
                        params!!.height = newHeight.coerceIn(150, screenHeight)
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return
        }

        startUpdateTimer()
    }

    private fun toggleMinimize() {
        isMinimized = !isMinimized
        val content = floatingView?.findViewById<LinearLayout>(R.id.fw_content)
        val resizeHandle = floatingView?.findViewById<View>(R.id.fw_resize_handle)

        if (isMinimized) {
            content?.visibility = View.GONE
            resizeHandle?.visibility = View.GONE
            params?.width = WindowManager.LayoutParams.WRAP_CONTENT
            params?.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            content?.visibility = View.VISIBLE
            resizeHandle?.visibility = View.VISIBLE
            params?.width = defaultWidth
            params?.height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        try {
            if (floatingView != null && params != null) {
                windowManager?.updateViewLayout(floatingView, params)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startUpdateTimer() {
        updateRunnable = object : Runnable {
            override fun run() {
                if (floatingView != null) {
                    updateContent()
                    handler.postDelayed(this, 1000)
                }
            }
        }
        updateRunnable?.let { handler.post(it) }
    }

    private fun updateContent() {
        try {
            val now = LocalDateTime.now()

            tvTime?.text = String.format("%02d:%02d", now.hour, now.minute)

            val weekDays = arrayOf("日", "一", "二", "三", "四", "五", "六")
            tvDate?.text = "${now.monthValue}月${now.dayOfMonth}日 周${weekDays[now.dayOfWeek.value % 7]}"

            val lunarDate = LunarCalendar.solarToLunar(now.year, now.monthValue, now.dayOfMonth)
            tvLunar?.text = "${lunarDate.monthChinese}月${lunarDate.dayChinese}"

            val today = java.time.LocalDate.now()
            val nextTerm = SolarTerm.getNextSolarTerm(today)
            val daysUntil = SolarTerm.getDaysUntilNextSolarTerm(today)
            if (nextTerm != null) {
                tvSolarTerm?.text = "${nextTerm.name} 还有${daysUntil}天"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingView = null
        }
    }

    companion object {
        private const val CHANNEL_ID = "floating_calendar_service"
        private const val NOTIFICATION_ID = 1
    }
}
