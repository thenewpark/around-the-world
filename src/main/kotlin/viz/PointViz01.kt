package viz

import audio.loadAudio
import components.PointViz
import components.loadTilesheet
import extensions.Camera2DTranslating
import org.openrndr.application

fun main() {
    application {
        configure {
            width = 720
            height = 720
        }
        program {
            val camera = extend(Camera2DTranslating())
            val audio = loadAudio("data/extracted/star-guitar/star-guitar.ogg")
            audio.play()
            val dataDir = "data/extracted/star-guitar/"
            val tilesheet = loadTilesheet("$dataDir/star-guitar-tilesheet.csv", "$dataDir/star-guitar-tilesheet.png")
            val umap = loadUmap("data/extracted/star-guitar/drums_mert_4_umap-2-3-0.0.csv")
            val pointViz = PointViz(tilesheet, umap, this)

            pointViz.seek.listen {
                println("Seeking to: ${it.positionInSeconds}")
                audio.setPosition(it.positionInSeconds)
            }
            extend {
                pointViz.draw(audio.position())
                camera.fitTo(pointViz.content.offsetEdges(100.0), 0.01, 0.01)

            }
        }
    }
}