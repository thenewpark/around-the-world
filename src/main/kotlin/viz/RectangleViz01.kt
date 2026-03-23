package viz

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.loadFont
import org.openrndr.draw.renderTarget
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

            val objectRectangles = loadRectangles("data/extracted/star-guitar/star-guitar_object_boxes-2.csv")
            extend {
                video.draw {
                    drawer.isolatedWithTarget(cb) {
                        drawer.ortho(cb)
                        drawer.image(it)
                    }
                }
                val frame = (video.position * fr).toInt()

                drawer.fill = null
                drawer.stroke = ColorRGBa.RED
                val t = mouse.position.y / height
                val objects = objectRectangles.filter { it.frameIndex == frame && it.rectangle.width < 100&& it.score > t  }

                drawer.rectangles(
                    objects.map { it.rectangle }
                )

                val pairs = objects.map { it.rectangle to it.rectangle}
                drawer.image(cb.colorBuffer(0), pairs)

                drawer.fill = ColorRGBa.RED
                drawer.fontMap = loadFont("data/fonts/default.otf", 16.0)
                for (o in objects) {
                    writer {
                        verticalAlign = 0.0
                        box = o.rectangle
                        text("${o.label}: ${(100 * o.score).toInt()}")
                    }
                }
            }

        }
    }
}