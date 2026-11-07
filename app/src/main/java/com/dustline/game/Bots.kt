package com.dustline.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class Bot(val name: String, val shirt: Int, val gun: Gun) {
    var x = 0f; var y = 0f; var z = 0f
    var hp = 200f; var alive = false
    var yaw = 0f; var walk = 0f; var speed = 0f
    var cooldown = 1f; var think = 0f; var strafe = 1f
    var visible = false; var deadAt = -100f; var barTime = 0f
    var wanderX = 0f; var wanderZ = 0f; var wanderT = 0f
    val avoid = Random.nextFloat() * 6.28f
    var grenades = 1; var nadeCd = 4f + Random.nextFloat() * 6f
    // air drop: 0 in plane, 1 freefall, 2 parachute, 3 on the ground (normal AI)
    var air = 3; var jumpAt = 0f; var tx = 0f; var tz = 0f
    var vx = 0f; var vy = 0f; var vz = 0f; var dive = true; var chuteT = 0f
    var reported = false   // kill-feed entry already written
    var carHitUntil = -100f // run over by a vehicle: immune to a second hit until this clock time
    // DX PATCH roadkill knockback (dxKnock of the HTML): flung away from the car, flies, lands, lies stunned for a moment
    var kbT = 0f; var kbVx = 0f; var kbVy = 0f; var kbVz = 0f
    // smart cover AI
    var coverX = 0f; var coverZ = 0f; var coverT = 0f; var coverCd = 0f; var hurtT = 0f; var lastHp = 200f
    // driving
    var car: Vehicle? = null; var carTarget: Vehicle? = null; var carT = 0f; var carCd = 3f + Random.nextFloat() * 10f
    // bot-vs-bot fights
    var foe: Bot? = null; var kills = 0
    var driveT = 0f; var stuckT = 0f; var stuckN = 0; var revT = 0f; var revSteer = 1f; var hitCarUntil = -100f
    // Phase 28: air visuals (skate stance / dive / canopy pose = descend() + dskAirPose() of the HTML), sway only after the first shot
    // (HTML: e._pr exists from the first shot on), carEsc = walking to an escape car (reserveEscapeCar of the HTML)
    val av = AirVis(); var fired = false; var carEsc = false
}

class Tracer(val x0: Float, val y0: Float, val z0: Float, val x1: Float, val y1: Float, val z1: Float, var life: Float)

/**
 * Port of botUpdate()/botShoot() from the HTML (normal difficulty): picks the player as target, sees 53 m with
 * line of sight, strafes inside 17 m and backs off inside 8 m, walks 3.65 m/s (2.1 when fighting), fires every
 * 0.45-0.9 s with spread 0.028 * gun factor for 9 damage.
 */
class BotSystem(private val map: MapData, private val rnd: Random, count: Int) {
    @Volatile var noDamage = false   // warm-up: nobody gets hurt
    companion object {
        val NAMES = listOf("KESTREL", "MICA", "ROOK", "SABLE", "FLINT", "NOVA", "DUNE", "COBALT", "WREN", "EMBER", "ASH", "ECHO", "SLATE", "HAZE", "QUILL")
        val SHIRTS = intArrayOf(0xFF7A2F2F.toInt(), 0xFF2F5A7A.toInt(), 0xFF5A7A2F.toInt(), 0xFF7A5A2F.toInt(), 0xFF5A2F7A.toInt(), 0xFF2F7A6A.toInt())
        const val HP = 200f
        const val DAMAGE = 9f
        const val EYE = 1.35f
        const val BODY_H = 1.62f
        const val HEAD_Y = 1.38f
    }

    val bots = ArrayList<Bot>()
    val tracers = ArrayList<Tracer>()
    /** called when a bot fires (for the shot sound) */
    var onShot: ((Bot) -> Unit)? = null
    /** called when a bot kills another bot: (killer, victim, weapon name) - renderer writes the kill feed and drops loot */
    var onBotKill: ((Bot, Bot, String) -> Unit)? = null
    private val angles = floatArrayOf(0f, .5f, -.5f, 1f, -1f, 1.55f, -1.55f, 2.2f, -2.2f, 3.1416f)
    private val tmp = FloatArray(3)
    /** set by the renderer: car system (bots drive) and the car the player is driving right now */
    var vehicles: VehicleSystem? = null
    var playerCar: Vehicle? = null
    private val tmpBots = ArrayList<Bot>()
    private var playerHitUntil = -100f   // run over by an NPC car: immune to the next hit until this clock time (HTML vehicleImpactUntil, 0.9 s)

    init {
        for (i in 0 until count) {
            val g = Weapons.ALL[rnd.nextInt(Weapons.ALL.size)]
            bots.add(Bot(NAMES[i % NAMES.size] + (if (i >= NAMES.size) "${i / NAMES.size + 1}" else ""), SHIRTS[i % SHIRTS.size], g))
        }
    }

    fun spawnAll(px: Float, pz: Float, clock: Float) { for (b in bots) respawn(b, px, pz, clock) }

    private fun respawn(b: Bot, px: Float, pz: Float, clock: Float) {
        var p: FloatArray? = null
        for (n in 0 until 40) { // spread over the whole play area, at least 70 m from the player
            val c = map.randomLand(rnd, 0f, 0f, 20f, 760f) ?: continue
            if (hypot(c[0] - px, c[2] - pz) > 70f) { p = c; break }
        }
        if (p == null) { b.alive = false; return }
        b.x = p[0]; b.y = p[1]; b.z = p[2]; b.hp = HP; b.alive = true; b.cooldown = 1f + rnd.nextFloat() * 1.5f
        b.think = 0f; b.visible = false; b.barTime = 0f; b.speed = 0f
    }

    private fun between(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    private fun free(b: Bot, x: Float, z: Float) = !map.terrainBlocked(x, z, 0.45f, b.y) && !map.boxBlocked(x, z, 0.45f, b.y)

    private fun hasLos(b: Bot, px: Float, py: Float, pz: Float): Boolean {
        val ox = b.x; val oy = b.y + 1.4f; val oz = b.z
        var dx = px - ox; var dy = py + 1.3f - oy; var dz = pz - oz
        val d = sqrt(dx * dx + dy * dy + dz * dz); if (d < 0.01f) return true
        dx /= d; dy /= d; dz /= d
        val t = map.rayWorld(ox, oy, oz, dx, dy, dz, d)
        return t < 0f || t > d - 0.5f
    }

    /** onHit(damage, botName) is called when a bot bullet hits the player. */
    fun update(dt: Float, clock: Float, px: Float, py: Float, pz: Float, playerAlive: Boolean, zone: Float, zx: Float, zz: Float, zoneDmg: Float, onHit: (Float, String) -> Unit, onThrow: (Bot, Float, Float, Float) -> Unit) {
        val it = tracers.iterator(); while (it.hasNext()) { val t = it.next(); t.life -= dt; if (t.life <= 0f) it.remove() }
        val carCount = bots.count { it.car != null }
        for (b in bots) {
            if (b.kbT > 0f) stepKnock(b, dt)
            if (!b.alive) { releaseCar(b); continue }
            if (b.air != 3) continue
            if (b.kbT > 0f) { b.barTime = max(0f, b.barTime - dt); continue }   // flung by a car: no thinking / shooting until it lands and gets up
            b.nadeCd -= dt; b.cooldown -= dt; b.think -= dt; b.barTime = max(0f, b.barTime - dt); b.wanderT -= dt
            b.coverCd -= dt; b.carCd -= dt; b.carT -= dt; b.hurtT = max(0f, b.hurtT - dt)
            val dx0 = px - b.x; val dz0 = pz - b.z; val dist = hypot(dx0, dz0)
            if (b.think <= 0f) {
                b.think = between(.4f, .85f)
                b.visible = playerAlive && dist < 53f && hasLos(b, px, py, pz)
                // bot-vs-bot: nearest visible enemy bot (closer than the player if the player is visible too)
                b.foe = null
                if (b.car == null) {
                    var fd = if (b.visible) dist else 53f
                    for (o in bots) {
                        if (o === b || !o.alive || o.air != 3 || o.car != null) continue
                        val dd = hypot(o.x - b.x, o.z - b.z)
                        if (dd < fd && hasLos(b, o.x, o.y, o.z)) { fd = dd; b.foe = o }
                    }
                }
                val vs0 = vehicles
                if (vs0 != null && b.car == null && b.carTarget == null && b.carCd <= 0f && !b.visible && b.foe == null && b.hurtT <= 0f && dist > 55f && b.coverT <= 0f && carCount < 6) {
                    b.carCd = between(8f, 20f)
                    if (rnd.nextFloat() < 0.4f) { // walk to the nearest free car within 45 m
                        var bv: Vehicle? = null; var bd = 45f
                        for (v in vs0.list) {
                            if (v.botDriver != null || v === playerCar) continue
                            val dd = hypot(v.x - b.x, v.z - b.z); if (dd < bd) { bd = dd; bv = v }
                        }
                        if (bv != null) { b.carTarget = bv; b.carT = 25f }
                    }
                }
            }
            val rad = hypot(b.x - zx, b.z - zz); val urgent = rad > zone - 5f
            if (rad > zone && zoneDmg > 0f) { b.hp -= zoneDmg; if (b.hp <= 0f) { b.alive = false; b.deadAt = clock; continue } }
            val chase = !urgent && playerAlive && dist < 140f
            var foe = b.foe
            if (foe != null && (!foe.alive || foe.air != 3 || foe.car != null || hypot(foe.x - b.x, foe.z - b.z) > 60f)) { b.foe = null; foe = null }
            if (b.hp < b.lastHp - 0.01f) b.hurtT = 3f
            b.lastHp = b.hp
            // Phase 28: NPC escape car (reserveEscapeCar of the HTML): outside the safe zone (and far from its centre) or nearly dead and just hit
            // -> run to the nearest free car within 38 m, even while seen or hurt
            val vsE = vehicles
            if (vsE != null && b.car == null && b.carTarget == null && b.carCd <= 0f && b.coverT <= 0f && zone > 35f &&
                ((urgent && rad > 48f) || (b.hp < HP * 0.3f && b.hurtT > 0f))) {
                b.carCd = between(8f, 20f)
                var bvE: Vehicle? = null; var bdE = 38f
                for (v in vsE.list) {
                    if (v.botDriver != null || v === playerCar) continue
                    val ddE = hypot(v.x - b.x, v.z - b.z); if (ddE < bdE) { bdE = ddE; bvE = v }
                }
                if (bvE != null) { b.carTarget = bvE; b.carT = 25f; b.carEsc = true }
            }
            if (b.car == null) {
                var ctv = b.carTarget
                if (ctv != null) {
                    if (ctv.botDriver != null || ctv === playerCar || b.carT <= 0f || (!b.carEsc && (b.visible || b.foe != null || b.hurtT > 0f))) { b.carTarget = null; b.carEsc = false }
                    else if (hypot(ctv.x - b.x, ctv.z - b.z) < 2.9f) { // board
                        ctv.botDriver = b; b.car = ctv; b.carTarget = null; b.driveT = 0f; b.stuckT = 0f; b.stuckN = 0; b.revT = 0f; b.speed = 0f; b.wanderT = 0f
                    }
                }
                if (b.car == null && b.carTarget == null) {
                    if (b.coverT > 0f) b.coverT -= dt
                    else if (chase && b.visible && b.hurtT > 0f && dist > 6f && b.coverCd <= 0f) { // smart cover: hurt -> run behind a wall, peek again later
                        val cv = findCover(b, px, py, pz)
                        if (cv != null) { b.coverX = cv[0]; b.coverZ = cv[1]; b.coverT = between(2.5f, 4.5f); b.coverCd = 9f } else b.coverCd = 2f
                    }
                }
            }
            if (b.car != null) { driveBot(b, dt, clock, px, py, pz, playerAlive, urgent, chase, dist, zx, zz, onHit); continue }
            val ctv2 = b.carTarget
            var gx: Float; var gz: Float
            if (urgent) { gx = zx; gz = zz }
            else if (b.coverT > 0f) { gx = b.coverX; gz = b.coverZ }
            else if (ctv2 != null) { gx = ctv2.x; gz = ctv2.z }
            else if (foe != null) { gx = foe.x; gz = foe.z }
            else if (chase) { gx = px; gz = pz }
            else {
                if (b.wanderT <= 0f) {
                    b.wanderT = between(6f, 11f)
                    val w = map.randomLand(rnd, b.x, b.z, 25f, 70f); if (w != null) { b.wanderX = w[0]; b.wanderZ = w[2] } else { b.wanderX = b.x; b.wanderZ = b.z }
                }
                gx = b.wanderX; gz = b.wanderZ
            }
            var dx = gx - b.x; var dz = gz - b.z
            val ex = if (foe != null) foe.x else px; val ez = if (foe != null) foe.z else pz
            val edist = if (foe != null) hypot(foe.x - b.x, foe.z - b.z) else dist
            val engaged = foe != null || (chase && b.visible)
            if (engaged && !urgent && edist < 17f && b.coverT <= 0f && ctv2 == null) {
                val f = if (sin(clock * .65f + b.avoid) > 0) 1f else -1f
                val old = dx; dx = dz * f; dz = -old * f
                if (edist < 8f) { dx += (b.x - ex) * 1.5f; dz += (b.z - ez) * 1.5f }
            }
            val len = hypot(dx, dz)
            if (len > 0.5f) {
                dx /= len; dz /= len
                var best = -1e9f; var bx = 0f; var bz = 0f
                for (a in angles) {
                    val x = dx * cos(a) - dz * sin(a); val z = dx * sin(a) + dz * cos(a)
                    if (free(b, b.x + x * .75f, b.z + z * .75f)) {
                        val score = x * dx + z * dz - abs(a) * .035f
                        if (score > best) { best = score; bx = x; bz = z }
                    }
                }
                val sp = if (urgent || b.coverT > 0f) 4.7f else if (engaged && edist < 20f) 2.1f else 3.65f
                val nx = b.x + bx * sp * dt; val nz = b.z + bz * sp * dt
                if (free(b, nx, b.z)) b.x = nx
                if (free(b, b.x, nz)) b.z = nz
                tmp[0] = b.x; tmp[1] = b.y; tmp[2] = b.z; map.resolve(tmp, 0.42f); b.x = tmp[0]; b.z = tmp[2]
                b.speed = if (best > -1e8f) sp else 0f
            } else b.speed = 0f
            val g = map.footY(b.x, b.z, b.y); b.y += (g - b.y) * minOf(1f, dt * 14f)
            b.walk += b.speed * dt * 1.9f
            b.yaw = if (foe != null && !urgent && edist < 55f) atan2(b.x - ex, b.z - ez) else if (chase && dist < 50f) atan2(b.x - px, b.z - pz) else atan2(-dx, -dz)
            if (foe != null) {
                if (b.grenades > 0 && b.nadeCd <= 0f && edist > 10f && edist < 28f) { b.grenades--; b.nadeCd = between(6f, 12f); onThrow(b, foe.x, foe.y, foe.z) }
                if (b.cooldown <= 0f && edist < 64f) { b.cooldown = between(.45f, .9f); shootBot(b, foe, clock) }
            } else
            if (playerAlive && b.visible && b.grenades > 0 && b.nadeCd <= 0f && dist > 10f && dist < 28f) {
                b.grenades--; b.nadeCd = between(6f, 12f); onThrow(b, px, py, pz)
            }
            if (playerAlive && b.visible && b.cooldown <= 0f && dist < 64f) {
                b.cooldown = between(.45f, .9f)
                shoot(b, px, py, pz, onHit)
            }
        }
    }

    // ---------------------------------------------------------------- smart cover
    private fun blockedLine(ox: Float, oy: Float, oz: Float, tx: Float, ty: Float, tz: Float): Boolean {
        var dx = tx - ox; var dy = ty - oy; var dz = tz - oz
        val d = sqrt(dx * dx + dy * dy + dz * dz); if (d < 0.01f) return false
        dx /= d; dy /= d; dz /= d
        val t = map.rayWorld(ox, oy, oz, dx, dy, dz, d)
        return t >= 0f && t < d - 0.3f
    }

    /** A free spot 3-11 m away that the player cannot see (head and chest height both blocked), nearest first. */
    private fun findCover(b: Bot, px: Float, py: Float, pz: Float): FloatArray? {
        var best: FloatArray? = null; var bd = 1e9f
        for (n in 0 until 14) {
            val a = rnd.nextFloat() * 6.2831855f; val r = between(3f, 11f)
            val x = b.x + sin(a) * r; val z = b.z + cos(a) * r
            if (!free(b, x, z)) continue
            val y = map.footY(x, z, b.y)
            if (abs(y - b.y) > 1.2f) continue
            if (hypot(x - px, z - pz) < 5f) continue
            if (!blockedLine(px, py + 1.5f, pz, x, y + 1.4f, z)) continue
            if (!blockedLine(px, py + 1.5f, pz, x, y + 0.6f, z)) continue
            val d = hypot(x - b.x, z - b.z)
            if (d < bd) { bd = d; best = floatArrayOf(x, z) }
        }
        return best
    }

    // ---------------------------------------------------------------- bots driving cars
    private fun releaseCar(b: Bot) {
        val v = b.car
        if (v != null) { v.botDriver = null; v.speed = 0f }
        b.car = null; b.carTarget = null
    }

    private fun exitCar(b: Bot, v: Vehicle) {
        val vs = vehicles
        tmpBots.clear(); for (o in bots) if (o !== b) tmpBots.add(o)
        val out = FloatArray(3)
        if (vs != null && vs.exitSpot(v, v.x, v.z, tmpBots, out)) { b.x = out[0]; b.y = out[1]; b.z = out[2] }
        v.botDriver = null; v.speed = 0f
        b.car = null; b.speed = 0f; b.carCd = between(15f, 30f); b.think = 0f; b.wanderT = 0f
    }

    /** Fling a bot away from a car (dxRoadkill): ground speed 6..19 m/s along the car's heading + sideways, upward 4.5 + speed * .22, gravity 14. */
    fun knock(b: Bot, vx: Float, vz: Float, speed: Float) {
        val hs = max(6f, minOf(19f, speed * .95f))
        b.kbVx = vx * hs; b.kbVz = vz * hs; b.kbVy = 4.5f + speed * .22f
        b.kbT = 0.9f + b.kbVy / 14f * 2f   // flight time + a short moment lying on the ground
        if (b.car != null) releaseCar(b)
    }

    private fun stepKnock(b: Bot, dt: Float) {
        b.kbT -= dt
        val airborne = b.kbVy > 0f || b.y > map.footY(b.x, b.z, b.y) + 0.05f
        if (airborne) {
            val nx = b.x + b.kbVx * dt; val nz = b.z + b.kbVz * dt
            if (!map.terrainBlocked(nx, nz, .4f, b.y) && !map.boxBlocked(nx, nz, .4f, b.y)) { b.x = nx; b.z = nz } else { b.kbVx = 0f; b.kbVz = 0f }
            b.kbVy -= 14f * dt
            b.y += b.kbVy * dt
            val g = map.footY(b.x, b.z, b.y)
            if (b.y <= g) { b.y = g; b.kbVy = 0f; b.kbVx = 0f; b.kbVz = 0f }
        } else {
            b.kbVx = 0f; b.kbVz = 0f; b.kbVy = 0f
            b.y = map.footY(b.x, b.z, b.y)
        }
        b.speed = 0f
        if (b.kbT <= 0f) { b.kbT = 0f; b.kbVx = 0f; b.kbVz = 0f; b.kbVy = 0f }
    }

    private fun carKill(o: Bot, v: Vehicle, clock: Float) {
        if (!o.alive || clock < o.carHitUntil) return
        o.carHitUntil = clock + 0.9f
        run {
            val sg = if (v.speed >= 0f) 1f else -1f; val c = kotlin.math.cos(v.angle); val sn = kotlin.math.sin(v.angle)
            val side = if ((o.x - v.x) * c - (o.z - v.z) * sn >= 0f) 1f else -1f
            var kx = -sn * sg * .9f + c * side * .5f; var kz = -c * sg * .9f - sn * side * .5f
            val kl = hypot(kx, kz).let { if (it == 0f) 1f else it }
            knock(o, kx / kl, kz / kl, abs(v.speed))
        }
        if (!noDamage) o.hp -= minOf(220f, abs(v.speed) * 7.5f); o.barTime = 3f
        if (o.hp > 0f && o.hp <= HP * 0.35f) o.hp = 0f
        if (o.hp <= 0f) {
            o.alive = false; o.deadAt = clock; o.reported = true
            val drv = v.botDriver; if (drv != null) { drv.kills++; onBotKill?.invoke(drv, o, "CAR") }
        }
    }

    /** Steers the car with the same drive() physics as the player: toward the safe zone, the player, or a far wander point. */
    private fun driveBot(b: Bot, dt: Float, clock: Float, px: Float, py: Float, pz: Float, playerAlive: Boolean, urgent: Boolean, chase: Boolean, dist: Float, zx: Float, zz: Float, onHit: (Float, String) -> Unit) {
        val v = b.car ?: return
        val vs = vehicles ?: return
        b.driveT += dt
        var gx: Float; var gz: Float
        if (urgent) { gx = zx; gz = zz }
        else if (chase) { gx = px; gz = pz }
        else {
            if (b.wanderT <= 0f) {
                b.wanderT = between(10f, 18f)
                val w = map.randomLand(rnd, v.x, v.z, 80f, 220f)
                if (w != null) { b.wanderX = w[0]; b.wanderZ = w[2] } else { b.wanderX = v.x; b.wanderZ = v.z }
            }
            gx = b.wanderX; gz = b.wanderZ
        }
        // get out to fight, when arrived, or when the car is hopelessly stuck
        val arrived = !urgent && !chase && hypot(gx - v.x, gz - v.z) < 9f && b.driveT > 6f
        if ((chase && playerAlive && dist < 26f) || arrived || b.stuckN >= 3 || b.driveT > 90f) { exitCar(b, v); return }
        val da = atan2(-(gx - v.x), -(gz - v.z))
        var diff = da - v.angle
        while (diff > 3.1416f) diff -= 6.2832f
        while (diff < -3.1416f) diff += 6.2832f
        var thr = 1f; var steer = -(diff * 1.8f).coerceIn(-1f, 1f); var brake = false
        if (abs(diff) > 1.9f && v.speed > 6f) { brake = true; thr = 0f } else if (abs(diff) > 1.0f) thr = 0.45f
        if (abs(v.speed) < 0.6f) b.stuckT += dt else b.stuckT = max(0f, b.stuckT - dt * 2f)
        if (b.revT > 0f) { b.revT -= dt; thr = -1f; steer = b.revSteer; brake = false }
        else if (b.stuckT > 1.3f && b.driveT > 2f) { b.revT = 1.3f; b.revSteer = if (rnd.nextBoolean()) 1f else -1f; b.stuckT = 0f; b.stuckN++ }
        tmpBots.clear(); for (o in bots) if (o !== b) tmpBots.add(o)
        vs.drive(v, dt, thr, steer, brake, false, tmpBots, clock, { hit -> carKill(hit, v, clock) }, { }, 4.5f)
        vs.point(v, -0.48f, 0.28f, -0.1f, tmp)
        b.x = tmp[0]; b.y = tmp[1]; b.z = tmp[2]; b.yaw = v.angle; b.speed = 0f; b.visible = false
        // the car can run the player over
        if (playerAlive && abs(v.speed) >= 4.5f && clock >= playerHitUntil && abs(py - v.y) < 2.2f) {
            val c = cos(v.angle); val s = sin(v.angle); val ddx = px - v.x; val ddz = pz - v.z
            if (abs(ddx * c - ddz * s) < v.halfWidth + 0.39f && abs(ddx * s + ddz * c) < v.halfLength + 0.39f) {
                playerHitUntil = clock + 0.9f
                onHit(minOf(80f, abs(v.speed) * 6f), b.name)
            }
        }
        // shoot out of the window: slower fire rate and a wider spread than on foot (worse the faster the car goes), only with a clear line of sight
        if (playerAlive && b.cooldown <= 0f && dist < 48f) {
            b.cooldown = between(.8f, 1.5f)
            val blocked = blockedLine(b.x, b.y + EYE, b.z, px, py + 1.2f, pz)
            if (!blocked) shoot(b, px, py, pz, onHit, 1.8f + abs(v.speed) * 0.12f)
        }
    }

    /** Bot fires at another bot: same spread / damage as shoot(), blocked by walls, kill credit + loot via onBotKill. */
    private fun shootBot(b: Bot, o: Bot, clock: Float) {
        b.fired = true
        val spread = Prefs.botSpread() * max(.35f, b.gun.spread / .01f)
        val ox = b.x; val oy = b.y + EYE; val oz = b.z
        var dx = o.x - ox; var dy = o.y + 1.2f - oy; var dz = o.z - oz
        val d0 = sqrt(dx * dx + dy * dy + dz * dz); if (d0 < 0.01f) return
        dx /= d0; dy /= d0; dz /= d0
        dx += between(-spread, spread); dy += between(-spread, spread); dz += between(-spread, spread)
        val dl = sqrt(dx * dx + dy * dy + dz * dz); dx /= dl; dy /= dl; dz /= dl
        val tw = map.rayWorld(ox, oy, oz, dx, dy, dz, 64f)
        val tp = MapData.rayBox(ox, oy, oz, dx, dy, dz, o.x - .3f, o.x + .3f, o.y, o.y + BODY_H, o.z - .25f, o.z + .25f, 64f)
        var end = if (tw > 0f) tw else 40f
        if (tp >= 0f && (tw < 0f || tp < tw)) {
            end = tp
            if (!noDamage) o.hp -= Prefs.botDamage(); o.barTime = 3f
            if (o.hp <= 0f && o.alive) { o.alive = false; o.deadAt = clock; o.reported = true; b.kills++; onBotKill?.invoke(b, o, b.gun.name) }
        }
        tracers.add(Tracer(ox + dx * .6f, oy - .1f, oz + dz * .6f, ox + dx * end, oy + dy * end, oz + dz * end, .08f))
        onShot?.invoke(b)
    }

    private fun shoot(b: Bot, px: Float, py: Float, pz: Float, onHit: (Float, String) -> Unit, spreadK: Float = 1f) {
        b.fired = true
        val spread = Prefs.botSpread() * max(.35f, b.gun.spread / .01f) * spreadK
        val ox = b.x; val oy = b.y + EYE; val oz = b.z
        var dx = px - ox + 0f; var dy = py + 1.2f - oy; var dz = pz - oz
        val d0 = sqrt(dx * dx + dy * dy + dz * dz); dx /= d0; dy /= d0; dz /= d0
        dx += between(-spread, spread); dy += between(-spread, spread); dz += between(-spread, spread)
        val dl = sqrt(dx * dx + dy * dy + dz * dz); dx /= dl; dy /= dl; dz /= dl
        val tw = map.rayWorld(ox, oy, oz, dx, dy, dz, 64f)
        val tp = MapData.rayBox(ox, oy, oz, dx, dy, dz, px - .3f, px + .3f, py, py + 1.7f, pz - .3f, pz + .3f, 64f)
        var end = if (tw > 0f) tw else 40f
        if (tp >= 0f && (tw < 0f || tp < tw)) { end = tp; onHit(Prefs.botDamage(), b.name) }
        tracers.add(Tracer(ox + dx * .6f, oy - .1f, oz + dz * .6f, ox + dx * end, oy + dy * end, oz + dz * end, .08f))
        onShot?.invoke(b)
    }
}
