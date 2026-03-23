package viz

import components.ColorViz
import components.loadColors
import org.openrndr.application
import org.openrndr.extra.imageFit.FitMethod
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.ffmpeg.loadVideo
import org.openrndr.ffmpeg.probeVideo

fun main() {
    application {
        program {

            val colors = loadColors("data/extracted/the-one-moment/the-one-moment-colors.csv")
            val video = loadVideo("data/extracted/the-one-moment/the-one-moment.mp4")
            val details = probeVideo("data/extracted/the-one-moment/the-one-moment.mp4")!!
            val colorViz = ColorViz(colors, details.framerate, this)

            video.play()
            extend {
                video.draw {

                    colorViz.draw(seconds)
                    drawer.imageFit(it, drawer.bounds.offsetEdges(-100.0), fitMethod = FitMethod.Contain)
                }
            }
        }
    }
}