package viz

import components.RadialViz
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

            extend(Camera2DTranslating())

            val dataDir = "data/extracted/eple"
            val tilesheet = loadTilesheet("$dataDir/eple-tilesheet.csv", "$dataDir/eple-tilesheet.png")

            val radialData = loadRadial("$dataDir/eple_dino_features_umap-radial-n_5-s_0.0-md_0.0.csv")
            val radialViz = RadialViz(tilesheet, radialData, width, height)

            radialViz.seek.listen {
                println(it.positionInSeconds)
            }
            extend {
                radialViz.draw(seconds)
            }
        }
    }
}