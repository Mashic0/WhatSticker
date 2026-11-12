package com.whatsticker.app.service

import com.whatsticker.app.util.AnimatedWebpInspection
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimatedStickerSaveRulesTest {

    private fun inspection(
        containerValid: Boolean = true,
        rejection: com.whatsticker.app.util.AnimatedWebpRejection? = null,
        frames: Int = 2
    ) = AnimatedWebpInspection(
        isContainerValid = containerValid,
        rejection = rejection,
        hasVp8x = true,
        hasAnimationFlag = true,
        hasAnim = true,
        frameCount = frames,
        declaredSize = 100,
        actualSize = 100
    )

    @Test
    fun validOnApi28WithDecodeOkInserts() {
        assertEquals(SaveDisposition.INSERT, saveDisposition(inspection(), DecodeCheck.CONFIRMED_ANIMATED))
    }

    @Test
    fun validOnOldApiWithoutDecodeCheckInserts() {
        assertEquals(SaveDisposition.INSERT, saveDisposition(inspection(), DecodeCheck.NOT_APPLICABLE))
    }

    @Test
    fun invalidStructureDiscards() {
        val bad = inspection(containerValid = false, rejection = com.whatsticker.app.util.AnimatedWebpRejection.CHUNK_TRUNCATED)
        assertEquals(SaveDisposition.DISCARD, saveDisposition(bad, DecodeCheck.NOT_APPLICABLE))
        assertEquals(SaveDisposition.DISCARD, saveDisposition(bad, DecodeCheck.CONFIRMED_ANIMATED))
    }

    @Test
    fun singleFrameDiscards() {
        val one = inspection(rejection = com.whatsticker.app.util.AnimatedWebpRejection.TOO_FEW_FRAMES, frames = 1)
        assertEquals(SaveDisposition.DISCARD, saveDisposition(one, DecodeCheck.CONFIRMED_ANIMATED))
        assertEquals(SaveDisposition.DISCARD, saveDisposition(inspection(frames = 1), DecodeCheck.NOT_APPLICABLE))
    }

    @Test
    fun failedRealDecodeDiscards() {
        assertEquals(SaveDisposition.DISCARD, saveDisposition(inspection(), DecodeCheck.FAILED))
    }
}
