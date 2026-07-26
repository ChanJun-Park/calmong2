package com.jingom.calmong.core.datetime

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 한국천문연구원(KASI) 음양력 표를 이용한 음력 ↔ 양력(KST) 변환기.
 *
 * 변환은 KST '벽시계 날짜' 기준이라 과거 한국 표준시 오프셋 변천(1908/1948~1988)과 무관하다
 * (인스턴트가 아니라 날짜를 매핑한다). 지원 범위를 벗어나거나 존재하지 않는 음력일은
 * [IllegalArgumentException]을 던진다.
 *
 * 데이터: [KoreanLunarData] (생성기로 KASI에서 추출), 해석: [LunarCodec].
 */
object KoreanLunarCalendar {
    /** 한국 표준시. */
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    /** 지원 양력 날짜 범위 — 이 범위만 변환 가능(1900-01-01 ~ 2050-12-31). 내장 데이터는 이보다 넓다. */
    val MIN_SOLAR_DATE: LocalDate = LocalDate.of(1900, 1, 1)
    val MAX_SOLAR_DATE: LocalDate = LocalDate.of(2050, 12, 31)

    /** 위 양력 범위에 대응하는 음력 날짜(년·월·일·윤달). 시각은 의미 없음(자정). */
    val MIN_LUNAR_DATE: LunarDateTime = decode(MIN_SOLAR_DATE)
    val MAX_LUNAR_DATE: LunarDateTime = decode(MAX_SOLAR_DATE)

    /** 음력 일시 → 양력 ZonedDateTime(KST). 시각은 그대로 보존된다. 지원 범위 밖이면 예외. */
    fun toZonedDateTime(lunar: LunarDateTime): ZonedDateTime {
        require(lunar.year in LunarCodec.MIN_YEAR..LunarCodec.MAX_YEAR) { "lunar year ${lunar.year} has no data" }
        val encoded = LunarCodec.yearData(lunar.year)
        val chronoIndex = LunarCodec.chronoIndexOf(encoded, lunar.month, lunar.isLeapMonth)
        require(chronoIndex < LunarCodec.monthCount(encoded)) {
            "lunar ${lunar.year}-${lunar.month}(leap=${lunar.isLeapMonth}) does not exist"
        }
        require(lunar.day <= LunarCodec.monthDays(encoded, chronoIndex)) {
            "lunar ${lunar.year}-${lunar.month}-${lunar.day} exceeds month length ${LunarCodec.monthDays(
                encoded,
                chronoIndex,
            )}"
        }

        var offset = 0L
        for (year in LunarCodec.MIN_YEAR until lunar.year) offset += LunarCodec.yearLength(LunarCodec.yearData(year))
        for (index in 0 until chronoIndex) offset += LunarCodec.monthDays(encoded, index)
        offset += (lunar.day - 1)

        val solar = KoreanLunarData.EPOCH.plusDays(offset)
        require(!solar.isBefore(MIN_SOLAR_DATE) && !solar.isAfter(MAX_SOLAR_DATE)) {
            "lunar ${lunar.year}-${lunar.month}-${lunar.day} -> solar $solar out of supported range " +
                "$MIN_SOLAR_DATE..$MAX_SOLAR_DATE"
        }
        return solar.atTime(lunar.time).atZone(ZONE)
    }

    /** 양력 ZonedDateTime → 음력 일시. KST로 환산한 날짜를 음력으로, 시각은 그대로 보존한다. 범위 밖이면 예외. */
    fun toLunarDateTime(zoned: ZonedDateTime): LunarDateTime {
        val kst = zoned.withZoneSameInstant(ZONE)
        val solar: LocalDate = kst.toLocalDate()
        require(!solar.isBefore(MIN_SOLAR_DATE) && !solar.isAfter(MAX_SOLAR_DATE)) {
            "solar $solar out of supported range $MIN_SOLAR_DATE..$MAX_SOLAR_DATE"
        }
        return decode(solar).copy(time = kst.toLocalTime())
    }

    /** 그 음력 연도의 윤달 위치(0=없음, 1..12). */
    fun leapMonthOf(year: Int): Int {
        require(year in LunarCodec.MIN_YEAR..LunarCodec.MAX_YEAR) { "lunar year $year has no data" }
        return LunarCodec.leapMonth(LunarCodec.yearData(year))
    }

    /** 음력 (year, month, isLeapMonth) 한 달의 일수(29 또는 30). 존재하지 않는 달/윤달이면 예외. */
    fun lunarMonthLength(
        year: Int,
        month: Int,
        isLeapMonth: Boolean,
    ): Int {
        require(year in LunarCodec.MIN_YEAR..LunarCodec.MAX_YEAR) { "lunar year $year has no data" }
        val encoded = LunarCodec.yearData(year)
        val chronoIndex = LunarCodec.chronoIndexOf(encoded, month, isLeapMonth)
        require(chronoIndex < LunarCodec.monthCount(encoded)) {
            "lunar $year-$month(leap=$isLeapMonth) does not exist"
        }
        return LunarCodec.monthDays(encoded, chronoIndex)
    }

    /** 양력 LocalDate → 음력(자정). 호출부가 데이터 범위(epoch ≤ solar)를 보장한다. */
    private fun decode(solar: LocalDate): LunarDateTime {
        var remaining = ChronoUnit.DAYS.between(KoreanLunarData.EPOCH, solar)
        var year = LunarCodec.MIN_YEAR
        while (true) {
            val length = LunarCodec.yearLength(LunarCodec.yearData(year))
            if (remaining < length) break
            remaining -= length
            year++
        }
        val encoded = LunarCodec.yearData(year)
        var chronoIndex = 0
        while (true) {
            val days = LunarCodec.monthDays(encoded, chronoIndex)
            if (remaining < days) break
            remaining -= days
            chronoIndex++
        }
        val (month, isLeap) = LunarCodec.monthOf(encoded, chronoIndex)
        return LunarDateTime(year, month, remaining.toInt() + 1, isLeap)
    }
}

/** 음력 일시 → 양력 ZonedDateTime(KST). */
fun LunarDateTime.toZonedDateTime(): ZonedDateTime = KoreanLunarCalendar.toZonedDateTime(this)

/** 양력 ZonedDateTime → 음력 일시(KST 기준). */
fun ZonedDateTime.toLunarDateTime(): LunarDateTime = KoreanLunarCalendar.toLunarDateTime(this)
