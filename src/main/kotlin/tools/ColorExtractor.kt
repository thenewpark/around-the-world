import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.MagnifyingFilter
import org.openrndr.draw.MinifyingFilter
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.extra.color.statistics.calculateHistogramRGB
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.ffmpeg.PlayMode
import org.openrndr.ffmpeg.VideoPlayerConfiguration
import org.openrndr.ffmpeg.loadVideo
import org.openrndr.ffmpeg.probeVideo
import org.openrndr.math.smoothstep
import org.openrndr.shape.Rectangle
import java.io.File

fun main() = application {
    configure {
        width = 720
        height = 720
    }

    program {

        val input = "sledgehammer"

        val details = probeVideo("/Users/edwinjakobs/Downloads/yt-dlp/${input}.mp4")

        val colors = mutableListOf<ColorRGBa>()
        val video = loadVideo(
            "/Users/edwinjakobs/Downloads/yt-dlp/${input}.mp4",
            mode = PlayMode.VIDEO,
            configuration = VideoPlayerConfiguration().apply {
                allowFrameSkipping = false
                synchronizeToClock = false
//                videoFrameQueueSize = 2
//                displayQueueSize = 2
            })

        video.play()



        var ended = false
        video.ended.listen {
            println("yo the video ended at $frameCount")
            ended = true
        }

        val cb = colorBuffer(video.width, video.height, levels = 8)

        val frameRt = renderTarget(video.width, video.height) {
            colorBuffer(cb)
        }
        extend {
            video.draw(blockUntilFinished = true) {

                println(frameCount)
                drawer.isolatedWithTarget(frameRt) {
                    drawer.ortho(frameRt)
                    drawer.image(it)
                }
                cb.generateMipmaps()
                cb.filter(MinifyingFilter.LINEAR_MIPMAP_LINEAR, MagnifyingFilter.LINEAR)

            }
            val hist = calculateHistogramRGB(frameRt.colorBuffer(0), weighting = fun ColorRGBa.() = this.toHSVa().s * this.toHSVa().v.smoothstep(0.0, 0.2))

            val dom = hist.sortedColors()[0].first
            colors.add(dom)

            drawer.clear(dom)
            if (ended) {
                File("$input-colors.csv").writeText("r,g,b\n" + colors.joinToString("\n") { "${it.r},${it.g},${it.b}" })
                application.exit()
            }

        }
    }
}

