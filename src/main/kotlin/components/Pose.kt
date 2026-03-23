package viz

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.openrndr.math.Vector2
import java.io.File

class Pose(
    val frameIndex: Int,
    val personIndex: Int,
    val limbIndex: Int,
    val start: Vector2,
    val end: Vector2,
    val startScore: Double = 0.0,
    val endScore: Double = 0.0
)

fun loadPoses(filename: String): List<Pose> {
    return csvReader().readAll(File(filename)).drop(1).map { row ->
        Pose(
            row[0].toInt(),
            row[1].toInt(),
            row[2].toInt(),
            Vector2(row[3].toDouble(), row[4].toDouble()),
            Vector2(row[5].toDouble(), row[6].toDouble()),
            row[7].toDouble(),
            row[8].toDouble()
        )
    }

}