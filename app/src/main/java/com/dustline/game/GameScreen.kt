package com.dustline.game

import android.content.Context
import android.graphics.*
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.cos

/** GL map view + touch HUD: stick = move, drag right = look, FIRE / RELOAD / JUMP / RUN, weapon chip, EXIT. */
class GameScreen(ctx: Context, map: MapData, sfx: SoundFx?, private val onExit: (MatchResult?) -> Unit, private val onResult: (MatchResult) -> Unit, private val onRestart: () -> Unit, private val onSettings: () -> Unit, warm: Boolean = false, private val onWarmEnd: () -> Unit = {}) : FrameLayout(ctx) {
    private val renderer = GameRenderer(ctx, map, sfx)
    private var warmHandled = false
    init { if (warm) renderer.startWarmup() }
    private val mapImg = MapImage(ctx, map)
    private val gl = GLSurfaceView(ctx)
    private val hud: Hud

    init {
        gl.setEGLContextClientVersion(3)
        gl.setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        gl.setRenderer(renderer)
        gl.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        HudIcons.preload(ctx)
        hud = Hud(ctx)
        addView(gl, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        addView(hud, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    fun setCharacter(id: String) { renderer.playerChar = id }
    fun onPause() { renderer.silence(); gl.onPause() }
    fun onResume() = gl.onResume()
    /** pause the simulation while the in-match settings are open */
    fun setPaused(p: Boolean) { renderer.paused = p; if (p) { renderer.firing = false; renderer.moveX = 0f; renderer.moveY = 0f; renderer.drvLeft = false; renderer.drvRight = false; renderer.drvFwd = false; renderer.drvBack = false; hud.clearTouch() } }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        applyGfx()
    }

    /** Graphics setting: render resolution of the 3D view (low 60 %, mid 85 %, high 100 %). */
    fun applyGfx() {
        renderer.resetPerf()
        val sc = when (Prefs.gfx) { "low" -> 0.6f; "mid" -> 0.85f; else -> 1f }
        if (width <= 0 || height <= 0) return
        if (sc >= 1f) gl.holder.setSizeFromLayout() else gl.holder.setFixedSize((width * sc).toInt().coerceAtLeast(320), (height * sc).toInt().coerceAtLeast(180))
    }

    private inner class Hud(c: Context) : View(c) {
        private val d = resources.displayMetrics.density
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var stickId = -1; private var lookId = -1
        private var sx = 0f; private var sy = 0f; private var kx = 0f; private var ky = 0f
        private var lx = 0f; private var ly = 0f
        private val exitR = RectF(); private val gunR = RectF(); private val pickR = RectF(); private val getOffR = RectF()
        private val restartR = RectF(); private val lobbyR = RectF(); private val scopeR = RectF(); private var reported = false
        private val gearR = RectF(); private val mmR = RectF(); private val bigR = RectF(); private val closeR = RectF()
        private val prevR = RectF(); private val nextR = RectF(); private val leaveR = RectF(); private var specLeft = false
        private var mapOpen = false; private var hasPin = false; private var pinX = 0f; private var pinZ = 0f
        private val vib = c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        /** forget every finger (called when the settings open, so no stick / look / button stays stuck after they close) */
        fun clearTouch() { stickId = -1; lookId = -1; btnIds.clear(); invalidate() }
        private val btnIds = HashMap<Int, Int>() // pointer -> 1 jump, 2 run, 3 exit, 4 fire, 5 reload, 6/7 prev/next gun, 8 heal, 9 pick up
        private val R get() = 60f * d * HudLayout.scale(46)
        // circle buttons: id, cx, cy, r, label
        private fun circle(id: Int, w: Float, h: Float): FloatArray = HudLayout.circle(id, w, h, d)
        /** draws a HUD block at the player's own position + size (HUD editor ids 48..52): code between wBegin / c.restore() is written for the DEFAULT position */
        private fun wBegin(c: Canvas, id: Int, w: Float, h: Float) {
            val k = HudLayout.circle(id, w, h, d); val b = HudLayout.base(id, w, h, d); val s = HudLayout.scale(id)
            c.save(); c.translate(k[0], k[1]); c.scale(s, s); c.translate(-b[0], -b[1])
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            if (renderer.spec) { drawSpec(c, w, h); postInvalidateOnAnimation(); return }
            HudLayout.rectOf(40, w, h, d, exitR)
            HudLayout.rectOf(42, w, h, d, getOffR)
            HudLayout.rectOf(41, w, h, d, gearR)
            val vm = renderer.vibMs; if (vm > 0) { renderer.vibMs = 0; buzz(vm) }
            HudLayout.rectOf(43, w, h, d, gunR)
            if (renderer.driving) drawDriveHud(c, w, h) else {
            // stick
            val jk = HudLayout.circle(46, w, h, d); val cx = if (stickId >= 0) sx else jk[0]; val cy = if (stickId >= 0) sy else jk[1]
            if (!HudIcons.draw(c, context, "joy_base", cx, cy, R * 2.2f, 215)) {
                p.style = Paint.Style.FILL; p.color = 0x33FFFFFF; c.drawCircle(cx, cy, R, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = 0x88FFFFFF.toInt(); c.drawCircle(cx, cy, R, p)
            }
            if (!HudIcons.draw(c, context, "joy_knob", if (stickId >= 0) kx else cx, if (stickId >= 0) ky else cy, R * 0.95f, 240)) {
                p.style = Paint.Style.FILL; p.color = 0xAAFDB913.toInt()
                c.drawCircle(if (stickId >= 0) kx else cx, if (stickId >= 0) ky else cy, R * 0.45f, p)
            }
            if (renderer.airState != 3) {   // plane / freefall / parachute: only DROP-CHUTE and SOUND (HTML hides the rest while airborne)
                bagOpen = false
                ibtn(c, circle(101, w, h), when (renderer.airState) { 0 -> "door"; 1 -> null; else -> "jump" }, when (renderer.airState) { 0 -> "DROP"; 1 -> "CHUTE"; else -> "JUMP" }, renderer.airState == 1 && renderer.chuteReady, 12f)
                if (renderer.airState == 1) ibtn(c, circle(102, w, h), if (renderer.skateMode) "jump" else "prone", if (renderer.skateMode) "DIVE" else "SKATE", renderer.skateMode, 10f)
                ibtn(c, circle(119, w, h), "sound", if (Prefs.sfx) "SND" else "MUTE", false, 9f, slash = !Prefs.sfx)
            } else {
            val fistsNow = renderer.gunName == "FISTS"   // nothing in the hand: the FIRE button becomes the fist (punch) button and RELOAD goes away
            ibtn(c, circle(1, w, h), "jump", "JUMP", false, 12f); ibtn(c, circle(2, w, h), "run", "RUN", renderer.run, 12f); ibtn(c, circle(17, w, h), "crouch", if (renderer.crouching) "STAND" else "SIT", renderer.crouching, 10f)
            ibtn(c, circle(4, w, h), if (fistsNow) "fist" else "fire", if (fistsNow) "PUNCH" else "FIRE", renderer.firing, 20f)
            if (!fistsNow) ibtn(c, circle(5, w, h), "reload", "RELOAD", renderer.reloadProgress >= 0f, 10f)
            ibtn(c, circle(6, w, h), "medkit", "HEAL x${renderer.medkits}", renderer.healProgress >= 0f, 9f, dim = renderer.medkits < 1, badge = "${renderer.medkits}")
            ibtn(c, circle(7, w, h), "aim", "AIM", renderer.aiming, 13f)
            ibtn(c, circle(8, w, h), "grenade", "NADE x${renderer.grenadeCount}", false, 9f, dim = renderer.grenadeCount < 1, badge = "${renderer.grenadeCount}")
            ibtn(c, circle(9, w, h), "heal", if (renderer.mushrooms > 0) "EAT ${renderer.mushrooms}" else "EAT", false, 10f, dim = renderer.mushrooms < 1, badge = "${renderer.mushrooms}")
            ibtn(c, circle(18, w, h), "bag", "BAG", bagOpen, 10f); ibtn(c, circle(19, w, h), "sound", if (Prefs.sfx) "SND" else "MUTE", false, 9f, slash = !Prefs.sfx)
            ibtn(c, circle(29, w, h), if (Prefs.thirdPerson) "tpp" else "fpp", if (Prefs.thirdPerson) "TPP" else "FPP", false, 9f)
            if (renderer.healProgress >= 0f) ibtn(c, circle(31, w, h), "cancel", "CANCEL", false, 9f)
            if (bagOpen) drawBag(c, w, h)
            if (renderer.vehPrompt.isNotEmpty() && renderer.airState == 3) {
                val k = circle(16, w, h)
                ibtn(c, k, "wheel", "USE", false, 13f)
                p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textSize = 10f * d; p.textAlign = Paint.Align.CENTER; p.color = Color.WHITE
                c.drawText(renderer.vehPrompt, k[0], k[1] - k[2] - 6f * d, p); p.textAlign = Paint.Align.LEFT
            }
            }
            }
            rectIcon(c, exitR, "exit", "EXIT", 14f * HudLayout.scale(40)); rectIcon(c, gearR, "gear", "", 14f)
            // top-centre: alive + kills
            wBegin(c, 48, w, h)
            p.style = Paint.Style.FILL; p.color = 0x99000000.toInt()
            val tb = RectF(w / 2 - 80f * d, 8f * d, w / 2 + 80f * d, 34f * d); c.drawRoundRect(tb, 6f * d, 6f * d, p)
            p.typeface = Ui.FONT_BOLD; p.textSize = 14f * d; p.textAlign = Paint.Align.CENTER; p.color = Color.WHITE
            c.drawText("ALIVE ${renderer.botsAlive + 1}   KILLS ${renderer.kills}", tb.centerX(), tb.centerY() + 5f * d, p)
            c.restore()
            if (renderer.warm) {   // #warmBanner of the HTML
                p.color = 0x99000000.toInt(); c.drawRoundRect(RectF(w / 2 - 110f * d, 40f * d, w / 2 + 110f * d, 82f * d), 6f * d, 6f * d, p)
                p.textAlign = Paint.Align.CENTER; p.textSize = 15f * d; p.color = Color.WHITE; c.drawText("PRE-MATCH: ${Math.ceil(renderer.warmLeft.toDouble()).toInt()}", w / 2, 58f * d, p)
                p.textSize = 10f * d; p.color = 0xFFFFAC78.toInt(); c.drawText("WARM-UP / NO DAMAGE", w / 2, 74f * d, p)
                if (renderer.warmEnded && !warmHandled) { warmHandled = true; post { onWarmEnd() } }
            }
            // zone timer + distance to the edge
            val outside = renderer.zoneDist < 0f
            wBegin(c, 49, w, h)
            val zr = RectF(w / 2 - 130f * d, 38f * d, w / 2 + 130f * d, 60f * d)
            p.color = if (outside) 0xCC8A1010.toInt() else 0x99000000.toInt(); c.drawRoundRect(zr, 6f * d, 6f * d, p)
            p.textSize = 11f * d; p.color = if (outside) 0xFFFFD0D0.toInt() else 0xFF9AD0FF.toInt()
            val pinTxt = if (hasPin) "  ·  PIN ${hypot(pinX - renderer.posX, pinZ - renderer.posZ).toInt()} m" else ""
            c.drawText((if (outside) "OUTSIDE ZONE · GO ${(-renderer.zoneDist).toInt()} m INSIDE" else renderer.zoneText + " · EDGE ${renderer.zoneDist.toInt()} m") + pinTxt, zr.centerX(), zr.centerY() + 4f * d, p)
            c.restore()
            // health bar above the weapon chip
            wBegin(c, 50, w, h)
            val hw = 220f * d; val hx = w / 2 - hw / 2; val hy = h - 62f * d
            p.color = 0x99000000.toInt(); c.drawRect(hx, hy, hx + hw, hy + 9f * d, p)
            val hf = (renderer.playerHp / 200f).coerceIn(0f, 1f)
            p.color = if (hf > 0.5f) 0xFF7BE05A.toInt() else if (hf > 0.25f) 0xFFFFC21A.toInt() else 0xFFFF4B3A.toInt()
            c.drawRect(hx, hy, hx + hw * hf, hy + 9f * d, p)
            HudIcons.drawRect(c, context, "badge_hp", hx - 22f * d, hy + 4.5f * d - 9f * d, hx - 4f * d, hy + 4.5f * d + 9f * d)
            p.textSize = 11f * d; p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER
            c.drawText("${renderer.playerHp.toInt()} / 200", w / 2, hy - 3f * d, p); p.textAlign = Paint.Align.LEFT
            // shield bar (blue) above the health bar
            val shy = hy - 20f * d
            p.color = 0x99000000.toInt(); c.drawRect(hx, shy, hx + hw, shy + 6f * d, p)
            p.color = 0xFF5FB4E8.toInt(); c.drawRect(hx, shy, hx + hw * (renderer.shield / 100f).coerceIn(0f, 1f), shy + 6f * d, p)
            HudIcons.drawRect(c, context, "badge_shield", hx - 22f * d, shy + 3f * d - 9f * d, hx - 4f * d, shy + 3f * d + 9f * d)
            if (renderer.vestLvl > 0) { p.textSize = 10f * d; p.textAlign = Paint.Align.RIGHT; p.color = 0xFFB9E3FF.toInt(); c.drawText("VEST Lv.${renderer.vestLvl}", hx - 26f * d, shy + 6f * d, p); p.textAlign = Paint.Align.LEFT }
            if (renderer.helmLvl > 0) { p.textSize = 10f * d; p.textAlign = Paint.Align.RIGHT; p.color = 0xFFFFD34D.toInt(); c.drawText("HELM Lv.${renderer.helmLvl}  ${renderer.helmHp.toInt()}", hx - 26f * d, shy - 6f * d, p); p.textAlign = Paint.Align.LEFT }
            // EP bar (gold): 1 EP -> 1 HP per second; mushrooms give +60 EP (max 250)
            val epy = shy - 11f * d
            p.color = 0x99000000.toInt(); c.drawRect(hx, epy, hx + hw, epy + 5f * d, p)
            p.color = 0xFFF2B01E.toInt(); c.drawRect(hx, epy, hx + hw * (renderer.ep / 250f).coerceIn(0f, 1f), epy + 5f * d, p)
            p.textSize = 9f * d; p.textAlign = Paint.Align.RIGHT; p.color = 0xFFFFE08A.toInt(); c.drawText("EP ${renderer.ep.toInt()}", hx + hw, epy - 2f * d, p); p.textAlign = Paint.Align.LEFT
            c.restore()
            // air-drop panel: plane / freefall / parachute info
            if (renderer.airState != 3 && renderer.airText.isNotEmpty()) {
                p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textSize = 13f * d; p.textAlign = Paint.Align.CENTER
                val aw = p.measureText(renderer.airText) + 30f * d
                val ar = RectF(w / 2 - aw / 2, 100f * d, w / 2 + aw / 2, 126f * d)
                p.color = 0xCC000000.toInt(); c.drawRoundRect(ar, 6f * d, 6f * d, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = Ui.GOLD; c.drawRoundRect(ar, 6f * d, 6f * d, p)
                p.style = Paint.Style.FILL; p.color = Color.WHITE; c.drawText(renderer.airText, ar.centerX(), ar.centerY() + 5f * d, p); p.textAlign = Paint.Align.LEFT
            }
            // airdrop tag (gold pill)
            if (renderer.airState == 3 && renderer.airdropText.isNotEmpty()) {
                p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textSize = 12f * d; p.textAlign = Paint.Align.CENTER
                val tw = p.measureText(renderer.airdropText) + 28f * d
                val ar = RectF(w / 2 - tw / 2, 100f * d, w / 2 + tw / 2, 120f * d)
                p.color = 0xB3281C00.toInt(); c.drawRoundRect(ar, 10f * d, 10f * d, p)
                p.color = 0xFFFFD34D.toInt(); c.drawText(renderer.airdropText, ar.centerX(), ar.centerY() + 4f * d, p); p.textAlign = Paint.Align.LEFT
                HudIcons.draw(c, context, "airdrop", ar.left - 16f * d, ar.centerY(), 30f * d)
            }
            drawMiniMap(c, w, h, d)
            drawFeed(c, w, d)
            // pick-up prompt
            if (renderer.promptLabel.isNotEmpty()) {
                HudLayout.rectOf(45, w, h, d, pickR)
                p.style = Paint.Style.FILL; p.color = 0xE6000000.toInt(); c.drawRoundRect(pickR, 6f * d, 6f * d, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = Ui.GOLD; c.drawRoundRect(pickR, 6f * d, 6f * d, p)
                p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textSize = 14f * d * HudLayout.scale(45); p.textAlign = Paint.Align.CENTER; p.color = Color.WHITE
                c.drawText("PICK UP  ·  ${renderer.promptLabel}", pickR.centerX(), pickR.centerY() + 5f * d * HudLayout.scale(45), p); p.textAlign = Paint.Align.LEFT
                HudIcons.draw(c, context, "hand", pickR.left - 24f * d, pickR.centerY(), 42f * d)
            } else pickR.setEmpty()
            // toast
            val tage = (System.nanoTime() - renderer.toastTime) / 1e9f
            if (renderer.toastTime != 0L && tage < 2.2f) {
                p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textSize = 14f * d; p.textAlign = Paint.Align.CENTER
                val tw = p.measureText(renderer.toastText) + 28f * d
                val tr = RectF(w / 2 - tw / 2, 66f * d, w / 2 + tw / 2, 92f * d)
                p.color = Ui.GOLD; c.drawRoundRect(tr, 4f * d, 4f * d, p)
                p.color = Ui.INK; c.drawText(renderer.toastText, w / 2, tr.centerY() + 5f * d, p); p.textAlign = Paint.Align.LEFT
            }
            // damage flash (red edges)
            val dage = (System.nanoTime() - renderer.damageTime) / 1e9f
            if (renderer.damageTime != 0L && dage < 0.45f) {
                p.shader = RadialGradient(w / 2, h / 2, w * 0.7f, intArrayOf(0x00FF0000, 0x00FF0000, ((1f - dage / 0.45f) * 150).toInt() shl 24 or 0xE01010), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, p); p.shader = null
            }
            // weapon chip: < NAME >
            val gs = HudLayout.scale(43); val ss = HudLayout.scale(44)
            p.style = Paint.Style.FILL; p.color = 0x99000000.toInt(); c.drawRoundRect(gunR, 6f * d, 6f * d, p)
            p.typeface = Ui.FONT_BOLD; p.color = Ui.GOLD; p.textAlign = Paint.Align.LEFT; p.textSize = 20f * d * gs
            c.drawText("‹", gunR.left + 12f * d * gs, gunR.centerY() + 7f * d * gs, p)
            p.textAlign = Paint.Align.RIGHT; c.drawText("›", gunR.right - 12f * d * gs, gunR.centerY() + 7f * d * gs, p)
            p.textAlign = Paint.Align.CENTER; p.color = Color.WHITE; p.textSize = 15f * d * gs
            c.drawText(renderer.gunName, gunR.centerX(), gunR.centerY() + 5f * d * gs, p)
            // scope tag (tap to cycle: AIM -> 1X ... 8X)
            HudLayout.rectOf(44, w, h, d, scopeR)
            p.style = Paint.Style.FILL; p.color = 0x99000000.toInt(); c.drawRoundRect(scopeR, 6f * d, 6f * d, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = if (renderer.scopeLabel != "AIM") 0xFF35D0C8.toInt() else 0x66FFFFFF; c.drawRoundRect(scopeR, 6f * d, 6f * d, p)
            p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.textSize = 14f * d * ss; p.color = Color.WHITE
            c.drawText(renderer.scopeLabel, scopeR.centerX(), scopeR.centerY() + 5f * d * ss, p)
            // ammo
            wBegin(c, 51, w, h); val ab = HudLayout.base(51, w, h, d)
            p.textAlign = Paint.Align.RIGHT; p.textSize = 30f * d; p.color = if (renderer.ammoNow == 0 && renderer.ammoMax > 0) 0xFFFF6A5A.toInt() else Color.WHITE
            p.setShadowLayer(4f * d, 0f, 2f * d, 0xCC000000.toInt())
            c.drawText(if (renderer.ammoMax == 0) "--" else "${renderer.ammoNow}", ab[0] - 3f * d, ab[1] + 11f * d, p)
            p.textAlign = Paint.Align.LEFT; p.textSize = 14f * d; p.color = 0xFFCCCCCC.toInt(); if (renderer.ammoMax > 0) c.drawText("/ ${renderer.ammoReserve}", ab[0] + 3f * d, ab[1] + 11f * d, p)
            p.clearShadowLayer(); p.textAlign = Paint.Align.LEFT
            c.restore()
            if (renderer.reloadProgress >= 0f) {
                val bw = 120f * d; val bx = w / 2 - bw / 2; val by = h / 2 + 40f * d
                p.color = 0x88000000.toInt(); c.drawRect(bx, by, bx + bw, by + 6f * d, p)
                p.color = Ui.GOLD; c.drawRect(bx, by, bx + bw * renderer.reloadProgress, by + 6f * d, p)
            }
            // crosshair + hit marker
            val mx = w / 2; val my = h / 2
            val ads = renderer.adsAmount
            if (renderer.scopeOverlay) {
                // sniper scope: black mask with circular lens, cross-hair and mil dots
                val r = h * 0.44f
                val path = Path(); path.addRect(0f, 0f, w, h, Path.Direction.CW); path.addCircle(mx, my, r, Path.Direction.CCW)
                p.style = Paint.Style.FILL; p.color = Color.BLACK; c.drawPath(path, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f * d; p.color = 0xFF111111.toInt(); c.drawCircle(mx, my, r, p)
                p.strokeWidth = 1.4f * d; p.color = 0xE6000000.toInt()
                c.drawLine(mx - r, my, mx - 18f * d, my, p); c.drawLine(mx + 18f * d, my, mx + r, my, p)
                c.drawLine(mx, my - r, mx, my - 18f * d, p); c.drawLine(mx, my + 18f * d, mx, my + r, p)
                p.strokeWidth = 2.2f * d; c.drawLine(mx - 18f * d, my, mx + 18f * d, my, p); c.drawLine(mx, my - 18f * d, mx, my + 18f * d, p)
                p.style = Paint.Style.FILL; for (k in 1..4) { c.drawCircle(mx + k * 22f * d, my, 1.8f * d, p); c.drawCircle(mx - k * 22f * d, my, 1.8f * d, p); c.drawCircle(mx, my + k * 22f * d, 1.8f * d, p) }
                p.color = Color.WHITE; p.textSize = 12f * d; p.textAlign = Paint.Align.CENTER; c.drawText(renderer.scopeLabel, mx, my + r + 16f * d, p); p.textAlign = Paint.Align.LEFT
            } else if (ads > 0.5f) {
                p.style = Paint.Style.FILL; p.color = if (renderer.scopeLabel == "1X") 0xFFFF3B30.toInt() else 0xFFFFFFFF.toInt(); c.drawCircle(mx, my, 2.5f * d, p)
            } else if (!renderer.driving) {
                val gap = (5f + 6f * ads) * d
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = 0xCCFFFFFF.toInt()
                c.drawLine(mx - gap - 9f * d, my, mx - gap, my, p); c.drawLine(mx + gap, my, mx + gap + 9f * d, my, p)
                c.drawLine(mx, my - gap - 9f * d, mx, my - gap, p); c.drawLine(mx, my + gap, mx, my + gap + 9f * d, p)
            }
            val age = (System.nanoTime() - renderer.hitTime) / 1e9f
            if (renderer.hitTime != 0L && age < 0.5f) {
                p.color = if (renderer.hitKill) 0xFFFF3B30.toInt() else if (renderer.hitHead) 0xFFFFD60A.toInt() else Color.WHITE
                p.strokeWidth = 3f * d
                for (sx2 in intArrayOf(-1, 1)) for (sy2 in intArrayOf(-1, 1)) c.drawLine(mx + sx2 * 6f * d, my + sy2 * 6f * d, mx + sx2 * 14f * d, my + sy2 * 14f * d, p)
                p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.textSize = 18f * d; p.typeface = Ui.FONT_BOLD
                c.drawText((if (renderer.hitHead) "HEADSHOT " else "") + renderer.hitDamage, mx, my - 30f * d - age * 30f * d, p)
                p.textAlign = Paint.Align.LEFT
            }
            p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textSize = 11f * d; p.color = Color.WHITE; p.textAlign = Paint.Align.RIGHT
            wBegin(c, 52, w, h); val cb = HudLayout.base(52, w, h, d)
            c.drawText(renderer.posText, cb[0] + cb[2], cb[1] + 4f * d, p); p.textAlign = Paint.Align.LEFT
            c.restore()
            val ms = renderer.matchState
            if (ms != 0) {
                val res = renderer.result
                if (res != null && !reported) { reported = true; post { onResult(res) } }
                if (res == null) reported = true
                p.style = Paint.Style.FILL; p.color = if (ms == 1) 0xB0102010.toInt() else 0xB0200808.toInt(); c.drawRect(0f, 0f, w, h, p)
                val cw = 380f * d; val ch = 250f * d
                val card = RectF(w / 2 - cw / 2, h / 2 - ch / 2, w / 2 + cw / 2, h / 2 + ch / 2)
                p.color = 0xF00E0B16.toInt(); c.drawRect(card, p)
                p.color = Ui.GOLD; c.drawRect(card.left, card.top, card.right, card.top + 4f * d, p)
                p.textAlign = Paint.Align.CENTER; p.typeface = Ui.FONT_BOLD
                p.textSize = 11f * d; p.color = 0xFFB9B2C6.toInt()
                c.drawText(if (ms == 1) "EXTRACTION COMPLETE / VICTORY" else "MATCH COMPLETE / DEBRIEF", card.centerX(), card.top + 24f * d, p)
                p.textSize = 26f * d; p.color = Color.WHITE
                c.drawText(if (ms == 1) "LAST ONE STANDING." else "BACK TO THE DUST.", card.centerX(), card.top + 56f * d, p)
                p.textSize = 14f * d; p.color = 0xFFCCCCCC.toInt()
                c.drawText("PLACE #${renderer.alivePlace}     KILLS ${renderer.kills}     TIME ${"%d:%02d".format(renderer.matchTime.toInt() / 60, renderer.matchTime.toInt() % 60)}", card.centerX(), card.top + 84f * d, p)
                if (res != null) {
                    p.color = Ui.GOLD; p.textSize = 16f * d; c.drawText("+${res.xp} XP     +${res.coins} COINS", card.centerX(), card.top + 112f * d, p)
                    p.color = 0xFF9A95A8.toInt(); p.textSize = 10f * d; c.drawText(res.label, card.centerX(), card.top + 130f * d, p)
                }
                val bw = 150f * d; val bh = 42f * d
                restartR.set(card.centerX() - bw - 8f * d, card.bottom - bh - 20f * d, card.centerX() - 8f * d, card.bottom - 20f * d)
                lobbyR.set(card.centerX() + 8f * d, card.bottom - bh - 20f * d, card.centerX() + bw + 8f * d, card.bottom - 20f * d)
                p.color = Ui.GOLD; c.drawRect(restartR, p); p.color = 0xFF2A2438.toInt(); c.drawRect(lobbyR, p)
                p.textSize = 15f * d; p.color = Ui.INK; c.drawText("DROP IN AGAIN", restartR.centerX(), restartR.centerY() + 5f * d, p)
                p.color = Color.WHITE; c.drawText("LOBBY", lobbyR.centerX(), lobbyR.centerY() + 5f * d, p)
                p.textAlign = Paint.Align.LEFT
            }
            if (mapOpen) drawBigMap(c, w, h, d)
            postInvalidateOnAnimation()
        }


        /** Spectate after death (HTML dl-spec): no debrief card; banner, PREV / NEXT bar and the BACK (10) countdown. */
        private fun drawSpec(c: Canvas, w: Float, h: Float) {
            val res = renderer.result
            if (res != null && !reported) { reported = true; post { onResult(res) } }
            if (res == null) reported = true
            val vm = renderer.vibMs; if (vm > 0) { renderer.vibMs = 0; buzz(vm) }
            p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textAlign = Paint.Align.CENTER
            // match strip (alive / kills) stays visible
            p.color = 0x99000000.toInt()
            wBegin(c, 48, w, h)
            val tb = RectF(w / 2 - 80f * d, 8f * d, w / 2 + 80f * d, 34f * d); c.drawRoundRect(tb, 6f * d, 6f * d, p)
            p.textSize = 14f * d; p.color = Color.WHITE
            c.drawText("ALIVE ${renderer.botsAlive}   KILLS ${renderer.kills}", tb.centerX(), tb.centerY() + 5f * d, p)
            c.restore()
            drawFeed(c, w, d)
            // banner (fades after ~5 s, like the sp-banner animation)
            val bt = renderer.specBannerT
            if (bt < 5f) {
                val a = (if (bt < 0.3f) bt / 0.3f else if (bt > 4f) (5f - bt) else 1f).coerceIn(0f, 1f)
                p.color = ((a * 0xB0).toInt() shl 24) or 0x17251F; c.drawRect(w / 2 - 190f * d, 46f * d, w / 2 + 190f * d, 108f * d, p)
                p.color = ((a * 255).toInt() shl 24) or 0xFFAC78; p.textSize = 22f * d; c.drawText(renderer.specTitle, w / 2, 76f * d, p)
                p.color = ((a * 255).toInt() shl 24) or 0xF1EFE5; p.textSize = 12f * d; c.drawText(renderer.specSub, w / 2, 98f * d, p)
            }
            // bottom bar:  <  SPECTATING / NAME  >  BACK (n)
            val bw = 440f * d; val bh = 54f * d; val bx = w / 2 - bw / 2; val by = h - bh - 18f * d
            p.color = 0xD117251F.toInt(); c.drawRect(bx, by, bx + bw, by + bh, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1f * d; p.color = 0x40F1EFE5; c.drawRect(bx, by, bx + bw, by + bh, p)
            p.style = Paint.Style.FILL
            prevR.set(bx + 6f * d, by + 6f * d, bx + 62f * d, by + bh - 6f * d)
            leaveR.set(bx + bw - 122f * d, by + 6f * d, bx + bw - 6f * d, by + bh - 6f * d)
            nextR.set(leaveR.left - 62f * d, by + 6f * d, leaveR.left - 6f * d, by + bh - 6f * d)
            if (renderer.specOver) { prevR.setEmpty(); nextR.setEmpty() }
            else for (r in arrayOf(prevR, nextR)) { p.color = 0x14F1EFE5; c.drawRect(r, p) }
            p.textSize = 26f * d; p.color = Color.WHITE
            if (!prevR.isEmpty) c.drawText("‹", prevR.centerX(), prevR.centerY() + 9f * d, p)
            if (!nextR.isEmpty) c.drawText("›", nextR.centerX(), nextR.centerY() + 9f * d, p)
            val midL = if (prevR.isEmpty) bx + 12f * d else prevR.right; val midR = if (nextR.isEmpty) leaveR.left - 6f * d else nextR.left
            p.textSize = 10f * d; p.color = 0xFFB9B2C6.toInt(); c.drawText(renderer.specLabel, (midL + midR) / 2, by + 21f * d, p)
            p.textSize = 17f * d; p.color = Color.WHITE; c.drawText(renderer.specName, (midL + midR) / 2, by + 42f * d, p)
            p.color = 0x14FFAC78; c.drawRect(leaveR, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = 0xFFFFAC78.toInt(); c.drawRect(leaveR, p)
            p.style = Paint.Style.FILL; p.textSize = 13f * d; p.color = 0xFFFFAC78.toInt()
            val left = Math.ceil(renderer.specLeft.toDouble()).toInt()
            c.drawText("BACK ($left)", leaveR.centerX(), leaveR.centerY() + 5f * d, p)
            p.textAlign = Paint.Align.LEFT
            if (renderer.specLeft <= 0f && !specLeft) { specLeft = true; post { onExit(null) } }
        }

        private fun buzz(ms: Int) {
            try {
                val v = vib ?: return; if (!Prefs.vib) return
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(ms.toLong())
            } catch (e: Exception) { }
        }

        private fun drawFeed(c: Canvas, w: Float, d: Float) {
            var y = 72f * d
            val now = System.nanoTime()
            p.typeface = Ui.FONT_BOLD; p.textSize = 11f * d; p.textAlign = Paint.Align.RIGHT; p.style = Paint.Style.FILL
            for (f in renderer.feed) {
                val age = (now - f.time) / 1e9f
                if (age > 6f) continue
                val a = ((6f - age).coerceAtMost(1f) * 255).toInt()
                val tw = p.measureText(f.text)
                p.color = (a * 0.55f).toInt() shl 24; c.drawRoundRect(RectF(w - 12f * d - tw - 10f * d, y - 11f * d, w - 12f * d, y + 5f * d), 4f * d, 4f * d, p)
                p.color = (a shl 24) or (if (f.text.startsWith("YOU")) 0xFFD34D else 0xFFFFFF); c.drawText(f.text, w - 17f * d, y, p)
                y += 20f * d
            }
            p.textAlign = Paint.Align.LEFT
        }

        // ---- maps (north-up picture of the island; world x/z in [-half, half])
        private fun wx(r: RectF, x: Float) = r.left + (x + mapImg.half) / (2f * mapImg.half) * r.width()
        private fun wz(r: RectF, z: Float) = r.top + (z + mapImg.half) / (2f * mapImg.half) * r.height()

        private fun drawMapContent(c: Canvas, r: RectF, d: Float, big: Boolean) {
            val pic = mapImg.pick(r.width())   // satellite picture (smaller copies for small / medium drawings)
            val src = Rect(0, 0, pic.width, pic.height)
            p.style = Paint.Style.FILL; p.color = Color.WHITE; p.isFilterBitmap = true
            c.drawBitmap(pic, src, r, p)
            val k = r.width() / (2f * mapImg.half)
            // everything outside the safe zone is tinted blue
            val out = Path(); out.fillType = Path.FillType.EVEN_ODD
            out.addRect(r, Path.Direction.CW); out.addCircle(wx(r, renderer.zoneCx), wz(r, renderer.zoneCz), renderer.zoneRadius * k, Path.Direction.CW)
            p.color = 0x442F7BFF; c.drawPath(out, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = (if (big) 2.5f else 1.6f) * d; p.color = 0xFF3D9BFF.toInt()
            c.drawCircle(wx(r, renderer.zoneCx), wz(r, renderer.zoneCz), renderer.zoneRadius * k, p)
            if (renderer.nextR > 1f) { p.color = Color.WHITE; c.drawCircle(wx(r, renderer.nextX), wz(r, renderer.nextZ), renderer.nextR * k, p) }
            // airdrops
            val dm = renderer.dropMarks
            var i = 0
            while (i + 1 < dm.size) {
                val x = wx(r, dm[i]).coerceIn(r.left + 4f * d, r.right - 4f * d); val y = wz(r, dm[i + 1]).coerceIn(r.top + 4f * d, r.bottom - 4f * d)
                val s = (if (big) 6f else 3.5f) * d
                p.style = Paint.Style.FILL; p.color = 0xFFFFD34D.toInt(); c.drawRect(x - s, y - s, x + s, y + s, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f * d; p.color = 0xFF3A2A00.toInt(); c.drawRect(x - s, y - s, x + s, y + s, p)
                i += 2
            }
            // vehicles: outlined squares (drivable four-wheelers)
            val cm = renderer.carMarks
            var q = 0
            while (q + 1 < cm.size) {
                val cxm = wx(r, cm[q]); val czm = wz(r, cm[q + 1])
                if (cxm > r.left && cxm < r.right && czm > r.top && czm < r.bottom) {
                    val sq = (if (big) 3.5f else 2.2f) * d
                    p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * d; p.color = 0xFFE8E4C8.toInt(); c.drawRect(cxm - sq, czm - sq, cxm + sq, czm + sq, p)
                }
                q += 2
            }
            // pin
            if (hasPin) {
                val x = wx(r, pinX); val y = wz(r, pinZ)
                p.style = Paint.Style.FILL; p.color = 0xFFFF4D4D.toInt(); c.drawCircle(x, y, (if (big) 6f else 3f) * d, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = Color.WHITE; c.drawCircle(x, y, (if (big) 9f else 5f) * d, p)
            }
            // player arrow (rotates with the camera)
            val px = wx(r, renderer.posX); val py = wz(r, renderer.posZ)
            val s = (if (big) 9f else 5.5f) * d
            val sv = c.save(); c.translate(px, py); c.rotate(Math.toDegrees(renderer.yawNow.toDouble()).toFloat())
            val ar = Ui.polygon(0f, -s * 1.3f, s * 0.8f, s, 0f, s * 0.45f, -s * 0.8f, s)
            p.style = Paint.Style.FILL; p.color = Ui.GOLD; c.drawPath(ar, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.3f * d; p.color = Ui.INK; c.drawPath(ar, p)
            c.restoreToCount(sv)
            p.style = Paint.Style.FILL
        }

        /** how many metres of satellite ground the round minimap shows across (just the area around the player) */
        private val MM_VIEW = 190f

        private fun drawMiniMap(c: Canvas, w: Float, h: Float, d: Float) {
            if (!Prefs.minimap || mapOpen) { mmR.setEmpty(); return }
            val mk = HudLayout.circle(47, w, h, d); val rad = mk[2]; val cx = mk[0]; val cy = mk[1]
            mmR.set(cx - rad, cy - rad, cx + rad, cy + rad)
            val sv = c.save(); val clip = Path(); clip.addCircle(cx, cy, rad, Path.Direction.CW); c.clipPath(clip)
            // in the plane: whole island, north-up. Afterwards: only the ground right around you (MM_VIEW metres across), turning so that the way you look is always UP (v41 minimap + DLMAPROT of the HTML)
            val full = renderer.airState == 0
            val mr = RectF()
            if (full) mr.set(mmR) else {
                val sz = 2f * rad * (2f * mapImg.half) / MM_VIEW
                val l = cx - (renderer.posX + mapImg.half) / (2f * mapImg.half) * sz
                val t = cy - (renderer.posZ + mapImg.half) / (2f * mapImg.half) * sz
                mr.set(l, t, l + sz, t + sz)
            }
            val yawDeg = Math.toDegrees(renderer.yawNow.toDouble()).toFloat()
            p.style = Paint.Style.FILL; p.color = if (full) 0xCC0A1018.toInt() else 0xFF0D384F.toInt(); c.drawRect(mmR, p)
            val svRot = c.save()
            if (!full) c.rotate(-yawDeg, cx, cy)
            drawMapContent(c, mr, d, false)
            c.restoreToCount(svRot)
            if (!full) {   // red N marks north
                val nr = rad - 9f * d; val a = Math.toRadians(yawDeg.toDouble())
                val nx = cx - (Math.sin(a) * nr).toFloat(); val ny = cy - (Math.cos(a) * nr).toFloat()
                p.typeface = Ui.FONT_BOLD; p.textSize = 11f * d; p.textAlign = Paint.Align.CENTER
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f * d; p.color = 0xBF000000.toInt(); c.drawText("N", nx, ny + 4f * d, p)
                p.style = Paint.Style.FILL; p.color = 0xFFFF5A4A.toInt(); c.drawText("N", nx, ny + 4f * d, p)
                p.textAlign = Paint.Align.LEFT
            }
            c.restoreToCount(sv)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = 0xFF1A1A1A.toInt(); c.drawCircle(cx, cy, rad, p)
            p.strokeWidth = 1.5f * d; p.color = Ui.GOLD; c.drawCircle(cx, cy, rad, p); p.style = Paint.Style.FILL
            p.typeface = Ui.FONT_BOLD; p.textSize = 9f * d; p.color = 0xFFCCCCCC.toInt(); p.textAlign = Paint.Align.CENTER
            c.drawText("TAP = MAP", cx, cy + rad + 11f * d, p); p.textAlign = Paint.Align.LEFT
        }

        private fun drawBigMap(c: Canvas, w: Float, h: Float, d: Float) {
            p.style = Paint.Style.FILL; p.color = 0xE6060A10.toInt(); c.drawRect(0f, 0f, w, h, p)
            val sz = minOf(w - 40f * d, h - 24f * d)
            bigR.set(w / 2 - sz / 2, h / 2 - sz / 2, w / 2 + sz / 2, h / 2 + sz / 2)
            drawMapContent(c, bigR, d, true)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = Ui.GOLD; c.drawRect(bigR, p)
            closeR.set(bigR.right + 8f * d, bigR.top, bigR.right + 8f * d + 44f * d, bigR.top + 44f * d)
            if (closeR.right > w - 4f * d) closeR.offsetTo(w - 48f * d, 4f * d)
            p.style = Paint.Style.FILL; p.color = Ui.GOLD; c.drawRect(closeR, p)
            p.typeface = Ui.FONT_BOLD; p.textSize = 22f * d; p.color = Ui.INK; p.textAlign = Paint.Align.CENTER; c.drawText("X", closeR.centerX(), closeR.centerY() + 8f * d, p)
            p.textSize = 11f * d; p.color = Color.WHITE
            c.drawText("TAP THE MAP TO PLACE A PIN  ·  WHITE CIRCLE = NEXT SAFE ZONE  ·  GOLD SQUARE = AIRDROP", w / 2, bigR.bottom + 0f * d - 2f * d, p)
            p.textAlign = Paint.Align.LEFT
        }

        private var bagOpen = false
        private val bagR = RectF()
        private fun drawBag(c: Canvas, w: Float, h: Float) {
            val lines = renderer.backpackLines()
            val pw = 300f * d; val rh = 24f * d; val ph = 40f * d + rh * lines.size
            bagR.set(w / 2 - pw / 2, h / 2 - ph / 2, w / 2 + pw / 2, h / 2 + ph / 2)
            p.style = Paint.Style.FILL; p.color = 0xEE0E0B16.toInt(); c.drawRoundRect(bagR, 8f * d, 8f * d, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = 0xFFFDB913.toInt(); c.drawRoundRect(bagR, 8f * d, 8f * d, p)
            p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textSize = 15f * d; p.color = 0xFFFDB913.toInt(); p.textAlign = Paint.Align.LEFT
            c.drawText("BACKPACK", bagR.left + 14f * d, bagR.top + 26f * d, p)
            var y = bagR.top + 26f * d + rh * 0.9f
            for ((l, v) in lines) {
                p.textSize = 13f * d; p.color = 0xFFCCCCCC.toInt(); p.textAlign = Paint.Align.LEFT; c.drawText(l, bagR.left + 14f * d, y, p)
                p.color = Color.WHITE; p.textAlign = Paint.Align.RIGHT; c.drawText(v, bagR.right - 14f * d, y, p); y += rh
            }
            p.textAlign = Paint.Align.LEFT
        }

        /** PNG round button (HudIcons); when the PNG is missing the old text button is drawn instead */
        private fun ibtn(c: Canvas, k: FloatArray, icon: String?, label: String, on: Boolean, ts: Float, dim: Boolean = false, rot: Float = 0f, badge: String? = null, slash: Boolean = false, sub: String? = null) {
            if (!HudIcons.button(c, context, d, k, icon, label, ts, on, dim, rot, badge, slash, sub)) round(c, k, label, on, ts)
        }

        /** small rectangular button: dark pill + PNG icon on the left + text (empty text = the icon alone) */
        private fun rectIcon(c: Canvas, r: RectF, icon: String, t: String, ts: Float) {
            if (HudIcons.get(context, icon) == null) { rect(c, r, if (t.isEmpty()) "⚙" else t); return }
            if (t.isEmpty()) { HudIcons.draw(c, context, icon, r.centerX(), r.centerY(), r.height() * 1.15f); return }
            p.style = Paint.Style.FILL; p.color = 0x99000000.toInt(); c.drawRoundRect(r, 8f * d, 8f * d, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = 0xFFFDB913.toInt(); c.drawRoundRect(r, 8f * d, 8f * d, p)
            HudIcons.draw(c, context, icon, r.left + r.height() * 0.55f, r.centerY(), r.height() * 0.85f)
            val tx0 = r.left + r.height() * 1.05f
            p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textSize = ts * d; p.textAlign = Paint.Align.CENTER; p.color = Color.WHITE
            c.drawText(t, tx0 + (r.right - tx0) / 2f, r.centerY() + ts * d * 0.35f, p); p.textAlign = Paint.Align.LEFT
        }

        private fun round(c: Canvas, k: FloatArray, t: String, on: Boolean, ts: Float) {
            p.style = Paint.Style.FILL; p.color = if (on) 0xFFFDB913.toInt() else 0x99000000.toInt()
            c.drawCircle(k[0], k[1], k[2], p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = 0xFFFDB913.toInt(); c.drawCircle(k[0], k[1], k[2], p)
            p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textSize = ts * d; p.textAlign = Paint.Align.CENTER
            p.color = if (on) Ui.INK else Color.WHITE; c.drawText(t, k[0], k[1] + ts * d * 0.35f, p); p.textAlign = Paint.Align.LEFT
        }

        private fun rect(c: Canvas, r: RectF, t: String) {
            p.style = Paint.Style.FILL; p.color = 0x99000000.toInt(); c.drawRoundRect(r, 8f * d, 8f * d, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = 0xFFFDB913.toInt(); c.drawRoundRect(r, 8f * d, 8f * d, p)
            p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textSize = 16f * d; p.textAlign = Paint.Align.CENTER
            p.color = Color.WHITE; c.drawText(t, r.centerX(), r.centerY() + 6f * d, p); p.textAlign = Paint.Align.LEFT
        }

        private fun drawDriveHud(c: Canvas, w: Float, h: Float) {
            ibtn(c, circle(20, w, h), "wheel", "LEFT", renderer.drvLeft, 11f, rot = HudIcons.rotFor(20))
            ibtn(c, circle(21, w, h), "wheel", "RIGHT", renderer.drvRight, 11f, rot = HudIcons.rotFor(21))
            ibtn(c, circle(22, w, h), null, "GAS", renderer.drvFwd || renderer.drvAuto, 18f)
            ibtn(c, circle(23, w, h), null, "BRAKE", renderer.drvBack, 11f)
            ibtn(c, circle(24, w, h), "car", "AUTO", renderer.drvAuto, 10f)
            ibtn(c, circle(25, w, h), "fire", renderer.boostLabel, renderer.boostOn, 10f, sub = renderer.boostLabel)
            ibtn(c, circle(26, w, h), null, "HORN", false, 10f)
            ibtn(c, circle(27, w, h), "medkit", "MED x${renderer.medkits}", renderer.healProgress >= 0f, 9f, dim = renderer.medkits < 1, badge = "${renderer.medkits}")
            rectIcon(c, getOffR, "door", "GET OFF", 11f * HudLayout.scale(42))
            // speedometer: half-circle gauge + km/h
            val cx = w - 72f * d; val cy = h - 200f * d; val rr = 44f * d
            p.style = Paint.Style.FILL; p.color = 0x88000000.toInt(); c.drawCircle(cx, cy, rr + 6f * d, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 4f * d; p.color = 0x88FFFFFF.toInt()
            c.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), 180f, 180f, false, p)
            val kmh = renderer.vehSpeedKmh
            val ang = Math.toRadians(180.0 + 180.0 * (kmh / 140f).coerceIn(0f, 1f))
            p.color = if (renderer.boostOn) 0xFFFFAC78.toInt() else 0xFFFDB913.toInt(); p.strokeWidth = 3f * d
            c.drawLine(cx, cy, cx + (rr * 0.85f * Math.cos(ang)).toFloat(), cy + (rr * 0.85f * Math.sin(ang)).toFloat(), p)
            p.style = Paint.Style.FILL; p.typeface = Ui.FONT_BOLD; p.textAlign = Paint.Align.CENTER; p.color = Color.WHITE
            p.textSize = 16f * d; c.drawText("$kmh", cx, cy + 20f * d, p)
            p.textSize = 8f * d; c.drawText("KM/H  ${renderer.vehName}", cx, cy + 32f * d, p); p.textAlign = Paint.Align.LEFT
        }

        /** Driving controls (touch-down). Returns true when the touch hit one of them. */
        private fun driveDown(id: Int, x: Float, y: Float): Boolean {
            when {
                inCircle(20, x, y) -> { btnIds[id] = 20; renderer.drvLeft = true }
                inCircle(21, x, y) -> { btnIds[id] = 21; renderer.drvRight = true }
                inCircle(22, x, y) -> { btnIds[id] = 22; renderer.drvFwd = true }
                inCircle(23, x, y) -> { btnIds[id] = 23; renderer.drvBack = true; if (renderer.drvAuto) renderer.autopilot() }
                inCircle(24, x, y) -> { btnIds[id] = 24; renderer.autopilot() }
                inCircle(25, x, y) -> { btnIds[id] = 25; renderer.boost() }
                inCircle(26, x, y) -> { btnIds[id] = 26; renderer.horn() }
                inCircle(27, x, y) -> { btnIds[id] = 27; renderer.heal() }
                getOffR.contains(x, y) -> { btnIds[id] = 28 }
                else -> return false
            }
            return true
        }

        /** the joystick may be moved anywhere in the HUD editor: a touch on it starts the stick even outside the left 45 % */
        private fun nearStick(x: Float, y: Float): Boolean { val k = HudLayout.circle(46, width.toFloat(), height.toFloat(), d); return hypot(x - k[0], y - k[1]) <= k[2] * 1.3f }

        private fun inCircle(id: Int, x: Float, y: Float): Boolean {
            val k = circle(id, width.toFloat(), height.toFloat()); return hypot(x - k[0], y - k[1]) <= k[2] * 1.15f
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val a = e.actionMasked
            if (mapOpen) {
                if (a == MotionEvent.ACTION_UP) {
                    if (closeR.contains(e.x, e.y) || !bigR.contains(e.x, e.y)) mapOpen = false
                    else {
                        val half = mapImg.half
                        pinX = (e.x - bigR.left) / bigR.width() * 2f * half - half; pinZ = (e.y - bigR.top) / bigR.height() * 2f * half - half; hasPin = true
                    }
                }
                return true
            }
            if (renderer.spec) {
                when (a) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        val i = e.actionIndex; val id = e.getPointerId(i); val x = e.getX(i); val y = e.getY(i)
                        when {
                            !prevR.isEmpty && prevR.contains(x, y) -> btnIds[id] = 40
                            !nextR.isEmpty && nextR.contains(x, y) -> btnIds[id] = 41
                            leaveR.contains(x, y) -> btnIds[id] = 42
                            lookId < 0 -> { lookId = id; lx = x; ly = y }
                        }
                    }
                    MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                        if (e.getPointerId(i) == lookId) { renderer.look(e.getX(i) - lx, e.getY(i) - ly); lx = e.getX(i); ly = e.getY(i) }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        val i = e.actionIndex; val id = e.getPointerId(i)
                        if (id == lookId) lookId = -1
                        val b = btnIds.remove(id)
                        if (a != MotionEvent.ACTION_CANCEL) {
                            if (b == 40 && prevR.contains(e.getX(i), e.getY(i))) renderer.specCycle(-1)
                            if (b == 41 && nextR.contains(e.getX(i), e.getY(i))) renderer.specCycle(1)
                            if (b == 42 && leaveR.contains(e.getX(i), e.getY(i)) && !specLeft) { specLeft = true; onExit(null) }
                        }
                    }
                }
                return true
            }
            if (renderer.matchState != 0) {
                if (a == MotionEvent.ACTION_UP) {
                    if (restartR.contains(e.x, e.y)) onRestart()
                    else if (lobbyR.contains(e.x, e.y)) onExit(null)
                }
                return true
            }
            when (a) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = e.actionIndex; val id = e.getPointerId(i); val x = e.getX(i); val y = e.getY(i)
                    when {
                        exitR.contains(x, y) -> btnIds[id] = 3
                        gearR.contains(x, y) -> btnIds[id] = 14
                        !mmR.isEmpty && mmR.contains(x, y) -> btnIds[id] = 13
                        renderer.driving && driveDown(id, x, y) -> { }
                        renderer.driving -> { if (lookId < 0) { lookId = id; lx = x; ly = y } }
                        renderer.airState != 3 && inCircle(101, x, y) -> { btnIds[id] = 1; renderer.jump() }
                        renderer.airState == 1 && inCircle(102, x, y) -> { btnIds[id] = 30; renderer.toggleDive() }
                        renderer.airState != 3 && inCircle(119, x, y) -> { btnIds[id] = 30; Prefs.sfx = !Prefs.sfx; Prefs.save(context) }
                        renderer.airState != 3 && (x < width * 0.45f || nearStick(x, y)) && stickId < 0 -> { stickId = id; sx = x; sy = y; kx = x; ky = y }
                        renderer.airState != 3 && lookId < 0 -> { lookId = id; lx = x; ly = y }
                        renderer.vehPrompt.isNotEmpty() && renderer.airState == 3 && inCircle(16, x, y) -> { btnIds[id] = 16; renderer.useVehicle() }
                        bagOpen && bagR.contains(x, y) -> { btnIds[id] = 30; bagOpen = false }
                        inCircle(18, x, y) -> { btnIds[id] = 30; bagOpen = !bagOpen }
                        inCircle(19, x, y) -> { btnIds[id] = 30; Prefs.sfx = !Prefs.sfx; Prefs.save(context) }
                        inCircle(29, x, y) -> { btnIds[id] = 30; Prefs.thirdPerson = !Prefs.thirdPerson; Prefs.save(context) }
                        renderer.healProgress >= 0f && inCircle(31, x, y) -> { btnIds[id] = 31; renderer.cancelHeal() }
                        inCircle(9, x, y) -> { btnIds[id] = 15; renderer.eat() }
                        inCircle(4, x, y) -> { btnIds[id] = 4; renderer.firing = true }
                        inCircle(1, x, y) -> { btnIds[id] = 1; renderer.jump() }
                        inCircle(2, x, y) -> { btnIds[id] = 2; renderer.run = !renderer.run }
                        inCircle(17, x, y) -> { btnIds[id] = 17; renderer.toggleCrouch() }
                        renderer.gunName != "FISTS" && inCircle(5, x, y) -> { btnIds[id] = 5; renderer.reload() }
                        inCircle(6, x, y) -> { btnIds[id] = 8; renderer.heal() }
                        inCircle(7, x, y) -> { btnIds[id] = 10; renderer.toggleAim() }
                        inCircle(8, x, y) -> { btnIds[id] = 12; renderer.throwGrenade() }
                        scopeR.contains(x, y) -> { btnIds[id] = 11; renderer.cycleScope() }
                        !pickR.isEmpty && pickR.contains(x, y) -> { btnIds[id] = 9; renderer.pickup() }
                        gunR.contains(x, y) -> { btnIds[id] = if (x < gunR.centerX()) 6 else 7; renderer.switchGun(if (x < gunR.centerX()) -1 else 1) }
                        (x < width * 0.45f || nearStick(x, y)) && stickId < 0 -> { stickId = id; sx = x; sy = y; kx = x; ky = y }
                        lookId < 0 -> { lookId = id; lx = x; ly = y }
                    }
                }
                MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i); val x = e.getX(i); val y = e.getY(i)
                    if (id == stickId) {
                        var dx = x - sx; var dy = y - sy; val l = hypot(dx, dy)
                        if (l > R) { dx = dx / l * R; dy = dy / l * R }
                        kx = sx + dx; ky = sy + dy
                        renderer.moveX = dx / R; renderer.moveY = dy / R
                    } else if (id == lookId) { renderer.look(x - lx, y - ly); lx = x; ly = y }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    val i = e.actionIndex; val id = e.getPointerId(i)
                    if (id == stickId) { stickId = -1; renderer.moveX = 0f; renderer.moveY = 0f }
                    if (id == lookId) lookId = -1
                    val b = btnIds.remove(id)
                    when (b) { 20 -> renderer.drvLeft = false; 21 -> renderer.drvRight = false; 22 -> renderer.drvFwd = false; 23 -> renderer.drvBack = false }
                    if (b == 28 && a != MotionEvent.ACTION_CANCEL && getOffR.contains(e.getX(i), e.getY(i))) renderer.useVehicle()
                    if (b == 4) renderer.firing = false
                    if (b == 3 && a != MotionEvent.ACTION_CANCEL && exitR.contains(e.getX(i), e.getY(i))) onExit(renderer.quitResult())
                    if (b == 14 && a != MotionEvent.ACTION_CANCEL && gearR.contains(e.getX(i), e.getY(i))) onSettings()
                    if (b == 13 && a != MotionEvent.ACTION_CANCEL && mmR.contains(e.getX(i), e.getY(i))) mapOpen = true
                    if (a == MotionEvent.ACTION_CANCEL) { renderer.drvLeft = false; renderer.drvRight = false; renderer.drvFwd = false; renderer.drvBack = false; renderer.firing = false; stickId = -1; lookId = -1; renderer.moveX = 0f; renderer.moveY = 0f; btnIds.clear() }
                }
            }
            return true
        }
    }
}
