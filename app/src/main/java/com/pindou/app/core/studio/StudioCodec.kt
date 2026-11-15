package com.pindou.app.core.studio

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 分层工程 sidecar。
 *
 * 项目库的 `cells` 是拍平结果（每帧一页），本文件保存帧/图层/透明度/混合模式。
 * 用固定二进制格式而不是 JSON：200×200×多层时 JSON 的整数文本会把文件撑到
 * 十几兆，解析也慢；二进制定长 4 字节/格，读写各一次遍历。
 */
object StudioCodec {

    private const val MAGIC = 0x50535444 // "PSTD"
    private const val VERSION = 2
    private const val MAX_FRAMES = 240
    private const val MAX_LAYERS = 64
    private const val MAX_SIDE = 512

    fun save(engine: StudioEngine, file: File): Boolean = runCatching {
        file.parentFile?.mkdirs()
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(engine.width)
            out.writeInt(engine.height)
            out.writeInt(engine.frameCount)
            out.writeInt(engine.frameIndex)
            for (frame in engine.frames) {
                out.writeInt(frame.holdMs)
                out.writeInt(frame.activeLayer)
                out.writeInt(frame.layers.size)
                for (layer in frame.layers) {
                    out.writeUTF(layer.name)
                    out.writeBoolean(layer.visible)
                    out.writeBoolean(layer.locked)
                    out.writeFloat(layer.opacity)
                    out.writeInt(layer.blend.ordinal)
                    for (c in layer.cells) out.writeInt(c)
                }
                val reference = frame.reference
                out.writeBoolean(reference != null)
                if (reference != null) {
                    out.writeInt(reference.width)
                    out.writeInt(reference.height)
                    out.writeBoolean(reference.visible)
                    for (pixel in reference.argb) out.writeInt(pixel)
                }
            }
        }
        true
    }.getOrDefault(false)

    /** 读取 sidecar；格式不符/尺寸越界/文件截断一律返回 null，由调用方回退旧编辑器。 */
    fun load(file: File, paletteArgb: IntArray = IntArray(0)): StudioEngine? = runCatching {
        if (!file.isFile || file.length() < 24L) return null
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            if (input.readInt() != MAGIC) return null
            val version = input.readInt()
            if (version !in 1..VERSION) return null
            val width = input.readInt()
            val height = input.readInt()
            if (width !in 1..MAX_SIDE || height !in 1..MAX_SIDE) return null
            val frameCount = input.readInt()
            if (frameCount !in 1..MAX_FRAMES) return null
            val frameIndex = input.readInt()
            val frames = ArrayList<StudioFrame>(frameCount)
            repeat(frameCount) {
                val hold = input.readInt().coerceIn(20, 10_000)
                val active = input.readInt()
                val layerCount = input.readInt()
                if (layerCount !in 1..MAX_LAYERS) return null
                val layers = ArrayList<StudioLayer>(layerCount)
                repeat(layerCount) {
                    val name = input.readUTF().take(40)
                    val visible = input.readBoolean()
                    val locked = input.readBoolean()
                    val opacity = input.readFloat().coerceIn(0f, 1f)
                    val blendOrdinal = input.readInt()
                    val blend = StudioBlendMode.entries.getOrElse(blendOrdinal) { StudioBlendMode.NORMAL }
                    val cells = IntArray(width * height) { input.readInt() }
                    layers.add(StudioLayer(name, cells, visible, locked, opacity, blend))
                }
                val reference = if (version >= 2 && input.readBoolean()) {
                    val refW = input.readInt()
                    val refH = input.readInt()
                    if (refW !in 1..MAX_SIDE * 4 || refH !in 1..MAX_SIDE * 4) return null
                    val refVisible = input.readBoolean()
                    val pixels = IntArray(refW * refH) { input.readInt() }
                    StudioReference(refW, refH, pixels, refVisible)
                } else {
                    null
                }
                frames.add(
                    StudioFrame(
                        layers,
                        active.coerceIn(0, layers.size - 1),
                        hold,
                        reference,
                    ),
                )
            }
            StudioEngine(width, height, frames, frameIndex, paletteArgb)
        }
    }.getOrNull()

    /** 新建空白工程。 */
    fun blank(width: Int, height: Int, paletteArgb: IntArray): StudioEngine {
        val w = width.coerceIn(1, MAX_SIDE)
        val h = height.coerceIn(1, MAX_SIDE)
        return StudioEngine(
            w,
            h,
            listOf(
                StudioFrame(
                    mutableListOf(StudioLayer("图层 1", IntArray(w * h) { -1 })),
                ),
            ),
            paletteArgb = paletteArgb,
        )
    }
}
