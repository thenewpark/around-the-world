package components

import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolated
import org.openrndr.math.Vector2
import org.openrndr.shape.LineSegment
import org.openrndr.shape.bounds
import viz.Pose

class PoseViz(
    val poses: List<Pose>,
    val frameRate: Double,
    val program: Program
) {
    var content = program.drawer.bounds
    var stroke = ColorRGBa.PINK

    var minScore = 0.0
    fun draw(time: Double) {
        program.drawer.isolated {

            val activePoints = mutableListOf<Vector2>()

            val frame = (time * frameRate).toInt()
            val ls = poses.filter {
                it.frameIndex == frame && it.startScore >= minScore && it.endScore >= minScore

            }.map { pose ->
                activePoints.add(pose.start)
                activePoints.add(pose.end)
                LineSegment(pose.start, pose.end)
            }
            if (activePoints.isNotEmpty()) {
                content = activePoints.bounds
            } else {
                content = program.drawer.bounds
            }
            program.drawer.stroke = this@PoseViz.stroke
            program.drawer.lineSegments(ls)
        }
    }
}
