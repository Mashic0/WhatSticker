package com.whatsticker.app.service

import com.whatsticker.app.util.AnimatedWebpInspection
import com.whatsticker.app.util.isAcceptableAnimatedWebp

/** Outcome of the platform animated-decode check performed on the candidate file. */
enum class DecodeCheck {
    /** API < 28: no real decode check is available. */
    NOT_APPLICABLE,
    CONFIRMED_ANIMATED,
    FAILED
}

enum class SaveDisposition { INSERT, DISCARD }

/**
 * Decides whether a freshly encoded animated candidate may be registered.
 * Kept free of side effects so it is testable on the JVM; the caller performs the
 * file deletion and Room insertion (effects) according to the result.
 */
fun saveDisposition(inspection: AnimatedWebpInspection, decodeCheck: DecodeCheck): SaveDisposition =
    when {
        !isAcceptableAnimatedWebp(inspection) -> SaveDisposition.DISCARD
        decodeCheck == DecodeCheck.FAILED -> SaveDisposition.DISCARD
        else -> SaveDisposition.INSERT
    }
