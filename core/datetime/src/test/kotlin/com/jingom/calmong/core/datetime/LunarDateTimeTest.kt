package com.jingom.calmong.core.datetime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalTime

class LunarDateTimeTest {
    @Test
    fun `기본 시각은 자정이다`() {
        assertEquals(LocalTime.MIDNIGHT, LunarDateTime(2024, 1, 1).time)
    }

    @Test
    fun `월은 1에서 12 범위여야 한다`() {
        assertThrows(IllegalArgumentException::class.java) { LunarDateTime(2024, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { LunarDateTime(2024, 13, 1) }
    }

    @Test
    fun `일은 1에서 30 범위여야 한다`() {
        assertThrows(IllegalArgumentException::class.java) { LunarDateTime(2024, 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { LunarDateTime(2024, 1, 31) }
    }
}
