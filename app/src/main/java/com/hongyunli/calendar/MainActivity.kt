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
        btnFloating = findViewById(R.id.btnFloating)

        updateDateTime()
        fetchWeather()

        // 悬浮窗按钮
        btnFloating.setOnClickListener {
            checkAndStartFloatingWindow()
        }
    }

    private fun checkAndStartFloatingWindow() {
        // Android 6.0+ 需要检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // 请求权限
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
            } else {
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
        finish() // 启动悬浮窗后关闭主界面
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startFloatingService()
                } else {
                    Toast.makeText(this, "需要悬浮窗权限才能使用此功能", Toast.LENGTH_LONG).show()
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

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }
}
