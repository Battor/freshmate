package com.battor.freshmate.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideStateMachineTest {
    @Test
    fun `初始停在第一步`() {
        val machine = GuideStateMachine(stepCount = 5)
        assertEquals(0, machine.current)
        assertFalse(machine.isLast)
    }

    @Test
    fun `逐步推进到末步`() {
        val machine = GuideStateMachine(stepCount = 3)
        machine.next()
        assertEquals(1, machine.current)
        assertFalse(machine.isLast)
        machine.next()
        assertEquals(2, machine.current)
        assertTrue(machine.isLast)
    }

    @Test
    fun `末步再推进不越界`() {
        val machine = GuideStateMachine(stepCount = 2)
        machine.next()
        machine.next()
        machine.next()
        assertEquals(1, machine.current)
    }
}
