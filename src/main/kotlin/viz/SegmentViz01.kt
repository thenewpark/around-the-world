package viz

import components.SegmentViz
import components.loadSegments
import extensions.Camera2DTranslating
import org.openrndr.application
import org.openrndr.ffmpeg.loadVideo

fun main() {
    application {
        configure {
            width = 720
            height = 720
        }
        program {
            val camera = extend(Camera2DTranslating())

            val video = loadVideo("data/extracted/eple/eple.mp4")
            val segments = loadSegments("data/segments/segments.csv")
            val segmentViz = SegmentViz(segments)

            segmentViz.seek.listen {
                video.seek(it.positionInSeconds)
            }
            video.play()
            extend {
                video.draw(drawer)
                segmentViz.draw(video.position)
                camera.fitTo(segmentViz.content.offsetEdges(100.0), 0.01)
            }
        }
    }
}