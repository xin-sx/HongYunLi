package com.hongyunli.calendar

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.net.URL
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var tvClock: TextView
    private lateinit var tvSolarDate: TextView
    private lateinit var tvLunarDate: TextView
    private lateinit var tvGanZhi: TextView
    private lateinit var tvSolarTerm: TextView
    private lateinit var tvSolarTermCountdown: TextView
    private lateinit var tvWeather: TextView
    private lateinit var tvSystemInfo: TextView
    private lateinit var btnFloating: Button

    private val handler = Handler(Looper.getMainLooper())
    private val weekDays = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")

    // 修复1: 标记是否正在等待权限授权结果，防止 onResume 死循环
    private var waitingForPermissionResult = false
    private var hasStartedFloating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvClock = findViewById(R.id.tvClock)
        tvSolarDate = findViewById(R.id.tvSolarDate)
        tvLunarDate = findViewById(R.id.tvLunarDate)
        tvGanZhi = findViewById(R.id.tvGanZhi)
        tvSolarTerm = findViewById(R.id.tvSolarTerm)
        tvSolarTermCountdown = findViewById(R.id.tvSolarTermCountdown)
        tvWeather = findViewById(R.id.tvWeather)
        tvSystemInfo = findViewById(R.id.tvSystemInfo)
        btnFloating = findViewById(R.id.btnFloating)

        updateDateTime()
        fetchWeather()
        showSystemInfo()

        btnFloating.setOnClickListener {
            checkAndStartFloatingWindow()
        }
    }

    private fun showSystemInfo() {
        val info = StringBuilder()
        if (HyperOSHelper.isHyperOS()) {
            val version = HyperOSHelper.getHyperOSVersion() ?: "未知"
            info.append("小米澎湃OS ").append(version)
            if (HyperOSHelper.isHyperOS3OrAbove()) {
                info.append(" ✓ 已适配")
            }
        } else {
            info.append("Android ").append(Build.VERSION.RELEASE)
        }
        tvSystemInfo.text = info.toString()
    }

    /**
     * 修复1+2: 重写悬浮窗启动逻辑
     * - 先检查悬浮窗权限
     * - 再检查澎湃OS后台权限（不阻塞，只提示）
     * - 使用状态标记防止 onResume 死循环
     */
    private fun checkAndStartFloatingWindow() {
        if (hasStartedFloating) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!HyperOSHelper.canDrawOverlays(this)) {
                // 需要悬浮窗权限
                waitingForPermissionResult = true
                HyperOSHelper.requestOverlayPermission(this)
                Toast.makeText(this, "请授予悬浮窗权限后返回", Toast.LENGTH_LONG).show()
                return
            }
        }

        // 修复2: 澎湃OS后台权限只提示，不阻塞启动
        if (HyperOSHelper.isHyperOS3OrAbove()) {
            Toast.makeText(this, "建议在设置中开启后台弹出界面权限", Toast.LENGTH_SHORT).show()
        }

        startFloatingService()
    }

    private fun startFloatingService() {
        if (hasStartedFloating) return
        hasStartedFloating = true

        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
        finish()
    }

    /**
     * 修复1: onResume 中使用状态标记防止死循环
     * 只在从权限设置页面返回时检查一次
     */
    override fun onResume() {
        super.onResume()
        if (waitingForPermissionResult) {
            waitingForPermissionResult = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (HyperOSHelper.canDrawOverlays(this)) {
                    // 权限已授予，启动悬浮窗
                    startFloatingService()
                } else {
                    // 权限仍未授予，提示用户
                    Toast.makeText(this, "未获得悬浮窗权限，无法启动", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateDateTime() {
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val weekDay = now.get(Calendar.DAY_OF_WEEK)

        tvClock.text = String.format("%02d:%02d", hour, minute)
        tvSolarDate.text = "${year}年${month}月${day}日 ${weekDays[weekDay - 1]}"

        val lunar = LunarCalendar.solarToLunar(year, month, day)
        tvLunarDate.text = "农历${lunar.ganZhiYear}年 ${lunar.lunarMonthName}${lunar.lunarDayName}"
        tvGanZhi.text = "${lunar.ganZhiYear}年（${lunar.zodiac}）"

        val solarTermInfo = SolarTerm.getCurrentOrNextSolarTerm(year, month, day)
        tvSolarTerm.text = solarTermInfo.name
        if (solarTermInfo.isCurrent) {
            tvSolarTermCountdown.text = "今日节气"
        } else {
            tvSolarTermCountdown.text = "距今还有 ${solarTermInfo.daysUntil} 天"
        }

        handler.postDelayed({ updateDateTime() }, 1000)
    }

    private fun fetchWeather() {
        Thread {
            try {
                val url = URL("https://wttr.in/?format=%C+%t")
                val conn = url.openConnection()
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val stream = conn.getInputStream()
                val reader = stream.bufferedReader()
                val result = reader.readText().trim()
                reader.close()
                handler.post {
                    tvWeather.text = result
                }
            } catch (e: Exception) {
                handler.post {
                    tvWeather.text = "天气获取失败"
                }
            }
        }.start()
    }
}
