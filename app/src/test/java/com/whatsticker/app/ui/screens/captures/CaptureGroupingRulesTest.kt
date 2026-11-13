package com.whatsticker.app.ui.screens.captures

import com.whatsticker.app.data.model.CapturedSticker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureGroupingRulesTest {

    private fun capture(id: Long, animated: Boolean, createdAt: Long) =
        CapturedSticker(id = id, filePath = "f$id", isAnimated = animated, createdAt = createdAt)

    @Test
    fun emptyList_returnsNoSectionsAndEmptyState() {
        val sections = groupUnassignedCaptures(emptyList())
        assertTrue(sections.isEmpty())
        val v = sectionVisibility(sections)
        assertTrue(v.showEmptyState)
        assertFalse(v.showAnimated)
        assertFalse(v.showStatic)
    }

    @Test
    fun onlyAnimated_returnsSingleAnimatedSection() {
        val sections = groupUnassignedCaptures(listOf(capture(1, true, 1), capture(2, true, 2)))
        assertEquals(listOf(CaptureSectionKind.ANIMATED), sections.map { it.kind })
        assertEquals(listOf(2L, 1L), sections[0].captures.map { it.id })
        val v = sectionVisibility(sections)
        assertTrue(v.showAnimated)
        assertFalse(v.showStatic)
        assertFalse(v.showEmptyState)
    }

    @Test
    fun onlyStatic_returnsSingleStaticSection() {
        val sections = groupUnassignedCaptures(listOf(capture(1, false, 5)))
        assertEquals(listOf(CaptureSectionKind.STATIC), sections.map { it.kind })
        val v = sectionVisibility(sections)
        assertFalse(v.showAnimated)
        assertTrue(v.showStatic)
        assertFalse(v.showEmptyState)
    }

    @Test
    fun mixed_animatedFirst_andNoCrossContamination() {
        val sections = groupUnassignedCaptures(
            listOf(capture(1, false, 10), capture(2, true, 20), capture(3, false, 30), capture(4, true, 5))
        )
        assertEquals(listOf(CaptureSectionKind.ANIMATED, CaptureSectionKind.STATIC), sections.map { it.kind })
        assertTrue(sections[0].captures.all { it.isAnimated })
        assertTrue(sections[1].captures.none { it.isAnimated })
        assertEquals(listOf(2L, 4L), sections[0].captures.map { it.id })
        assertEquals(listOf(3L, 1L), sections[1].captures.map { it.id })
        assertTrue(sections.all { it.captures.isNotEmpty() })
        val v = sectionVisibility(sections)
        assertTrue(v.showAnimated && v.showStatic)
        assertFalse(v.showEmptyState)
    }

    @Test
    fun unorderedInput_isSortedByCreatedAtDescending() {
        val sections = groupUnassignedCaptures(
            listOf(capture(1, false, 2), capture(2, false, 9), capture(3, false, 5))
        )
        assertEquals(listOf(2L, 3L, 1L), sections[0].captures.map { it.id })
    }

    @Test
    fun equalDates_tieBrokenByIdDescending_deterministically() {
        val input = listOf(capture(1, true, 7), capture(3, true, 7), capture(2, true, 7))
        val expected = listOf(3L, 2L, 1L)
        assertEquals(expected, groupUnassignedCaptures(input)[0].captures.map { it.id })
        assertEquals(expected, groupUnassignedCaptures(input.reversed())[0].captures.map { it.id })
    }

    @Test
    fun grouping_doesNotLoseOrDuplicateCaptures() {
        val input = listOf(capture(1, true, 1), capture(2, false, 1), capture(3, true, 3))
        val ids = groupUnassignedCaptures(input).flatMap { it.captures }.map { it.id }.sorted()
        assertEquals(listOf(1L, 2L, 3L), ids)
    }
}
