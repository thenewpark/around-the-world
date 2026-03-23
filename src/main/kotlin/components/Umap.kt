package viz

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.openrndr.math.Vector2
import java.io.File

fun loadUmap(filename: String): List<Vector2> {
    return csvReader().readAll(File(filename)).drop(1).map { row ->
    Vector2(row[0].toDouble(), row[1].toDouble())
    }
}

fun loadMert(filename: String): Array<DoubleArray> {
    return csvReader().readAll(File(filename)).drop(1).map { row ->
    DoubleArray(row.size) { row[it].toDouble() }
    }.toTypedArray()
}

data class Link(val from: Int, val to: Int, val similarity: Double)

fun loadLinks(filename: String): List<Link> {
    return csvReader().readAll(File(filename)).drop(1).map { row ->
        Link(row[0].toInt(), row[1].toInt(), row[2].toDouble())
    }
}

