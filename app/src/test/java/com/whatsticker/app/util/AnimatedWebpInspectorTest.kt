package com.whatsticker.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedWebpInspectorTest {

    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /** Builds a chunk; padding is appended for odd sizes unless [pad] is false. */
    private fun chunk(fourCc: String, payload: ByteArray, pad: Boolean = true): ByteArray =
        fourCc.toByteArray(Charsets.US_ASCII) + le32(payload.size) + payload +
            if (payload.size % 2 == 1 && pad) byteArrayOf(0) else byteArrayOf()

    private fun riff(body: ByteArray, sizeDelta: Int = 0, tag: String = "WEBP", magic: String = "RIFF"): ByteArray =
        magic.toByteArray(Charsets.US_ASCII) + le32(4 + body.size + sizeDelta) + tag.toByteArray(Charsets.US_ASCII) + body

    private fun vp8x(flags: Int = 0x02) = chunk("VP8X", byteArrayOf(flags.toByte()) + ByteArray(9))
    private fun anim() = chunk("ANIM", ByteArray(6))
    private fun anmf(size: Int = 20) = chunk("ANMF", ByteArray(size))

    private fun validBody(frames: Int = 2) =
        vp8x() + anim() + (1..frames).fold(byteArrayOf()) { acc, _ -> acc + anmf() }

    @Test
    fun validContainerWithTwoFramesIsAccepted() {
        val r = inspectAnimatedWebp(riff(validBody()))
        assertTrue(r.isContainerValid)
        assertEquals(null, r.rejection)
        assertTrue(r.hasVp8x && r.hasAnimationFlag && r.hasAnim)
        assertEquals(2, r.frameCount)
        assertEquals(r.declaredSize, r.actualSize)
        assertTrue(isAcceptableAnimatedWebp(r))
    }

    @Test
    fun emptyBytesAreRejected() {
        val r = inspectAnimatedWebp(ByteArray(0))
        assertEquals(AnimatedWebpRejection.EMPTY_OR_TOO_SHORT, r.rejection)
        assertFalse(isAcceptableAnimatedWebp(r))
    }

    @Test
    fun missingRiffIsRejected() {
        val r = inspectAnimatedWebp(riff(validBody(), magic = "RIFX"))
        assertEquals(AnimatedWebpRejection.NOT_RIFF, r.rejection)
    }

    @Test
    fun missingWebpSignatureIsRejected() {
        val r = inspectAnimatedWebp(riff(validBody(), tag = "WEBQ"))
        assertEquals(AnimatedWebpRejection.NOT_WEBP, r.rejection)
    }

    @Test
    fun wrongDeclaredRiffSizeIsRejected() {
        assertEquals(AnimatedWebpRejection.RIFF_SIZE_MISMATCH, inspectAnimatedWebp(riff(validBody(), sizeDelta = 2)).rejection)
        assertEquals(AnimatedWebpRejection.RIFF_SIZE_MISMATCH, inspectAnimatedWebp(riff(validBody(), sizeDelta = -2)).rejection)
    }

    @Test
    fun truncatedChunkIsRejected() {
        // Last ANMF declares 20 bytes but only 5 are present; RIFF size matches actual bytes.
        val truncated = validBody(1) + "ANMF".toByteArray() + le32(20) + ByteArray(5)
        val r = inspectAnimatedWebp(riff(truncated))
        assertEquals(AnimatedWebpRejection.CHUNK_TRUNCATED, r.rejection)
        assertFalse(r.isContainerValid)
    }

    @Test
    fun trailingBytesShorterThanChunkHeaderAreRejected() {
        val r = inspectAnimatedWebp(riff(validBody() + ByteArray(3)))
        assertEquals(AnimatedWebpRejection.CHUNK_TRUNCATED, r.rejection)
    }

    @Test
    fun oddSizedChunkWithPaddingIsAccepted() {
        val body = vp8x() + anim() + chunk("JUNK", ByteArray(3)) + anmf() + anmf()
        val r = inspectAnimatedWebp(riff(body))
        assertTrue(isAcceptableAnimatedWebp(r))
        assertEquals(2, r.frameCount)
    }

    @Test
    fun oddSizedChunkWithoutPaddingIsRejected() {
        val body = vp8x() + anim() + anmf() + anmf() + chunk("JUNK", ByteArray(3), pad = false)
        val r = inspectAnimatedWebp(riff(body))
        assertEquals(AnimatedWebpRejection.CHUNK_TRUNCATED, r.rejection)
    }

    @Test
    fun oddSizedAnmfPaddingIsCountedInWalk() {
        // ANMF with 21-byte payload (odd) must be padded so the next ANMF is found.
        val body = vp8x() + anim() + anmf(21) + anmf()
        val r = inspectAnimatedWebp(riff(body))
        assertEquals(2, r.frameCount)
        assertTrue(isAcceptableAnimatedWebp(r))
    }

    @Test
    fun missingVp8xIsRejected() {
        val r = inspectAnimatedWebp(riff(anim() + anmf() + anmf()))
        assertEquals(AnimatedWebpRejection.MISSING_VP8X, r.rejection)
        assertFalse(r.hasVp8x)
        assertFalse(isAcceptableAnimatedWebp(r))
    }

    @Test
    fun vp8xTooSmallIsRejected() {
        val r = inspectAnimatedWebp(riff(chunk("VP8X", ByteArray(4)) + anim() + anmf() + anmf()))
        assertEquals(AnimatedWebpRejection.VP8X_TOO_SMALL, r.rejection)
    }

    @Test
    fun vp8xWithoutAnimationFlagIsRejected() {
        val r = inspectAnimatedWebp(riff(vp8x(flags = 0x10) + anim() + anmf() + anmf()))
        assertEquals(AnimatedWebpRejection.ANIMATION_FLAG_NOT_SET, r.rejection)
        assertTrue(r.isContainerValid)
        assertFalse(isAcceptableAnimatedWebp(r))
    }

    @Test
    fun missingAnimIsRejected() {
        val r = inspectAnimatedWebp(riff(vp8x() + anmf() + anmf()))
        assertEquals(AnimatedWebpRejection.MISSING_ANIM, r.rejection)
    }

    @Test
    fun singleFrameIsRejected() {
        val r = inspectAnimatedWebp(riff(validBody(1)))
        assertEquals(AnimatedWebpRejection.TOO_FEW_FRAMES, r.rejection)
        assertEquals(1, r.frameCount)
        assertFalse(isAcceptableAnimatedWebp(r))
    }

    @Test
    fun anmfBelowMinimumPayloadIsRejected() {
        val r = inspectAnimatedWebp(riff(vp8x() + anim() + anmf() + anmf(10)))
        assertEquals(AnimatedWebpRejection.ANMF_TOO_SMALL, r.rejection)
    }

    @Test
    fun absurdChunkSizeDoesNotThrow() {
        val body = vp8x() + anim() + "ANMF".toByteArray() + byteArrayOf(-1, -1, -1, -1)
        val r = inspectAnimatedWebp(riff(body))
        assertEquals(AnimatedWebpRejection.CHUNK_TRUNCATED, r.rejection)
    }

    @Test
    fun absurdRiffSizeDoesNotThrow() {
        val bytes = "RIFF".toByteArray() + byteArrayOf(-1, -1, -1, -1) + "WEBP".toByteArray()
        assertEquals(AnimatedWebpRejection.RIFF_SIZE_MISMATCH, inspectAnimatedWebp(bytes).rejection)
    }
}
