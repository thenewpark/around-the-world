package viz

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Polar
import org.openrndr.shape.LineSegment
import org.openrndr.shape.Segment2D

fun main() {
    application {
        configure {
            width = 720
            height = 720
        }
        program {
            val umap = loadUmap("data/umap/other_layer_4_umap2d-2-5-2.0.csv")
            val links = loadLinks("data/links/other_layer_4_linked_pairs.csv")

            println(links.size)
            extend(ScreenRecorder()) {
                maximumDuration = 60.0 * 4.0
            }
            extend {
                val lines = (0 until umap.size step 50).map {
                    val f = it / umap.size.toDouble()
                    val p0 = Polar(f  * 360.0, 300.0).cartesian + drawer.bounds.center
                    val p1 = Polar(f  * 360.0, 310.0).cartesian + drawer.bounds.center
                    LineSegment(p0, p1)
                }
                drawer.stroke = ColorRGBa.PINK
                drawer.lineSegments(lines)

                val linkLines = links.map {
                    val f0 = it.from / umap.size.toDouble()
                    val f1 = it.to / umap.size.toDouble()
                    val p0 = Polar(f0  * 360.0, 300.0).cartesian + drawer.bounds.center
                    val p1 = Polar(f1  * 360.0, 300.0).cartesian + drawer.bounds.center
                    Segment2D(p0, drawer.bounds.center, p1)


                }
                drawer.stroke = ColorRGBa.RED
                drawer.segments(linkLines)

                val r = (umap.size / 5969.0) * 25.0
                val f0 = (seconds * r).toInt() / umap.size.toDouble()
                val p0 = Polar(f0  * 360.0, 305.0).cartesian + drawer.bounds.center
                drawer.fill = ColorRGBa.PINK
                drawer.circle(p0, 15.0)
            }
        }
    }
}