package com.agentfold.app.control

enum class Verification { CHANGED, UNCHANGED, TARGET_LOST, CANCELLED }

object ActionVerification {
    fun compare(before: String, after: String?, sameWindow: Boolean): Verification = when {
        after == null || !sameWindow -> Verification.TARGET_LOST
        before != after -> Verification.CHANGED
        else -> Verification.UNCHANGED
    }
}
