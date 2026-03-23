package components

import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.events.Event
import org.openrndr.events.listen
import org.openrndr.extra.shapes.primitives.bounds
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle
import org.openrndr.shape.bounds
import javax.swing.Spring.height
import kotlin.math.abs
import kotlin.math.exp

class GridViz(
    val tilesheet: Tilesheet,
    val grid: List<Vector2>,
    val program: Program = Program.active!!
) {

    var content = Rectangle(0.0, 0.0, 720.0, 720.0)

    var baseSize = 0.5
    var zoomSize = 2.0

    val seek = Event<SeekEvent>()

    init {
        listOf(program.mouse.buttonUp, program.mouse.dragged).listen {
            if (it.propagationCancelled)
                return@listen


            for ((index, g) in grid.withIndex()) {
                val r = Rectangle((g+Vector2(0.5, 0.5)) * Vector2(tilesheet.width.toDouble(), tilesheet.height.toDouble()), tilesheet.width.toDouble(), tilesheet.height.toDouble())
                if (it.position in r) {
                    println(it.position)
                    println(r)
                    println(index)
                    val duration = tilesheet.size / tilesheet.frameRate
                    val time = (index.toDouble() / grid.size) * duration
                    seek.trigger(SeekEvent(time))
                    it.cancelPropagation()
                    break
                }
            }
        }
    }

    fun draw(time: Double) {
        val drawer = program.drawer
        val frames = grid.size

        val tiles = tilesheet.image
        val tileWidth = tilesheet.width
        val tileHeight = tilesheet.height

        val activeRects = mutableListOf<Rectangle>()

        val rects = (0 until frames).map { iit ->
            val it = (iit * (tilesheet.size.toDouble() / grid.size)).toInt()

            val sx = (it * tileWidth.toDouble()).mod(tiles.width.toDouble())
            val sy = ((it * tileWidth) / tiles.width) * tileHeight.toDouble()

            val gx = grid[iit].x * tileWidth.toDouble()
            val gy = grid[iit].y * tileHeight.toDouble()

            val dt = exp(4.0 * -abs(time - it / tilesheet.frameRate)) * (zoomSize - baseSize) + baseSize

            val dw = dt * tileWidth
            val dh = dt * tileHeight
            val r = Rectangle(sx, sy, tileWidth.toDouble(), tileHeight.toDouble()) to Rectangle(
                gx + tileWidth - dw / 2.0,
                gy + tileHeight - dh / 2.0,
                dw,
                dh
            )
            if (dt > baseSize*1.15) {
                activeRects.add(r.second)
            }
            r
        }
        content = if (activeRects.isNotEmpty()) activeRects.bounds else Rectangle(0.0, 0.0, 100.0, 100.0)
        drawer.image(tiles, rects)
    }
}