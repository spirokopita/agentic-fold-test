package com.agentfold.app.nerv

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager

object AccessibilityHelper {

    fun label(view: View, text: String) {
        view.contentDescription = text
    }

    fun decorative(view: View) {
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.isFocusable = false
    }

    fun announce(view: View, text: String) {
        view.announceForAccessibility(text)
    }

    fun screenReaderOn(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            manager?.isEnabled == true && manager.isTouchExplorationEnabled
        } catch (_: Exception) {
            false
        }
    }
}
