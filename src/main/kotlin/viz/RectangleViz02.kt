package viz

import components.RectangleViz
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.loadFont
import org.openrndr.draw.renderTarget
import org.openrndr.extra.color.spaces.OKHSV
import org.openrndr.extra.color.tools.shiftHue
import org.openrndr.extra.textwriter.writer
import org.openrndr.ffmpeg.loadVideo
import org.openrndr.ffmpeg.probeVideo

fun main() {
    application {
        configure {
            width = 1800
            height = 1024
        }
        program {
            val fr = probeVideo("data/extracted/star-guitar/star-guitar.mp4")?.framerate ?: error("krka")
            val video = loadVideo("data/extracted/star-guitar/star-guitar.mp4")
            video.play()

            val cb = renderTarget(video.width, video.height) {
                colorBuffer()

            }
            //val personRectangles = loadRectangles("data/rectangles/around-the-world-360_person_boxes.csv")

            val objectRectangles = loadRectangles("data/extracted/star-guitar/star-guitar_object_boxes-2.csv")
            val rectangleViz = RectangleViz(objectRectangles, fr, this)


            rectangleViz.fill = { index, o ->
                ColorRGBa.WHITE.opacify(o.score)
            }
            rectangleViz.stroke = { index, o ->
                ColorRGBa.RED.shiftHue<OKHSV>(index * 2.0)
            }
            rectangleViz.font = loadFont("data/fonts/default.otf", 24.0)
            extend {
                video.draw {
                    drawer.isolatedWithTarget(cb) {
                        drawer.ortho(cb)
                        drawer.image(it)
                    }
                }
                drawer.image(cb.colorBuffer(0))
                rectangleViz.draw(video.position)


            }
        }
    }
}