package viz

import components.PoseViz
import extensions.Camera2DTranslating
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorType
import org.openrndr.draw.isolated
import org.openrndr.extra.noclear.NoClear
import org.openrndr.shape.LineSegment

fun main() {
    application {
        configure {
            width = 1280
            height = 720
        }
        program {
            val camera = extend(Camera2DTranslating()) {

            }
            val poses = loadPoses("data/extracted/around-the-world/around-the-world_poses2.csv")
            val poseViz = PoseViz(poses, 29.97, this)

            poseViz.minScore = 0.5
            extend {

                poseViz.draw(seconds)
                camera.fitTo(poseViz.content.offsetEdges(100.0), 0.01)
//                drawer.rectangle(poseViz.content)
            }
        }
    }
}