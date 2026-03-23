package viz

import org.openrndr.application
import org.openrndr.color.ColorRGBa

fun main() {
    application {
        configure {
            width = 768
            height = 768
        }
        program {
            val mert = loadMert("data/extracted/eple/eple_mert_4.csv")
            extend {

                val r = (mert.size / 5969.0) * 25.0

                val si = ((seconds * r).toInt() - height).coerceAtLeast(0)
                val ei = ((seconds * r).toInt()).coerceAtMost(mert.size)

                drawer.translate(0.0, -seconds * r)
                drawer.points {
                    for (i in si until ei) {
                        for (x in 0 until mert[i].size) {
                            fill = ColorRGBa.WHITE.shade(mert[i][x] * 0.5 + 0.5)
                            point(x.toDouble(), i.toDouble() + height)
                        }
                    }
                }
            }
        }
    }
}