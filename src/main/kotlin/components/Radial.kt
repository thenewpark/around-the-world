package viz

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.openrndr.math.asDegrees
import java.io.File
import kotlin.math.PI

fun loadRadial(filename: String): List<Double> {
    return csvReader().readAll(File(filename)).drop(1).map { row ->
        row[0].toDouble().mod(PI*2.0).asDegrees
    }
}