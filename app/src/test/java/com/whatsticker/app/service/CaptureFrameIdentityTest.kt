package com.whatsticker.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureFrameIdentityTest {

    private fun frame(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()

    @Test
    fun `empty list is not identical`() {
        assertFalse(allByteFramesIdentical(emptyList()))
    }

    @Test
    fun `single frame is treated as no movement`() {
        assertTrue(allByteFramesIdentical(listOf(frame(1, 2, 3))))
    }

    @Test
    fun `identical frames are detected`() {
        assertTrue(allByteFramesIdentical(listOf(frame(1, 2, 3), frame(1, 2, 3), frame(1, 2, 3))))
    }

    @Test
    fun `different frames are not identical`() {
        assertFalse(allByteFramesIdentical(listOf(frame(1, 2, 3), frame(1, 2, 4))))
    }

    @Test
    fun `same length different content is not identical`() {
        assertFalse(allByteFramesIdentical(listOf(frame(9, 9, 9), frame(9, 9, 8), frame(9, 9, 9))))
    }
}
