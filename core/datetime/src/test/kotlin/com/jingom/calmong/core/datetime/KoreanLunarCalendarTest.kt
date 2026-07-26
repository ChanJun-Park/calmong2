package com.jingom.calmong.core.datetime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class KoreanLunarCalendarTest {
    private val zone = KoreanLunarCalendar.ZONE

    private fun lunarToSolar(
        year: Int,
        month: Int,
        day: Int,
        leap: Boolean = false,
    ): LocalDate = LunarDateTime(year, month, day, leap).toZonedDateTime().toLocalDate()

    private fun solarToLunar(
        year: Int,
        month: Int,
        day: Int,
    ): LunarDateTime = ZonedDateTime.of(year, month, day, 0, 0, 0, 0, zone).toLunarDateTime()

    @Test
    fun `설날(음력 1월 1일)을 양력으로 변환한다`() {
        assertEquals(LocalDate.of(2024, 2, 10), lunarToSolar(2024, 1, 1))
        assertEquals(LocalDate.of(2023, 1, 22), lunarToSolar(2023, 1, 1))
        assertEquals(LocalDate.of(2020, 1, 25), lunarToSolar(2020, 1, 1))
        assertEquals(LocalDate.of(2000, 2, 5), lunarToSolar(2000, 1, 1))
    }

    @Test
    fun `추석(음력 8월 15일)을 양력으로 변환한다`() {
        assertEquals(LocalDate.of(2024, 9, 17), lunarToSolar(2024, 8, 15))
        assertEquals(LocalDate.of(2023, 9, 29), lunarToSolar(2023, 8, 15))
    }

    @Test
    fun `부처님오신날(음력 4월 8일)을 양력으로 변환한다`() {
        assertEquals(LocalDate.of(2024, 5, 15), lunarToSolar(2024, 4, 8))
    }

    @Test
    fun `양력을 음력으로 변환한다`() {
        assertEquals(LunarDateTime(2024, 1, 1), solarToLunar(2024, 2, 10))
        assertEquals(LunarDateTime(2024, 8, 15), solarToLunar(2024, 9, 17))
    }

    @Test
    fun `윤달과 평달은 서로 다른 양력일로 매핑된다 (2023 윤2월)`() {
        val plain = lunarToSolar(2023, 2, 1, leap = false)
        val leap = lunarToSolar(2023, 2, 1, leap = true)
        assertEquals(LocalDate.of(2023, 2, 20), plain)
        assertEquals(LocalDate.of(2023, 3, 22), leap)
        assertNotEquals(plain, leap)
    }

    @Test
    fun `윤달 양력일을 음력으로 변환하면 isLeapMonth가 true다`() {
        val lunar = solarToLunar(2023, 3, 22)
        assertEquals(LunarDateTime(2023, 2, 1, isLeapMonth = true), lunar)
    }

    @Test
    fun `시각은 변환 전후로 보존된다 (KST)`() {
        val original = ZonedDateTime.of(2024, 9, 17, 14, 30, 15, 0, zone)
        val lunar = original.toLunarDateTime()
        assertEquals(LunarDateTime(2024, 8, 15, time = LocalTime.of(14, 30, 15)), lunar)
        assertEquals(original, lunar.toZonedDateTime())
    }

    @Test
    fun `다른 타임존 입력도 KST 날짜 기준으로 변환된다`() {
        // 2024-02-10 00:30 KST == 2024-02-09 15:30 UTC. KST 날짜(02-10)가 설날.
        val utc = ZonedDateTime.of(2024, 2, 9, 15, 30, 0, 0, java.time.ZoneOffset.UTC)
        assertEquals(LunarDateTime(2024, 1, 1, time = LocalTime.of(0, 30)), utc.toLunarDateTime())
    }

    @Test
    fun `음력 1900년 1월 1일은 양력 1900-01-31`() {
        assertEquals(LocalDate.of(1900, 1, 31), lunarToSolar(1900, 1, 1))
        assertEquals(LunarDateTime(1900, 1, 1), solarToLunar(1900, 1, 31))
    }

    @Test
    fun `왕복 변환 - 양력에서 음력 후 다시 양력이면 원본과 같다`() {
        var date = LocalDate.of(1900, 1, 1)
        val end = LocalDate.of(2050, 12, 31)
        var count = 0
        while (!date.isAfter(end)) {
            val zoned = date.atStartOfDay(zone)
            val roundTrip = zoned.toLunarDateTime().toZonedDateTime().toLocalDate()
            assertEquals("round-trip 실패 at $date", date, roundTrip)
            date = date.plusDays(37)
            count++
        }
        assertEquals(true, count > 1400)
    }

    @Test
    fun `지원 범위가 양력 음력 날짜로 정확히 노출된다`() {
        assertEquals(LocalDate.of(1900, 1, 1), KoreanLunarCalendar.MIN_SOLAR_DATE)
        assertEquals(LocalDate.of(2050, 12, 31), KoreanLunarCalendar.MAX_SOLAR_DATE)
        assertEquals(LunarDateTime(1899, 12, 1), KoreanLunarCalendar.MIN_LUNAR_DATE)
        assertEquals(LunarDateTime(2050, 11, 18), KoreanLunarCalendar.MAX_LUNAR_DATE)
    }

    @Test
    fun `양력 경계는 변환되고 음력 경계와 왕복 일치한다`() {
        val maxSolar = KoreanLunarCalendar.MAX_SOLAR_DATE
        assertEquals(KoreanLunarCalendar.MAX_LUNAR_DATE, maxSolar.atStartOfDay(zone).toLunarDateTime())
        assertEquals(maxSolar, KoreanLunarCalendar.MAX_LUNAR_DATE.toZonedDateTime().toLocalDate())
        assertEquals(LocalDate.of(1900, 1, 1), KoreanLunarCalendar.MIN_LUNAR_DATE.toZonedDateTime().toLocalDate())
    }

    @Test
    fun `양력 경계를 하루라도 벗어나면 예외`() {
        assertThrows(IllegalArgumentException::class.java) {
            KoreanLunarCalendar.MIN_SOLAR_DATE
                .minusDays(1)
                .atStartOfDay(zone)
                .toLunarDateTime()
        }
        assertThrows(IllegalArgumentException::class.java) {
            KoreanLunarCalendar.MAX_SOLAR_DATE
                .plusDays(1)
                .atStartOfDay(zone)
                .toLunarDateTime()
        }
    }

    @Test
    fun `음력 경계 다음 달은 존재하지 않아 예외 (2050년 12월)`() {
        assertThrows(IllegalArgumentException::class.java) { lunarToSolar(2050, 12, 1) }
    }

    @Test
    fun `지원 범위 밖 음력 연도는 예외`() {
        assertThrows(IllegalArgumentException::class.java) { lunarToSolar(1899, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { lunarToSolar(2051, 1, 1) }
    }

    @Test
    fun `존재하지 않는 윤달은 예외 (2024년은 윤달 없음)`() {
        assertThrows(IllegalArgumentException::class.java) {
            LunarDateTime(2024, 2, 1, isLeapMonth = true).toZonedDateTime()
        }
    }

    @Test
    fun `그 달에 없는 일(29일 달의 30일)은 예외 (2023년 정월은 29일)`() {
        assertThrows(IllegalArgumentException::class.java) {
            LunarDateTime(2023, 1, 30).toZonedDateTime()
        }
    }

    @Test
    fun `양력 최소일은 음력 1899년 12월 1일이다`() {
        assertEquals(LunarDateTime(1899, 12, 1), solarToLunar(1900, 1, 1))
    }
}
