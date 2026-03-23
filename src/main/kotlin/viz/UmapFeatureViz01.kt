package viz

import org.openrndr.application
import org.openrndr.draw.loadImage
import org.openrndr.shape.Rectangle
import org.openrndr.shape.bounds
import org.openrndr.shape.map

fun main() {
    application {
        configure {
            width = 720
            height = 720
        }
        program {
            val umap = loadUmap("data/umap/other_layer_4_features_-2-3-125.0.csv")

            val ogbounds = umap.bounds
            val remap = umap.map(ogbounds, drawer.bounds)



            extend {

                drawer.circles(remap, 5.0)
            }
        }
    }
}