package com.battor.freshmate.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditPagesTest {

    @Test
    fun `全空时只有表单页`() {
        val structure = editingPages(hasSession = false, hasExisting = false)
        assertEquals(listOf(EditPageKind.FORM), structure.pages)
        assertEquals(0, structure.defaultIndex)
    }

    @Test
    fun `无会话有既有时两段式`() {
        val structure = editingPages(hasSession = false, hasExisting = true)
        assertEquals(listOf(EditPageKind.FORM, EditPageKind.EXISTING), structure.pages)
        assertEquals(0, structure.defaultIndex)
    }

    @Test
    fun `有会话无既有时表单在第二页`() {
        val structure = editingPages(hasSession = true, hasExisting = false)
        assertEquals(listOf(EditPageKind.SESSION, EditPageKind.FORM), structure.pages)
        assertEquals(1, structure.defaultIndex)
    }

    @Test
    fun `三俱全是三段式`() {
        val structure = editingPages(hasSession = true, hasExisting = true)
        assertEquals(
            listOf(EditPageKind.SESSION, EditPageKind.FORM, EditPageKind.EXISTING),
            structure.pages,
        )
        assertEquals(1, structure.defaultIndex)
    }

    @Test
    fun `会话段上梯形隐藏下梯形继续编辑`() {
        val (top, bottom) = trapLabels(EditPageKind.SESSION, sessionCount = 2, hasExisting = true)
        assertNull(top)
        assertEquals(TrapLabel.ContinueEditDown, bottom)
    }

    @Test
    fun `既有段上梯形继续编辑下梯形隐藏`() {
        val (top, bottom) = trapLabels(EditPageKind.EXISTING, sessionCount = 2, hasExisting = true)
        assertEquals(TrapLabel.ContinueEditUp, top)
        assertNull(bottom)
    }

    @Test
    fun `表单段会话零条时上梯形隐藏`() {
        val (top, bottom) = trapLabels(EditPageKind.FORM, sessionCount = 0, hasExisting = true)
        assertNull(top)
        assertEquals(TrapLabel.ViewExisting, bottom)
    }

    @Test
    fun `表单段无既有项时下梯形隐藏`() {
        val (top, bottom) = trapLabels(EditPageKind.FORM, sessionCount = 3, hasExisting = false)
        assertEquals(TrapLabel.SessionCount(3), top)
        assertNull(bottom)
    }

    @Test
    fun `表单段满配两条都在`() {
        val (top, bottom) = trapLabels(EditPageKind.FORM, sessionCount = 3, hasExisting = true)
        assertEquals(TrapLabel.SessionCount(3), top)
        assertEquals(TrapLabel.ViewExisting, bottom)
    }
}
