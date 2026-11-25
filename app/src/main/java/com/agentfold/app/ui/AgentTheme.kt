package com.agentfold.app.ui

// MAGI EVOLVER — five themes, 25 Nov 2026.
//
// What changed in this file and why:
// - Every colour is now a var, set by NervActivity.applyTheme() from the
//   active palette. Before this, text, background, surface, signal and
//   green were fixed vals (text was fixed orange 0xffff9a1a) — a
//   monochrome theme would still have had orange text, and Borg green
//   would still have had orange text. Now the whole UI follows the
//   palette: accent, accentSecondary, muted, text, background, signal,
//   danger, green, stroke, surface and raised.
// - Shape parameters, also set by applyTheme(): cornerRadiusDp,
//   strokeWidthDp, fillAlpha (0x00 = flat fill, higher = a subtle
//   accent-tinted fill) and chamferDp. panel() uses them as its defaults,
//   so every button, tab and panel takes the active theme's shape.
//   chamferDp > 0 (cyberpunk red and mono, per their reference images)
//   gives cut corners — an octagon-ish chamfer built from the standard
//   ShapeDrawable + PathShape + LayerDrawable classes. No new custom
//   View: the MAGI node boxes already clip themselves to a chamfered
//   path on main (MagiNodeLayout), and this helper covers the rest.
// - The ripple tint in style() / styleTab() derives from the active
//   accent (accent with alpha) instead of hardcoded orange 0x44ff9a1a.
//   Same for the shell buttons in NervActivity, which use
//   rippleTint() too.
// - The PixelCanvas background is NOT themed here — it stays identical
//   across all five themes, as Spiros asked. Only the styling of what
//   sits on it changes.

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.PathShape
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.agentfold.app.R

object AgentTheme {
    // Colours — all vars, set by applyTheme() from the active palette.
    // The amber defaults below are exactly the old fixed values, so the
    // default theme looks identical to before this change.
    var background = 0xff000000.toInt()
    var surface = 0xff000000.toInt()
    var raised = 0xff050200.toInt()
    var stroke = 0xffff9a1a.toInt()
    var accent = 0xffff9a1a.toInt()
    var accentSecondary = 0xffff5a10.toInt()
    var signal = 0xff6fffe0.toInt()
    var green = 0xff3dff7a.toInt()
    var text = 0xffff9a1a.toInt()
    var muted = 0xff5a3200.toInt()
    var danger = 0xffff2a1a.toInt()
    var error = 0xffff2a1a.toInt()

    // Shape — set by applyTheme() from the active palette.
    var cornerRadiusDp = 2
    var strokeWidthDp = 2
    // 0x00 = flat fill. Higher = a subtle accent-tinted fill (fleshy
    // purple uses 0x14).
    var fillAlpha = 0x00
    // 0 = normal corners (radius above). > 0 = cut corners, in dp — the
    // chamfered octagon look of the cyberpunk and mono reference images.
    var chamferDp = 0

    // Animation + density — set by applyTheme() from the active palette.
    // animFactor multiplies the battery-saver scale; it never overrides
    // reduce-motion or battery saver. minimal = the cleaner, sparser
    // screen configuration (cyberpunk, mono).
    var animFactor = 1.0f
    var minimal = false

    // Theme layout — per-theme screen configuration (MAGI EVOLVER fix,
    // 25 Nov 2026). Spiros called out that recolours were not enough:
    // each theme must lay its screens out DIFFERENTLY. The layout is
    // data, set by applyTheme() from the active palette, and the screens
    // read it when building their views — no screen is written five
    // times. Same PixelCanvas background in every theme; same screens,
    // same features, same navigation — only the arrangement differs.
    // STANDARD: the NERV layout as on main today (amber).
    //   VERTICAL: sparse vertical — big clock, single column of buttons,
    //     lots of empty space (cyberpunk).
    //   DENSE: dense multi-panel — tight gaps, extra readout/progress
    //     bars, panels packed in (borg).
    //   GRID: clean grid — buttons and tiles in a strict 2-column grid,
    //     thin lines, lots of space (mono).
    //   FLOW: organic flowing — rounded panels in a staggered vertical
    //     flow, no straight grid (flesh).
    enum class ThemeLayoutKind { STANDARD, VERTICAL, DENSE, GRID, FLOW }

    // How the three MAGI node boxes are arranged on the MAGI screen.
    enum class NodeArrangement { TRIANGLE, STACK, GRID, STAGGER }

    data class ThemeLayout(
        val kind: ThemeLayoutKind,
        val sectionGapDp: Int,    // gap between sections on a screen
        val itemGapDp: Int,       // gap between items in a row/column
        val panelPaddingDp: Int,  // padding inside bordered panels
        // Minimum button height. 32dp for STANDARD is the buttons'
        // natural height on main today (text + padding), so amber looks
        // unchanged; the other themes make their buttons taller.
        // It is a minimum, never a fixed height — buttons still grow
        // if the text size setting makes the text bigger.
        val buttonHeightDp: Int,
        val clockSp: Float,       // home clock text size
        val clockHeightDp: Int,   // home clock frame height
        val buttonColumns: Int,   // buttons per row (1 = single column)
        val tileColumns: Int,     // home tile grid columns (0 = dual-pane rule)
        val nodeArrangement: NodeArrangement,
        val showExtraReadouts: Boolean, // borg: extra readout/progress bars
        val staggerDp: Int        // FLOW: alternating left/right offset
    )

    val standardLayout = ThemeLayout(
        ThemeLayoutKind.STANDARD, 12, 6, 10, 32, 50f, 96, 4, 0,
        NodeArrangement.TRIANGLE, false, 0
    )
    val verticalLayout = ThemeLayout(
        ThemeLayoutKind.VERTICAL, 28, 14, 16, 64, 64f, 128, 1, 1,
        NodeArrangement.STACK, false, 0
    )
    val denseLayout = ThemeLayout(
        ThemeLayoutKind.DENSE, 6, 4, 6, 40, 40f, 76, 4, 4,
        NodeArrangement.TRIANGLE, true, 0
    )
    val gridLayout = ThemeLayout(
        ThemeLayoutKind.GRID, 16, 8, 12, 48, 50f, 96, 2, 2,
        NodeArrangement.GRID, false, 0
    )
    val flowLayout = ThemeLayout(
        ThemeLayoutKind.FLOW, 20, 12, 14, 52, 50f, 64, 1, 2,
        NodeArrangement.STAGGER, false, 28
    )

    var layout: ThemeLayout = standardLayout

    // Fonts — per-theme font configuration (MAGI EVOLVER, 25 Nov 2026).
    // SIX fonts are now in play. Two were already bundled in the app:
    //   MONO — Share Tech Mono (res/font/share_tech_mono.ttf).
    //   MINCHO — Shippori Mincho B1 Extra Bold, stored gzipped at
    //     app/font-source/ and unzipped into res/font/ by the
    //     unpackBundledFont task wired into preBuild.
    // Four more were downloaded by Spiros's instruction on 25 Nov 2026
    // and ship in this folder under fonts/ — copy them into
    // app/src/main/res/font/ when applying this build (see
    // CODEX_PROMPT.md). All four are free licences (Roboto Apache 2.0;
    // the rest SIL OFL 1.1), all verified as real static TTFs:
    //   THIN_SANS — Roboto Thin (roboto_thin.ttf, 157,272 bytes,
    //     1,326 glyphs). Instantiated from the Roboto variable font
    //     (google/fonts ofl/roboto/Roboto[wdth,wght].ttf) pinned to
    //     wght=100, wdth=100. Note: the file's internal name table
    //     still reads "Roboto Regular" — the instancer keeps the
    //     family name; the file itself is the thin weight.
    //   STENCIL — Black Ops One (black_ops_one.ttf, 166,532 bytes,
    //     760 glyphs). Static single-weight TTF from google/fonts.
    //   PIXEL — VT323 (vt323.ttf, 153,116 bytes, 587 glyphs). Static
    //     single-weight TTF from google/fonts.
    //   SERIF_ITALIC — Playfair Display Italic
    //     (playfair_display_italic.ttf, 177,848 bytes, 1,101 glyphs).
    //     Instantiated from the Playfair Display Italic variable font
    //     (google/fonts ofl/playfairdisplay/
    //     PlayfairDisplay-Italic[wght].ttf) pinned to wght=400.
    // A theme has a DISPLAY face (headings, node names, clock, Japanese)
    // and a BODY face (small labels, body text, buttons, tabs), each
    // with a style and a letter spacing. The four new files are already
    // the right weight/style (thin / stencil / pixel / italic), so
    // their themes use style NORMAL — never synthesise bold or italic
    // on top of them. Japanese text always uses MINCHO in every theme
    // — none of the other faces has CJK glyphs.
    enum class FontFamily { MONO, MINCHO, THIN_SANS, STENCIL, PIXEL, SERIF_ITALIC }

    data class ThemeFont(
        val displayFamily: FontFamily,
        val displayStyle: Int,
        val displaySpacingEm: Float,
        val bodyFamily: FontFamily,
        val bodyStyle: Int,
        val bodySpacingEm: Float,
        val forceCaps: Boolean
    )

    // amber: exactly as on main today — mincho headings, mono body,
    // no extra spacing, no forced caps.
    val amberFont = ThemeFont(FontFamily.MINCHO, Typeface.NORMAL, 0f, FontFamily.MONO, Typeface.NORMAL, 0f, false)
    // cyberpunk: Black Ops One everywhere, tight spacing, all caps —
    // the real stencil/military face of the reference image. The file
    // is already bold and stencil-cut, so style is NORMAL.
    val cyberpunkFont = ThemeFont(FontFamily.STENCIL, Typeface.NORMAL, 0.02f, FontFamily.STENCIL, Typeface.NORMAL, 0.02f, true)
    // borg: VT323 everywhere, wider spacing than cyberpunk, all caps —
    // the real pixel/terminal face of the reference image. The file is
    // already a pixel face, so style is NORMAL.
    val borgFont = ThemeFont(FontFamily.PIXEL, Typeface.NORMAL, 0.10f, FontFamily.PIXEL, Typeface.NORMAL, 0.10f, true)
    // mono: Roboto Thin everywhere, no bold, very wide spacing, mixed
    // case — the true thin elegant sans of the reference image.
    val monoFont = ThemeFont(FontFamily.THIN_SANS, Typeface.NORMAL, 0.18f, FontFamily.THIN_SANS, Typeface.NORMAL, 0.18f, false)
    // flesh: Playfair Display Italic for display (node names, clock,
    // headings), mono for small labels — the true italic serif of the
    // reference image. The file is already italic, so style is NORMAL.
    val fleshFont = ThemeFont(FontFamily.SERIF_ITALIC, Typeface.NORMAL, 0.04f, FontFamily.MONO, Typeface.NORMAL, 0.06f, false)

    var font: ThemeFont = amberFont

    private fun baseTypeface(context: Context, family: FontFamily): Typeface =
        when (family) {
            FontFamily.MONO -> monoTypeface(context)
            FontFamily.MINCHO -> minchoTypeface(context)
            FontFamily.THIN_SANS -> thinSansTypeface(context)
            FontFamily.STENCIL -> stencilTypeface(context)
            FontFamily.PIXEL -> pixelTypeface(context)
            FontFamily.SERIF_ITALIC -> serifItalicTypeface(context)
        }

    // Display typeface for the active theme: headings, node names,
    // clock, Japanese labels.
    fun displayTypeface(context: Context): Typeface {
        val base = baseTypeface(context, font.displayFamily)
        return if (font.displayStyle == Typeface.NORMAL) base else Typeface.create(base, font.displayStyle)
    }

    // Body typeface for the active theme: small labels, body text,
    // buttons, tabs, inputs.
    fun bodyTypeface(context: Context): Typeface {
        val base = baseTypeface(context, font.bodyFamily)
        return if (font.bodyStyle == Typeface.NORMAL) base else Typeface.create(base, font.bodyStyle)
    }

    // True when the text has any Japanese in it (hiragana, katakana,
    // kanji). Such text always uses the mincho face, in every theme.
    fun containsJapanese(text: String): Boolean =
        text.any { c ->
            (c in '\u3040'..'\u30ff') || (c in '\u4e00'..'\u9fff') || (c in '\u3400'..'\u4dbf')
        }

    // Upper-case a display label when the theme forces caps. Japanese
    // and digits are unaffected. Locale.ROOT so the result never
    // depends on the phone's language.
    fun capsText(value: String): String =
        if (font.forceCaps) value.uppercase(java.util.Locale.ROOT) else value

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()

    // The active accent with an alpha channel — used for ripples and
    // glows so they follow the theme instead of hardcoded orange.
    fun rippleTint(alpha: Int = 0x44): Int = (accent and 0x00FFFFFF) or (alpha shl 24)

    // The fill a button or panel gets: plain background, or a subtle
    // accent tint when the theme asks for one (fleshy purple).
    fun buttonFill(): Int =
        if (fillAlpha == 0) background else (accent and 0x00FFFFFF) or (fillAlpha shl 24)

    // Theme-aware panel. radius < 0 means "the theme's corner radius".
    // When the theme has a chamfer, the corners are cut instead of
    // rounded. Returns Drawable (not GradientDrawable) because the
    // chamfer path is a LayerDrawable — every caller only assigns it to
    // a background, so that is safe.
    fun panel(context: Context, fill: Int = surface, border: Int = stroke, radius: Int = -1): Drawable {
        val r = if (radius < 0) cornerRadiusDp else radius
        return if (chamferDp > 0) chamferPanel(context, fill, border)
        else GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, r).toFloat()
            setStroke(dp(context, strokeWidthDp), border)
        }
    }

    // Cut-corner (octagon-ish) panel from standard drawables only: a
    // filled PathShape plus a stroked PathShape layered together. The
    // path lives in a 100x100 space and scales to the view, so the cut
    // is proportional — a long shallow diagonal on a wide button, an even
    // octagon cut on a square panel. That is the look of the cyberpunk
    // and mono reference images. No new custom View.
    fun chamferPanel(context: Context, fill: Int, border: Int): Drawable {
        val strokePx = dp(context, strokeWidthDp).toFloat()
        val cut = 12f
        val path = Path().apply {
            moveTo(cut, 0f)
            lineTo(100f - cut, 0f)
            lineTo(100f, cut)
            lineTo(100f, 100f - cut)
            lineTo(100f - cut, 100f)
            lineTo(cut, 100f)
            lineTo(0f, 100f - cut)
            lineTo(0f, cut)
            close()
        }
        val fillDrawable = ShapeDrawable(PathShape(path, 100f, 100f)).apply {
            paint.color = fill
            paint.style = Paint.Style.FILL
        }
        val strokeDrawable = ShapeDrawable(PathShape(path, 100f, 100f)).apply {
            paint.color = border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokePx
        }
        val layers = LayerDrawable(arrayOf(fillDrawable, strokeDrawable))
        val inset = (strokePx / 2).toInt()
        layers.setLayerInset(1, inset, inset, inset, inset)
        return layers
    }

    fun style(button: Button, dangerButton: Boolean = false) {
        val context = button.context
        button.setTextColor(if (dangerButton) background else accent)
        // EVOLVER (fonts): buttons use the theme's body face. Amber's
        // body face is the mono font, exactly as before this change.
        button.typeface = bodyTypeface(context)
        button.letterSpacing = font.bodySpacingEm
        if (font.forceCaps) {
            button.isAllCaps = true
            button.text = capsText(button.text.toString())
        }
        button.minHeight = dp(context, 48)
        button.minimumHeight = dp(context, 48)
        val edge = if (dangerButton) danger else accent
        button.background = RippleDrawable(
            ColorStateList.valueOf(rippleTint()),
            panel(context, if (dangerButton) danger else buttonFill(), edge),
            null
        )
        button.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
    }

    fun style(textView: TextView) {
        textView.setTextColor(text)
    }

    fun style(input: EditText) {
        input.setTextColor(text)
        input.setHintTextColor(muted)
        // EVOLVER (fonts): inputs use the theme's body face. Never
        // forced caps here — the user types into these.
        input.typeface = bodyTypeface(input.context)
        input.letterSpacing = font.bodySpacingEm
        input.background = panel(input.context, background, stroke)
        val padding = dp(input.context, 14)
        input.setPadding(padding, padding, padding, padding)
        input.backgroundTintList = null
    }

    fun styleTab(button: Button, selected: Boolean) {
        button.setTextColor(if (selected) background else accent)
        // EVOLVER (fonts): tabs use the theme's body face.
        button.typeface = bodyTypeface(button.context)
        button.letterSpacing = font.bodySpacingEm
        if (font.forceCaps) {
            button.isAllCaps = true
            button.text = capsText(button.text.toString())
        }
        button.background = RippleDrawable(
            ColorStateList.valueOf(rippleTint()),
            panel(button.context, if (selected) accent else background, if (selected) accent else muted),
            null
        )
    }

    fun monoTypeface(context: Context): Typeface = context.resources.getFont(R.font.share_tech_mono)
    fun minchoTypeface(context: Context): Typeface = context.resources.getFont(R.font.shippori_mincho_b1_extra_bold)
    // EVOLVER (fonts): the four downloaded theme fonts — copy the TTF
    // files from fonts/ in this folder into app/src/main/res/font/
    // when applying the build (see CODEX_PROMPT.md).
    fun thinSansTypeface(context: Context): Typeface = context.resources.getFont(R.font.roboto_thin)
    fun stencilTypeface(context: Context): Typeface = context.resources.getFont(R.font.black_ops_one)
    fun pixelTypeface(context: Context): Typeface = context.resources.getFont(R.font.vt323)
    fun serifItalicTypeface(context: Context): Typeface = context.resources.getFont(R.font.playfair_display_italic)
}
