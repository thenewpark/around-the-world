package viz

import components.RadialViz
import components.loadTilesheet
import extensions.Camera2DTranslating
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.shapes.primitives.grid
import org.openrndr.extra.viewbox.viewBox

fun main() {
    application {
        configure {
            width = 1440
            height = 720
        }
        program {
            val dataDir = "data/extracted/eple"
            val tilesheet = loadTilesheet("$dataDir/eple-tilesheet.csv", "$dataDir/eple-tilesheet.png")

            val grid = drawer.bounds.grid(2, 2).flatten()

            val vb0 = viewBox(grid[0]) {
                val camera = extend(Camera2DTranslating())
                val radialData = loadRadial("$dataDir/eple_dino_features_umap-radial-n_5-s_0.0-md_0.0.csv")
                val radialViz = RadialViz(tilesheet, radialData, width, height, this)

                radialViz.seek.listen {
                    println("seeking to: ${it.positionInSeconds}")
                }
                extend {
                    radialViz.draw(seconds)
                    drawer.fill = null
                    drawer.stroke = ColorRGBa.RED
                    drawer.rectangle(radialViz.content)
                    camera.fitTo(radialViz.content.offsetEdges(100.0), 0.01, 0.0)
                }
            }

            val vb1 = viewBox(grid[1]) {
                extend(Camera2DTranslating())
                val radialData = loadRadial("$dataDir/eple_dino_features_umap-radial-n_5-s_0.0-md_0.0.csv")
                val radialViz = RadialViz(tilesheet, radialData, width, height, this)

                radialViz.seek.listen {
                    println("seeking to: ${it.positionInSeconds}")
                }
                extend {
                    radialViz.draw(seconds)
                }
            }

            extend {
                vb0.draw()
                vb1.draw()
            }

        }
    }
}