package com.dustline.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Bermuda Remastered map, parsed from assets/maps/bermudaData.bin (same container the HTML used):
 *   [u32 jsonLen][json][pad to 4][blobs]   tile: p=float3 pos, n=float3 normal, u=float2 uv / c=float3 colour, i=uint16 indices
 * x/z are multiplied by MAPK (4/3) exactly like the web version.
 */
class MapData(ctx: Context, progress: (Float) -> Unit) {
    class Group(val mat: String, val verts: FloatArray, val indices: IntArray) {
        /** First vertex index that was cloned / added by road-clear or the replacement houses (triangles using it are "moved" geometry; the satellite map redraws them). */
        @JvmField var movedFrom = Int.MAX_VALUE
    }
    class Box(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float, val minZ: Float, val maxZ: Float) { @JvmField var stamp = 0 }

    companion object {
        const val MAPK = 4f / 3f
        const val STRIDE = 11 // pos3 nor3 uv2 col3

        /** Slab test, no allocations. Returns entry distance (>=0) or -1. */
        fun rayBox(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float,
                   x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float, maxD: Float): Float {
            var t0 = 0f; var t1 = maxD
            // X
            if (kotlin.math.abs(dx) < 1e-8f) { if (ox < x0 || ox > x1) return -1f }
            else { var a = (x0 - ox) / dx; var b = (x1 - ox) / dx; if (a > b) { val t = a; a = b; b = t }; if (a > t0) t0 = a; if (b < t1) t1 = b; if (t0 > t1) return -1f }
            // Y
            if (kotlin.math.abs(dy) < 1e-8f) { if (oy < y0 || oy > y1) return -1f }
            else { var a = (y0 - oy) / dy; var b = (y1 - oy) / dy; if (a > b) { val t = a; a = b; b = t }; if (a > t0) t0 = a; if (b < t1) t1 = b; if (t0 > t1) return -1f }
            // Z
            if (kotlin.math.abs(dz) < 1e-8f) { if (oz < z0 || oz > z1) return -1f }
            else { var a = (z0 - oz) / dz; var b = (z1 - oz) / dz; if (a > b) { val t = a; a = b; b = t }; if (a > t0) t0 = a; if (b < t1) t1 = b; if (t0 > t1) return -1f }
            return t0
        }
    }

    val groups = ArrayList<Group>()
    val blockers = ArrayList<Box>()
    val seaColor: Int
    val n: Int
    val cell: Float
    val x0: Float
    private val gh: FloatArray
    private val walk: ByteArray
    val spawn = FloatArray(3)
    /** Car spawn points of the HTML (x, z already x MAPK, angle). */
    val cars = ArrayList<FloatArray>()
    private var roadMask = ByteArray(0)
    /** Green / flat / open ground mask of the HTML (H.grass): trees and bushes only grow here. */
    private var grassBits = ByteArray(0)
    private var grN = 0
    private var grCell = 1f
    /** Collision boxes in a 24 m grid (BERM.bgrid of the HTML): every box sits in all cells within 4 m of it. Includes tree trunks and boulders. */
    private val BC = 24f
    private var bgN = 0
    private var bgrid: Array<ArrayList<Box>?> = arrayOfNulls(0)
    private var stampNow = 0
    var treeBoxes = 0
    /** Water level grid (wl of the HTML): -999 = dry, else the surface height. Sea cells (ground <= 0) = 0, lakes / rivers from the water meshes. */
    private var wl = FloatArray(0)
    /** Trees / bushes / rocks of the ULTRA NATURE pack (Nature.kt), ready to be uploaded by NatureGl. */
    var nature: Nature.Result? = null
    /** Replacement houses (real Bermuda houses cloned in `assets/maps/extra_houses.bin`) that stand where the 4 old landmark models used to be: x, z, width, depth, ground y. */
    val extraHouses = ArrayList<FloatArray>()
    /** Road-clear moves (dx, dz, dy, oldX0, oldX1, oldZ0, oldZ1, yLo, yHi) kept so the house footprints below can follow the moved houses. */
    private var roadMoves: Array<FloatArray> = emptyArray()
    /** Footprints of every house a player can walk into (DX PATCH `dxStockHouses` of the HTML): cx, cz, width, depth. Loot is only placed in / right next to these. */
    val houseSpots = ArrayList<FloatArray>()

    init {
        val bytes = ctx.assets.open("maps/bermudaData.bin").use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val jl = bb.getInt(0)
        val h = JSONObject(String(bytes, 4, jl, Charsets.UTF_8))
        val base = 4 + jl + ((4 - (4 + jl) % 4) % 4)
        progress(0.05f)

        n = h.getInt("N"); cell = h.getDouble("cell").toFloat() * MAPK; x0 = h.getDouble("x0").toFloat() * MAPK
        val hs = h.getDouble("hs").toFloat()
        h.optJSONObject("grass")?.let { gro ->
            grN = gro.getInt("n"); grCell = gro.getDouble("cell").toFloat() * MAPK
            val nb = (grN * grN + 7) / 8; val off = base + gro.getInt("off")
            grassBits = ByteArray(nb); System.arraycopy(bytes, off, grassBits, 0, nb)
        }
        val ho = base + h.getInt("heights")
        gh = FloatArray(n * n) { bb.getShort(ho + it * 2) / hs }
        // arch viaduct: lower the baked ridge under the open arches (HTML MDL.reshape) BEFORE the walk grid is computed
        Viaduct.load(ctx); Viaduct.reshape(gh, n, cell, x0)
        val slopeMax = h.getDouble("slopeMax").toFloat()
        walk = ByteArray(n * n)
        for (k in 0 until n) for (i in 0 until n) {
            val i0 = max(i - 1, 0); val i1 = min(i + 1, n - 1); val k0 = max(k - 1, 0); val k1 = min(k + 1, n - 1)
            val dx = (gh[k * n + i1] - gh[k * n + i0]) / ((i1 - i0) * h.getDouble("cell").toFloat())
            val dz = (gh[k1 * n + i] - gh[k0 * n + i]) / ((k1 - k0) * h.getDouble("cell").toFloat())
            walk[k * n + i] = if (gh[k * n + i] > 0f && hypot(dx, dz) < slopeMax) 1 else 0
        }
        roadMask = ByteArray(n * n)
        progress(0.15f)

        val gs = h.getJSONArray("groups")
        for (gi in 0 until gs.length()) {
            val g = gs.getJSONObject(gi)
            val tiles = g.getJSONArray("tiles")
            var nv = 0; var ni = 0
            for (t in 0 until tiles.length()) { nv += tiles.getJSONObject(t).getInt("nv"); ni += tiles.getJSONObject(t).getInt("ni") }
            val verts = FloatArray(nv * STRIDE); val idx = IntArray(ni)
            var vo = 0; var io = 0
            val isRoad = g.getString("mat") == "road"
            for (t in 0 until tiles.length()) {
                val T = tiles.getJSONObject(t); val c = T.getInt("nv")
                val hasUv = T.has("u"); val hasCol = T.has("c")
                for (v in 0 until c) {
                    val pb = base + T.getInt("p") + v * 12; val nb = base + T.getInt("n") + v * 12
                    val o = (vo + v) * STRIDE
                    val px = bb.getFloat(pb) * MAPK; val py = bb.getFloat(pb + 4) + (if (isRoad) 0.09f else 0f); val pz = bb.getFloat(pb + 8) * MAPK
                    verts[o] = px; verts[o + 1] = py; verts[o + 2] = pz
                    val nx = bb.getFloat(nb) / MAPK; val ny = bb.getFloat(nb + 4); val nz = bb.getFloat(nb + 8) / MAPK
                    val l = hypot(hypot(nx, ny), nz).let { if (it == 0f) 1f else it }
                    verts[o + 3] = nx / l; verts[o + 4] = ny / l; verts[o + 5] = nz / l
                    if (hasUv) { val ub = base + T.getInt("u") + v * 8; verts[o + 6] = bb.getFloat(ub); verts[o + 7] = bb.getFloat(ub + 4) }
                    if (hasCol) { val cb = base + T.getInt("c") + v * 12; verts[o + 8] = bb.getFloat(cb); verts[o + 9] = bb.getFloat(cb + 4); verts[o + 10] = bb.getFloat(cb + 8) }
                }
                val ib = base + T.getInt("i"); val cnt = T.getInt("ni")
                for (q in 0 until cnt) idx[io + q] = (bb.getShort(ib + q * 2).toInt() and 0xFFFF) + vo
                vo += c; io += cnt
            }
            groups.add(Group(g.getString("mat"), verts, idx))
            if (isRoad) rasterRoad(verts, idx)
            progress(0.15f + 0.7f * (gi + 1) / gs.length())
        }

        val bl = h.getJSONArray("blockers")
        for (i in 0 until bl.length()) {
            val r = bl.getJSONArray(i)
            var a = r.getDouble(0).toFloat(); var b = r.getDouble(1).toFloat()
            var c = r.getDouble(2).toFloat(); var d = r.getDouble(3).toFloat()
            var e = r.getDouble(4).toFloat(); var f = r.getDouble(5).toFloat()
            if (a > b) { val t = a; a = b; b = t }; if (c > d) { val t = c; c = d; d = t }; if (e > f) { val t = e; e = f; f = t }
            if (b - a < 0.3f || d - c < 0.3f || f - e < 0.05f) continue
            blockers.add(Box(a * MAPK, b * MAPK, e, f, c * MAPK, d * MAPK))
        }
        val cr = h.optJSONArray("cars")
        if (cr != null) for (i in 0 until cr.length()) {
            val c = cr.getJSONArray(i)
            cars.add(floatArrayOf(c.getDouble(0).toFloat() * MAPK, c.getDouble(1).toFloat() * MAPK, c.optDouble(2, 0.0).toFloat()))
        }
        try { applyRoadClear(ctx) } catch (e: Throwable) { /* damaged file = original layout */ }
        try { loadExtraHouses(ctx) } catch (e: Throwable) { extraHouses.clear() }   // a damaged file just means no extra houses
        try { buildHouseSpots(h) } catch (e: Throwable) { houseSpots.clear() }      // no list = loot falls back to the old open-ground spots
        seaColor = android.graphics.Color.parseColor(h.optString("sea", "#0d384f"))
        buildGrid()
        try { buildWater() } catch (e: Throwable) { wl = FloatArray(0) }   // no grid = no swimming, the rest still works
        progress(0.88f)
        // ULTRA NATURE (pines, oaks, palms, bushes, boulders, pebbles): placed before the spawn point is chosen so nobody spawns inside a tree
        try { nature = Nature.place(this) } catch (e: Throwable) { nature = null }

        // spawn: nearest walkable land cell to map centre that is not inside a blocker
        var best = Float.MAX_VALUE; var bx = 0f; var bz = 0f
        for (k in 0 until n step 2) for (i in 0 until n step 2) {
            if (walk[k * n + i].toInt() != 1) continue
            val x = x0 + i * cell; val z = x0 + k * cell
            val d = hypot(x, z)
            if (d < best && !blocked(x, z, 0.6f, gh[k * n + i])) { best = d; bx = x; bz = z }
        }
        spawn[0] = bx; spawn[1] = ground(bx, bz); spawn[2] = bz
        progress(1f)
    }

    /**
     * ROAD-CLEAR (README v9 + v13 ROAD-FREE of the HTML): 80 of the 221 original houses are pushed back so that no part of a house
     * (walls, porch, roof edge) is closer than 7 m to the real asphalt. `maps/roadclear.bin` was computed by running the HTML's own
     * `DLCLEAR.run` on this exact bermudaData.bin (80 moved, 0 failed, same as the HTML log).
     * Layout (little endian): int nMoves; nMoves x float[9] = dx, dz, dy, oldX0, oldX1, oldZ0, oldZ1, yLo, yHi (world units);
     * int nOwned; nOwned x (int blockerIndex, int moveIndex).
     * The collision boxes of a moved house move with it, and the baked atlas / house triangles that stood on the old spot are cloned
     * to the new spot (same rule as DLCLEAR.moveGeo, so neighbouring houses are never dragged along).
     */
    private fun applyRoadClear(ctx: Context) {
        val bytes = ctx.assets.open("maps/roadclear.bin").use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val nm = bb.getInt(0)
        val mv = Array(nm) { mi -> FloatArray(9) { j -> bb.getFloat(4 + (mi * 9 + j) * 4) } }
        roadMoves = mv
        val owned = bb.getInt(4 + nm * 36)
        val ob = 8 + nm * 36
        // read everything first, then touch the map (all or nothing)
        val qi = IntArray(owned) { bb.getInt(ob + it * 8) }
        val qm = IntArray(owned) { bb.getInt(ob + it * 8 + 4) }
        for (i in 0 until owned) if (qi[i] < 0 || qi[i] >= blockers.size || qm[i] < 0 || qm[i] >= nm) return
        for (i in 0 until owned) {
            val b = blockers[qi[i]]; val m = mv[qm[i]]
            blockers[qi[i]] = Box(b.minX + m[0], b.maxX + m[0], b.minY + m[2], b.maxY + m[2], b.minZ + m[1], b.maxZ + m[1])
        }
        var ux0 = 1e9f; var ux1 = -1e9f; var uz0 = 1e9f; var uz1 = -1e9f
        for (m in mv) {
            if (m[3] - 1.4f < ux0) ux0 = m[3] - 1.4f; if (m[4] + 1.4f > ux1) ux1 = m[4] + 1.4f
            if (m[5] - 1.4f < uz0) uz0 = m[5] - 1.4f; if (m[6] + 1.4f > uz1) uz1 = m[6] + 1.4f
        }
        for (gi in groups.indices) {
            val g = groups[gi]
            if (g.mat != "atlas" && g.mat != "house") continue
            val v = g.verts; val ia = g.indices
            val sel = ArrayList<Int>(); val which = ArrayList<Int>()
            var q = 0
            while (q + 2 < ia.size) {
                val a = ia[q] * STRIDE; val b = ia[q + 1] * STRIDE; val c = ia[q + 2] * STRIDE
                val x = (v[a] + v[b] + v[c]) / 3f; val y = (v[a + 1] + v[b + 1] + v[c + 1]) / 3f; val z = (v[a + 2] + v[b + 2] + v[c + 2]) / 3f
                if (x >= ux0 && x <= ux1 && z >= uz0 && z <= uz1) {
                    for (k in 0 until nm) {
                        val m = mv[k]
                        if (x > m[3] - 1.3f && x < m[4] + 1.3f && z > m[5] - 1.3f && z < m[6] + 1.3f && y > m[7] && y < m[8]) { sel.add(q); which.add(k); break }
                    }
                }
                q += 3
            }
            if (sel.isEmpty()) continue
            val nv0 = v.size / STRIDE
            val nv = v.copyOf(v.size + sel.size * 3 * STRIDE); val ni = ia.copyOf()
            var w = nv0
            for (s in sel.indices) {
                val tq = sel[s]; val m = mv[which[s]]
                for (j in 0..2) {
                    val src = ia[tq + j] * STRIDE; val dst = w * STRIDE
                    System.arraycopy(v, src, nv, dst, STRIDE)
                    nv[dst] += m[0]; nv[dst + 1] += m[2]; nv[dst + 2] += m[1]
                    ni[tq + j] = w; w++
                }
            }
            val ng = Group(g.mat, nv, ni); ng.movedFrom = nv0
            groups[gi] = ng
        }
    }

    /**
     * extra_houses.bin (little endian): int nVerts, nTris, nBoxes, nHouses; float[nVerts * 11] (pos3 nor3 uv2 col3, already world space);
     * int[nTris * 3]; float[nBoxes * 6] = minX, maxX, minY, maxY, minZ, maxZ; float[nHouses * 5] = x, z, w, d, groundY.
     * Mesh = the vertex-coloured houses of the Bermuda data (same walls, doors, roofs), so they look and collide exactly like the old ones.
     */
    private fun loadExtraHouses(ctx: Context) {
        val bytes = ctx.assets.open("maps/extra_houses.bin").use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // header counts TRIANGLES in slot 1 (file size check: 16 + nv*44 + tri*12 + nb*24 + nh*20), so there are 3 indices per entry
        val nv = bb.getInt(0); val ni = bb.getInt(4) * 3; val nb = bb.getInt(8); val nh = bb.getInt(12)
        var o = 16
        val verts = FloatArray(nv * STRIDE)
        for (i in verts.indices) verts[i] = bb.getFloat(o + i * 4)
        o += nv * STRIDE * 4
        val idx = IntArray(ni)
        for (i in 0 until ni) idx[i] = bb.getInt(o + i * 4)
        o += ni * 4
        val newBoxes = ArrayList<Box>(nb)
        for (i in 0 until nb) {
            val q = o + i * 24
            newBoxes.add(Box(bb.getFloat(q), bb.getFloat(q + 4), bb.getFloat(q + 8), bb.getFloat(q + 12), bb.getFloat(q + 16), bb.getFloat(q + 20)))
        }
        o += nb * 24
        val newHouses = ArrayList<FloatArray>(nh)
        for (i in 0 until nh) {
            val q = o + i * 20
            newHouses.add(floatArrayOf(bb.getFloat(q), bb.getFloat(q + 4), bb.getFloat(q + 8), bb.getFloat(q + 12), bb.getFloat(q + 16)))
        }
        // everything parsed fine: only now touch the map (all or nothing)
        blockers.addAll(newBoxes)
        extraHouses.addAll(newHouses)
        val eg = Group("house", verts, idx); eg.movedFrom = 0
        groups.add(eg)
    }

    /** Old footprints (x0, x1, z0, z1 in world units) of the houses that road-clear pushed away; the satellite map wipes them. */
    fun movedHouseRects(): List<FloatArray> = roadMoves.map { floatArrayOf(it[3], it[4], it[5], it[6]) }

    fun ground(x: Float, z: Float): Float {
        var fx = (x - x0) / cell; var fz = (z - x0) / cell
        fx = fx.coerceIn(0f, n - 1.001f); fz = fz.coerceIn(0f, n - 1.001f)
        val i = fx.toInt(); val k = fz.toInt(); val tx = fx - i; val tz = fz - k; val a = k * n + i
        val g = gh[a] * (1 - tx) * (1 - tz) + gh[a + 1] * tx * (1 - tz) + gh[a + n] * (1 - tx) * tz + gh[a + n + 1] * tx * tz
        return g
    }

    fun walkAt(x: Float, z: Float): Boolean {
        val i = Math.round((x - x0) / cell); val k = Math.round((z - x0) / cell)
        return i in 0 until n && k in 0 until n && walk[k * n + i].toInt() == 1
    }

    /** Terrain part of free(): water and cliffs block ground movement. */
    fun terrainBlocked(x: Float, z: Float, r: Float, y: Float = 0f, swim: Boolean = false): Boolean {
        if (y > 1.2f && y > ground(x, z) + 1.5f) return false   // BERM.blocked(): a body high above the ground (viaduct deck, roofs) is not stopped by water / cliffs below
        if (swim && wl.isNotEmpty()) {   // swimMode of the HTML: water cells are walkable too, but only inside the island circle (free())
            val R = 790f * MAPK
            if (x * x + z * z > R * R) return true
            return !((walkAt(x, z) || wetCell(x, z)) && (walkAt(x + r, z) || wetCell(x + r, z)) && (walkAt(x - r, z) || wetCell(x - r, z)) &&
                (walkAt(x, z + r) || wetCell(x, z + r)) && (walkAt(x, z - r) || wetCell(x, z - r)))
        }
        return !(walkAt(x, z) && walkAt(x + r, z) && walkAt(x - r, z) && walkAt(x, z + r) && walkAt(x, z - r))
    }

    // ---- water (sea + lakes / rivers): waterLevelAt / waterDepth / swimSpeed / swimFloor of the HTML
    private fun buildWater() {
        val w = FloatArray(n * n) { -999f }
        for (i in 0 until n * n) if (gh[i] <= 0f) w[i] = 0f
        for (g in groups) {
            if (g.mat != "water") continue
            val v = g.verts; val ix = g.indices; val S = STRIDE
            var q = 0
            while (q + 2 < ix.size) {
                val a = ix[q] * S; val b = ix[q + 1] * S; val c = ix[q + 2] * S; q += 3
                val ax = v[a]; val az = v[a + 2]; val bx = v[b]; val bz = v[b + 2]; val cx = v[c]; val cz = v[c + 2]
                val den = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz)
                if (kotlin.math.abs(den) < 1e-9f) continue
                val i0 = max(0, kotlin.math.floor((min(ax, min(bx, cx)) - x0) / cell).toInt()); val i1 = min(n - 1, kotlin.math.ceil((max(ax, max(bx, cx)) - x0) / cell).toInt())
                val k0 = max(0, kotlin.math.floor((min(az, min(bz, cz)) - x0) / cell).toInt()); val k1 = min(n - 1, kotlin.math.ceil((max(az, max(bz, cz)) - x0) / cell).toInt())
                for (k in k0..k1) for (i in i0..i1) {
                    val px = x0 + i * cell; val pz = x0 + k * cell
                    val l1 = ((bz - cz) * (px - cx) + (cx - bx) * (pz - cz)) / den
                    val l2 = ((cz - az) * (px - cx) + (ax - cx) * (pz - cz)) / den
                    val l3 = 1f - l1 - l2
                    if (l1 >= -0.02f && l2 >= -0.02f && l3 >= -0.02f) {
                        val y = l1 * v[a + 1] + l2 * v[b + 1] + l3 * v[c + 1]
                        if (y > w[k * n + i]) w[k * n + i] = y
                    }
                }
            }
        }
        wl = w
    }

    fun wetCell(x: Float, z: Float): Boolean {
        if (wl.isEmpty()) return false
        val i = Math.round((x - x0) / cell); val k = Math.round((z - x0) / cell)
        return i in 0 until n && k in 0 until n && wl[k * n + i] > -900f
    }

    fun waterLevelAt(x: Float, z: Float): Float {
        if (wl.isEmpty()) return -999f
        val i = Math.round((x - x0) / cell); val k = Math.round((z - x0) / cell)
        return if (i in 0 until n && k in 0 until n) wl[k * n + i] else -999f
    }

    fun waterDepth(x: Float, z: Float): Float {
        val w = waterLevelAt(x, z); if (w < -900f) return 0f
        val d = w - ground(x, z); return if (d > 0f) d else 0f
    }

    fun swimSpeed(x: Float, z: Float): Float { val d = waterDepth(x, z); return if (d > 1.25f) 0.55f else if (d > 0.45f) 0.8f else 1f }

    /** Height the feet float at when the water is deeper than 1.25 m (the body sinks to the neck and bobs a little), else -1e9. */
    fun swimFloor(x: Float, z: Float, t: Float): Float {
        val d = waterDepth(x, z); if (d <= 1.25f) return -1e9f
        return waterLevelAt(x, z) - 1.25f + 0.05f * kotlin.math.sin(t * 1000f / 450f)
    }

    private fun blocked(x: Float, z: Float, r: Float, y: Float): Boolean {
        val cl = boxesAt(x, z) ?: return false
        for (qi in 0 until cl.size) {
            val b = cl[qi]
            if (x > b.minX - r && x < b.maxX + r && z > b.minZ - r && z < b.maxZ + r && y < b.maxY - 0.3f && y + 1.7f > b.minY) return true
        }
        return false
    }

    // ---- collision grid (BERM.addBlocker / BERM.bl of the HTML)
    private fun buildGrid() {
        bgN = Math.ceil((n * cell / BC).toDouble()).toInt() + 2
        bgrid = arrayOfNulls(bgN * bgN)
        for (b in blockers) gridInsert(b)
    }

    private fun gridInsert(b: Box) {
        val i0 = kotlin.math.floor((b.minX - 4f - x0) / BC).toInt().coerceIn(0, bgN - 1)
        val i1 = kotlin.math.floor((b.maxX + 4f - x0) / BC).toInt().coerceIn(0, bgN - 1)
        val k0 = kotlin.math.floor((b.minZ - 4f - x0) / BC).toInt().coerceIn(0, bgN - 1)
        val k1 = kotlin.math.floor((b.maxZ + 4f - x0) / BC).toInt().coerceIn(0, bgN - 1)
        for (k in k0..k1) for (i in i0..i1) {
            var l = bgrid[k * bgN + i]
            if (l == null) { l = ArrayList(); bgrid[k * bgN + i] = l }
            l.add(b)
        }
    }

    /** Every box within 4 m of (x, z) (the list of its grid cell), or null. */
    fun boxesAt(x: Float, z: Float): ArrayList<Box>? {
        val i = kotlin.math.floor((x - x0) / BC).toInt(); val k = kotlin.math.floor((z - x0) / BC).toInt()
        if (i < 0 || k < 0 || i >= bgN || k >= bgN) return null
        return bgrid[k * bgN + i]
    }

    /** A tree trunk / boulder: a collision box like BERM.addBlocker(). */
    fun addTreeBox(minX: Float, maxX: Float, minY: Float, maxY: Float, minZ: Float, maxZ: Float) {
        gridInsert(Box(minX, maxX, minY, maxY, minZ, maxZ)); treeBoxes++
    }

    /** free() of the HTML (placement check): inside the island circle, terrain ok, no collision box, no viaduct pier / rail. */
    fun freeSpot(x: Float, z: Float, r: Float, y: Float): Boolean {
        val R = 790f * MAPK
        if (x * x + z * z > R * R) return false
        if (terrainBlocked(x, z, r, y)) return false
        val cl = boxesAt(x, z)
        if (cl != null) for (qi in 0 until cl.size) {
            val b = cl[qi]
            if (y + 0.08f < b.maxY && y + 1.7f > b.minY + 0.025f && x > b.minX - r && x < b.maxX + r && z > b.minZ - r && z < b.maxZ + r) return false
        }
        if (Viaduct.blocks(x, z, r, y)) return false
        return true
    }

    /** grassOK() of the HTML. */
    fun grassOK(x: Float, z: Float): Boolean {
        if (grN == 0) return false
        val i = kotlin.math.floor((x - x0) / grCell).toInt(); val k = kotlin.math.floor((z - x0) / grCell).toInt()
        if (i < 0 || k < 0 || i >= grN || k >= grN) return false
        val q = k * grN + i
        return ((grassBits[q shr 3].toInt() shr (7 - (q and 7))) and 1) == 1
    }

    /** Push (x,z) out of any collision box the body overlaps. */
    fun resolve(pos: FloatArray, r: Float) {
        val y = pos[1]
        val cl = boxesAt(pos[0], pos[2]) ?: return
        for (qi in 0 until cl.size) {
            val b = cl[qi]
            if (y >= b.maxY - 0.3f || y + 1.7f <= b.minY) continue
            val cx = pos[0].coerceIn(b.minX, b.maxX); val cz = pos[2].coerceIn(b.minZ, b.maxZ)
            var dx = pos[0] - cx; var dz = pos[2] - cz
            val d2 = dx * dx + dz * dz
            if (d2 >= r * r) continue
            if (d2 > 1e-8f) { val d = kotlin.math.sqrt(d2); pos[0] = cx + dx / d * r; pos[2] = cz + dz / d * r }
            else { // centre inside the box: leave through the nearest face
                val l = pos[0] - b.minX; val rr = b.maxX - pos[0]; val t = pos[2] - b.minZ; val bt = b.maxZ - pos[2]
                val m = min(min(l, rr), min(t, bt))
                when (m) { l -> pos[0] = b.minX - r; rr -> pos[0] = b.maxX + r; t -> pos[2] = b.minZ - r; else -> pos[2] = b.maxZ + r }
            }
        }
    }

    /** Distance along the ray to the first world hit (collision boxes or terrain), or -1. */
    fun rayWorld(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxD: Float): Float {
        var best = -1f
        run { val vt = Viaduct.ray(ox, oy, oz, dx, dy, dz, maxD); if (vt > 0f && (best < 0f || vt < best)) best = vt }   // viaduct deck / arch slab / piers stop bullets
        // collision boxes: walk the 24 m grid along the ray (a box is listed in every cell within 4 m, so a 6 m step never misses one)
        stampNow++
        var s = 0f
        while (true) {
            val cl = boxesAt(ox + dx * s, oz + dz * s)
            if (cl != null) for (qi in 0 until cl.size) {
                val b = cl[qi]
                if (b.stamp == stampNow) continue
                b.stamp = stampNow
                val t = rayBox(ox, oy, oz, dx, dy, dz, b.minX, b.maxX, b.minY, b.maxY, b.minZ, b.maxZ, maxD)
                if (t > 0.05f && (best < 0f || t < best)) best = t
            }
            if (s >= maxD) break
            s = min(s + 6f, maxD)
            if (best >= 0f && s > best + 6f) break
        }
        // terrain: march, then refine
        var t = 0.5f; var prev = 0f
        val limit = if (best > 0f) best else maxD
        while (t < limit) {
            val y = oy + dy * t
            if (y <= ground(ox + dx * t, oz + dz * t)) {
                var lo = prev; var hi = t
                for (i in 0 until 6) { val m = (lo + hi) / 2; if (oy + dy * m <= ground(ox + dx * m, oz + dz * m)) hi = m else lo = m }
                best = hi; break
            }
            prev = t; t += 1.0f
        }
        return best
    }

    /** True when a body of radius r standing at (x,y,z) overlaps a collision box. */
    fun boxBlocked(x: Float, z: Float, r: Float, y: Float) = blocked(x, z, r, y) || Viaduct.blocks(x, z, r, y)

    /** Surface a car drives on: the viaduct deck when the car is up on it, else the ground. */
    fun carY(x: Float, z: Float, y: Float): Float {
        val g = ground(x, z)
        val f = Viaduct.floor(x, z, y + 0.8f)
        return if (f > g) f else g
    }

    /** Head bump under a viaduct arch (MDL.ceil): underside height inside (y0, y1] or 1e9. */
    fun ceilAt(x: Float, z: Float, y0: Float, y1: Float): Float = Viaduct.ceil(x, z, y0, y1)

    /** Standing height: viaduct deck when one is within step height, else the ground. */
    fun footY(x: Float, z: Float, y: Float): Float {
        val f = Viaduct.floor(x, z, y + 0.58f)
        return if (f > -1e8f) f else ground(x, z)
    }

    /** Marks every grid cell whose centre lies inside a road triangle (HTML roadMask). */
    private fun rasterRoad(v: FloatArray, idx: IntArray) {
        var t = 0
        while (t + 2 < idx.size) {
            val a = idx[t] * STRIDE; val b = idx[t + 1] * STRIDE; val c = idx[t + 2] * STRIDE
            t += 3
            val ax = v[a]; val az = v[a + 2]; val bx = v[b]; val bz = v[b + 2]; val cx = v[c]; val cz = v[c + 2]
            val den = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz)
            if (kotlin.math.abs(den) < 1e-9f) continue
            val i0 = max(0, kotlin.math.floor((min(ax, min(bx, cx)) - x0) / cell).toInt())
            val i1 = min(n - 1, kotlin.math.ceil((max(ax, max(bx, cx)) - x0) / cell).toInt())
            val k0 = max(0, kotlin.math.floor((min(az, min(bz, cz)) - x0) / cell).toInt())
            val k1 = min(n - 1, kotlin.math.ceil((max(az, max(bz, cz)) - x0) / cell).toInt())
            for (k in k0..k1) for (i in i0..i1) {
                val px = x0 + i * cell; val pz = x0 + k * cell
                val l1 = ((bz - cz) * (px - cx) + (cx - bx) * (pz - cz)) / den
                val l2 = ((cz - az) * (px - cx) + (ax - cx) * (pz - cz)) / den
                val l3 = 1f - l1 - l2
                if (l1 >= -0.02f && l2 >= -0.02f && l3 >= -0.02f) roadMask[k * n + i] = 1
            }
        }
    }

    fun roadAt(x: Float, z: Float): Boolean {
        val i = Math.round((x - x0) / cell); val k = Math.round((z - x0) / cell)
        return i in 0 until n && k in 0 until n && roadMask[k * n + i].toInt() == 1
    }

    /** carOK() of the HTML: all 9 footprint samples walkable and ground height differs by < 1.5 m. */
    fun carOK(x: Float, z: Float, ang: Float, hw: Float, hl: Float, y: Float = 0f): Boolean {
        val c = kotlin.math.cos(ang); val s = kotlin.math.sin(ang)
        var lo = 1e9f; var hi = -1e9f
        for (u in floatArrayOf(-hw, 0f, hw)) for (w in floatArrayOf(-hl, 0f, hl)) {
            val px = x + c * u + s * w; val pz = z - s * u + c * w
            val g0 = ground(px, pz)
            val up = y > 1.2f && y > g0 + 1.5f   // car on the viaduct deck: every sample must have deck under it, water / cliffs below do not matter
            if (up) { if (Viaduct.floor(px, pz, y + 0.8f) < -1e8f) return false }
            else if (!walkAt(px, pz)) return false
            val g = carY(px, pz, y); if (g < lo) lo = g; if (g > hi) hi = g
        }
        return hi - lo < 1.5f
    }

    /**
     * HTML `plan()` + `dxInHouse()`: every house of `H.houses` that is not flagged (h[5] == 0) and is 5..95 m wide / deep is an enterable house.
     * The footprint follows the road-clear moves (a house that was pushed back off the asphalt is looked up in `roadMoves` by its old rectangle),
     * and the 32 replacement houses are added too.
     */
    private fun buildHouseSpots(h: JSONObject) {
        val hs = h.getJSONArray("houses")
        for (i in 0 until hs.length()) {
            val r = hs.getJSONArray(i)
            if (r.optDouble(5, 0.0) != 0.0) continue
            var cx = r.getDouble(0).toFloat() * MAPK; var cz = r.getDouble(1).toFloat() * MAPK
            val w = r.getDouble(2).toFloat() * MAPK; val d = r.getDouble(3).toFloat() * MAPK
            if (w < 5f || d < 5f || w > 95f || d > 95f) continue
            for (m in roadMoves) if (cx >= m[3] && cx <= m[4] && cz >= m[5] && cz <= m[6]) { cx += m[0]; cz += m[1]; break }
            houseSpots.add(floatArrayOf(cx, cz, w, d))
        }
        for (e in extraHouses) houseSpots.add(floatArrayOf(e[0], e[1], e[2], e[3]))
    }

    /**
     * One try of `dxStockHouses()`: 65 % a point INSIDE a house footprint, 35 % on the porch / yard strip 1.4-3 m outside one wall.
     * Returns null if the point is in water, inside a wall / blocker or the list is empty (the caller tries again).
     */
    fun houseSpotTry(rnd: kotlin.random.Random): FloatArray? {
        if (houseSpots.isEmpty()) return null
        val hh = houseSpots[rnd.nextInt(houseSpots.size)]
        val cx = hh[0]; val cz = hh[1]; val w = hh[2]; val d = hh[3]
        val x: Float; val z: Float
        if (rnd.nextFloat() < .65f) {
            x = cx + (rnd.nextFloat() - .5f) * (w - 3.4f).coerceAtLeast(0.5f)
            z = cz + (rnd.nextFloat() - .5f) * (d - 3.4f).coerceAtLeast(0.5f)
        } else {
            val side = rnd.nextInt(4); val t = rnd.nextFloat() * 2f - 1f; val off = 1.4f + rnd.nextFloat() * 1.6f
            when (side) {
                0 -> { x = cx + t * w / 2f; z = cz + d / 2f + off }
                1 -> { x = cx + t * w / 2f; z = cz - d / 2f - off }
                2 -> { x = cx + w / 2f + off; z = cz + t * d / 2f }
                else -> { x = cx - w / 2f - off; z = cz + t * d / 2f }
            }
        }
        if (terrainBlocked(x, z, 0.6f)) return null
        val g = ground(x, z)
        if (g < 1f) return null
        val y = footY(x, z, g + 0.5f)
        if (blocked(x, z, 0.7f, y + 0.05f)) return null
        return floatArrayOf(x, y, z)
    }

    /** Random walkable land point between minD..maxD metres from (cx,cz); null if none found. */
    fun randomLand(rnd: kotlin.random.Random, cx: Float, cz: Float, minD: Float, maxD: Float): FloatArray? {
        for (n in 0 until 300) {
            val a = rnd.nextFloat() * 6.2831855f; val d = minD + rnd.nextFloat() * (maxD - minD)
            val x = cx + kotlin.math.sin(a) * d; val z = cz - kotlin.math.cos(a) * d
            if (terrainBlocked(x, z, 0.6f)) continue
            val y = ground(x, z)
            if (y < 1f || blocked(x, z, 0.6f, y)) continue
            return floatArrayOf(x, y, z)
        }
        return null
    }
}
