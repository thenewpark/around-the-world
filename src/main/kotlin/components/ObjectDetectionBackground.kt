package components

import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.RenderTarget
import org.openrndr.draw.isolated
import org.openrndr.draw.loadFont
import org.openrndr.math.Matrix44
import org.openrndr.shape.Rectangle
import viz.LabeledRectangle
import viz.loadRectangles
import kotlin.math.min

private const val OBJECT_DETECTION_MIN_SCORE = 0.22
private const val OBJECT_DETECTION_MIN_WIDTH = 14.0
private const val OBJECT_DETECTION_MIN_HEIGHT = 28.0
private const val OBJECT_DETECTION_MAX_BOXES = 18

class ObjectDetectionBackground(
    private val program: Program,
    boxesCsvPath: String,
    private val frameRate: Double,
    private val sourceWidth: Int,
    private val sourceHeight: Int
) {
    private val font = program.loadFont("data/fonts/default.otf", 18.0)
    private val boxes = loadRectangles(boxesCsvPath)
        .filter { it.label == "person" || it.label == "backpack" }
    private val boxesByFrame = boxes.groupBy(LabeledRectangle::frameIndex)
    private val detectionWidth = boxes.maxOfOrNull { it.rectangle.x + it.rectangle.width } ?: sourceWidth.toDouble()
    private val detectionHeight = boxes.maxOfOrNull { it.rectangle.y + it.rectangle.height } ?: sourceHeight.toDouble()

    fun draw(frame: ColorBuffer, playbackTime: Double) {
        val drawer = program.drawer
        val frameIndex = (playbackTime * frameRate).toInt()
        val detections = boxesByFrame[frameIndex].orEmpty()
            .asSequence()
            .filter { it.score >= OBJECT_DETECTION_MIN_SCORE }
            .filter { it.rectangle.width >= OBJECT_DETECTION_MIN_WIDTH && it.rectangle.height >= OBJECT_DETECTION_MIN_HEIGHT }
            .sortedByDescending(LabeledRectangle::score)
            .take(OBJECT_DETECTION_MAX_BOXES)
            .toList()

        if (detections.isEmpty()) {
            return
        }

        val targetFrame = targetFrameBounds()
        drawer.isolated {
            drawer.defaults()
            drawer.ortho(RenderTarget.active)
            drawer.view = Matrix44.IDENTITY
            drawer.model = Matrix44.IDENTITY
            drawer.fontMap = font
            drawer.stroke = null

            for (detection in detections) {
                val source = sourceRectangleInVideo(detection.rectangle, frame) ?: continue
                val target = Rectangle(
                    targetFrame.x + detection.rectangle.x / detectionWidth * targetFrame.width,
                    targetFrame.y + detection.rectangle.y / detectionHeight * targetFrame.height,
                    detection.rectangle.width / detectionWidth * targetFrame.width,
                    detection.rectangle.height / detectionHeight * targetFrame.height
                )

                drawer.image(frame, source, target)

                drawer.fill = ColorRGBa.RED.opacify(0.9)
                val labelY = (target.y - 4.0).coerceAtLeast(targetFrame.y + 18.0)
                drawer.text("${detection.label}: ${(detection.score * 100.0).toInt()}", target.x, labelY)
            }
        }
    }

    private fun targetFrameBounds(): Rectangle {
        val maxWidth = program.width * 0.74
        val maxHeight = program.height * 0.8
        val scale = min(maxWidth / sourceWidth, maxHeight / sourceHeight)
        return Rectangle.fromCenter(
            program.drawer.bounds.center,
            sourceWidth * scale,
            sourceHeight * scale
        )
    }

    private fun sourceRectangleInVideo(rectangle: Rectangle, frame: ColorBuffer): Rectangle? {
        val scaleX = frame.width / detectionWidth
        val scaleY = frame.height / detectionHeight

        val x0 = (rectangle.x * scaleX).coerceIn(0.0, frame.width.toDouble())
        val y0 = (rectangle.y * scaleY).coerceIn(0.0, frame.height.toDouble())
        val x1 = ((rectangle.x + rectangle.width) * scaleX).coerceIn(0.0, frame.width.toDouble())
        val y1 = ((rectangle.y + rectangle.height) * scaleY).coerceIn(0.0, frame.height.toDouble())
        val width = x1 - x0
        val height = y1 - y0

        if (width <= 1.0 || height <= 1.0) {
            return null
        }

        return Rectangle(x0, y0, width, height)
    }
}
