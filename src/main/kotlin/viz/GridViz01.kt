package viz

import components.GridViz
import components.loadTilesheet
import extensions.Camera2DTranslating
import org.openrndr.application
import org.openrndr.color.ColorRGBa

fun main() {
    application {
        configure {
            width = 720
            height = 720
        }
        program {
            val camera = extend(Camera2DTranslating())
            val dataDir = "data/extracted/eple"
            val tilesheet = loadTilesheet("$dataDir/eple-tilesheet.csv", "$dataDir/eple-tilesheet.png")

            val gridData = loadUmap("$dataDir/eple_dino_features_umap-2-5-0.0_grid.csv")
            val gridViz = GridViz(tilesheet, gridData, this)

            gridViz.seek.listen {
                println("seeking to: ${it.positionInSeconds}")
            }
            gridViz.baseSize = 0.25
            gridViz.zoomSize = 2.0
            extend {
                gridViz.draw(seconds)
                camera.fitTo(gridViz.content.offsetEdges(100.0), 0.01, 0.01)
                drawer.fill = null
                drawer.stroke = ColorRGBa.RED
                drawer.rectangle(gridViz.content)
            }
        }
    }
}