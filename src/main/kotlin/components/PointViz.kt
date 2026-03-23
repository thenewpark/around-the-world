package components

import org.openrndr.Program
import org.openrndr.events.Event
import org.openrndr.math.Vector2
import org.openrndr.shape.bounds
import org.openrndr.shape.map
import org.openrndr.extra.kdtree.kdTree
import org.openrndr.shape.Rectangle
import kotlin.math.abs
import kotlin.math.exp

class PointViz(
    val tilesheet: Tilesheet,
    val points: List<Vector2>,
    val program: Program
) {
    val seek = Event<SeekEvent>()

    var content = program.drawer.bounds
    private val ogbounds = points.bounds
    private val remap = points.map(ogbounds, program.drawer.bounds)
    private val kdtree = remap.kdTree()

    init {
        program.mouse.buttonUp.listen {
            if (it.propagationCancelled)
                return@listen

            val mousePos = it.position
            val nearest = kdtree.findNearest(mousePos) ?: return@listen
            if (nearest.distanceTo(mousePos) < 16.0) {
                val index = remap.indexOf(nearest)
                val t = index.toDouble()/points.size.toDouble()
                val duration = tilesheet.size / tilesheet.frameRate
                seek.trigger(SeekEvent(t * duration))
                it.cancelPropagation()
            }
        }
    }

    fun draw(time: Double) {
        val frames = remap.size

        val tiles = tilesheet.image
        val tileWidth = tilesheet.width
        val tileHeight = tilesheet.height

        val activeRects = mutableListOf<Rectangle>()
        val rects = (0 until frames).map { it ->
            val iit = (it * (tilesheet.size.toDouble() / remap.size)).toInt()
            val sx = (iit * tileWidth.toDouble()).mod(tiles.width.toDouble())
            val sy = ((iit * tileWidth) / tiles.width) * tileHeight.toDouble()

            val tx = remap[it].x
            val ty = remap[it].y

            val dt = exp(4.0 * -abs(time - iit/tilesheet.frameRate)) * 0.9 + 0.1
            val dw = dt * tileWidth
            val dh = dt * tileHeight

            val r = Rectangle(sx, sy, tileWidth.toDouble(), tileHeight.toDouble()) to Rectangle.fromCenter(Vector2(tx, ty), dw, dh)

            if (dt > 0.5) {
                activeRects.add(r.second)
            }

            r
        }
        program.drawer.image(tiles, rects)

        content = if (activeRects.isNotEmpty()) activeRects.bounds else Rectangle(0.0, 0.0, 100.0, 100.0)

    }
}