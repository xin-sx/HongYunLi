package com.hongyunli.calendar

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.net.URL
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class MainActivity : Activity() {

    private lateinit var tvClock: TextView
    private lateinit var tvSolarDate: TextView
    private lateinit var tvLunarDate: TextView
    private lateinit var tvGanZhi: TextView
    private lateinit var tvSolarTerm: TextView
    private lateinit var tvSolarTermCountdown: TextView
    private lateinit var tvWeather: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val weekDays = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvClock = findViewById(R.id.tvClock) as TextView
        tvSolarDate = findViewById(R.id.tvSolarDate) as TextView
        tvLunarDate = findViewById(R.id.tvLunarDate) as TextView
        tvGanZhi = findViewById(R.id.tvGanZhi) as TextView
        tvSolarTerm = findViewById(R.id.tvSolarTerm) as TextView
        tvSolarTermCountdown = findViewById(R.id.tvSolarTermCountdown) as TextView
        tvWeather = findViewById(R.id.tvWeather) as TextView

        updateDateTime()
        fetchWeather()
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

        // 每分钟更新
        val delayMs = 60000 - (now.get(Calendar.SECOND) * 1000 + now.get(Calendar.MILLISECOND))
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateDateTime()
            }
        }, delayMs.toLong())
    }

    private fun fetchWeather() {
        Thread(object : Runnable {
            override fun run() {
                try {
                    val url = URL("https://wttr.in/?format=%C+%t")
                    val conn = url.openConnection()
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    val stream = conn.getInputStream()
                    val reader = stream.bufferedReader()
                    val result = reader.readText().trim()
                    reader.close()
                    handler.post(object : Runnable {
                        override fun run() {
                            tvWeather.text = result
                        }
                    })
                } catch (e: Exception) {
                    handler.post(object : Runnable {
                        override fun run() {
                            tvWeather.text = "天气获取失败"
                        }
                    })
                }
            }
        }).start()
    }
}
