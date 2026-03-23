package viz

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadImage
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Polar
import org.openrndr.math.Vector2
import org.openrndr.shape.LineSegment
import org.openrndr.shape.Rectangle
import org.openrndr.shape.Segment2D
import kotlin.math.abs
import kotlin.math.exp

fun main() {
    application {
        configure {
            width = 720
            height = 720
        }
        program {
            val tiles = loadImage("tilesheet.png")
            val umap = loadUmap("data/dino/star-guitar_dino_features.csv")
            val links = loadLinks("data/links/star-guitar_dino_features_linked_pairs-2.csv")

            println(links.size)
            extend(ScreenRecorder()) {
                maximumDuration = 60.0 * 4.0
            }
            extend {
                val rects = (0 until umap.size step 1).map {

                    val sx = (it * 64.0).mod(tiles.width.toDouble())
                    val sy = ((it * 64) / tiles.width) * 64.0


                    val f = it / umap.size.toDouble()
                    val p0 = Polar(f  * 360.0, 300.0).cartesian + drawer.bounds.center
                    val p1 = Polar(f  * 360.0, 310.0).cartesian + drawer.bounds.center
                    //LineSegment(p0, p1)

                    val dt = 32.0 //exp(1.0 * -abs(seconds - it/25.0)) * 64.0 + 4.0
                    Rectangle(sx, sy, 64.0, 64.0) to Rectangle(p0 - Vector2(dt / 2.0, dt / 2.0), dt, dt)
                }
                val it = (seconds * 25.0).toInt()
                val sx = (it * 64.0).mod(tiles.width.toDouble())
                val sy = ((it * 64) / tiles.width) * 64.0

                val link = links.find { l -> l.from == it || l.to == it}

                if (link != null) {
                    val sx0 = (link.from * 64.0).mod(tiles.width.toDouble())
                    val sy0 = ((link.from * 64) / tiles.width) * 64.0
                    val sx1 = (link.to * 64.0).mod(tiles.width.toDouble())
                    val sy1 = ((link.to * 64) / tiles.width) * 64.0
                    drawer.image(tiles, Rectangle(sx0, sy0, 64.0, 64.0),Rectangle.fromCenter(drawer.bounds.center - Vector2(64.0, 0.0), 64.0, 64.0) )
                    drawer.image(tiles, Rectangle(sx1, sy1, 64.0, 64.0),Rectangle.fromCenter(drawer.bounds.center + Vector2(64.0, 0.0), 64.0, 64.0))
                }

                drawer.stroke = ColorRGBa.PINK
                drawer.image(tiles, rects)

                drawer.image(tiles, Rectangle(sx, sy, 64.0, 64.0),Rectangle.fromCenter(drawer.bounds.center, 64.0, 64.0) )
                val linkLines = links.map {
                    val f0 = it.from / umap.size.toDouble()
                    val f1 = it.to / umap.size.toDouble()
                    val p0 = Polar(f0  * 360.0, 270.0).cartesian + drawer.bounds.center
                    val p1 = Polar(f1  * 360.0, 270.0).cartesian + drawer.bounds.center
                    Segment2D(p0, drawer.bounds.center, p1)


                }
                drawer.stroke = ColorRGBa.WHITE
                drawer.segments(linkLines)

                val r = (umap.size / 5969.0) * 25.0
                val f0 = (seconds * r).toInt() / umap.size.toDouble()
                val p0 = Polar(f0  * 360.0, 275.0).cartesian + drawer.bounds.center
                drawer.fill = ColorRGBa.BLACK
                drawer.circle(p0, 5.0)
            }
        }
    }
}