package com.hongyunli.calendar

import java.util.Calendar
import java.util.GregorianCalendar
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 二十四节气计算工具类
 */
object SolarTerm {

    data class SolarTermData(
        val name: String,
        val date: LocalDate
    )

    class SolarTermInfo(
        val name: String,
        val isCurrent: Boolean,
        val daysUntil: Int
    )

    private val termNames = arrayOf(
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
        "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
        "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    )

    // 21世纪节气C值
    private val termC = doubleArrayOf(
        5.4055, 20.12, 3.87, 18.73, 5.63, 20.646, 4.81, 20.1,
        5.52, 21.04, 5.678, 21.37, 7.108, 22.83, 7.5, 23.13,
        7.646, 23.042, 8.318, 23.438, 7.438, 22.36, 7.18, 21.94
    )

    private fun getSolarTermDay(year: Int, termIndex: Int): Int {
        val y = year.toDouble()
        val c = termC[termIndex]
        val result = Math.floor(y * 0.2422 + c) - Math.floor((y - 1) / 4.0)
        return result.toInt()
    }

    private fun getAllSolarTerms(year: Int): List<Array<Any>> {
        val result = mutableListOf<Array<Any>>()
        for (i in 0 until 24) {
            val month = i / 2
            val day = getSolarTermDay(year, i)
            val cal = GregorianCalendar(year, month, day)
            result.add(arrayOf(termNames[i], cal))
        }
        return result
    }

    fun getCurrentOrNextSolarTerm(year: Int, month: Int, day: Int): SolarTermInfo {
        val allTerms = getAllSolarTerms(year)
        val today = GregorianCalendar(year, month - 1, day)
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        for (entry in allTerms) {
            val name = entry[0] as String
            val termDate = entry[1] as Calendar
            val termDay = termDate.get(Calendar.DAY_OF_MONTH)
            val termMonth = termDate.get(Calendar.MONTH) + 1

            if (termMonth == month && termDay == day) {
                return SolarTermInfo(name = name, isCurrent = true, daysUntil = 0)
            }

            if (termDate.timeInMillis > today.timeInMillis) {
                val diffMs = termDate.timeInMillis - today.timeInMillis
                val daysUntil = (diffMs / (1000 * 60 * 60 * 24)).toInt()
                return SolarTermInfo(name = name, isCurrent = false, daysUntil = daysUntil)
            }
        }

        val nextYearTerms = getAllSolarTerms(year + 1)
        if (nextYearTerms.isNotEmpty()) {
            val name = nextYearTerms[0][0] as String
            val termDate = nextYearTerms[0][1] as Calendar
            val diffMs = termDate.timeInMillis - today.timeInMillis
            val daysUntil = (diffMs / (1000 * 60 * 60 * 24)).toInt()
            return SolarTermInfo(name = name, isCurrent = false, daysUntil = daysUntil)
        }

        return SolarTermInfo(name = "小寒", isCurrent = false, daysUntil = 0)
    }

    // 获取下一个节气（用于悬浮窗）
    fun getNextSolarTerm(date: LocalDate): SolarTermData? {
        val year = date.year
        val allTerms = getAllSolarTermsForYear(year)
        
        for (term in allTerms) {
            if (term.date.isAfter(date) || term.date.isEqual(date)) {
                return term
            }
        }
        
        // 如果今年没有更多节气，返回明年的第一个
        val nextYearTerms = getAllSolarTermsForYear(year + 1)
        return nextYearTerms.firstOrNull()
    }

    // 计算距离下一个节气的天数
    fun getDaysUntilNextSolarTerm(date: LocalDate): Int {
        val nextTerm = getNextSolarTerm(date) ?: return 0
        return ChronoUnit.DAYS.between(date, nextTerm.date).toInt()
    }

    // 获取指定年份的所有节气
    private fun getAllSolarTermsForYear(year: Int): List<SolarTermData> {
        val result = mutableListOf<SolarTermData>()
        for (i in 0 until 24) {
            val month = i / 2 + 1
            val day = getSolarTermDay(year, i)
            result.add(SolarTermData(termNames[i], LocalDate.of(year, month, day)))
        }
        return result
    }
}
