package viz

import extensions.Camera2DTranslating
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadImage
import org.openrndr.extra.camera.Camera2D
import org.openrndr.math.Polar
import org.openrndr.math.asDegrees
import org.openrndr.shape.Rectangle
import kotlin.math.abs
import kotlin.math.exp

fun main() {
    application {
        configure {
            width = 720
            height = 720
        }
        program {
            val dataDir = "data/extracted/eple"
            val tiles = loadImage("$dataDir/eple-tilesheet.png")

            val tileWidth = 64
            val tileHeight = 39

            val radial = loadRadial("$dataDir/eple_dino_features_umap-radial-n_5-s_0.0-md_0.0.csv")
            val umap = radial.mapIndexed { index, it ->
                Polar(it, 100 + 200 * index.toDouble()/radial.size).cartesian + drawer.bounds.center
            }
            extend(Camera2DTranslating())
            extend {


                drawer.circles {
                    for (i in 0 until 10) {
                        fill = null
                        stroke = ColorRGBa.WHITE
                        circle(drawer.bounds.center, 100.0 + 20 * i)
                    }
                }

                val frames = umap.size
                val rects = (0 until frames).map { it ->
                    val iit = it
                    val sx = (iit * tileWidth.toDouble()).mod(tiles.width.toDouble())
                    val sy = ((iit * tileWidth) / tiles.width) * tileHeight.toDouble()
                    val tx = umap[iit].x
                    val ty = umap[iit].y
                    val dt = exp(4.0 * -abs(seconds - iit/25.0)) * 1.9 + 0.1
                    Rectangle(sx, sy, tileWidth.toDouble(), tileHeight.toDouble()) to Rectangle(tx, ty, dt * tileWidth, dt * tileHeight)
                }
                drawer.image(tiles, rects)
            }
        }
    }
}