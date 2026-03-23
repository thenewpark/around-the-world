package viz

import org.openrndr.application
import org.openrndr.extra.noise.hammersley.radicalInverse
import org.openrndr.shape.bounds
import org.openrndr.shape.map
import kotlin.math.sqrt

fun main() {
    application {
        configure {
            width = 720
            height = 720
        }
        program {
            val mert = loadMert("data/umap/other_layer_4.csv")

            for (x in 0 until mert[0].size) {
                val values = (0 until mert.size).map { mert[it][x] }.sorted()
                val min = values[0]
                val max = values[values.size - 1]
                for (i in 0 until mert.size) {
                    mert[i][x] = (mert[i][x] - min) / (max - min)
                }

            }
            val umap = loadUmap("data/umap/other_layer_4_features_-2-3-125.0.csv")

            val ogbounds = umap.bounds
            val remap = umap.map(ogbounds, drawer.bounds)

            extend {
                val r = (mert.size / 5969.0) * 25.0
                val idx = (seconds*r).toInt() % mert.size
                val radii = mert[idx].map { sqrt(it * 500.0) }
                drawer.circles(remap, radii)
            }
        }
    }
}