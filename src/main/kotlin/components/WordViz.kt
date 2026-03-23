package components

import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.extra.textwriter.writer


class WordViz(
    val words: List<Segment>,
    val program: Program
) {

    var fill: (Int, Segment) -> ColorRGBa? = { _, _ -> ColorRGBa.WHITE }
    var stroke: (Int, Segment) -> ColorRGBa? = { _, _ -> ColorRGBa.BLACK }

    var textColor: (Int, Segment) -> ColorRGBa? = { _, _ -> ColorRGBa.WHITE }
    var font = program.loadFont("data/fonts/default.otf", 64.0)


    fun draw(time: Double) {
        val drawer = program.drawer
        val active = words.filter { it.start <= time && it.end > time }

        drawer.fontMap = font
        for (word in active) {
            drawer.fill = textColor(words.indexOf(word), word)
            program.writer {
                box = drawer.bounds
                verticalAlign = 0.5
                horizontalAlign = 0.5
                text(word.text)
            }
        }
    }
}