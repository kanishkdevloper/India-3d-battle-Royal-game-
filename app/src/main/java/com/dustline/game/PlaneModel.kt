package com.dustline.game

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The aircraft (assets/models/plane/plane.bin, extracted from the HTML build's PLANE_DATA).
 * 6 parts: 4 spinning propellers (own pivot) + fuselage/wings + cockpit details. Nose = -Z.
 * Texture index per part: 0 = plane_tex0.jpg, 1 = plane_tex1.jpg.
 */
class PlaneModel(ctx: Context) {
    class Part(val mat: Int, val prop: Boolean, val px: Float, val py: Float, val pz: Float, val verts: FloatArray, val indices: IntArray)

    val parts: List<Part>

    init {
        val b = ctx.assets.open("models/plane/plane.bin").use { it.readBytes() }
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var o = 0
        val n = bb.getInt(o); o += 4
        val list = ArrayList<Part>()
        for (k in 0 until n) {
            val mat = bb.getInt(o); val prop = bb.getInt(o + 4) == 1
            val px = bb.getFloat(o + 8); val py = bb.getFloat(o + 12); val pz = bb.getFloat(o + 16)
            val nv = bb.getInt(o + 20); val ni = bb.getInt(o + 24); o += 28
            val pOff = o; o += nv * 12
            val nOff = o; o += nv * 12
            val uOff = o; o += nv * 8
            val iOff = o; o += ni * 2
            while (o % 4 != 0) o++
            val verts = FloatArray(nv * MapData.STRIDE)
            for (v in 0 until nv) {
                val d = v * MapData.STRIDE
                verts[d] = bb.getFloat(pOff + v * 12); verts[d + 1] = bb.getFloat(pOff + v * 12 + 4); verts[d + 2] = bb.getFloat(pOff + v * 12 + 8)
                verts[d + 3] = bb.getFloat(nOff + v * 12); verts[d + 4] = bb.getFloat(nOff + v * 12 + 4); verts[d + 5] = bb.getFloat(nOff + v * 12 + 8)
                verts[d + 6] = bb.getFloat(uOff + v * 8); verts[d + 7] = bb.getFloat(uOff + v * 8 + 4)
                verts[d + 8] = 1f; verts[d + 9] = 1f; verts[d + 10] = 1f
            }
            val idx = IntArray(ni) { bb.getShort(iOff + it * 2).toInt() and 0xFFFF }
            list.add(Part(mat, prop, px, py, pz, verts, idx))
        }
        parts = list
    }
}
