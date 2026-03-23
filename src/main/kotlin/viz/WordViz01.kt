package viz

import components.WordViz
import components.loadSegments
import org.openrndr.application
import org.openrndr.extra.imageFit.FitMethod
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.ffmpeg.loadVideo

fun main() {
    application {
        program {
            val video = loadVideo("data/extracted/imitation-of-life/imitation-of-life.mp4")
            video.play()
            val words = loadSegments("data/extracted/imitation-of-life/vocals_transcription.csv")
            val wordViz = WordViz(words, this)
            extend {
                video.draw {
                    drawer.imageFit(it, drawer.bounds, fitMethod = FitMethod.Contain)
                }
                wordViz.draw(seconds)
            }
        }
    }
}