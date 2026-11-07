package com.dustline.game

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** One mesh part of a vehicle (body or wheel), already expanded to the renderer vertex layout (pos3 nor3 uv2 col3). */
class VMesh(val verts: FloatArray, val indices: IntArray, val mat: Int)

/** Material: tint colour + optional base64 JPEG texture (decoded on the GL thread). */
class VMat(val col: FloatArray, val texB64: String?)

class VWheelDef(val x: Float, val y: Float, val z: Float, val r: Float, val w: Float, val parts: List<VMesh>)

class VehicleModel(
    val key: String, val name: String, val mats: List<VMat>, val body: List<VMesh>,
    val wheels: List<VWheelDef>, val height: Float, val boxes: List<FloatArray>
)

/** Loads assets/models/vehicles/vehicles.json (the 5 GLB models of the HTML: SUV, FF car, Lamborghini, monster truck, jeep). */
object VehicleModels {
    val ORDER = listOf("suv", "ffcar", "lambo", "truck", "jeep")
    private var cache: Map<String, VehicleModel>? = null

    @Synchronized
    fun load(ctx: Context): Map<String, VehicleModel> {
        cache?.let { return it }
        val text = ctx.assets.open("models/vehicles/vehicles.json").use { String(it.readBytes(), Charsets.UTF_8) }
        val root = JSONObject(text)
        val out = LinkedHashMap<String, VehicleModel>()
        for (key in ORDER) {
            if (!root.has(key)) continue
            val d = root.getJSONObject(key)
            val ms = d.getJSONArray("mats")
            val mats = ArrayList<VMat>()
            for (i in 0 until ms.length()) {
                val m = ms.getJSONObject(i)
                val c = m.optJSONArray("col")
                val col = floatArrayOf(c?.optDouble(0, 1.0)?.toFloat() ?: 1f, c?.optDouble(1, 1.0)?.toFloat() ?: 1f, c?.optDouble(2, 1.0)?.toFloat() ?: 1f)
                var tex: String? = null
                if (m.has("tex")) { val t = m.getString("tex"); val k = t.indexOf(','); if (k >= 0) tex = t.substring(k + 1) }
                mats.add(VMat(col, tex))
            }
            val body = ArrayList<VMesh>()
            val bj = d.getJSONArray("body")
            for (i in 0 until bj.length()) body.add(mesh(bj.getJSONObject(i)))
            val wheels = ArrayList<VWheelDef>()
            val wj = d.getJSONArray("wheels")
            for (i in 0 until wj.length()) {
                val w = wj.getJSONObject(i)
                val parts = ArrayList<VMesh>()
                val pj = w.getJSONArray("parts")
                for (q in 0 until pj.length()) parts.add(mesh(pj.getJSONObject(q)))
                wheels.add(VWheelDef(w.getDouble("x").toFloat(), w.getDouble("y").toFloat(), w.getDouble("z").toFloat(), w.getDouble("r").toFloat(), w.getDouble("w").toFloat(), parts))
            }
            val boxes = ArrayList<FloatArray>()
            val bx = d.getJSONArray("boxes")
            for (i in 0 until bx.length()) {
                val b = bx.getJSONArray(i)
                boxes.add(FloatArray(6) { b.getDouble(it).toFloat() })
            }
            out[key] = VehicleModel(key, d.optString("name", key.uppercase()), mats, body, wheels, d.getDouble("height").toFloat(), boxes)
        }
        cache = out
        return out
    }

    private fun mesh(g: JSONObject): VMesh {
        val nv = g.getInt("nv"); val ni = g.getInt("ni")
        val pb = ByteBuffer.wrap(Base64.decode(g.getString("p"), Base64.DEFAULT)).order(ByteOrder.LITTLE_ENDIAN)
        val ib = ByteBuffer.wrap(Base64.decode(g.getString("i"), Base64.DEFAULT)).order(ByteOrder.LITTLE_ENDIAN)
        val i32 = g.optBoolean("i32", false)
        val idx = IntArray(ni) { if (i32) ib.getInt(it * 4) else ib.getShort(it * 2).toInt() and 0xFFFF }
        val st = MapData.STRIDE
        val v = FloatArray(nv * st)
        for (k in 0 until nv) {
            val o = k * st
            v[o] = pb.getFloat(k * 12); v[o + 1] = pb.getFloat(k * 12 + 4); v[o + 2] = pb.getFloat(k * 12 + 8)
            v[o + 8] = 1f; v[o + 9] = 1f; v[o + 10] = 1f
        }
        if (g.has("n")) {
            val nb = Base64.decode(g.getString("n"), Base64.DEFAULT)
            for (k in 0 until nv) {
                val o = k * st
                v[o + 3] = (nb[k * 3].toInt() / 127f).coerceIn(-1f, 1f)
                v[o + 4] = (nb[k * 3 + 1].toInt() / 127f).coerceIn(-1f, 1f)
                v[o + 5] = (nb[k * 3 + 2].toInt() / 127f).coerceIn(-1f, 1f)
            }
        } else {
            var t = 0
            while (t + 2 < ni) {
                val a = idx[t] * st; val b = idx[t + 1] * st; val c = idx[t + 2] * st; t += 3
                val ux = v[b] - v[a]; val uy = v[b + 1] - v[a + 1]; val uz = v[b + 2] - v[a + 2]
                val wx = v[c] - v[a]; val wy = v[c + 1] - v[a + 1]; val wz = v[c + 2] - v[a + 2]
                val nx = uy * wz - uz * wy; val ny = uz * wx - ux * wz; val nz = ux * wy - uy * wx
                for (q in intArrayOf(a, b, c)) { v[q + 3] += nx; v[q + 4] += ny; v[q + 5] += nz }
            }
            for (k in 0 until nv) {
                val o = k * st; val l = sqrt(v[o + 3] * v[o + 3] + v[o + 4] * v[o + 4] + v[o + 5] * v[o + 5]).let { if (it < 1e-8f) 1f else it }
                v[o + 3] /= l; v[o + 4] /= l; v[o + 5] /= l
            }
        }
        if (g.has("uv")) {
            val ub = ByteBuffer.wrap(Base64.decode(g.getString("uv"), Base64.DEFAULT)).order(ByteOrder.LITTLE_ENDIAN)
            for (k in 0 until nv) { v[k * st + 6] = ub.getFloat(k * 8); v[k * st + 7] = ub.getFloat(k * 8 + 4) }
        }
        return VMesh(v, idx, g.optInt("mat", 0))
    }
}

/** A drivable vehicle. Angle convention = HTML: forward is (-sin a, -cos a), local +x is the right side. */
class Vehicle(val model: VehicleModel, var x: Float, var z: Float, var angle: Float, val pickup: Boolean) {
    var y = 0f
    var speed = 0f
    var steer = 0f
    var travel = 0f
    var impactUntil = 0f
    /** bot sitting at the wheel (null = free car) */
    var botDriver: Bot? = null
    var tiltX = 0f
    var tiltZ = 0f
    val halfWidth = 1.46f
    val halfLength = 2.5f
    val height = max(model.height + 0.04f, 1.5f)
    val name: String get() = model.name
    /** minX, maxX, minY, maxY, minZ, maxZ in vehicle space: body boxes + one box per wheel (foot blocking). */
    val parts = ArrayList<FloatArray>()

    init {
        for (b in model.boxes) parts.add(b)
        for (w in model.wheels) parts.add(floatArrayOf(w.x - w.w / 2, w.x + w.w / 2, 0f, w.y + w.r, w.z - w.r, w.z + w.r))
    }
}

/** Spawn, collision and driving physics: port of dlCarSpawns / makeVehicle / vehiclePoseClear / stepVehicle / bermTilt. */
class VehicleSystem(private val map: MapData, private val models: Map<String, VehicleModel>) {
    val list = ArrayList<Vehicle>()
    private val ax4 = FloatArray(8)

    companion object {
        const val BODY_H = 1.7f
        private val EXITS = arrayOf(floatArrayOf(-2.12f, -.1f), floatArrayOf(2.12f, -.1f), floatArrayOf(-2.12f, 1.1f), floatArrayOf(2.12f, 1.1f), floatArrayOf(0f, 3.2f))
    }

    // ------------------------------------------------------------------ spawn
    private fun footOK(x: Float, z: Float, r: Float, y: Float) = !map.terrainBlocked(x, z, r) && !map.boxBlocked(x, z, r, y)

    private fun spawnPoints(): ArrayList<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (c in map.cars) out.add(floatArrayOf(c[0], c[1], c[2]))
        val target = 44
        var sd = 987654321
        fun rn(): Float { sd = sd * 1664525 + 1013904223; return ((sd.toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0).toFloat() }
        fun farEnough(x: Float, z: Float, d: Float): Boolean { for (p in out) if (hypot(p[0] - x, p[1] - z) < d) return false; return true }
        for (pass in 0..1) {
            if (out.size >= target) break
            var t = 0
            while (t < 9000 && out.size < target) {
                t++
                val a = rn() * 6.2832f; val r = sqrt(rn()) * (if (pass == 1) 1080f else 960f)
                val x = sin(a) * r; val z = cos(a) * r
                if (pass == 0 && !map.roadAt(x, z)) continue
                val g = map.ground(x, z); if (g < 1.2f) continue
                val ang = rn() * 6.2832f
                if (!map.carOK(x, z, ang, 1.6f, 2.8f) || !footOK(x, z, 2.2f, g + .05f) || !farEnough(x, z, if (pass == 1) 70f else 55f)) continue
                out.add(floatArrayOf(x, z, ang))
            }
        }
        return out
    }

    fun spawnAll() {
        list.clear()
        val sp = spawnPoints()
        for ((i, p) in sp.withIndex()) {
            val m = models[VehicleModels.ORDER[i % VehicleModels.ORDER.size]] ?: continue
            val v = Vehicle(m, p[0], p[1], p[2], i % 2 == 1)
            v.y = map.ground(v.x, v.z)
            list.add(v)
            if (!poseClear(v, v.x, v.z, v.angle)) {
                var done = false
                var rad = 3
                while (rad < 90 && !done) {
                    for (k in 0 until 12) {
                        val a = k / 12f * 6.2832f
                        val x = p[0] + sin(a) * rad; val z = p[1] + cos(a) * rad
                        if (poseClear(v, x, z, p[2])) { v.x = x; v.z = z; v.y = map.ground(x, z); done = true; break }
                    }
                    rad += 3
                }
            }
            tilt(v)
        }
    }

    // ------------------------------------------------------------------ geometry
    private fun overlap(ax: Float, az: Float, aa: Float, ahw: Float, ahl: Float, bx: Float, bz: Float, ba: Float, bhw: Float, bhl: Float): Boolean {
        val ac = cos(aa); val asn = sin(aa); val bc = cos(ba); val bs = sin(ba); val dx = bx - ax; val dz = bz - az
        val a = ax4
        a[0] = ac; a[1] = -asn; a[2] = asn; a[3] = ac; a[4] = bc; a[5] = -bs; a[6] = bs; a[7] = bc
        for (q in 0 until 4) {
            val nx = a[q * 2]; val nz = a[q * 2 + 1]
            val ar = ahw * abs(nx * ac - nz * asn) + ahl * abs(nx * asn + nz * ac)
            val br = bhw * abs(nx * bc - nz * bs) + bhl * abs(nx * bs + nz * bc)
            if (abs(dx * nx + dz * nz) >= ar + br) return false
        }
        return true
    }

    fun poseClear(v: Vehicle, x: Float, z: Float, angle: Float): Boolean {
        val c = cos(angle); val s = sin(angle)
        val lim = 785f * MapData.MAPK
        for (xx in floatArrayOf(-v.halfWidth, v.halfWidth)) for (zz in floatArrayOf(-v.halfLength, v.halfLength))
            if (hypot(x + c * xx + s * zz, z - s * xx + c * zz) > lim) return false
        if (!map.carOK(x, z, angle, v.halfWidth, v.halfLength, v.y)) return false
        // viaduct: solid piers and the rails at the deck edge stop the car
        if (Viaduct.on) for (xx in floatArrayOf(-v.halfWidth, 0f, v.halfWidth)) for (zz in floatArrayOf(-v.halfLength, 0f, v.halfLength))
            if (Viaduct.blocks(x + c * xx + s * zz, z - s * xx + c * zz, 0.3f, v.y)) return false
        val ex = abs(c) * v.halfWidth + abs(s) * v.halfLength; val ez = abs(s) * v.halfWidth + abs(c) * v.halfLength
        val cl = map.boxesAt(x, z)
        if (cl != null) for (qi in 0 until cl.size) {
            val b = cl[qi]
            if (b.maxY < .16f || b.minY > v.height || b.maxX < x - ex || b.minX > x + ex || b.maxZ < z - ez || b.minZ > z + ez) continue
            if (overlap(x, z, angle, v.halfWidth, v.halfLength, (b.minX + b.maxX) / 2, (b.minZ + b.maxZ) / 2, 0f, (b.maxX - b.minX) / 2, (b.maxZ - b.minZ) / 2)) return false
        }
        for (o in list) if (o !== v && overlap(x, z, angle, v.halfWidth, v.halfLength, o.x, o.z, o.angle, o.halfWidth, o.halfLength)) return false
        return true
    }

    /** bermTilt(): pitch / roll the body to the four ground heights under it. */
    fun tilt(v: Vehicle) {
        val c = cos(v.angle); val s = sin(v.angle); val l = v.halfLength * .9f; val w = v.halfWidth * .9f
        val f = map.carY(v.x - s * l, v.z - c * l, v.y); val k = map.carY(v.x + s * l, v.z + c * l, v.y)
        val r = map.carY(v.x + c * w, v.z - s * w, v.y); val lf = map.carY(v.x - c * w, v.z + s * w, v.y)
        v.tiltX = atan2(f - k, 2 * l); v.tiltZ = atan2(r - lf, 2 * w)
    }

    // ------------------------------------------------------------------ on-foot interaction
    /** vehicleFootFree(): false when a body of radius r at (x,y,z) touches a vehicle part. */
    fun footFree(x: Float, z: Float, r: Float, y: Float, ignore: Vehicle? = null): Boolean {
        for (v in list) {
            if (v === ignore || y > v.y + v.height || y + BODY_H < v.y) continue
            val c = cos(v.angle); val s = sin(v.angle); val dx = x - v.x; val dz = z - v.z
            val lx = dx * c - dz * s; val lz = dx * s + dz * c
            if (abs(lx) > v.halfWidth + r || abs(lz) > v.halfLength + r) continue
            for (b in v.parts) if (y + .08f < v.y + b[3] && y + BODY_H > v.y + b[2] + .025f && lx > b[0] - r && lx < b[1] + r && lz > b[4] - r && lz < b[5] + r) return false
        }
        return true
    }

    /** Pushes a walking player out of vehicle parts (like free() refusing the step in the HTML). */
    fun resolveFoot(pos: FloatArray, r: Float) {
        for (v in list) {
            if (pos[1] > v.y + v.height || pos[1] + BODY_H < v.y) continue
            val c = cos(v.angle); val s = sin(v.angle); val dx = pos[0] - v.x; val dz = pos[2] - v.z
            if (abs(dx) > 6f || abs(dz) > 6f) continue
            var lx = dx * c - dz * s; var lz = dx * s + dz * c
            if (abs(lx) > v.halfWidth + r || abs(lz) > v.halfLength + r) continue
            var moved = false
            for (b in v.parts) {
                if (!(pos[1] + .08f < v.y + b[3] && pos[1] + BODY_H > v.y + b[2] + .025f)) continue
                if (!(lx > b[0] - r && lx < b[1] + r && lz > b[4] - r && lz < b[5] + r)) continue
                val pl = lx - (b[0] - r); val pr = (b[1] + r) - lx; val pt = lz - (b[4] - r); val pb = (b[5] + r) - lz
                val m = minOf(minOf(pl, pr), minOf(pt, pb))
                when (m) { pl -> lx = b[0] - r; pr -> lx = b[1] + r; pt -> lz = b[4] - r; else -> lz = b[5] + r }
                moved = true
            }
            if (moved) { pos[0] = v.x + c * lx + s * lz; pos[2] = v.z - s * lx + c * lz }
        }
    }

    /** nearestVehicle(): closest vehicle within 3.6 m of the player's feet (distance to the footprint rectangle). */
    fun nearest(px: Float, py: Float, pz: Float): Vehicle? {
        var sel: Vehicle? = null; var best = 3.6f
        for (v in list) {
            if (v.botDriver != null) continue
            if (abs(v.y - py) > 3.2f) continue
            val c = cos(v.angle); val s = sin(v.angle); val dx = px - v.x; val dz = pz - v.z
            val lx = dx * c - dz * s; val lz = dx * s + dz * c
            val d = hypot(max(0f, abs(lx) - v.halfWidth), max(0f, abs(lz) - v.halfLength))
            if (d < best) { best = d; sel = v }
        }
        return sel
    }

    /** vehiclePoint(v, x, y, z) */
    fun point(v: Vehicle, lx: Float, ly: Float, lz: Float, out: FloatArray) {
        val c = cos(v.angle); val s = sin(v.angle)
        out[0] = v.x + c * lx + s * lz; out[1] = v.y + ly; out[2] = v.z - s * lx + c * lz
    }

    /** Looks for a free spot beside the vehicle (the 5 candidates of useVehicle()). Fills out[x,y,z]. */
    fun exitSpot(v: Vehicle, fromX: Float, fromZ: Float, bots: List<Bot>, out: FloatArray): Boolean {
        val p = FloatArray(3)
        for (e in EXITS) {
            point(v, e[0], 0f, e[1], p)
            val g = map.footY(p[0], p[2], v.y)
            if (map.terrainBlocked(p[0], p[2], .46f, g) || map.boxBlocked(p[0], p[2], .46f, g) || !footFree(p[0], p[2], .46f, g, v)) continue
            val d = hypot(p[0] - fromX, p[2] - fromZ); val steps = max(1, ceil(d / .2f).toInt())
            var ok = true
            for (i in 0..steps) {
                val t = i / steps.toFloat(); val x = fromX + (p[0] - fromX) * t; val z = fromZ + (p[2] - fromZ) * t
                val gy = map.footY(x, z, v.y)
                if (map.terrainBlocked(x, z, .45f, gy) || map.boxBlocked(x, z, .45f, gy) || !footFree(x, z, .45f, gy, v)) { ok = false; break }
            }
            if (!ok) continue
            if (bots.any { it.alive && it.air == 3 && hypot(it.x - p[0], it.z - p[2]) < .95f }) continue
            out[0] = p[0]; out[1] = g; out[2] = p[2]
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ driving physics
    /**
     * stepVehicle(): throttle / steering in -1..1. Returns the total heading change in radians (the camera follows it).
     * onRoadkill is called for a bot hit at >= 2.2 m/s, onImpact when the car hits a wall at > 2 m/s.
     */
    fun drive(
        v: Vehicle, dt: Float, throttle: Float, steering: Float, braking: Boolean, boost: Boolean,
        bots: List<Bot>, clock: Float, onRoadkill: (Bot) -> Unit, onImpact: () -> Unit, minHit: Float = 2.2f
    ): Float {
        val baseMax = if (v.pickup) 22f else 20f
        val maxF = if (boost) baseMax * 1.65f else baseMax; val maxR = 7f
        var sp = v.speed
        if (braking) sp = sign(sp) * max(0f, abs(sp) - 19f * dt)
        else if (abs(throttle) > .08f) {
            val opposing = sign(throttle) != sign(sp) && abs(sp) > .15f
            sp += throttle * (if (opposing) 20f else if (boost) 20f else 9f) * dt
        } else sp = sign(sp) * max(0f, abs(sp) - (1.8f + .028f * sp * sp) * dt)
        sp = if (sp > maxF) max(maxF, sp - 14f * dt) else sp.coerceIn(-maxR, maxF)
        if (abs(sp) < .035f) sp = 0f
        val steerTarget = -steering * (.48f / (1f + abs(sp) * .025f))
        v.steer += (steerTarget - v.steer) * (1f - exp(-dt * 9f))
        val n = max(1, ceil(abs(sp) * dt / .12f).toInt()); val step = dt / n
        var totalTurn = 0f
        for (i in 0 until n) {
            val turn = sp / 2.92f * tan(v.steer) * step; val na = v.angle + turn
            val x = v.x - sin(na) * sp * step; val z = v.z - cos(na) * sp * step
            var blocked = !poseClear(v, x, z, na)
            if (!blocked) {
                val c = cos(na); val s = sin(na)
                for (b in bots) {
                    if (!b.alive || b.air != 3 || b.y > v.y + v.height) continue
                    val dx = b.x - x; val dz = b.z - z
                    if (abs(dx * c - dz * s) < v.halfWidth + .39f && abs(dx * s + dz * c) < v.halfLength + .39f) {
                        if (b.carHitUntil > clock) continue
                        if (abs(sp) >= minHit) { v.speed = sp; onRoadkill(b) } else blocked = true
                    }
                }
            }
            if (blocked) {
                if (abs(sp) > 2f && clock >= v.impactUntil) { v.impactUntil = clock + .35f; onImpact() }
                sp = 0f; break
            }
            v.x = x; v.z = z; v.y = map.carY(x, z, v.y); v.angle = na; totalTurn += turn; v.travel += sp * step
        }
        v.speed = sp
        v.angle = atan2(sin(v.angle), cos(v.angle))
        tilt(v)
        return totalTurn
    }
}
