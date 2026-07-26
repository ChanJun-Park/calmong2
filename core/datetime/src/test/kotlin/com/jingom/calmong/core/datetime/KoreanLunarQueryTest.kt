package com.jingom.calmong.core.datetime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KoreanLunarQueryTest {
    @Test
    fun `leapMonthOf는 그 해 윤달 위치를 돌려준다`() {
        assertEquals(2, KoreanLunarCalendar.leapMonthOf(2023)) // 윤2월
        assertEquals(4, KoreanLunarCalendar.leapMonthOf(2020)) // 윤4월
        assertEquals(5, KoreanLunarCalendar.leapMonthOf(2017)) // 윤5월
        assertEquals(6, KoreanLunarCalendar.leapMonthOf(2025)) // 윤6월
        assertEquals(0, KoreanLunarCalendar.leapMonthOf(2024)) // 윤달 없음
    }

    @Test
    fun `lunarMonthLength는 29 또는 30을 돌려준다 (2023년)`() {
        assertEquals(29, KoreanLunarCalendar.lunarMonthLength(2023, 1, isLeapMonth = false)) // 정월
        assertEquals(30, KoreanLunarCalendar.lunarMonthLength(2023, 2, isLeapMonth = false)) // 평2월
        assertEquals(29, KoreanLunarCalendar.lunarMonthLength(2023, 2, isLeapMonth = true)) // 윤2월
        assertEquals(30, KoreanLunarCalendar.lunarMonthLength(2023, 3, isLeapMonth = false))
    }

    @Test
    fun `존재하지 않는 윤달 길이는 예외 (2024년은 윤달 없음)`() {
        assertThrows(IllegalArgumentException::class.java) {
            KoreanLunarCalendar.lunarMonthLength(2024, 2, isLeapMonth = true)
        }
    }

    @Test
    fun `데이터 범위 밖 연도 질의는 예외`() {
        assertThrows(IllegalArgumentException::class.java) { KoreanLunarCalendar.leapMonthOf(1800) }
        assertThrows(IllegalArgumentException::class.java) { KoreanLunarCalendar.lunarMonthLength(2100, 1, false) }
    }
}
