package components

import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.extra.shapes.primitives.bounds
import org.openrndr.extra.textwriter.writer
import org.openrndr.shape.Rectangle
import org.openrndr.shape.bounds
import viz.LabeledRectangle

class RectangleViz(
    val labeledRectangles: List<LabeledRectangle>,
    val frameRate: Double,
    val program: Program
) {

    var content = Rectangle(0.0, 0.0, 720.0, 720.0)

    var minWidth = 0.0
    var maxWidth = 100.0

    var minHeight = 0.0
    var maxHeight = 100.0

    var minScore = 0.0
    var maxScore = 1.1

    var fill: (Int, LabeledRectangle) -> ColorRGBa? = { _, _ -> null }
    var stroke: (Int, LabeledRectangle) -> ColorRGBa? = { _, _ -> ColorRGBa.RED }

    var textColor: (Int, LabeledRectangle) -> ColorRGBa? = { _, _ -> ColorRGBa.WHITE }
    var font = program.loadFont("data/fonts/default.otf", 16.0)

    var filter = { _: Int, _: LabeledRectangle -> true }

    var label ={ index: Int, o: LabeledRectangle -> "${o.label}: ${(100 * o.score).toInt()}" }

    fun draw(time: Double) {
        val drawer = program.drawer
        val frame = (time * frameRate).toInt()

        val objects = labeledRectangles.filter { it.frameIndex == frame &&
                it.rectangle.width < maxWidth &&
                it.rectangle.width >= minWidth &&
                it.rectangle.height < maxHeight &&
                it.rectangle.height >= minHeight &&
                it.score > minScore && it.score < maxScore &&
                filter(it.frameIndex, it)
        }

        drawer.rectangles {
            for ((index, o) in objects.withIndex()) {
                fill = fill(index, o)
                stroke = stroke(index, o)
                rectangle(o.rectangle)
            }
        }

        drawer.fontMap = font
        for ((index, o) in objects.withIndex()) {
            drawer.fill = textColor(index, o)
            program.writer {
                verticalAlign = 0.0
                box = o.rectangle
                text(label(index, o))
            }
        }
        content = if (objects.isNotEmpty()) objects.map { it.rectangle }.bounds else Rectangle(0.0, 0.0, 100.0, 100.0)

    }
}