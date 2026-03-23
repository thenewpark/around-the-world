package viz

import org.openrndr.application
import org.openrndr.draw.loadImage
import org.openrndr.extra.camera.Camera2D
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.shape.Rectangle
import org.openrndr.shape.bounds
import org.openrndr.shape.map
import kotlin.math.abs
import kotlin.math.exp

fun main() {
    application {
        configure {
            width = 1024
            height = 1024
        }
        program {
            val tiles = loadImage("tilesheet.png")

            val umap = loadUmap("data/umap/other_layer_4_umap2d-2-5-2.0.csv")

            val ogbounds = umap.bounds
            val remap = umap.map(ogbounds, drawer.bounds)

            println("umap size ${umap.size}")
//            extend(ScreenRecorder()) {
//                frameRate = 25.0
//            }
            extend(Camera2D())
            extend {

                val frames = umap.size
                val rects = (0 until frames).map { it ->

                    val iit = (it * (5969.0 / umap.size)).toInt()



                    val sx = (iit * 64.0).mod(tiles.width.toDouble())
                    val sy = ((iit * 64) / tiles.width) * 64.0


                    val tx = remap[it].x
                    val ty = remap[it].y

                    val dt = exp(4.0 * -abs(seconds - iit/25.0)) * 32.0 + 4.0
                    Rectangle(sx, sy, 64.0, 64.0) to Rectangle(tx, ty, dt, dt)
                }

                drawer.image(tiles, rects)
            }

        }
    }
}