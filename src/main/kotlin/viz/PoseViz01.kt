package viz

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorType
import org.openrndr.draw.isolated
import org.openrndr.extra.noclear.NoClear
import org.openrndr.shape.LineSegment

fun main() {
    application {
        program {
            val poses = loadPoses("data/extracted/around-the-world/around-the-world_poses2.csv")

            extend(NoClear()) {
                colorType = ColorType.FLOAT32
            }
            extend {
                drawer.isolated {
                    drawer.fill = ColorRGBa.BLACK.opacify(0.01)
                    drawer.rectangle(drawer.bounds)
                }
                drawer.stroke = ColorRGBa.PINK
                val frame = (seconds * 29.97).toInt()
                val ls = poses.filter {
                    it.frameIndex == frame
                }.map { pose ->
                    LineSegment(pose.start, pose.end)
                }
                drawer.lineSegments(ls)
            }
        }
    }
}