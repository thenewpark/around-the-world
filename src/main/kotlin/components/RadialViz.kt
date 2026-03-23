package components

import org.openrndr.Program
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.events.Event
import org.openrndr.math.Polar
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle
import org.openrndr.shape.bounds
import kotlin.math.abs
import kotlin.math.exp

class RadialViz(
    val tilesheet: Tilesheet,
    val radialData: List<Double>,
    val width: Int,
    val height: Int,
    val program: Program = Program.active!!
) {

    var content = Rectangle(0.0, 0.0, width.toDouble(), height.toDouble())

    init {
        program.mouse.buttonUp.listen {
            val duration = tilesheet.size / tilesheet.frameRate
            val t = ((it.position - Vector2(width/2.0, height/2.0)).length - 100.0) / 200.0 * duration
            if (t >= 0.0) {
                seek.trigger(SeekEvent(t))
            }
        }
    }

    val seek = Event<SeekEvent>()

    val umap = radialData.mapIndexed { index, it ->
        Polar(it, 100 + 200 * index.toDouble()/radialData.size).cartesian + Vector2(width / 2.0, height / 2.0)
    }

    fun draw(time: Double) {
        val drawer = program.drawer
        drawer.circles {
            for (i in 0 until 10) {
                fill = null
                stroke = ColorRGBa.WHITE.opacify(0.1)
                circle(drawer.bounds.center, 100.0 + 20 * i)
            }
        }

        val tiles = tilesheet.image
        val tileWidth = tilesheet.width
        val tileHeight = tilesheet.height

        val frames = radialData.size

        val activeRects = mutableListOf<Rectangle>()

        val rects = (0 until frames).map { it ->
            val iit = it
            val sx = (iit * tileWidth.toDouble()).mod(tiles.width.toDouble())
            val sy = ((iit * tileWidth) / tiles.width) * tileHeight.toDouble()
            val tx = umap[iit].x
            val ty = umap[iit].y
            val dt = exp(4.0 * -abs(time - iit / tilesheet.frameRate)) * 1.9 + 0.1
            val r = Rectangle(sx, sy, tileWidth.toDouble(), tileHeight.toDouble()) to Rectangle(tx, ty, dt * tileWidth, dt * tileHeight)
            if (dt > 0.25) {
                activeRects.add(r.second)
            }
            r
        }
        content = if (activeRects.isNotEmpty()) activeRects.bounds else Rectangle(0.0, 0.0, width.toDouble(), height.toDouble())

        drawer.image(tiles, rects)

        drawer.fill = null
        drawer.stroke = ColorRGBa.WHITE
        val duration = tilesheet.size / tilesheet.frameRate
        val t = (time / duration) * 200.0
        drawer.circle(drawer.bounds.center, 100.0 + t)
    }
}