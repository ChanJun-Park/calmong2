package com.jingom.calmong.core.datetime

/**
 * [KoreanLunarData]의 연도별 Int 인코딩을 해석하는 내부 코덱.
 *
 * 인코딩(각 연도 Int): bit 0..12 = 시간순 월 대소(1=30일/0=29일),
 * bit 13..16 = 윤달 위치(0=없음, 1..12), bit 17..21 = 그 해 음력 월 수.
 * 변환기([KoreanLunarCalendar])와 음력 월 질의가 공유한다.
 */
internal object LunarCodec {
    const val MIN_YEAR = KoreanLunarData.MIN_YEAR
    const val MAX_YEAR = KoreanLunarData.MAX_YEAR

    fun yearData(year: Int): Int = KoreanLunarData.YEARS[year - MIN_YEAR]

    fun monthCount(encoded: Int): Int = (encoded ushr COUNT_SHIFT) and COUNT_MASK

    fun leapMonth(encoded: Int): Int = (encoded ushr LEAP_SHIFT) and LEAP_MASK

    /** 시간순 [chronoIndex] 월의 일수(29 또는 30). */
    fun monthDays(
        encoded: Int,
        chronoIndex: Int,
    ): Int = if ((encoded ushr chronoIndex) and 1 == 1) LONG_MONTH else SHORT_MONTH

    fun yearLength(encoded: Int): Int {
        var sum = 0
        for (index in 0 until monthCount(encoded)) sum += monthDays(encoded, index)
        return sum
    }

    /** (음력 월, 윤달 여부) → 시간순 인덱스. 존재하지 않는 윤달이면 예외. */
    fun chronoIndexOf(
        encoded: Int,
        month: Int,
        isLeap: Boolean,
    ): Int {
        val leap = leapMonth(encoded)
        if (isLeap) {
            require(leap == month) { "lunar month $month is not a leap month this year" }
            return leap
        }
        return if (leap == 0 || month <= leap) month - 1 else month
    }

    /** 시간순 인덱스 → (음력 월, 윤달 여부). [chronoIndexOf]의 역. */
    fun monthOf(
        encoded: Int,
        chronoIndex: Int,
    ): Pair<Int, Boolean> {
        val leap = leapMonth(encoded)
        return when {
            leap == 0 || chronoIndex < leap -> (chronoIndex + 1) to false
            chronoIndex == leap -> leap to true
            else -> chronoIndex to false
        }
    }

    private const val LEAP_SHIFT = 13
    private const val COUNT_SHIFT = 17
    private const val LEAP_MASK = 0xF
    private const val COUNT_MASK = 0x1F
    private const val LONG_MONTH = 30
    private const val SHORT_MONTH = 29
}
