package components.rotoscope

import java.awt.image.BufferedImage
import java.util.ArrayDeque
import kotlin.math.min
import kotlin.math.sqrt

data class BinaryMask(
    val width: Int,
    val height: Int,
    val cellSize: Int,
    val pixels: BooleanArray
) {
    fun index(x: Int, y: Int): Int = y * width + x

    fun isForeground(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) {
            return false
        }
        return pixels[index(x, y)]
    }

    val foregroundCount: Int
        get() = pixels.count { it }
}

class MaskProcessor(
    private val sampleStep: Int = RotoscopeConfig.MASK_SAMPLE_STEP,
    private val threshold: Double = RotoscopeConfig.MASK_THRESHOLD,
    private val minComponentArea: Int = RotoscopeConfig.MIN_COMPONENT_AREA
) {
    fun process(image: BufferedImage): BinaryMask {
        val maskWidth = (image.width + sampleStep - 1) / sampleStep
        val maskHeight = (image.height + sampleStep - 1) / sampleStep
        val pixels = BooleanArray(maskWidth * maskHeight)
        val background = estimateBackground(image)

        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                pixels[y * maskWidth + x] = sampleCell(image, x, y, background) >= threshold
            }
        }

        removeSmallComponents(maskWidth, maskHeight, pixels)

        return BinaryMask(
            width = maskWidth,
            height = maskHeight,
            cellSize = sampleStep,
            pixels = pixels
        )
    }

    private fun sampleCell(image: BufferedImage, cellX: Int, cellY: Int, background: DoubleArray): Double {
        val startX = cellX * sampleStep
        val startY = cellY * sampleStep
        val endX = min(startX + sampleStep - 1, image.width - 1)
        val endY = min(startY + sampleStep - 1, image.height - 1)
        val midX = (startX + endX) / 2
        val midY = (startY + endY) / 2

        val samplePoints = arrayOf(
            startX to startY,
            endX to startY,
            midX to midY,
            startX to endY,
            endX to endY
        )

        var total = 0.0
        for ((x, y) in samplePoints) {
            total += colorDistance(image.getRGB(x, y), background)
        }
        return total / samplePoints.size
    }

    private fun estimateBackground(image: BufferedImage): DoubleArray {
        val margin = min(24, min(image.width, image.height) / 8).coerceAtLeast(1)
        val origins = arrayOf(
            0 to 0,
            image.width - margin to 0,
            0 to image.height - margin,
            image.width - margin to image.height - margin
        )

        var r = 0.0
        var g = 0.0
        var b = 0.0
        var count = 0

        for ((originX, originY) in origins) {
            for (y in originY until originY + margin) {
                for (x in originX until originX + margin) {
                    val rgb = image.getRGB(x, y)
                    r += rgb shr 16 and 0xff
                    g += rgb shr 8 and 0xff
                    b += rgb and 0xff
                    count += 1
                }
            }
        }

        return doubleArrayOf(r / count, g / count, b / count)
    }

    private fun removeSmallComponents(width: Int, height: Int, pixels: BooleanArray) {
        val visited = BooleanArray(pixels.size)
        val neighbours = intArrayOf(1, 0, -1, 0, 0, 1, 0, -1)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val startIndex = y * width + x
                if (!pixels[startIndex] || visited[startIndex]) {
                    continue
                }

                val component = mutableListOf<Int>()
                val queue = ArrayDeque<Int>()
                queue.add(startIndex)
                visited[startIndex] = true

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    component += current

                    val cx = current % width
                    val cy = current / width
                    for (index in neighbours.indices step 2) {
                        val nx = cx + neighbours[index]
                        val ny = cy + neighbours[index + 1]
                        if (nx !in 0 until width || ny !in 0 until height) {
                            continue
                        }

                        val neighbourIndex = ny * width + nx
                        if (visited[neighbourIndex] || !pixels[neighbourIndex]) {
                            continue
                        }

                        visited[neighbourIndex] = true
                        queue.add(neighbourIndex)
                    }
                }

                if (component.size < minComponentArea) {
                    component.forEach { pixels[it] = false }
                }
            }
        }
    }

    private fun colorDistance(rgb: Int, background: DoubleArray): Double {
        val r = (rgb shr 16 and 0xff).toDouble()
        val g = (rgb shr 8 and 0xff).toDouble()
        val b = (rgb and 0xff).toDouble()
        val dr = r - background[0]
        val dg = g - background[1]
        val db = b - background[2]
        return sqrt(dr * dr + dg * dg + db * db) / 441.6729559300637
    }
}
