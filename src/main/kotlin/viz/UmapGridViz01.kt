package viz

import audio.loadAudio
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.BlendMode
import org.openrndr.draw.loadImage
import org.openrndr.extra.camera.Camera2D
import org.openrndr.extra.color.colormatrix.tint
import org.openrndr.math.Matrix44
import org.openrndr.shape.Rectangle
import kotlin.math.abs
import kotlin.math.exp

fun main() {
    application {
        configure {
            width = 1024
            height = 1024
        }

        var viewInv = Matrix44.IDENTITY

        program {

            val audio = loadAudio("data/audio/star-guitar.ogg")
            audio.play()
            val tiles = loadImage("tilesheet.png")
            val grid = loadUmap("data/grid/layer_4_umap2d-2-5-0.0_grid.csv")
            println("number of distinct cells: ${grid.distinct().size}")
            println(grid.size)
            extend(Camera2D())

            mouse.buttonUp.listen {
                val p = (viewInv * it.position.xy01).xy
                println(p)
                for ((index, g) in grid.withIndex()) {
                    val r = Rectangle(g * 64.0, 64.0, 64.0)
                    if (p in r) {
                        println("seekied to $index")
                        val time = (index.toDouble() / grid.size) * audio.duration()
                        println(time)
                        audio.setPosition(time)
                        break
                    }
                }
            }

            extend {

                viewInv = drawer.view.inversed

                drawer.clear(ColorRGBa.BLACK)
                val frames = grid.size
                val rects = (0 until frames).map { iit ->
                    val it = (iit * (5969.0 / grid.size)).toInt()
                    //val it = iit * frames.size

                    val sx = (it * 64.0).mod(tiles.width.toDouble())
                    val sy = ((it * 64) / tiles.width) * 64.0

                    val gx = grid[iit].x * 64.0
                    val gy = grid[iit].y * 64.0

                    val seconds = audio.position()
                    val dt = exp(4.0 * -abs(seconds - it/25.0)) * 16.0 + 48.0
//                    val gx = sx
//                    val gy = sy
                    Rectangle(sx, sy, 64.0, 64.0) to Rectangle(gx+32.0 - dt/2.0, gy+32-dt/2.0, dt, dt)
                }

//                drawer.image(tiles)
                //drawer.drawStyle.colorMatrix = tint(ColorRGBa.WHITE.opacify(0.2))
//                drawer.drawStyle.blendMode = BlendMode.ADD
                drawer.image(tiles, rects)

                val rects2 = (0 until frames).map {
                    val gx = grid[it].x * 64.0
                    val gy = grid[it].y * 64.0
                    Rectangle(gx, gy, 4.0, 4.0)
                }


                drawer.stroke= ColorRGBa.PINK
                drawer.rectangles(rects2)

                drawer.defaults()
                val it =  ((audio.position()/audio.duration()) * 5969).toInt()
                val sx = (it * 64.0).mod(tiles.width.toDouble())
                val sy = ((it * 64) / tiles.width) * 64.0
                drawer.image(tiles, Rectangle(sx, sy, 64.0, 64.0),Rectangle.fromCenter(drawer.bounds.center, 256.0, 256.0) )


            }
        }
    }
}