import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.MagnifyingFilter
import org.openrndr.draw.MinifyingFilter
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.ffmpeg.PlayMode
import org.openrndr.ffmpeg.VideoPlayerConfiguration
import org.openrndr.ffmpeg.loadVideo
import org.openrndr.ffmpeg.probeVideo
import org.openrndr.shape.Rectangle
import java.io.File

fun main() = application {
    configure {
        width = 720
        height = 720
    }

    program {

        val input = "star-guitar"

        val details = probeVideo("/Users/edwinjakobs/Downloads/yt-dlp/${input}.mp4")

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

        val tileWidth = 64
        val tileHeight = (64.0 * (video.height.toDouble() / video.width)).toInt()




        var ended = false
        video.ended.listen {
            println("yo the video ended at $frameCount")
            ended = true
        }

        val cb = colorBuffer(video.width, video.height,levels = 8)
        val rt = renderTarget(4096, 4096 + 1024 * 3) {
            colorBuffer()
        }

        val frameRt = renderTarget(video.width, video.height) {
            colorBuffer(cb)
        }
        rt.clearColor(0, ColorRGBa.TRANSPARENT)
        extend {

            drawer.isolatedWithTarget(rt) {

                drawer.ortho(rt)

                video.draw(blockUntilFinished = true) {

                    drawer.isolatedWithTarget(frameRt) {
                        drawer.ortho(frameRt)
                        drawer.image(it)
                    }
                    cb.generateMipmaps()
                    cb.filter(MinifyingFilter.LINEAR_MIPMAP_LINEAR, MagnifyingFilter.LINEAR)
                    print("${frameCount}\r")

                    val x = (frameCount * tileWidth.toDouble()).mod(rt.width.toDouble())
                    val y = ((frameCount * tileWidth) / rt.width) * tileHeight.toDouble()

                    if (y >= rt.height) {
                        error("tilesheet height too low")
                    }
                    drawer.imageFit(cb, Rectangle(x, y, tileWidth.toDouble(), tileHeight.toDouble()))
                }
            }

            if (ended) {
                rt.colorBuffer(0).saveToFile(File("$input-tilesheet.png"))
                File("$input-tilesheet.csv").writeText("tile-width,tile-height,count,framerate\n$tileWidth,$tileHeight,${frameCount+1},${details!!.framerate}")
                application.exit()
            }
            drawer.imageFit(rt.colorBuffer(0), drawer.bounds)
        }
    }
}

