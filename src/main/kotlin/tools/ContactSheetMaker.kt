import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.loadFont
import org.openrndr.draw.renderTarget
import org.openrndr.extra.imageFit.FitMethod
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.extra.shapes.primitives.grid
import org.openrndr.extra.textwriter.writer
import org.openrndr.ffmpeg.PlayMode
import org.openrndr.ffmpeg.VideoPlayerConfiguration
import org.openrndr.ffmpeg.loadVideo
import org.openrndr.ffmpeg.probeVideo
import java.io.File

fun main() = application {
    configure {
        width = 600
        height = 400
    }

    program {

        val input = "imitation-of-life"

        val details = probeVideo("/Users/edwinjakobs/Downloads/yt-dlp/${input}.mp4",)
        val fps = details?.framerate ?: 0.0
        val video = loadVideo(
            "/Users/edwinjakobs/Downloads/yt-dlp/${input}.mp4",
            mode = PlayMode.VIDEO,
            configuration = VideoPlayerConfiguration().apply {
                allowFrameSkipping = false
                synchronizeToClock = false
            })

        video.play()

//        val tileWidth = 600
//        val tileHeight = (tileWidth * (video.height.toDouble() / video.width)).toInt()


        var ended = false
        video.ended.listen {
            println("yo the video ended at $frameCount")
            ended = true
        }

        val rt = renderTarget(3000, 2000 ) {
            colorBuffer()
        }
        val grid = rt.colorBuffer(0).bounds.grid(6,4).flatten()

        var gridIndex = 0
        var sheetIndex = 1

        rt.clearColor(0, ColorRGBa.BLACK)
        extend {

            drawer.isolatedWithTarget(rt) {

                drawer.ortho(rt)
                video.draw(blockUntilFinished = true) {

                    if((frameCount-1) % 25 == 0) {
                        drawer.imageFit(it, grid[gridIndex], fitMethod = FitMethod.Contain)

                        drawer.fontMap = loadFont("data/fonts/default.otf", 32.0)
                        writer {
                            box = grid[gridIndex].offsetEdges(-20.0)
                            newLine()
                            val fc= String.format("%04d", frameCount)
                            val seconds = frameCount.toDouble() / fps
                            val minutes = (seconds / 60).toInt()
                            val remainingSeconds = (seconds % 60).toInt()
                            val timestamp = String.format("%02d:%02d", minutes, remainingSeconds)
                            text("${fc} - ${timestamp}")
                        }


                        gridIndex++
                    }

//                    print("${frameCount}\r")
//
//                    val x = (frameCount * tileWidth.toDouble()).mod(rt.width.toDouble())
//                    val y = ((frameCount * tileWidth) / rt.width) * tileHeight.toDouble()
//
//                    if (y >= rt.height) {
//                        error("tilesheet height too low")
//                    }
//                    drawer.imageFit(it, Rectangle(x, y, tileWidth.toDouble(), tileHeight.toDouble()))
                }

            }
            if (gridIndex == grid.size) {
                gridIndex = 0
                val sheet = String.format("%03d", sheetIndex)

                rt.colorBuffer(0).saveToFile(File("$input-contact-sheet-$sheet.png"))
                sheetIndex++
                rt.clearColor(0, ColorRGBa.BLACK)
            }

            if (ended && gridIndex > 0) {
                val sheet = String.format("%03d", sheetIndex)
                rt.colorBuffer(0).saveToFile(File("$input-contact-sheet-$sheet.png"))
                application.exit()
            }
            drawer.imageFit(rt.colorBuffer(0), drawer.bounds)
        }
    }
}

