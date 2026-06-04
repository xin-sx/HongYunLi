package com.rili.xin

import java.time.LocalDate
import java.util.Calendar

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

    private val solarTermNames = arrayOf(
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
        "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
        "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    )

    fun getCurrentOrNextSolarTerm(year: Int, month: Int, day: Int): SolarTermInfo {
        val today = LocalDate.of(year, month, day)
        val terms = getSolarTerms(year)
        
        for (term in terms) {
            if (term.date == today) {
                return SolarTermInfo(term.name, true, 0)
            }
            if (term.date.isAfter(today)) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, term.date).toInt()
                return SolarTermInfo(term.name, false, days)
            }
        }
        
        // 如果今年节气都过了，取明年第一个
        val nextYearTerms = getSolarTerms(year + 1)
        val firstTerm = nextYearTerms.first()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, firstTerm.date).toInt()
        return SolarTermInfo(firstTerm.name, false, days)
    }

    fun getSolarTerms(year: Int): List<SolarTermData> {
        val terms = mutableListOf<SolarTermData>()
        val baseDate = Calendar.getInstance().apply { set(1900, 0, 6, 2, 5, 0) }
        
        for (i in 0 until 24) {
            val offset = (year - 1900) * 525948 + i * 21915
            val termCal = Calendar.getInstance().apply {
                timeInMillis = baseDate.timeInMillis + offset * 60000L
            }
            terms.add(SolarTermData(
                solarTermNames[i],
                LocalDate.of(termCal.get(Calendar.YEAR), termCal.get(Calendar.MONTH) + 1, termCal.get(Calendar.DAY_OF_MONTH))
            ))
        }
        return terms
    }

    fun getNextSolarTerm(date: LocalDate): SolarTermData? {
        val terms = getSolarTerms(date.year)
        for (term in terms) {
            if (term.date.isAfter(date) || term.date == date) {
                return term
            }
        }
        val nextYearTerms = getSolarTerms(date.year + 1)
        return nextYearTerms.firstOrNull()
    }

    fun getDaysUntilNextSolarTerm(date: LocalDate): Int {
        val nextTerm = getNextSolarTerm(date) ?: return 0
        return java.time.temporal.ChronoUnit.DAYS.between(date, nextTerm.date).toInt()
    }
}
