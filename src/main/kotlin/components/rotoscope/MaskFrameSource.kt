package components.rotoscope

import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.loadImage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.floor

class MaskFrameSource(
    framesDir: String,
    private val fps: Double = RotoscopeConfig.FRAME_RATE,
    private val firstFrameIndex: Int = 1
) {
    private val frameFilesByIndex = File(framesDir)
        .listFiles()
        ?.filter { it.isFile }
        ?.mapNotNull { file ->
            FRAME_PATTERN.find(file.name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { index ->
                index to file
            }
        }
        ?.toMap()
        ?.toSortedMap()
        ?: emptyMap()

    val frameIndices = frameFilesByIndex.keys.toList()
    val minimumFrameIndex = frameIndices.firstOrNull() ?: firstFrameIndex
    val maximumFrameIndex = frameIndices.lastOrNull() ?: firstFrameIndex

    private val bufferedCache = linkedMapOf<Int, BufferedImage>()
    private val previewCache = linkedMapOf<Int, ColorBuffer>()

    init {
        require(frameFilesByIndex.isNotEmpty()) {
            "Mask frames not found in $framesDir"
        }
    }

    fun frameIndexForTime(time: Double): Int {
        val rawFrameIndex = floor(time.coerceAtLeast(0.0) * fps).toInt() + firstFrameIndex
        return rawFrameIndex.coerceIn(minimumFrameIndex, maximumFrameIndex)
    }

    fun loadBuffered(frameIndex: Int): BufferedImage? {
        val clamped = frameIndex.coerceIn(minimumFrameIndex, maximumFrameIndex)
        bufferedCache[clamped]?.let { cached ->
            bufferedCache.remove(clamped)
            bufferedCache[clamped] = cached
            return cached
        }

        val file = frameFilesByIndex[clamped] ?: return null
        val image = ImageIO.read(file) ?: return null
        bufferedCache[clamped] = image
        evictBufferedCache()
        return image
    }

    fun loadPreview(frameIndex: Int): ColorBuffer? {
        val clamped = frameIndex.coerceIn(minimumFrameIndex, maximumFrameIndex)
        previewCache[clamped]?.let { cached ->
            previewCache.remove(clamped)
            previewCache[clamped] = cached
            return cached
        }

        val file = frameFilesByIndex[clamped] ?: return null
        val image = loadImage(file.absolutePath)
        previewCache[clamped] = image
        evictPreviewCache()
        return image
    }

    fun destroy() {
        previewCache.values.forEach { it.destroy() }
        previewCache.clear()
        bufferedCache.clear()
    }

    private fun evictBufferedCache() {
        while (bufferedCache.size > RotoscopeConfig.IMAGE_CACHE_SIZE) {
            val eldestKey = bufferedCache.entries.first().key
            bufferedCache.remove(eldestKey)
        }
    }

    private fun evictPreviewCache() {
        while (previewCache.size > RotoscopeConfig.PREVIEW_CACHE_SIZE) {
            val eldest = previewCache.entries.first()
            eldest.value.destroy()
            previewCache.remove(eldest.key)
        }
    }

    companion object {
        private val FRAME_PATTERN = Regex("""frame_(\d+)\.(jpg|jpeg|png)""", RegexOption.IGNORE_CASE)
    }
}
