package com.whatsticker.app.ui.screens.captures

import com.whatsticker.app.data.model.CapturedSticker

/** Kind of section shown in the unassigned captures grid. */
enum class CaptureSectionKind { ANIMATED, STATIC }

/** A non-empty group of captures of a single kind, already sorted for display. */
data class CaptureSection(
    val kind: CaptureSectionKind,
    val captures: List<CapturedSticker>
)

/** Which sections and empty state the screen must show. */
data class CaptureSectionVisibility(
    val showAnimated: Boolean,
    val showStatic: Boolean,
    val showEmptyState: Boolean
)

/**
 * Splits captures by [CapturedSticker.isAnimated], sorts each group by createdAt descending
 * (ties broken by id descending) and omits empty groups. Animated section comes first.
 * Selection is by id, so it is not affected by grouping.
 */
fun groupUnassignedCaptures(captures: List<CapturedSticker>): List<CaptureSection> {
    val order = compareByDescending<CapturedSticker> { it.createdAt }.thenByDescending { it.id }
    val (animated, static) = captures.partition { it.isAnimated }
    return listOf(
        CaptureSection(CaptureSectionKind.ANIMATED, animated.sortedWith(order)),
        CaptureSection(CaptureSectionKind.STATIC, static.sortedWith(order))
    ).filter { it.captures.isNotEmpty() }
}

/** Decides which headers and empty state are shown for the given grouped sections. */
fun sectionVisibility(sections: List<CaptureSection>): CaptureSectionVisibility {
    val nonEmpty = sections.filter { it.captures.isNotEmpty() }
    return CaptureSectionVisibility(
        showAnimated = nonEmpty.any { it.kind == CaptureSectionKind.ANIMATED },
        showStatic = nonEmpty.any { it.kind == CaptureSectionKind.STATIC },
        showEmptyState = nonEmpty.isEmpty()
    )
}
