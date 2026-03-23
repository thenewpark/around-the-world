package components

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.MagnifyingFilter
import org.openrndr.draw.MinifyingFilter
import org.openrndr.draw.loadImage
import java.io.File

class Tilesheet(
    val width: Int,
    val height: Int,
    val size: Int,
    val frameRate: Double,
    val image: ColorBuffer)

fun loadTilesheet(csvFile: String, imageFile: String): Tilesheet {

    val image = loadImage(imageFile)

    image.filter(MinifyingFilter.LINEAR_MIPMAP_LINEAR, MagnifyingFilter.LINEAR)
    val ts = csvReader().readAll(File(csvFile)).drop(1).take(1).map { row ->
        val width = row[0].toInt()
        val height = row[1].toInt()
        val size = row[2].toInt()
        val frameRate = row[3].toDouble()
        Tilesheet(width, height, size, frameRate, image)
    }

    return ts.first()
}