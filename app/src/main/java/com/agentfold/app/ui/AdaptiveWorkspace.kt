package com.agentfold.app.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

class AdaptiveWorkspace @JvmOverloads constructor(
    context: Context, private val cards: List<View> = emptyList()
) : LinearLayout(context) {
    enum class Panel(val label: String) { ACTIVITY("Activity"), TASK("Task"), FILES("Files"), CONTROL("Control"), HISTORY("History") }
    var selection = Panel.ACTIVITY
        private set
    private var wideMode = false
    private val left = column()
    private val right = column()
    private val leftParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
    private val rightParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
        marginStart = AgentTheme.dp(context, 18)
    }

    init { arrange(false) }

    fun select(panel: Panel) {
        selection = panel
        applyVisibility()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wide = MeasureSpec.getSize(widthMeasureSpec) / resources.displayMetrics.density >= 720
        if (wideMode != wide) arrange(wide)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun arrange(wide: Boolean) {
        wideMode = wide
        cards.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        removeAllViews()
        orientation = if (wide) HORIZONTAL else VERTICAL
        if (wide) {
            addView(left, leftParams)
            addView(right, rightParams)
            cards.forEachIndexed { index, view ->
                (if (index == 1 || index == 3) right else left).addView(view)
            }
        } else cards.forEach { addView(it) }
        applyVisibility()
    }

    private fun applyVisibility() {
        cards.forEachIndexed { index, view ->
            val visible = when (selection) {
                Panel.ACTIVITY -> index == 0 || index == 4 || wideMode && index == 1
                Panel.TASK -> index == 1
                Panel.FILES -> index == 2
                Panel.CONTROL -> index == 3
                Panel.HISTORY -> index == 4
            }
            view.visibility = if (visible) View.VISIBLE else View.GONE
        }
        if (wideMode) {
            val hasLeft = (0 until left.childCount).any { left.getChildAt(it).visibility == View.VISIBLE }
            val hasRight = (0 until right.childCount).any { right.getChildAt(it).visibility == View.VISIBLE }
            left.visibility = if (hasLeft) View.VISIBLE else View.GONE
            right.visibility = if (hasRight) View.VISIBLE else View.GONE
            rightParams.marginStart = if (hasLeft && hasRight) AgentTheme.dp(context, 18) else 0
        }
    }

    private fun column() = LinearLayout(context).apply { orientation = VERTICAL }
}
