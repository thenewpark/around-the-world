package viz

import audio.loadAudio
import components.loadTilesheet
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.color.hsv
import org.openrndr.extra.color.spaces.OKHSV
import org.openrndr.ffmpeg.ScreenRecorder

fun main() {
    application {
        configure {
            width = 768
            height = 768
        }
        program {
            val audio = loadAudio("data/extracted/eple/eple.ogg")
            audio.play()
            val mert = loadMert("data/extracted/eple/drums_mert_4_umap-16-5-0.0.csv")

            for (x in 0 until mert[0].size) {
                val values = (0 until mert.size).map { mert[it][x] }.sorted()
                val min = values[0]
                val max = values[values.size - 1]
                for (i in 0 until mert.size) {
                    mert[i][x] = (mert[i][x] - min) / (max - min)
                }
            }
            extend {

                val t = audio.position() / audio.duration()
                val seconds = audio.position()

                val offset = mert.size * t
                val si = ((offset).toInt() - height).coerceAtLeast(0)
                val ei = ((offset).toInt()).coerceAtMost(mert.size)

                drawer.stroke = null
                drawer.translate(0.0, -offset)
                drawer.rectangles {

                    for (i in si until ei) {
                        for (x in 0 until mert[i].size) {
                            val cw = width.toDouble() / mert[i].size
                            fill = OKHSV(mert[i][x]*360.0, 0.5, 1.0).toRGBa() ///hsv(mert[i][x]*360.0, 0.5, 1.0).toRGBa()//ColorRGBa.WHITE.shade(mert[i][x])
                            //point(x.toDouble(), i.toDouble() + height)
                            rectangle(x.toDouble()*cw, i.toDouble() + height, cw, 1.0)
                        }
                    }
                }
            }
        }
    }
}