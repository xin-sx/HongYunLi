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

        // 悬浮窗按钮
        btnFloating.setOnClickListener {
            checkAndStartFloatingWindow()
        }
    }

    /**
     * 显示系统信息（适配澎湃OS检测）
     */
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
     * 检查并启动悬浮窗
     * 针对澎湃OS做了特殊权限处理
     */
    private fun checkAndStartFloatingWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!HyperOSHelper.canDrawOverlays(this)) {
                // 使用澎湃OS适配的权限请求
                HyperOSHelper.requestOverlayPermission(this)
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
            } else {
                // 澎湃OS 3.0 额外检查后台启动权限
                if (HyperOSHelper.isHyperOS3OrAbove()) {
                    HyperOSHelper.requestBackgroundStartPermission(this)
                }
                startFloatingService()
            }
        } else {
            startFloatingService()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onResume() {
        super.onResume()
        // 从权限设置页面返回后检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (HyperOSHelper.canDrawOverlays(this)) {
                startFloatingService()
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

        // 时钟
        tvClock.text = String.format("%02d:%02d", hour, minute)

        // 公历日期
        tvSolarDate.text = "${year}年${month}月${day}日 ${weekDays[weekDay - 1]}"

        // 农历
        val lunar = LunarCalendar.solarToLunar(year, month, day)
        tvLunarDate.text = "农历${lunar.ganZhiYear}年 ${lunar.lunarMonthName}${lunar.lunarDayName}"
        tvGanZhi.text = "${lunar.ganZhiYear}年（${lunar.zodiac}）"

        // 节气
        val solarTermInfo = SolarTerm.getCurrentOrNextSolarTerm(year, month, day)
        tvSolarTerm.text = solarTermInfo.name
        if (solarTermInfo.isCurrent) {
            tvSolarTermCountdown.text = "今日节气"
        } else {
            val daysLeft = solarTermInfo.daysUntil
            tvSolarTermCountdown.text = "距今还有 ${daysLeft} 天"
        }

        // 每秒更新
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
