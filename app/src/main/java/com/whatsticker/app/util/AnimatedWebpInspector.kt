package com.whatsticker.app.util

/** Why an animated WebP candidate was rejected. Only the first failing rule is reported. */
enum class AnimatedWebpRejection {
    EMPTY_OR_TOO_SHORT,
    NOT_RIFF,
    RIFF_SIZE_MISMATCH,
    NOT_WEBP,
    CHUNK_TRUNCATED,
    VP8X_TOO_SMALL,
    ANMF_TOO_SMALL,
    MISSING_VP8X,
    ANIMATION_FLAG_NOT_SET,
    MISSING_ANIM,
    TOO_FEW_FRAMES
}

data class AnimatedWebpInspection(
    /** True when RIFF header, declared size and the whole chunk walk are structurally sound. */
    val isContainerValid: Boolean,
    val rejection: AnimatedWebpRejection?,
    val hasVp8x: Boolean,
    val hasAnimationFlag: Boolean,
    val hasAnim: Boolean,
    val frameCount: Int,
    /** Declared RIFF payload size + 8 header bytes, or -1 when the header was unreadable. */
    val declaredSize: Long,
    val actualSize: Long
)

const val MIN_ANIMATED_FRAMES = 2

private const val RIFF_HEADER_SIZE = 12
private const val CHUNK_HEADER_SIZE = 8
private const val VP8X_MIN_PAYLOAD = 10
private const val ANMF_MIN_PAYLOAD = 16
private const val VP8X_ANIMATION_FLAG = 0x02

private fun ByteArray.matchesAscii(offset: Int, text: String): Boolean {
    if (offset < 0 || offset + text.length > size) return false
    return text.indices.all { this[offset + it] == text[it].code.toByte() }
}

private fun ByteArray.readUInt32Le(offset: Int): Long =
    (this[offset].toLong() and 0xFF) or
        ((this[offset + 1].toLong() and 0xFF) shl 8) or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)

/** Inspects a RIFF/WebP byte array. Pure and never throws on malformed input. */
fun inspectAnimatedWebp(bytes: ByteArray): AnimatedWebpInspection {
    val actual = bytes.size.toLong()
    var declared = -1L
    var hasVp8x = false
    var animationFlag = false
    var hasAnim = false
    var frames = 0

    fun result(rejection: AnimatedWebpRejection?, containerValid: Boolean) = AnimatedWebpInspection(
        isContainerValid = containerValid,
        rejection = rejection,
        hasVp8x = hasVp8x,
        hasAnimationFlag = animationFlag,
        hasAnim = hasAnim,
        frameCount = frames,
        declaredSize = declared,
        actualSize = actual
    )

    if (bytes.size < RIFF_HEADER_SIZE) return result(AnimatedWebpRejection.EMPTY_OR_TOO_SHORT, false)
    if (!bytes.matchesAscii(0, "RIFF")) return result(AnimatedWebpRejection.NOT_RIFF, false)
    declared = bytes.readUInt32Le(4) + CHUNK_HEADER_SIZE
    if (declared != actual) return result(AnimatedWebpRejection.RIFF_SIZE_MISMATCH, false)
    if (!bytes.matchesAscii(8, "WEBP")) return result(AnimatedWebpRejection.NOT_WEBP, false)

    var offset = RIFF_HEADER_SIZE.toLong()
    while (offset < actual) {
        if (offset + CHUNK_HEADER_SIZE > actual) return result(AnimatedWebpRejection.CHUNK_TRUNCATED, false)
        val pos = offset.toInt()
        val payload = bytes.readUInt32Le(pos + 4)
        val payloadStart = offset + CHUNK_HEADER_SIZE
        // Odd-sized payloads are followed by one padding byte that is not part of the size.
        val next = payloadStart + payload + (payload and 1L)
        if (next > actual) return result(AnimatedWebpRejection.CHUNK_TRUNCATED, false)
        val start = payloadStart.toInt()
        when {
            bytes.matchesAscii(pos, "VP8X") -> {
                if (payload < VP8X_MIN_PAYLOAD) return result(AnimatedWebpRejection.VP8X_TOO_SMALL, false)
                if (!hasVp8x) {
                    hasVp8x = true
                    animationFlag = (bytes[start].toInt() and VP8X_ANIMATION_FLAG) != 0
                }
            }
            bytes.matchesAscii(pos, "ANIM") -> hasAnim = true
            bytes.matchesAscii(pos, "ANMF") -> {
                if (payload < ANMF_MIN_PAYLOAD) return result(AnimatedWebpRejection.ANMF_TOO_SMALL, false)
                frames++
            }
        }
        offset = next
    }

    val rejection = when {
        !hasVp8x -> AnimatedWebpRejection.MISSING_VP8X
        !animationFlag -> AnimatedWebpRejection.ANIMATION_FLAG_NOT_SET
        !hasAnim -> AnimatedWebpRejection.MISSING_ANIM
        frames < MIN_ANIMATED_FRAMES -> AnimatedWebpRejection.TOO_FEW_FRAMES
        else -> null
    }
    return result(rejection, true)
}

/** Accepts only a sound container that declares animation and holds at least two ANMF frames. */
fun isAcceptableAnimatedWebp(inspection: AnimatedWebpInspection): Boolean =
    inspection.isContainerValid &&
        inspection.rejection == null &&
        inspection.hasVp8x &&
        inspection.hasAnimationFlag &&
        inspection.hasAnim &&
        inspection.frameCount >= MIN_ANIMATED_FRAMES
