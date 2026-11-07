package com.dustline.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * HUD layout editor: drag any round button where you want it, SIZE -/+ for the selected one, RESET, switch between the
 * on-foot and the driving buttons, DONE saves on the device. The match uses the saved layout immediately.
 */
class HudEditView(ctx: Context) : View(ctx) {
    var onClose: (() -> Unit)? = null
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mode = 0   // 0 on foot, 1 driving, 2 plane + parachute
    private var sel = 4
    private var dragging = false
    private val bar = Array(5) { RectF() }
    private val tmp = RectF()

    private fun ids(): IntArray = when (mode) { 1 -> HudLayout.DRIVE; 2 -> HudLayout.AIR; else -> HudLayout.FOOT }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(0xF0100C18.toInt())
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = 0x14FFFFFF
        var gx = 0f; while (gx < w) { c.drawLine(gx, 0f, gx, h, p); gx += 40f * d }
        var gy = 0f; while (gy < h) { c.drawLine(0f, gy, w, gy, p); gy += 40f * d }
        p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textAlign = Paint.Align.CENTER
        p.textSize = 13f * d; p.color = 0xFFB9B2C6.toInt()
        c.drawText("HUD LAYOUT  -  drag ANY control (buttons, stick, map, bars, ammo, alive/kills, zone ...) to move it, SIZE -/+ resizes the selected one", w / 2, 74f * d, p)
        val bw = 108f * d; val gap = 6f * d; val x0 = w / 2 - (bw * 5 + gap * 4) / 2
        val labels = arrayOf("SIZE -", "SIZE +", "RESET", when (mode) { 1 -> "MODE: DRIVING"; 2 -> "MODE: PLANE/CHUTE"; else -> "MODE: ON FOOT" }, "DONE")
        for (i in 0 until 5) {
            bar[i].set(x0 + i * (bw + gap), 10f * d, x0 + i * (bw + gap) + bw, 52f * d)
            p.style = Paint.Style.FILL; p.color = if (i == 4) Ui.GOLD else 0xFF241B33.toInt(); c.drawRect(bar[i], p)
            p.color = if (i == 4) Ui.INK else Color.WHITE; p.textSize = 12f * d
            c.drawText(labels[i], bar[i].centerX(), bar[i].centerY() + 4f * d, p)
        }
        for (id in ids()) {
            val k = HudLayout.circle(id, w, h, d)
            val on = id == sel
            if (id in 40..52) { drawWidget(c, id, on, w, h); continue }
            val ts = (k[2] / d * 0.38f).coerceIn(8f, 18f)
            if (HudIcons.button(c, context, d, k, HudIcons.iconFor(id), HudLayout.label(id), ts, false, false, HudIcons.rotFor(id))) {
                // the PNG button is drawn exactly like in the match; the selected one gets a gold ring
                if (on) { p.style = Paint.Style.STROKE; p.strokeWidth = 4f * d; p.color = 0xFFFDB913.toInt(); c.drawCircle(k[0], k[1], k[2] * 1.12f, p) }
                p.style = Paint.Style.FILL
            } else {
                p.style = Paint.Style.FILL; p.color = if (on) 0x66FDB913 else 0x99000000.toInt(); c.drawCircle(k[0], k[1], k[2], p)
                p.style = Paint.Style.STROKE; p.strokeWidth = (if (on) 4f else 2f) * d; p.color = 0xFFFDB913.toInt(); c.drawCircle(k[0], k[1], k[2], p)
                p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textSize = ts * d
                c.drawText(HudLayout.label(id), k[0], k[1] + p.textSize * 0.35f, p)
            }
        }
        p.textAlign = Paint.Align.LEFT
    }

    /** the non-button controls (ids 40..47): wide ones as a rounded bar, joystick + minimap as circles */
    private fun drawWidget(c: Canvas, id: Int, on: Boolean, w: Float, h: Float) {
        val k = HudLayout.circle(id, w, h, d)
        p.textAlign = Paint.Align.CENTER; p.typeface = Ui.FONT_BOLD
        if (HudLayout.isRect(id)) {
            HudLayout.rectOf(id, w, h, d, tmp)
            p.style = Paint.Style.FILL; p.color = if (on) 0x66FDB913 else 0x99000000.toInt(); c.drawRoundRect(tmp, 8f * d, 8f * d, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = (if (on) 4f else 2f) * d; p.color = 0xFFFDB913.toInt(); c.drawRoundRect(tmp, 8f * d, 8f * d, p)
            p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textSize = (tmp.height() * 0.40f).coerceIn(8f * d, 22f * d)
            c.drawText(HudLayout.label(id), tmp.centerX(), tmp.centerY() + p.textSize * 0.35f, p)
        } else {
            if (id == 46) {
                HudIcons.draw(c, context, "joy_base", k[0], k[1], k[2] * 2.2f, 215); HudIcons.draw(c, context, "joy_knob", k[0], k[1], k[2] * 0.95f, 240)
            } else {
                p.style = Paint.Style.FILL; p.color = 0xCC0D384F.toInt(); c.drawCircle(k[0], k[1], k[2], p)
                p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textSize = (k[2] * 0.30f).coerceIn(8f * d, 22f * d)
                c.drawText("MAP", k[0], k[1] + p.textSize * 0.35f, p)
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = (if (on) 4f else 2f) * d; p.color = 0xFFFDB913.toInt(); c.drawCircle(k[0], k[1], k[2] * (if (id == 46) 1.08f else 1f), p)
        }
        p.style = Paint.Style.FILL
    }

    private fun scaleSel(delta: Float) {
        val a = Prefs.hudGet(sel).copyOf()
        a[2] = (a[2] + delta).coerceIn(HudLayout.MIN_S, HudLayout.MAX_S)
        Prefs.hudAdj[sel] = a
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = width.toFloat(); val h = height.toFloat()
        val x = e.x; val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                for (i in 0 until 5) if (bar[i].contains(x, y)) {
                    when (i) {
                        0 -> scaleSel(-0.1f)
                        1 -> scaleSel(0.1f)
                        2 -> { for (id in ids()) Prefs.hudAdj.remove(id) }
                        3 -> { mode = (mode + 1) % 3; sel = ids()[0] }
                        else -> { onClose?.invoke(); return true }
                    }
                    invalidate(); return true
                }
                val list = ids()
                for (n in list.indices.reversed()) {
                    val id = list[n]; val k = HudLayout.circle(id, w, h, d)
                    val hit = if (HudLayout.isRect(id)) { HudLayout.rectOf(id, w, h, d, tmp); tmp.inset(-10f * d, -10f * d); tmp.contains(x, y) } else hypot(x - k[0], y - k[1]) <= k[2] * 1.2f
                    if (hit) { sel = id; dragging = true; break }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val b = HudLayout.base(sel, w, h, d)
                val a = Prefs.hudGet(sel).copyOf()
                a[0] = (x.coerceIn(0f, w) - b[0]) / w
                a[1] = (y.coerceIn(0f, h) - b[1]) / h
                Prefs.hudAdj[sel] = a
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}
