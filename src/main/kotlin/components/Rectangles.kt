package viz

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.openrndr.shape.Rectangle
import java.io.File

class LabeledRectangle(val frameIndex: Int, val objectIndex: Int, val label: String, val rectangle: Rectangle, val score: Double = 0.0)

fun loadRectangles(filename: String): List<LabeledRectangle> {
    return csvReader().readAll(File(filename)).drop(1).map { row ->

        if (row.size == 6) {
            val frameIndex = row[0].toInt()
            val objectIndex = row[1].toInt()
            val x = row[2].toDouble()
            val y = row[3].toDouble()
            val w = row[4].toDouble()
            val h = row[5].toDouble()
            LabeledRectangle(frameIndex, objectIndex, "person", Rectangle(x, y, w, h))
        } else if (row.size == 8) {
            val frameIndex = row[0].toInt()
            val objectIndex = row[1].toInt()
            val labelIndex = row[2].toInt()
            val label = row[3]
            val x = row[4].toDouble()
            val y = row[5].toDouble()
            val w = row[6].toDouble()
            val h = row[7].toDouble()
            LabeledRectangle(frameIndex, objectIndex, label, Rectangle(x, y, w, h))
        }
        else if (row.size == 9) {
            val frameIndex = row[0].toInt()
            val objectIndex = row[1].toInt()
            val labelIndex = row[2].toInt()
            val label = row[3]
            val x = row[4].toDouble()
            val y = row[5].toDouble()
            val w = row[6].toDouble()
            val h = row[7].toDouble()
            val score = row[8].toDouble()
            LabeledRectangle(frameIndex, objectIndex, label, Rectangle(x, y, w, h), score)
        }else {
            error("unknown row size")
        }
    }
}