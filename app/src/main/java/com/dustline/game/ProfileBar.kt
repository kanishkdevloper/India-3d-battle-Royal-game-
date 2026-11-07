package com.dustline.game

import android.graphics.*
import androidx.core.graphics.PathParser

/** Top-left profile card + coin / diamond chips (pbarHTML in the HTML). */
class ProfileBar {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val plusRect = RectF()
    val cardRect = RectF()

    fun draw(c: Canvas, a: Account?, x: Float, y: Float, pbh: Float, maxW: Float, d: Float) {
        val cardW = minOf(maxOf(150f * d, pbh * 3.1f), maxW * 0.46f)
        cardRect.set(x, y, x + cardW, y + pbh)
        val slant = maxOf(6f * d, pbh * 0.12f)
        val card = Ui.polygon(x, y, x + cardW, y, x + cardW - slant, y + pbh, x, y + pbh)
        paint.shader = LinearGradient(x, 0f, x + cardW, 0f, 0xF00E0B16.toInt(), 0x9E0E0B16.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(card, paint); paint.shader = null
        // avatar square: red with gold cross + diamond
        val av = RectF(x, y, x + pbh, y + pbh)
        paint.color = 0xFFA01C12.toInt(); c.drawRect(av, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = pbh * 0.125f; paint.color = 0xFFFFB21A.toInt()
        c.drawLine(av.left, av.top, av.right, av.bottom, paint); c.drawLine(av.right, av.top, av.left, av.bottom, paint)
        paint.strokeWidth = pbh * 0.075f; paint.color = 0xFFFF7A1A.toInt()
        val dm = Ui.polygon(av.centerX(), av.top + pbh * 0.05f, av.right - pbh * 0.05f, av.centerY(), av.centerX(), av.bottom - pbh * 0.05f, av.left + pbh * 0.05f, av.centerY())
        c.drawPath(dm, paint); paint.style = Paint.Style.FILL

        val tx = av.right + maxOf(5f * d, pbh * 0.1f)
        paint.typeface = Ui.FONT_BOLD; paint.color = Color.WHITE; paint.textAlign = Paint.Align.LEFT
        paint.textSize = maxOf(11f * d, pbh * 0.29f)
        val nm = a?.name ?: "PLAYER"
        val avail = x + cardW - slant - tx - 4f * d
        c.drawText(TextUtilsEllipsize.fit(nm, paint, avail), tx, y + pbh * 0.36f, paint)
        paint.textSize = maxOf(8f * d, pbh * 0.2f); paint.typeface = Ui.FONT_MED
        paint.color = Ui.GOLD
        c.drawText(if (a != null && a.uid.isNotEmpty()) "ID ${a.uid}" else "", tx, y + pbh * 0.63f, paint)
        paint.color = 0xFFDDDDDD.toInt(); paint.textAlign = Paint.Align.RIGHT
        c.drawText("LV.${a?.level ?: 1}", x + cardW - slant - 4f * d, y + pbh * 0.63f, paint)
        paint.textAlign = Paint.Align.LEFT
        // xp bar
        val bx = tx; val bw = x + cardW - slant - 4f * d - tx; val by = y + pbh * 0.74f; val bh = maxOf(2f * d, pbh * 0.055f)
        paint.color = 0x33FFFFFF; c.drawRect(bx, by, bx + bw, by + bh, paint)
        paint.color = Ui.GOLD; c.drawRect(bx, by, bx + bw * (a?.progress ?: 0f), by + bh, paint)

        // chips: SAME height as avatar + banner (one line), coin and diamond have the same size
        val chipH = pbh
        val cy = y
        val gap = maxOf(4f * d, pbh * 0.09f)
        val cx = x + cardW + gap
        val coinTxt = (a?.coins ?: 0).toString()
        val diaTxt = (a?.diamonds ?: 0).toString()
        val chipW = maxOf(chipWidth(chipH, coinTxt, false), chipWidth(chipH, diaTxt, true))
        chip(c, cx, cy, chipH, chipW, d, coinTxt, true, false)
        chip(c, cx + chipW + gap, cy, chipH, chipW, d, diaTxt, false, true)
    }

    private fun chipWidth(h: Float, text: String, plus: Boolean): Float {
        paint.textSize = h * 0.42f; paint.typeface = Ui.FONT_BOLD
        val icon = h * 0.52f
        val tw = maxOf(paint.measureText(text), paint.measureText("000"))
        val pw = h * 0.5f
        return h * 0.2f + icon + h * 0.15f + tw + h * 0.2f + (if (plus) pw + h * 0.15f else 0f)
    }

    private fun chip(c: Canvas, x: Float, y: Float, h: Float, w: Float, d: Float, text: String, coin: Boolean, plus: Boolean): Float {
        paint.textSize = h * 0.42f; paint.typeface = Ui.FONT_BOLD; paint.textAlign = Paint.Align.LEFT; paint.style = Paint.Style.FILL
        val icon = h * 0.52f
        val pw = h * 0.5f
        paint.color = 0x80000000.toInt(); c.drawRect(x, y, x + w, y + h, paint)
        paint.color = 0xB3FDB913.toInt(); c.drawRect(x, y + h - 2f * d, x + w, y + h, paint)
        val ix = x + h * 0.2f; val iy = y + (h - icon) / 2
        if (coin) {
            val cx = ix + icon / 2; val cy = iy + icon / 2; val r = icon / 2
            paint.color = 0xFFA86A00.toInt(); c.drawCircle(cx, cy, r, paint)
            paint.color = 0xFFF2B11F.toInt(); c.drawCircle(cx, cy, r * 0.9f, paint)
            paint.color = 0xFFFFD95E.toInt(); c.drawCircle(cx, cy, r * 0.7f, paint)
            paint.color = 0xFFA86A00.toInt(); paint.textAlign = Paint.Align.CENTER; paint.textSize = icon * 0.62f
            c.drawText("F", cx, cy + icon * 0.22f, paint); paint.textAlign = Paint.Align.LEFT; paint.textSize = h * 0.42f
        } else {
            val s = icon / 32f; val save = c.save(); c.translate(ix, iy); c.scale(s, s)
            fun f(d2: String, col: Int) { paint.color = col; c.drawPath(PathParser.createPathFromPathData(d2), paint) }
            f("M11 4h10l3 8H8z", 0xFFC4F0FF.toInt()); f("M8 4h3L8 12H2z", 0xFF7FD0FF.toInt()); f("M21 4h3l6 8h-6z", 0xFF4AAAF0.toInt())
            f("M2 12h6l8 17z", 0xFF3A95E6.toInt()); f("M8 12h8v17z", 0xFF62BDFA.toInt()); f("M16 12h8l-8 17z", 0xFF2F86DD.toInt()); f("M24 12h6L16 29z", 0xFF1F6FC4.toInt())
            c.restoreToCount(save)
        }
        paint.color = Color.WHITE
        // text vertically centred in the chip (true centre via font metrics)
        val fm = paint.fontMetrics
        val ty = y + h / 2 - (fm.ascent + fm.descent) / 2
        c.drawText(text, ix + icon + h * 0.15f, ty, paint)
        if (plus) {
            val px = x + w - pw - h * 0.15f; val py = y + (h - pw) / 2
            plusRect.set(px, py, px + pw, py + pw)
            paint.color = Ui.GOLD; c.drawRect(plusRect, paint)
            paint.color = Ui.INK; paint.textAlign = Paint.Align.CENTER; paint.textSize = pw * 0.85f
            val fm2 = paint.fontMetrics
            c.drawText("+", plusRect.centerX(), plusRect.centerY() - (fm2.ascent + fm2.descent) / 2, paint); paint.textAlign = Paint.Align.LEFT
        }
        return x + w
    }
}

object TextUtilsEllipsize {
    fun fit(s: String, p: Paint, w: Float): String {
        if (p.measureText(s) <= w) return s
        var t = s
        while (t.length > 1 && p.measureText("$t…") > w) t = t.dropLast(1)
        return "$t…"
    }
}
