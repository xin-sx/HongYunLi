package com.hongyunli.calendar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(1, createNotification())
        createFloatingWindow()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "floating_service",
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, "floating_service")
            .setContentTitle("鸿运历悬浮窗运行中")
            .setContentText("点击打开主界面")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createFloatingWindow() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_window, null)

        // 获取屏幕尺寸
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        
        defaultWidth = (size.x * 0.7).toInt()
        defaultHeight = WindowManager.LayoutParams.WRAP_CONTENT

        params = WindowManager.LayoutParams(
            defaultWidth,
            defaultHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 100
        }

        // 初始化视图
        tvTime = floatingView!!.findViewById(R.id.fw_time)
        tvDate = floatingView!!.findViewById(R.id.fw_date)
        tvLunar = floatingView!!.findViewById(R.id.fw_lunar)
        tvSolarTerm = floatingView!!.findViewById(R.id.fw_solar_term)
        
        val btnClose = floatingView!!.findViewById<ImageButton>(R.id.fw_close)
        val btnMinimize = floatingView!!.findViewById<ImageButton>(R.id.fw_minimize)
        val resizeHandle = floatingView!!.findViewById<View>(R.id.fw_resize_handle)
        val header = floatingView!!.findViewById<View>(R.id.fw_header)

        // 关闭按钮
        btnClose.setOnClickListener {
            stopSelf()
        }

        // 最小化/恢复按钮
        btnMinimize.setOnClickListener {
            toggleMinimize()
        }

        // 拖动功能
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params!!.x
                    initialY = params!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params!!.x = initialX + (event.rawX - initialTouchX).toInt()
                    params!!.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        // 缩放功能
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    defaultWidth = params!!.width
                    defaultHeight = params!!.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newWidth = defaultWidth + (event.rawX - initialTouchX).toInt()
                    val newHeight = defaultHeight + (event.rawY - initialTouchY).toInt()
                    
                    params!!.width = newWidth.coerceIn(300, size.x)
                    params!!.height = newHeight.coerceIn(200, size.y)
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
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
            params!!.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            content.visibility = View.VISIBLE
            resizeHandle.visibility = View.VISIBLE
            params!!.height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        windowManager.updateViewLayout(floatingView, params)
    }

    private fun startUpdateTimer() {
        val runnable = object : Runnable {
            override fun run() {
                updateContent()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    private fun updateContent() {
        val now = LocalDateTime.now()
        
        // 时间
        tvTime.text = String.format("%02d:%02d", now.hour, now.minute)
        
        // 公历日期
        val weekDays = arrayOf("日", "一", "二", "三", "四", "五", "六")
        tvDate.text = "${now.monthValue}月${now.dayOfMonth}日 周${weekDays[now.dayOfWeek.value % 7]}"
        
        // 农历
        val lunarDate = LunarCalendar.solarToLunar(now.year, now.monthValue, now.dayOfMonth)
        tvLunar.text = "${lunarDate.monthChinese}月${lunarDate.dayChinese}"
        
        // 节气
        val today = java.time.LocalDate.now()
        val nextTerm = SolarTerm.getNextSolarTerm(today)
        val daysUntil = SolarTerm.getDaysUntilNextSolarTerm(today)
        if (nextTerm != null) {
            tvSolarTerm.text = "${nextTerm.name} 还有${daysUntil}天"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (floatingView != null) {
            windowManager.removeView(floatingView)
            floatingView = null
        }
    }
}
