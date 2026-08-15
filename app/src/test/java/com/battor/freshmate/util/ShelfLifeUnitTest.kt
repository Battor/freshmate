package com.battor.freshmate.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfLifeUnitTest {
    @Test
    fun `换算天`() = assertEquals(7, shelfLifeToDays(7, ShelfLifeUnit.DAY))

    @Test
    fun `换算周`() = assertEquals(14, shelfLifeToDays(2, ShelfLifeUnit.WEEK))

    @Test
    fun `换算月`() = assertEquals(540, shelfLifeToDays(18, ShelfLifeUnit.MONTH))

    @Test
    fun `换算年`() = assertEquals(365, shelfLifeToDays(1, ShelfLifeUnit.YEAR))

    @Test
    fun `单位标签`() {
        assertEquals("天", ShelfLifeUnit.DAY.label)
        assertEquals("年", ShelfLifeUnit.YEAR.label)
    }
}
