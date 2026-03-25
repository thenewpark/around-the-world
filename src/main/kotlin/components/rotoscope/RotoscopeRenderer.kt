package components.rotoscope

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.Drawer
import org.openrndr.draw.FontMap
import org.openrndr.draw.RenderTarget
import org.openrndr.draw.isolated
import org.openrndr.math.Vector2
import org.openrndr.math.Matrix44
import org.openrndr.shape.Rectangle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class RotoscopeViewMode {
    VIDEO,
    MASK,
    BINARY_MASK,
    ROTOSCOPE
}

class RotoscopeRenderer(
    private val font: FontMap
) {
    fun drawPreview(drawer: Drawer, image: ColorBuffer, background: ColorRGBa = RotoscopeConfig.BACKGROUND_COLOR) {
        drawer.clear(background)
        val target = fitRectangle(image.width, image.height, drawer.width, drawer.height)
        drawer.image(image, target)
    }

    fun drawBinaryMask(drawer: Drawer, frame: AnalyzedFrame) {
        drawer.isolated {
            defaults()
            ortho(RenderTarget.active)
            view = Matrix44.IDENTITY
            model = Matrix44.IDENTITY
            clear(RotoscopeConfig.BACKGROUND_COLOR)

            val target = fitRectangle(frame.sourceWidth, frame.sourceHeight, width, height)
            val scale = target.width / frame.sourceWidth

            stroke = null
            fill = RotoscopeConfig.MASK_FOREGROUND_COLOR

            for (y in 0 until frame.mask.height) {
                for (x in 0 until frame.mask.width) {
                    if (!frame.mask.isForeground(x, y)) {
                        continue
                    }

                    rectangle(
                        target.x + x * frame.mask.cellSize * scale,
                        target.y + y * frame.mask.cellSize * scale,
                        frame.mask.cellSize * scale,
                        frame.mask.cellSize * scale
                    )
                }
            }
        }
    }

    fun drawRotoscope(drawer: Drawer, frame: AnalyzedFrame, style: RotoscopeStyle) {
        drawRotoscopeLayer(
            drawer = drawer,
            frame = frame,
            style = style,
            clearBackground = true,
            backgroundColor = RotoscopeConfig.BACKGROUND_COLOR,
            lineColor = RotoscopeConfig.LINE_COLOR
        )
    }

    fun drawRotoscopeLayer(
        drawer: Drawer,
        frame: AnalyzedFrame,
        style: RotoscopeStyle,
        targetBounds: Rectangle? = null,
        clipInsetPx: Double = 0.0,
        clearBackground: Boolean = false,
        backgroundColor: ColorRGBa = RotoscopeConfig.BACKGROUND_COLOR,
        lineColor: ColorRGBa = RotoscopeConfig.LINE_COLOR
    ) {
        drawer.isolated {
            defaults()
            ortho(RenderTarget.active)
            view = Matrix44.IDENTITY
            model = Matrix44.IDENTITY

            if (clearBackground) {
                clear(backgroundColor)
            }

            val target = targetBounds ?: fitRectangle(frame.sourceWidth, frame.sourceHeight, width, height)
            val scale = target.width / frame.sourceWidth
            val offset = Vector2(target.x, target.y)
            if (clipInsetPx > 0.0) {
                drawStyle.clip = Rectangle(
                    target.x + clipInsetPx,
                    target.y + clipInsetPx,
                    (target.width - clipInsetPx * 2.0).coerceAtLeast(0.0),
                    (target.height - clipInsetPx * 2.0).coerceAtLeast(0.0)
                )
            }

            fill = null

            repeat(style.outlineLayers) { layer ->
                val layerAlpha = style.alpha * (1.0 - layer / maxOf(1.0, style.outlineLayers.toDouble() + 1.0) * 0.45)
                stroke = lineColor.opacify(layerAlpha.coerceIn(0.0, 1.0))
                strokeWeight = style.strokeWeight * (1.0 - layer * 0.06).coerceAtLeast(0.7)

                frame.contours.forEachIndexed { contourIndex, contour ->
                    if (contourIndex % style.contourStride != 0) {
                        return@forEachIndexed
                    }

                    val points = contour.points
                        .filterIndexed { pointIndex, _ -> pointIndex % style.pointStride == 0 }
                        .mapIndexed { pointIndex, point ->
                            toScreen(
                                jitterPoint(point, frame.frameIndex, layer, pointIndex, style) * scale,
                                offset
                            )
                        }

                    if (points.size >= 2) {
                        lineStrip(points)
                    }
                }
            }
        }
    }

    fun drawRotoscopeLayerInBounds(
        drawer: Drawer,
        frame: AnalyzedFrame,
        style: RotoscopeStyle,
        targetBounds: Rectangle,
        clipInsetPx: Double = 0.0,
        clearBackground: Boolean = false,
        backgroundColor: ColorRGBa = RotoscopeConfig.BACKGROUND_COLOR,
        lineColor: ColorRGBa = RotoscopeConfig.LINE_COLOR
    ) {
        drawRotoscopeLayer(
            drawer = drawer,
            frame = frame,
            style = style,
            targetBounds = targetBounds,
            clipInsetPx = clipInsetPx,
            clearBackground = clearBackground,
            backgroundColor = backgroundColor,
            lineColor = lineColor
        )
    }

    fun drawDebugPanel(
        drawer: Drawer,
        time: Double,
        frameIndex: Int,
        segmentLabel: String,
        contourCount: Int,
        foregroundCount: Int,
        viewMode: RotoscopeViewMode
    ) {
        drawer.isolated {
            defaults()
            ortho(RenderTarget.active)
            view = Matrix44.IDENTITY
            model = Matrix44.IDENTITY
            fontMap = font
            stroke = null
            fill = RotoscopeConfig.DEBUG_PANEL_COLOR
            rectangle(
                RotoscopeConfig.DEBUG_MARGIN,
                RotoscopeConfig.DEBUG_MARGIN,
                380.0,
                122.0
            )

            fill = RotoscopeConfig.DEBUG_TEXT_COLOR
            text("mode ${viewMode.name.lowercase()}", 40.0, 56.0)
            text("time ${"%.2f".format(time)}s", 40.0, 78.0)
            text("frame $frameIndex", 40.0, 100.0)
            text("segment $segmentLabel", 40.0, 122.0)
            text("contours $contourCount  mask $foregroundCount", 40.0, 144.0)
        }
    }

    fun drawHelp(drawer: Drawer) {
        drawer.isolated {
            defaults()
            ortho(RenderTarget.active)
            view = Matrix44.IDENTITY
            model = Matrix44.IDENTITY
            fontMap = font
            fill = RotoscopeConfig.DEBUG_TEXT_COLOR
            stroke = null
            text("1 video  2 mask  3 binary  4 rotoscope  d debug  left/right seek", 24.0, height - 24.0)
        }
    }

    private fun fitRectangle(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Rectangle {
        val scale = min(targetWidth / sourceWidth.toDouble(), targetHeight / sourceHeight.toDouble())
        val width = sourceWidth * scale
        val height = sourceHeight * scale
        val x = (targetWidth - width) * 0.5
        val y = (targetHeight - height) * 0.5
        return Rectangle(x, y, width, height)
    }

    private fun jitterPoint(
        point: Vector2,
        frameIndex: Int,
        layer: Int,
        pointIndex: Int,
        style: RotoscopeStyle
    ): Vector2 {
        val phase = frameIndex * 0.11 + layer * 1.7 + pointIndex * 0.09
        val x = sin(phase) + 0.5 * sin(phase * 0.63 + 1.7)
        val y = cos(phase * 0.87 + 0.9) + 0.5 * cos(phase * 0.41 + 2.6)
        val amount = style.jitterAmount * (0.45 + layer * 0.18)
        return point + Vector2(x, y) * amount + style.flowBias * layer.toDouble()
    }

    private fun toScreen(point: Vector2, offset: Vector2): Vector2 {
        return Vector2(point.x + offset.x, point.y + offset.y)
    }
}
