package com.dustline.game

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30 as GL
import android.opengl.GLUtils
import android.opengl.Matrix
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The rigged character of the Android build (SURVIVOR), ported 1:1:
 * every vertex is skinned to the same 11-bone rig (+ a 12th 'head' bone, verts above y=1.72 re-weighted exactly like crimInit()).
 * Mesh data = models/character/bin_0X.bin, rig / bind pose / rifle pose = models/character/<id>.json (taken from CRIM_DATA / SV_DATA).
 */
object CharRig {
    val BONES = arrayOf("body", "thighL", "thighR", "shinL", "shinR", "upperL", "upperR", "foreL", "foreR", "handL", "handR", "head")
    const val NB = 12
    val NECK = floatArrayOf(0f, 1.76f, -0.02f)
    const val SCALE = 0.8f // CHAR_K of the HTML
}

/** What the animation needs to know. Angles in radians, same meaning as m.left.rotation.x etc. of the HTML. */
class CharPose {
    var mode = RIFLE
    var walk = 0f        // walk phase (radians)
    var speed = 0f       // m/s
    var sprint = false
    var crouch = 0f      // 0..1
    var headYaw = 0f
    var headPitch = 0f
    var aimPitch = 0f    // raises / lowers the rifle arms a little
    var airT = 0f        // freefall pose blend 0..1 (CHUTE: landing flare)

    // AIR mode (Phase 28): every angle is given directly - port of dskAirPose() of the HTML, which drives the skate stance,
    // the head-first dive and the parachute hang through ONE smoothed pose. Same meaning as m.armR.rotation.z etc.
    var exTw = 0f                       // torso twist (m.body.rotation.y = -tw); shoulders + head follow it
    var exArmLx = 0f; var exArmRx = 0f; var exArmLz = 0f; var exArmRz = 0f
    var exForeLx = 0f; var exForeRx = 0f; var exForeLz = 0f; var exForeRz = 0f
    var exLegLx = 0f; var exLegRx = 0f; var exLegLz = 0f; var exLegRz = 0f; var exKneeL = 0f; var exKneeR = 0f

    companion object { const val RIFLE = 0; const val RELAXED = 1; const val DRIVE = 2; const val DIVE = 3; const val CHUTE = 4; const val AIR = 5 }
}

class CharModel(ctx: Context, val id: String) {
    class Prim(val vao: Int, val count: Int, val mat: Int, val vbo: Int = 0) {
        // kept only for meshes the store may trim (cut): CPU copy of the vertices / indices + the trimmed VAO per kind
        var vb: FloatArray? = null; var ib: IntArray? = null
        val alt = HashMap<String, IntArray>()   // kind -> [vao, indexCount]
    }

    val prims = ArrayList<Prim>()
    val texIds = ArrayList<Int>()
    val bind = Array(CharRig.NB) { FloatArray(16) }
    private val uq = Array(2) { FloatArray(4) }; private val fq = Array(2) { FloatArray(4) }; private val e1 = Array(2) { FloatArray(3) }
    private val elbow = Array(2) { FloatArray(3) }
    var vertexCount = 0

    init {
        val meta = JSONObject(ctx.assets.open("models/character/$id.json").use { String(it.readBytes()) })
        val bin = ctx.assets.open("models/character/" + meta.getString("bin")).use { it.readBytes() }
        val bb = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
        val bd = meta.getJSONObject("bind")
        for (i in 0 until 11) { val a = bd.getJSONArray(CharRig.BONES[i]); for (k in 0..15) bind[i][k] = a.getDouble(k).toFloat() }
        Matrix.setIdentityM(bind[11], 0); bind[11][12] = CharRig.NECK[0]; bind[11][13] = CharRig.NECK[1]; bind[11][14] = CharRig.NECK[2]
        val rf = meta.getJSONObject("rifle"); val rl = meta.getJSONObject("rel")
        for ((s, t) in arrayOf("L", "R").withIndex()) {
            val r = rf.getJSONObject(t)
            for (k in 0..3) { uq[s][k] = r.getJSONArray("uq").getDouble(k).toFloat(); fq[s][k] = r.getJSONArray("fq").getDouble(k).toFloat() }
            for (k in 0..2) { e1[s][k] = r.getJSONArray("E1").getDouble(k).toFloat(); elbow[s][k] = rl.getJSONObject(t).getJSONArray("elbow").getDouble(k).toFloat() }
        }
        val pa = meta.getJSONArray("prims")
        for (pi in 0 until pa.length()) {
            val p = pa.getJSONObject(pi); val n = p.getInt("n"); val ni = p.getInt("i")
            val pos = p.getInt("pos"); val nor = p.getInt("nor"); val uv = p.getInt("uv"); val idx = p.getInt("idx"); val si = p.getInt("si"); val sw = p.getInt("sw")
            val vb = FloatArray(n * 16)
            val idi = IntArray(4); val wf = FloatArray(4)
            for (v in 0 until n) {
                val o = v * 16
                val px = bb.getFloat(pos + v * 12); val py = bb.getFloat(pos + v * 12 + 4); val pz = bb.getFloat(pos + v * 12 + 8)
                vb[o] = px; vb[o + 1] = py; vb[o + 2] = pz
                vb[o + 3] = bin[nor + v * 3] / 127f; vb[o + 4] = bin[nor + v * 3 + 1] / 127f; vb[o + 5] = bin[nor + v * 3 + 2] / 127f
                vb[o + 6] = bb.getFloat(uv + v * 8); vb[o + 7] = bb.getFloat(uv + v * 8 + 4)
                var s = 0f
                for (k in 0..3) { idi[k] = bin[si + v * 4 + k].toInt() and 255; wf[k] = (bin[sw + v * 4 + k].toInt() and 255).toFloat(); s += wf[k] }
                if (s == 0f) s = 1f
                for (k in 0..3) wf[k] /= s
                // head bone: verts above the collar are re-weighted to bone 11 (smoothstep over 0.12 m), as in crimInit()
                if (py >= 1.72f) {
                    var bi = -1
                    for (k in 0..3) if (idi[k] == 0 && wf[k] > 0f) { bi = k; break }
                    if (bi >= 0) {
                        val u = ((py - 1.72f) / 0.12f).coerceIn(0f, 1f); val hw = wf[bi] * (u * u * (3f - 2f * u))
                        if (hw > 0f) {
                            var slot = -1
                            for (k in 0..3) if (k != bi && wf[k] <= 0f) { slot = k; break }
                            if (slot < 0) { var mn = 9f; for (k in 0..3) if (k != bi && wf[k] < mn) { mn = wf[k]; slot = k }; wf[bi] += wf[slot] }
                            wf[bi] -= hw; idi[slot] = 11; wf[slot] = hw
                        }
                    }
                }
                for (k in 0..3) { vb[o + 8 + k] = idi[k].toFloat(); vb[o + 12 + k] = wf[k] }
            }
            val ib = IntArray(ni) { (bin[idx + it * 2].toInt() and 255) or ((bin[idx + it * 2 + 1].toInt() and 255) shl 8) }
            prims.add(upload(vb, ib, p.getInt("mat")))
            if (Cloth.CFG[id]?.cut?.containsKey(pi) == true) { prims.last().vb = vb; prims.last().ib = ib }
            vertexCount += n
        }
        val ta = meta.getJSONArray("tex")
        for (i in 0 until ta.length()) texIds.add(loadTex(ctx, "models/character/" + ta.getString(i)))
    }

    private fun upload(v: FloatArray, idx: IntArray, mat: Int): Prim {
        val vao = IntArray(1); GL.glGenVertexArrays(1, vao, 0); GL.glBindVertexArray(vao[0])
        val b = IntArray(2); GL.glGenBuffers(2, b, 0)
        val vb = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()); vb.asFloatBuffer().put(v)
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER, b[0]); GL.glBufferData(GL.GL_ARRAY_BUFFER, v.size * 4, vb, GL.GL_STATIC_DRAW)
        val ib = ByteBuffer.allocateDirect(idx.size * 4).order(ByteOrder.nativeOrder()); ib.asIntBuffer().put(idx)
        GL.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, b[1]); GL.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, idx.size * 4, ib, GL.GL_STATIC_DRAW)
        val st = 64
        GL.glEnableVertexAttribArray(0); GL.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0)
        GL.glEnableVertexAttribArray(1); GL.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12)
        GL.glEnableVertexAttribArray(2); GL.glVertexAttribPointer(2, 2, GL.GL_FLOAT, false, st, 24)
        GL.glEnableVertexAttribArray(3); GL.glVertexAttribPointer(3, 4, GL.GL_FLOAT, false, st, 32)
        GL.glEnableVertexAttribArray(4); GL.glVertexAttribPointer(4, 4, GL.GL_FLOAT, false, st, 48)
        GL.glBindVertexArray(0)
        return Prim(vao[0], idx.size, mat, b[0])
    }

    /**
     * storeCutGeo(): a copy of the mesh without the triangles of the top part while a store top is worn.
     * kind 'sv' (Survivor jacket) hides centroid y in (1.2, 1.8); any other kind hides y > 1.05 and every arm triangle (dominant bone 5..8).
     * Returns [vao, indexCount] or null. Shares the vertex buffer with the original mesh.
     */
    fun ensureCut(p: Prim, kind: String): IntArray? {
        p.alt[kind]?.let { return it }
        val vb = p.vb ?: return null; val ib = p.ib ?: return null
        fun dom(v: Int): Int { var b = 0; var bw = -1f; for (k in 0..3) { val w = vb[v * 16 + 12 + k]; if (w > bw) { bw = w; b = vb[v * 16 + 8 + k].toInt() } }; return b }
        val out = ArrayList<Int>(ib.size)
        var t = 0
        while (t + 2 < ib.size) {
            val a = ib[t]; val b = ib[t + 1]; val c = ib[t + 2]
            val cy = (vb[a * 16 + 1] + vb[b * 16 + 1] + vb[c * 16 + 1]) / 3f
            val hide = if (kind == "sv") cy < 1.80f && cy > 1.2f
            else { val arm = { v: Int -> val d = dom(v); d in 5..8 }; cy > 1.05f || (arm(a) && arm(b) && arm(c)) }
            if (!hide) { out.add(a); out.add(b); out.add(c) }
            t += 3
        }
        val ia = out.toIntArray()
        val vao = IntArray(1); GL.glGenVertexArrays(1, vao, 0); GL.glBindVertexArray(vao[0])
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER, p.vbo)
        val st = 64
        GL.glEnableVertexAttribArray(0); GL.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0)
        GL.glEnableVertexAttribArray(1); GL.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12)
        GL.glEnableVertexAttribArray(2); GL.glVertexAttribPointer(2, 2, GL.GL_FLOAT, false, st, 24)
        GL.glEnableVertexAttribArray(3); GL.glVertexAttribPointer(3, 4, GL.GL_FLOAT, false, st, 32)
        GL.glEnableVertexAttribArray(4); GL.glVertexAttribPointer(4, 4, GL.GL_FLOAT, false, st, 48)
        val eb = IntArray(1); GL.glGenBuffers(1, eb, 0)
        val ibb = ByteBuffer.allocateDirect(ia.size * 4).order(ByteOrder.nativeOrder()); ibb.asIntBuffer().put(ia)
        GL.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, eb[0]); GL.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, ia.size * 4, ibb, GL.GL_STATIC_DRAW)
        GL.glBindVertexArray(0)
        val r = intArrayOf(vao[0], ia.size); p.alt[kind] = r
        return r
    }

    private fun loadTex(ctx: Context, path: String): Int {
        val bmp = ctx.assets.open(path).use { BitmapFactory.decodeStream(it) } ?: return 0
        val id = IntArray(1); GL.glGenTextures(1, id, 0)
        GL.glBindTexture(GL.GL_TEXTURE_2D, id[0])
        GLUtils.texImage2D(GL.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle()
        GL.glGenerateMipmap(GL.GL_TEXTURE_2D)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT)
        return id[0]
    }

    // ---------------------------------------------------------------- animation -> 12 skinning matrices
    private val g = FloatArray(16); private val a = FloatArray(16); private val t = FloatArray(16); private val r = FloatArray(16)
    private val w = FloatArray(16); private val inv = FloatArray(16); private val tmp = FloatArray(16)

    private fun rotX(m: FloatArray, ang: Float) { if (ang != 0f) Matrix.rotateM(m, 0, Math.toDegrees(ang.toDouble()).toFloat(), 1f, 0f, 0f) }
    private fun rotY(m: FloatArray, ang: Float) { if (ang != 0f) Matrix.rotateM(m, 0, Math.toDegrees(ang.toDouble()).toFloat(), 0f, 1f, 0f) }
    private fun rotZ(m: FloatArray, ang: Float) { if (ang != 0f) Matrix.rotateM(m, 0, Math.toDegrees(ang.toDouble()).toFloat(), 0f, 0f, 1f) }
    private fun quat(m: FloatArray, q: FloatArray) {
        val x = q[0]; val y = q[1]; val z = q[2]; val ww = q[3]
        r[0] = 1 - 2 * (y * y + z * z); r[1] = 2 * (x * y + z * ww); r[2] = 2 * (x * z - y * ww); r[3] = 0f
        r[4] = 2 * (x * y - z * ww); r[5] = 1 - 2 * (x * x + z * z); r[6] = 2 * (y * z + x * ww); r[7] = 0f
        r[8] = 2 * (x * z + y * ww); r[9] = 2 * (y * z - x * ww); r[10] = 1 - 2 * (x * x + y * y); r[11] = 0f
        r[12] = 0f; r[13] = 0f; r[14] = 0f; r[15] = 1f
        Matrix.multiplyMM(tmp, 0, m, 0, r, 0); System.arraycopy(tmp, 0, m, 0, 16)
    }
    /** out = world * inverse(bind). bind matrices are pure translations, so the inverse is a negated translation. */
    private fun emit(out: FloatArray, bone: Int, world: FloatArray) {
        System.arraycopy(bind[bone], 0, inv, 0, 16); inv[12] = -inv[12]; inv[13] = -inv[13]; inv[14] = -inv[14]
        Matrix.multiplyMM(out, bone * 16, world, 0, inv, 0)
    }

    fun skin(out: FloatArray, p: CharPose) {
        val sw = if (p.speed > 0.2f) sin(p.walk) * (if (p.sprint) 1.1f else if (p.crouch > 0.5f) 0.3f else 0.5f) else 0f
        var la = sw; var ra = -sw; var lk = 0f; var rk = 0f; var lz = 0f; var rz = 0f
        if (p.speed > 0.2f) { val k = if (p.sprint) 0.95f else 0.6f; lk = -(0.12f + k * max(0f, cos(p.walk))); rk = -(0.12f + k * max(0f, -cos(p.walk))) }
        var armRx = 0f; var armLx = 0f; var armRz = 0f; var armLz = 0f; var foreRx = 0f; var foreLx = 0f; var foreRz = 0f; var foreLz = 0f
        var tw = 0f
        var rifle = p.mode == CharPose.RIFLE
        when (p.mode) {
            CharPose.DRIVE -> { la = 1.4f; ra = 1.4f; lk = -1.4f; rk = -1.4f; armLx = 1.15f; armRx = 1.15f; foreLx = -0.35f; foreRx = -0.35f; rifle = false }
            CharPose.DIVE -> { la = 0.12f; ra = 0.12f; lk = 0f; rk = 0f; armRz = -2.7f; armLz = 2.7f; rifle = false }
            CharPose.CHUTE -> { la = 0.22f; ra = 0.12f; lk = -0.25f; rk = -0.2f; armRz = -2.8f; armLz = 2.8f; foreRx = -0.2f; foreLx = -0.2f; rifle = false }
            CharPose.RELAXED -> { armLx = -sw * 0.6f; armRx = sw * 0.6f; foreLx = -0.2f; foreRx = -0.2f }
            CharPose.AIR -> {
                la = p.exLegLx; ra = p.exLegRx; lz = p.exLegLz; rz = p.exLegRz; lk = p.exKneeL; rk = p.exKneeR
                armLx = p.exArmLx; armRx = p.exArmRx; armLz = p.exArmLz; armRz = p.exArmRz
                foreLx = p.exForeLx; foreRx = p.exForeRx; foreLz = p.exForeLz; foreRz = p.exForeRz
                tw = p.exTw; rifle = false
            }
        }
        if (rifle && p.sprint && p.speed > 1.5f) { val s = sin(p.walk); armLx = 0.38f + s * 0.12f; armRx = 0.38f - s * 0.12f }
        if (rifle) { armLx += p.aimPitch; armRx += p.aimPitch }
        if (p.crouch > 0.01f) { la += 0.5f * p.crouch; ra += 0.5f * p.crouch; lk -= 0.9f * p.crouch; rk -= 0.9f * p.crouch }

        // body + head
        Matrix.setIdentityM(w, 0); rotY(w, -tw); emit(out, 0, w)
        Matrix.setIdentityM(w, 0); rotY(w, -tw); Matrix.translateM(w, 0, CharRig.NECK[0], CharRig.NECK[1], CharRig.NECK[2]); rotY(w, p.headYaw); rotX(w, p.headPitch); emit(out, 11, w)
        // legs
        for (s in 0..1) {
            val sx = if (s == 0) -0.12f else 0.12f
            Matrix.setIdentityM(g, 0); Matrix.translateM(g, 0, sx, 1.04f, 0f); rotX(g, if (s == 0) la else ra); rotZ(g, if (s == 0) lz else rz)
            emit(out, 1 + s, g)
            System.arraycopy(g, 0, w, 0, 16); Matrix.translateM(w, 0, 0f, -0.5f, -0.015f); rotX(w, if (s == 0) lk else rk)
            emit(out, 3 + s, w)
        }
        // arms
        for (s in 0..1) {
            val sx = if (s == 0) -0.27f else 0.27f
            Matrix.setIdentityM(a, 0); Matrix.translateM(a, 0, sx * cos(tw), 1.67f, sx * sin(tw)); rotX(a, if (s == 0) armLx else armRx); rotZ(a, if (s == 0) armLz else armRz)
            if (rifle) {
                System.arraycopy(a, 0, w, 0, 16); quat(w, uq[s]); emit(out, 5 + s, w)
                System.arraycopy(a, 0, w, 0, 16); Matrix.translateM(w, 0, e1[s][0], e1[s][1], e1[s][2]); quat(w, fq[s])
                emit(out, 7 + s, w); emit(out, 9 + s, w)
            } else {
                emit(out, 5 + s, a)
                System.arraycopy(a, 0, w, 0, 16); Matrix.translateM(w, 0, elbow[s][0], elbow[s][1], elbow[s][2]); rotX(w, if (s == 0) foreLx else foreRx); rotZ(w, if (s == 0) foreLz else foreRz)
                emit(out, 7 + s, w); emit(out, 9 + s, w)
            }
        }
    }
}

/** GPU skinning shader + draw call for CharModel. Lighting / fog are the same formulas as the map shader. */
class CharRenderer(private val ctx: Context) {
    private val VS = """#version 300 es
layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNor; layout(location=2) in vec2 aUV; layout(location=3) in vec4 aSI; layout(location=4) in vec4 aSW;
uniform mat4 uVP; uniform mat4 uModel; uniform mat4 uBones[12]; uniform vec3 uEye;
out vec3 vN; out vec2 vUV; out float vD;
void main(){
  mat4 sk = aSW.x*uBones[int(aSI.x+0.5)] + aSW.y*uBones[int(aSI.y+0.5)] + aSW.z*uBones[int(aSI.z+0.5)] + aSW.w*uBones[int(aSI.w+0.5)];
  vec4 lp = sk*vec4(aPos,1.0); vec3 ln = mat3(sk)*aNor;
  vec4 w = uModel*lp; gl_Position = uVP*w; vN = mat3(uModel)*ln; vUV = aUV; vD = distance(w.xyz,uEye);
}"""
    private val FS = """#version 300 es
precision highp float;
in vec3 vN; in vec2 vUV; in float vD;
uniform sampler2D uTex; uniform float uFog; uniform vec3 uTint;
const vec3 SUN=vec3(-0.5652,0.6935,0.3596);
out vec4 o;
void main(){
  vec3 a = texture(uTex,vUV).rgb * uTint;
  vec3 n = normalize(vN); if(!gl_FrontFacing) n=-n;
  vec3 hemi = mix(vec3(0.49,0.54,0.31), vec3(0.81,0.89,1.0), 0.5+0.5*n.y);
  vec3 lit = a*(hemi*0.62 + vec3(1.0,0.91,0.78)*max(dot(n,SUN),0.0)*0.95) + vec3(0.12,0.115,0.10)*a;
  float f = clamp((vD-160.0*uFog)/(740.0*uFog),0.0,1.0);
  o = vec4(mix(lit, vec3(0.74,0.84,0.93), f*f), 1.0);
}"""
    private var prog = 0
    private var uVP = 0; private var uModel = 0; private var uBones = 0; private var uEye = 0; private var uFog = 0; private var uTint = 0; private var uTex = 0
    private val models = HashMap<String, CharModel?>()
    private val bones = FloatArray(CharRig.NB * 16)
    private val cloth = ClothRenderer(ctx)

    private fun compile(type: Int, src: String): Int {
        val s = GL.glCreateShader(type); GL.glShaderSource(s, src); GL.glCompileShader(s)
        val ok = IntArray(1); GL.glGetShaderiv(s, GL.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Char shader: " + GL.glGetShaderInfoLog(s))
        return s
    }

    /** call on the GL thread (onSurfaceCreated). A model that fails to load is simply not drawn (callers fall back to boxes). */
    fun init() {
        models.clear(); cloth.init()
        try {
            prog = GL.glCreateProgram()
            GL.glAttachShader(prog, compile(GL.GL_VERTEX_SHADER, VS)); GL.glAttachShader(prog, compile(GL.GL_FRAGMENT_SHADER, FS))
            GL.glLinkProgram(prog)
            uVP = GL.glGetUniformLocation(prog, "uVP"); uModel = GL.glGetUniformLocation(prog, "uModel"); uBones = GL.glGetUniformLocation(prog, "uBones")
            uEye = GL.glGetUniformLocation(prog, "uEye"); uFog = GL.glGetUniformLocation(prog, "uFog"); uTint = GL.glGetUniformLocation(prog, "uTint"); uTex = GL.glGetUniformLocation(prog, "uTex")
        } catch (e: Throwable) { prog = 0; return }
        for (id in arrayOf("survivor")) models[id] = try { CharModel(ctx, id) } catch (e: Throwable) { null }
    }

    val ready get() = prog != 0
    fun has(charId: String) = models[fileOf(charId)] != null
    /** Characters.ALL ids -> asset ids */
    fun fileOf(charId: String) = charId
    fun vertices(charId: String) = models[fileOf(charId)]?.vertexCount ?: 0

    /**
     * Draw one character. `model` = world matrix of the character root (position + yaw + scale already applied by the caller).
     * Leaves the skinning program bound: callers must glUseProgram(their own program) afterwards.
     */
    fun draw(charId: String, vp: FloatArray, model: FloatArray, ex: Float, ey: Float, ez: Float, fog: Float, pose: CharPose, tr: Float = 1f, tg: Float = 1f, tb: Float = 1f, wear: String? = null) {
        val fid = fileOf(charId)
        val m = models[fid] ?: return
        m.skin(bones, pose)
        // store top: hide / trim the character's own jacket, draw the top + bare arms afterwards
        val wcfg = if (wear != null && cloth.has(wear)) Cloth.CFG[fid] else null
        GL.glUseProgram(prog)
        GL.glUniformMatrix4fv(uVP, 1, false, vp, 0); GL.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GL.glUniformMatrix4fv(uBones, CharRig.NB, false, bones, 0)
        GL.glUniform3f(uEye, ex, ey, ez); GL.glUniform1f(uFog, fog); GL.glUniform3f(uTint, tr, tg, tb); GL.glUniform1i(uTex, 0)
        GL.glActiveTexture(GL.GL_TEXTURE0)
        for ((pi, p) in m.prims.withIndex()) {
            var vao = p.vao; var cnt = p.count
            if (wcfg != null) {
                if (wcfg.hide.contains(pi)) continue
                val kind = wcfg.cut[pi]
                if (kind != null) { val a = m.ensureCut(p, kind); if (a != null) { vao = a[0]; cnt = a[1] } }
            }
            GL.glBindTexture(GL.GL_TEXTURE_2D, m.texIds.getOrElse(p.mat) { 0 })
            GL.glBindVertexArray(vao); GL.glDrawElements(GL.GL_TRIANGLES, cnt, GL.GL_UNSIGNED_INT, 0)
        }
        GL.glBindVertexArray(0)
        if (wcfg != null && wear != null) cloth.draw(wear, fid, bones, vp, model, ex, ey, ez, fog)
    }
}
