package com.hongyunli.calendar

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

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvLunar: TextView
    private lateinit var tvSolarTerm: TextView
    
    private var isMinimized = false
    private var defaultWidth = 0
    private var defaultHeight = 0
    private var screenWidth = 0
    private var screenHeight = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingWindow()
    }

    /**
     * 修复8: 通知渠道适配澎湃OS 3.0
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗日历服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "鸿运历悬浮窗日历前台服务，显示实时时间、农历和节气信息"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
                // 修复8: 澎湃OS 3.0 锁屏不显示通知
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 修复8: 前台服务通知适配 Android 14+ / 澎湃OS 3.0
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("鸿运历")
            .setContentText("悬浮窗日历运行中")
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setStyle(Notification.BigTextStyle()
                .bigText("鸿运历悬浮窗日历正在运行\n点击可返回主界面"))
        }

        // 修复8: Android 14+ 设置前台服务类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    /**
     * 修复3+4: 悬浮窗创建适配澎湃OS 3.0
     * - 使用 DisplayMetrics 替代废弃的 defaultDisplay
     * - 添加 FLAG_NOT_TOUCH_MODAL 避免拦截所有触摸
     */
    private fun createFloatingWindow() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_window, null)

        // 修复3: 使用 DisplayMetrics 替代废弃的 defaultDisplay
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
                windowManager.defaultDisplay.getMetrics(metrics)
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
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
            // 修复4: 添加 FLAG_NOT_TOUCH_MODAL，避免拦截所有触摸事件
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 100
        }

        tvTime = floatingView!!.findViewById(R.id.fw_time)
        tvDate = floatingView!!.findViewById(R.id.fw_date)
        tvLunar = floatingView!!.findViewById(R.id.fw_lunar)
        tvSolarTerm = floatingView!!.findViewById(R.id.fw_solar_term)
        
        val btnClose = floatingView!!.findViewById<ImageButton>(R.id.fw_close)
        val btnMinimize = floatingView!!.findViewById<ImageButton>(R.id.fw_minimize)
        val resizeHandle = floatingView!!.findViewById<View>(R.id.fw_resize_handle)
        val header = floatingView!!.findViewById<View>(R.id.fw_header)

        btnClose.setOnClickListener {
            stopSelf()
        }

        btnMinimize.setOnClickListener {
            toggleMinimize()
        }

        // 拖动功能
        header.setOnTouchListener(object : View.OnTouchListener {
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
                        // 限制不超出屏幕
                        params!!.x = params!!.x.coerceIn(0, screenWidth - 100)
                        params!!.y = params!!.y.coerceIn(0, screenHeight - 100)
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        // 缩放功能
        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
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
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            // 澎湃OS 3.0 可能因为权限拒绝添加
            stopSelf()
        }

        startUpdateTimer()
    }

    private fun toggleMinimize() {
        isMinimized = !isMinimized
        val content = floatingView!!.findViewById<LinearLayout>(R.id.fw_content)
        val resizeHandle = floatingView!!.findViewById<View>(R.id.fw_resize_handle)
        
        if (isMinimized) {
            content.visibility = View.GONE
            resizeHandle.visibility = View.GONE
            params!!.width = WindowManager.LayoutParams.WRAP_CONTENT
            params!!.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            content.visibility = View.VISIBLE
            resizeHandle.visibility = View.VISIBLE
            params!!.width = defaultWidth
            params!!.height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        try {
            windowManager.updateViewLayout(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startUpdateTimer() {
        val runnable = object : Runnable {
            override fun run() {
                if (floatingView != null) {
                    updateContent()
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(runnable)
    }

    private fun updateContent() {
        try {
            val now = LocalDateTime.now()
            
            tvTime.text = String.format("%02d:%02d", now.hour, now.minute)
            
            val weekDays = arrayOf("日", "一", "二", "三", "四", "五", "六")
            tvDate.text = "${now.monthValue}月${now.dayOfMonth}日 周${weekDays[now.dayOfWeek.value % 7]}"
            
            val lunarDate = LunarCalendar.solarToLunar(now.year, now.monthValue, now.dayOfMonth)
            tvLunar.text = "${lunarDate.monthChinese}月${lunarDate.dayChinese}"
            
            val today = java.time.LocalDate.now()
            val nextTerm = SolarTerm.getNextSolarTerm(today)
            val daysUntil = SolarTerm.getDaysUntilNextSolarTerm(today)
            if (nextTerm != null) {
                tvSolarTerm.text = "${nextTerm.name} 还有${daysUntil}天"
            }
        } catch (e: Exception) {
            // 防止更新时崩溃导致服务终止
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView)
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
