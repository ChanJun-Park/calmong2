package com.jingom.calmong.core.datetime

import java.time.LocalTime

/**
 * 한국 음력 일시. 날짜는 음력(연/월/일 + 윤달 여부), 시각은 [time](KST 벽시계 시각)으로 들고 있다.
 *
 * 양력([java.time.ZonedDateTime], Asia/Seoul)과의 변환은 [toZonedDateTime] / [ZonedDateTime.toLunarDateTime].
 * 구조 검증(월 1..12, 일 1..30)만 생성 시 수행하고, 실제 해당 음력일의 존재 여부(윤달·월 일수)는
 * 변환 시점에 [KoreanLunarCalendar]가 데이터로 검증해 [IllegalArgumentException]을 던진다.
 *
 * @param isLeapMonth 윤달이면 true. 해당 연도의 윤달과 일치해야 변환된다.
 * @param time KST 벽시계 시각. 변환 시 그대로 보존된다(기본 자정).
 */
data class LunarDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean = false,
    val time: LocalTime = LocalTime.MIDNIGHT,
) {
    init {
        require(month in 1..MAX_MONTH) { "month must be 1..$MAX_MONTH but was $month" }
        require(day in 1..MAX_DAY) { "day must be 1..$MAX_DAY but was $day" }
    }

    private companion object {
        const val MAX_MONTH = 12
        const val MAX_DAY = 30
    }
}
