package com.agentfold.app.control

enum class Verification { CHANGED, UNCHANGED, TARGET_LOST, CANCELLED }

/** A changed UI is evidence of an effect, never proof that the user's objective succeeded. */
object ActionVerification {
    fun compare(before: String, after: String?, sameWindow: Boolean): Verification = when {
        after == null || !sameWindow -> Verification.TARGET_LOST
        before != after -> Verification.CHANGED
        else -> Verification.UNCHANGED
    }
}
