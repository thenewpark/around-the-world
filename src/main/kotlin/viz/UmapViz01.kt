package viz

import org.openrndr.application
import org.openrndr.draw.loadImage
import org.openrndr.shape.Rectangle

fun main() {
    application {
        configure {
            width = 720
            height = 720
        }
        program {
            val tiles = loadImage("tilesheet.png")



            extend {

                val frames = (seconds * 25.0).toInt()
                val rects = (0 until frames).map {
                    val sx = (it * 64.0).mod(tiles.width.toDouble())
                    val sy = ((it * 64) / tiles.width) * 64.0
                    Rectangle(sx, sy, 64.0, 64.0) to Rectangle(it.toDouble()-seconds*25.0 + width - 256.0, it.toDouble()-seconds*25.0 + height - 256.0, 64.0*4.0, 64.0*4.0)
                }

                drawer.image(tiles, rects)
            }

        }
    }
}