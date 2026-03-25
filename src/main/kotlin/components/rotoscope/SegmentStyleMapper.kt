package components.rotoscope

import components.Segment
import org.openrndr.math.Vector2

data class RotoscopeStyle(
    val outlineLayers: Int,
    val strokeWeight: Double,
    val jitterAmount: Double,
    val alpha: Double,
    val pointStride: Int = 1,
    val contourStride: Int = 1,
    val flowBias: Vector2 = Vector2.ZERO
)

class SegmentStyleMapper {
    fun styleFor(segment: Segment?): RotoscopeStyle {
        return when (segment?.text) {
            "Mummies" -> RotoscopeStyle(
                outlineLayers = 5,
                strokeWeight = 1.95,
                jitterAmount = 1.1,
                alpha = 0.92
            )

            "Skeletons" -> RotoscopeStyle(
                outlineLayers = 6,
                strokeWeight = 0.95,
                jitterAmount = 2.8,
                alpha = 0.66
            )

            "Robots" -> RotoscopeStyle(
                outlineLayers = 3,
                strokeWeight = 1.65,
                jitterAmount = 0.8,
                alpha = 0.88,
                pointStride = 2,
                flowBias = Vector2(0.45, 0.0)
            )

            "Athletes" -> RotoscopeStyle(
                outlineLayers = 4,
                strokeWeight = 1.25,
                jitterAmount = 2.0,
                alpha = 0.8,
                flowBias = Vector2(1.4, -0.2)
            )

            "Disco girls" -> RotoscopeStyle(
                outlineLayers = 5,
                strokeWeight = 1.15,
                jitterAmount = 2.35,
                alpha = 0.78,
                flowBias = Vector2(-0.5, 0.6)
            )

            else -> RotoscopeStyle(
                outlineLayers = RotoscopeConfig.OUTLINE_LAYERS,
                strokeWeight = RotoscopeConfig.STROKE_WEIGHT,
                jitterAmount = RotoscopeConfig.JITTER_AMOUNT,
                alpha = 0.76
            )
        }
    }
}
