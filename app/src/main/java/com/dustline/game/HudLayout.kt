package com.dustline.game

import android.graphics.RectF

/**
 * Positions of EVERY touch control of the match HUD. `base()` = the default layout, `circle()` = default + the player's own layout
 * from the HUD layout editor (offset as a fraction of the screen, size factor).
 * Round buttons: [cx, cy, radius]. The wide controls (ids 40..45: EXIT, settings gear, GET OFF, weapon chip, scope chip, pick-up bar)
 * use the same numbers with radius = HALF WIDTH; their height comes from [ratio], and [rectOf] turns them into a RectF.
 * Ids 46 (joystick) and 47 (minimap) are round too. No control can be made smaller than [MIN_S] x its default size.
 */
object HudLayout {
    const val MIN_S = 1.0f   // no control can be made smaller than its default size
    const val MAX_S = 2.5f

    /** widgets first, buttons last: in the editor the later id wins when two overlap, so a button is never hidden behind a big widget */
    val FOOT = intArrayOf(48, 49, 50, 51, 52, 46, 47, 40, 41, 43, 44, 45, 1, 2, 4, 5, 6, 7, 8, 9, 16, 17, 18, 19, 29, 31)
    val DRIVE = intArrayOf(48, 49, 50, 51, 52, 47, 40, 41, 42, 43, 44, 20, 21, 22, 23, 24, 25, 26, 27)
    /** plane + freefall + parachute context (ids 101+ so they are stored apart from the on-foot buttons) */
    val AIR = intArrayOf(48, 49, 50, 51, 52, 46, 47, 40, 41, 101, 102, 119)

    fun isRect(id: Int): Boolean = id in 40..45 || id in 48..52
    fun scale(id: Int): Float = Prefs.hudGet(id)[2].coerceIn(MIN_S, MAX_S)

    fun label(id: Int): String = when (id) {
        1 -> "JUMP"; 2 -> "RUN"; 4 -> "FIRE"; 5 -> "RELOAD"; 6 -> "HEAL"; 7 -> "AIM"; 8 -> "NADE"; 9 -> "EAT"; 16 -> "USE"; 17 -> "SIT"; 18 -> "BAG"; 19 -> "SOUND"; 29 -> "VIEW"; 31 -> "CANCEL"; 101 -> "DROP"; 102 -> "SKATE"; 119 -> "SOUND"
        20 -> "LEFT"; 21 -> "RIGHT"; 22 -> "GAS"; 23 -> "BRAKE"; 24 -> "AUTO"; 25 -> "BOOST"; 26 -> "HORN"; 27 -> "MED"
        40 -> "EXIT"; 41 -> "SETTINGS"; 42 -> "GET OFF"; 43 -> "WEAPON"; 44 -> "SCOPE"; 45 -> "PICK UP"; 46 -> "STICK"; 47 -> "MAP"
        48 -> "ALIVE / KILLS"; 49 -> "ZONE TIMER"; 50 -> "HP / SHIELD / EP"; 51 -> "AMMO"; 52 -> "COORDS / FPS"
        else -> "?"
    }

    /** height / half-width of the wide controls */
    private fun ratio(id: Int): Float = when (id) { 40, 42 -> 0.425f; 41 -> 0.9f; 43 -> 0.165f; 44 -> 0.58f; 45 -> 0.21f; 48 -> 0.1625f; 49 -> 0.085f; 50 -> 0.215f; 51 -> 0.5f; 52 -> 0.125f; else -> 1f }

    fun base(id: Int, w: Float, h: Float, d: Float): FloatArray = when (id) {
        40 -> floatArrayOf(w - 58f * d, 43f * d, 46f * d)          // EXIT
        41 -> floatArrayOf(w - 134f * d, 43f * d, 30f * d)         // settings gear
        42 -> floatArrayOf(w - 58f * d, 88f * d, 46f * d)          // GET OFF (driving)
        43 -> floatArrayOf(w / 2f, h - 30f * d, 110f * d)          // weapon chip  < NAME >
        44 -> floatArrayOf(w / 2f - 148f * d, h - 30f * d, 34f * d) // scope chip (left of the weapon chip, away from JUMP)
        45 -> floatArrayOf(w / 2f - 55f * d, h / 2f - 56f * d, 115f * d)    // PICK UP bar (above the crosshair)
        48 -> floatArrayOf(w / 2f, 21f * d, 80f * d)               // ALIVE / KILLS pill
        49 -> floatArrayOf(w / 2f, 49f * d, 130f * d)              // zone timer bar
        50 -> floatArrayOf(w / 2f - 11f * d, h - 78f * d, 121f * d) // HP + shield + EP bars (with their badges)
        51 -> floatArrayOf(w / 2f + 155f * d, h - 30f * d, 40f * d) // ammo counter  30 / 90
        52 -> floatArrayOf(w - 92f * d, 14f * d, 80f * d)          // coordinates + FPS text
        46 -> floatArrayOf(w * 0.14f, h * 0.72f, 60f * d)          // joystick
        47 -> floatArrayOf(74f * d, 72f * d, 62f * d)              // minimap
        16 -> floatArrayOf(w - 335f * d, h - 132f * d, 32f * d)   // USE (enter a vehicle)
        20 -> floatArrayOf(52f * d, h - 62f * d, 40f * d)         // steer left
        21 -> floatArrayOf(140f * d, h - 62f * d, 40f * d)        // steer right
        22 -> floatArrayOf(w - 72f * d, h - 112f * d, 52f * d)    // gas
        23 -> floatArrayOf(w - 176f * d, h - 70f * d, 38f * d)    // brake / reverse
        24 -> floatArrayOf(46f * d, h - 150f * d, 30f * d)        // autopilot
        25 -> floatArrayOf(108f * d, h - 150f * d, 30f * d)       // boost
        26 -> floatArrayOf(170f * d, h - 150f * d, 30f * d)       // horn
        27 -> floatArrayOf(232f * d, h - 150f * d, 30f * d)       // medkit
        17 -> floatArrayOf(w - 190f * d, h - 184f * d, 30f * d)   // SIT / STAND
        29 -> floatArrayOf(w - 134f * d, 100f * d, 30f * d)   // FPP / TPP view switch (under the settings gear)
        31 -> floatArrayOf(w - 196f * d, h - 262f * d, 30f * d)   // CANCEL the medkit heal (only visible while healing)
        101 -> base(1, w, h, d)
        102 -> base(2, w, h, d)   // SKATE / DIVE toggle (HTML toggleDive / V key), only visible in freefall
        119 -> base(19, w, h, d)
        18 -> floatArrayOf(w / 2f - 218f * d, h - 30f * d, 30f * d)   // BACKPACK (left of the scope chip)
        19 -> floatArrayOf(w * 0.731f, h * 0.0459f + 30f * d, 30f * d)    // SOUND on / off (ffSpk)
        1 -> floatArrayOf(w - 190f * d, h - 48f * d, 30f * d)
        2 -> floatArrayOf(w - 190f * d, h - 118f * d, 30f * d)
        4 -> floatArrayOf(w - 82f * d, h - 112f * d, 50f * d)
        9 -> floatArrayOf(w - 262f * d, h - 240f * d, 30f * d)
        6 -> floatArrayOf(w - 130f * d, h - 198f * d, 30f * d)
        7 -> floatArrayOf(w - 262f * d, h - 96f * d, 30f * d)
        8 -> floatArrayOf(w - 262f * d, h - 168f * d, 30f * d)
        else -> floatArrayOf(w - 58f * d, h - 210f * d, 30f * d) // 5 reload
    }

    fun circle(id: Int, w: Float, h: Float, d: Float): FloatArray {
        val b = base(id, w, h, d)
        val a = Prefs.hudGet(id)
        b[0] += a[0] * w; b[1] += a[1] * h; b[2] *= scale(id)
        return b
    }

    /** the rectangle of a wide control (ids 40..45), with the player's position + size already applied */
    fun rectOf(id: Int, w: Float, h: Float, d: Float, out: RectF) {
        val k = circle(id, w, h, d); val hw = k[2]; val hh = hw * ratio(id)
        out.set(k[0] - hw, k[1] - hh, k[0] + hw, k[1] + hh)
    }
}
