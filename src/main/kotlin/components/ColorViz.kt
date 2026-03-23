package components

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolated
import java.io.File

fun loadColors(filename: String): List<ColorRGBa> {
    return csvReader().readAll(File(filename)).drop(1).map { row ->
        val r = row[0].toDouble()
        val g = row[1].toDouble()
        val b = row[2].toDouble()
        ColorRGBa(r, g, b)
    }
}

class ColorViz(
    val colors: List<ColorRGBa>,
    val frameRate: Double,
    val program: Program
    ) {

    fun draw(time: Double) {
        program.drawer.isolated {
            val frame = (time * frameRate).toInt()
            program.drawer.fill = colors.getOrNull(frame) ?: ColorRGBa.BLACK
            program.drawer.rectangle(program.drawer.bounds)
        }
    }
}