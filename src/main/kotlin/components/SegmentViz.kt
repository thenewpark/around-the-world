package components

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.events.Event
import org.openrndr.events.listen
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle
import org.openrndr.shape.bounds
import java.io.File
import javax.swing.Spring.height

data class Segment(val text: String, val start: Double, val end: Double)

fun parseTime(time: String): Double {
    val parts = time.split(":")
    return parts[0].toDouble() * 60 + parts[1].toDouble()
}

fun loadSegments(filename:String): List<Segment> {
    return csvReader().readAll(File(filename)).drop(1).map { row ->
        val text = row[0]
        val start = row[1].toDoubleOrNull() ?: parseTime(row[1])
        val end = row[2].toDoubleOrNull() ?: parseTime(row[2])
        Segment(text, start, end)
    }
}

class SegmentViz(
    val segments: List<Segment>,
    val program: Program = Program.active!!
) {
    var content = Rectangle(0.0, 0.0, 720.0, 720.0)
    var segmentHeight = 30.0
    var widthScale = 10.0

    val seek = Event<SeekEvent>()

    var fill: (Int, Segment) -> ColorRGBa? = { _, _ -> ColorRGBa.WHITE }
    var stroke: (Int, Segment) -> ColorRGBa? = { _, _ -> ColorRGBa.BLACK }

    var textColor: (Int, Segment) -> ColorRGBa? = { _, _ -> ColorRGBa.BLACK }
    var font = program.loadFont("data/fonts/default.otf", 16.0)


    init {
        listOf(program.mouse.buttonUp, program.mouse.dragged).listen {
            if (it.propagationCancelled || it.modifiers.isNotEmpty())
                return@listen

            val t = it.position.x / widthScale
            if (t>= 0.0) {
                seek.trigger(SeekEvent(t))
                it.cancelPropagation()
            }
        }
    }

    fun draw(time: Double) {
        val drawer = program.drawer
        var y = 0.0

        val allRects = mutableListOf<Rectangle>()
        val activeRects = mutableListOf<Rectangle>()


        for ((index, segment) in segments.withIndex()) {
            drawer.fill = fill(index, segment)
            drawer.stroke = stroke(index, segment)
            val r = Rectangle(segment.start * widthScale, y, (segment.end - segment.start) * widthScale, segmentHeight)
            drawer.rectangle(r)

            allRects.add(r)
            if (time >= segment.start && time < segment.end) {
                activeRects.add(r)
            }
            drawer.fontMap = font
            drawer.fill = textColor(index, segment)
            drawer.text(segment.text, segment.start * widthScale + 10.0, y + segmentHeight / 2.0 + font.height / 2.0)
            y += segmentHeight
        }
        content = if (activeRects.isNotEmpty()) activeRects.bounds else if (allRects.isNotEmpty()) allRects.bounds else Rectangle(0.0, 0.0, 100.0, 100.0)

        drawer.stroke = ColorRGBa.WHITE
        drawer.lineSegment(Vector2(time * widthScale, -1000.0), Vector2(time * widthScale, 10_000.0))
    }
}