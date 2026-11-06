package com.example.sharedchest.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChestLayoutTest {
    @Test
    fun `行数は 1 から 6 に収める`() {
        assertEquals(9, ChestLayout.slotsFor(0))
        assertEquals(27, ChestLayout.slotsFor(3))
        assertEquals(54, ChestLayout.slotsFor(99))
    }

    @Test
    fun `空なら設定どおりの大きさ`() {
        assertEquals(27, ChestLayout.sizeFor(3, -1))
    }

    @Test
    fun `設定に収まるならそのままの大きさ`() {
        assertEquals(27, ChestLayout.sizeFor(3, 26))
    }

    @Test
    fun `行数を減らしてもアイテムが入る大きさで開く`() {
        assertEquals(36, ChestLayout.sizeFor(3, 27))
        assertEquals(54, ChestLayout.sizeFor(1, 53))
    }

    @Test
    fun `新しい形式かどうかを判定できる`() {
        assertFalse(ItemSerializer.isLegacyFormat("v2:AAAA"))
        assertTrue(ItemSerializer.isLegacyFormat("rO0ABXc="))
    }
}
