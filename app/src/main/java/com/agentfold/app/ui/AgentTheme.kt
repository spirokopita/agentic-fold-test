package com.agentfold.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import android.widget.EditText

/** Borg/network references expressed as native surfaces, rings and legible type. */
object AgentTheme {
    val background = 0xff000000.toInt()
    val surface = 0xff000000.toInt()
    val raised = 0xff02070b.toInt()
    val stroke = 0xff103644.toInt()
    val accent = 0xff00bbff.toInt()
    val signal = 0xffeefaff.toInt()
    val text = 0xffeefaff.toInt()
    val muted = 0xff91aab8.toInt()
    val danger = 0xff77273a.toInt()
    val error = 0xffffa4b4.toInt()

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun panel(context: Context, fill: Int = surface, border: Int = stroke, radius: Int = 18) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(context, radius).toFloat(); setStroke(dp(context, 1), border)
    }
    fun style(button: Button, dangerButton: Boolean = false) {
        val context = button.context
        button.setTextColor(if (dangerButton) text else accent)
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.minHeight = dp(context, 48); button.minimumHeight = dp(context, 48)
        button.background = RippleDrawable(ColorStateList.valueOf(0x3300bbff),
            panel(context, if (dangerButton) danger else raised, if (dangerButton) danger else stroke, 12), null)
        button.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
    }
    fun style(input: EditText) {
        input.setTextColor(text); input.setHintTextColor(muted)
        input.background = panel(input.context, background, stroke, 12)
        val d = dp(input.context, 14); input.setPadding(d, d, d, d)
        input.backgroundTintList = null
    }
    fun styleTab(button: Button, selected: Boolean) {
        button.setTextColor(if (selected) accent else muted)
        button.background = RippleDrawable(ColorStateList.valueOf(0x3300bbff),
            panel(button.context, if (selected) raised else surface, if (selected) accent else stroke, 12), null)
    }
}
