package com.agentfold.app.ui

import android.widget.TextView

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import android.widget.EditText
import com.agentfold.app.R

object AgentTheme {
    val background = 0xff000000.toInt()
    val surface = 0xff000000.toInt()
    val raised = 0xff050200.toInt()
    var stroke = 0xffff9a1a.toInt()
    var accent = 0xffff9a1a.toInt()
    var accentSecondary = 0xffff5a10.toInt()
    val signal = 0xff6fffe0.toInt()
    val green = 0xff3dff7a.toInt()
    val text = 0xffff9a1a.toInt()
    var muted = 0xff5a3200.toInt()
    var danger = 0xffff2a1a.toInt()
    var error = 0xffff2a1a.toInt()

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()

    fun panel(context: Context, fill: Int = surface, border: Int = stroke, radius: Int = 0) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(context, radius).toFloat()
        setStroke(dp(context, 2), border)
    }

    fun style(button: Button, dangerButton: Boolean = false) {
        val context = button.context
        button.setTextColor(if (dangerButton) background else accent)
        button.typeface = monoTypeface(context)
        button.minHeight = dp(context, 48)
        button.minimumHeight = dp(context, 48)
        val edge = if (dangerButton) danger else accent
        button.background = RippleDrawable(ColorStateList.valueOf(0x44ff9a1a), panel(context, if (dangerButton) danger else background, edge), null)
        button.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
    }

    fun style(textView: TextView) {
        textView.setTextColor(text)
    }

    fun style(input: EditText) {
        input.setTextColor(text)
        input.setHintTextColor(muted)
        input.typeface = monoTypeface(input.context)
        input.background = panel(input.context, background, stroke)
        val padding = dp(input.context, 14)
        input.setPadding(padding, padding, padding, padding)
        input.backgroundTintList = null
    }

    fun styleTab(button: Button, selected: Boolean) {
        button.setTextColor(if (selected) background else accent)
        button.typeface = monoTypeface(button.context)
        button.background = RippleDrawable(ColorStateList.valueOf(0x44ff9a1a), panel(button.context, if (selected) accent else background, if (selected) accent else muted), null)
    }

    fun monoTypeface(context: Context): Typeface = context.resources.getFont(R.font.share_tech_mono)
    fun minchoTypeface(context: Context): Typeface = context.resources.getFont(R.font.shippori_mincho_b1_extra_bold)
}
