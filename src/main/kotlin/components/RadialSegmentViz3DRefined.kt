package components

import org.openrndr.MouseButton
import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.CullTestPass
import org.openrndr.draw.DrawPrimitive
import org.openrndr.draw.RenderTarget
import org.openrndr.draw.VertexBuffer
import org.openrndr.draw.isolated
import org.openrndr.draw.loadFont
import org.openrndr.draw.shadeStyle
import org.openrndr.draw.vertexBuffer
import org.openrndr.draw.vertexFormat
import org.openrndr.extra.camera.Orbital
import org.openrndr.math.Matrix44
import org.openrndr.math.Polar
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.project
import org.openrndr.math.transforms.buildTransform
import org.openrndr.shape.Circle
import org.openrndr.shape.LineSegment
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

class RadialSegmentViz3DRefined(
    private val tilesheet: Tilesheet,
    private val segments: List<Segment>,
    private val program: Program,
    private val orbital: Orbital,
    private val ringLegends: Map<String, String>? = null
) {
    companion object {
        private const val TRACK_STEP = 10
        private const val TRACK_BASE_RADIUS = 16.0
        private const val TRACK_RADIUS_STEP = 14.0
        private const val TRACK_HIT_THRESHOLD = 3.8
        private const val TRACK_SELECTION_TIMEOUT = 5.0

        private const val CLICK_SCREEN_DRAG_THRESHOLD = 8.0
        private const val RING_SCREEN_HIT_THRESHOLD = 6.0

        private const val DEFAULT_LINE_OPACITY = 0.82
        private const val DIMMED_LINE_OPACITY = 0.14
        private const val DEFAULT_LINE_WEIGHT = 0.65
        private const val EMPHASIZED_LINE_WEIGHT = 0.65

        private const val DEFAULT_FRAME_OPACITY = 1.0
        private const val EMPHASIZED_FRAME_OPACITY = 0.96
        private const val DIMMED_FRAME_OPACITY = 0.06

        private const val FRAME_WORLD_SCALE = 0.07
        private const val FRAME_Y_OFFSET = 0.16
        private const val FRAME_RADIAL_OFFSET = 0.32

        private const val CURRENT_FRAME_SCALE = 0.115
        private const val CURRENT_FRAME_Y_OFFSET = 0.85
        private const val CURRENT_FRAME_RADIAL_OFFSET = 0.55
        private const val CURRENT_FRAME_EMPHASIS = 1.0
        private const val CURRENT_FRAME_DIMMED = 0.14

        private const val RAY_EPSILON = 1E-6
        private const val GENERAL_LABEL = "General"
        private const val AUTO_ROTATION_DEGREES_PER_SECOND = 3.5
        private const val RING_LEGEND_Y_OFFSET = 1.05
        private const val RING_LEGEND_RADIAL_OFFSET = 1.45
    }

    private data class TrackTile(
        val tileIndex: Int,
        val position: Vector3,
        val tangent: Vector3,
        val radial: Vector3,
        val trackIndex: Int
    )

    private val textureShader = shadeStyle {
        fragmentTransform = """
            vec4 color = texture(p_texture, va_texCoord0);
            x_fill = color * x_fill;
        """.trimIndent()
        parameter("texture", tilesheet.image)
    }
    private val ringLegendFont = program.loadFont("data/fonts/PPWatch-Medium.otf", 20.0)

    private val labelToTrack = mutableMapOf<String, Int>().apply {
        for (segment in segments) {
            getOrPut(segment.text) { size }
        }
    }

    private val trackLabels = labelToTrack.entries
        .sortedBy { it.value }
        .map { it.key }

    private val segmentTilesByTrack: Map<Int, List<TrackTile>>
    private val segmentTiles: List<TrackTile>
    private val trackGeometryByTrack: Map<Int, VertexBuffer>
    private val ringWorldPathsByTrack: Map<Int, List<Vector3>>
    private val ringProjectedPathsByTrack = mutableMapOf<Int, List<Vector2>>()
    private val currentFrameGeometry = vertexBuffer(vertexFormat { position(3); textureCoordinate(2) }, 6)

    var selectedTrackIndex: Int? = null
        private set

    private var automaticTrackIndex: Int? = null

    val selectedTrackLabel: String?
        get() = effectiveTrackIndex?.let(trackLabels::get)

    private var manualActivatedTrackLabel: String? = null
    private var automaticActivatedTrackLabel: String? = null

    val activatedTrackLabel: String?
        get() = manualActivatedTrackLabel ?: automaticActivatedTrackLabel

    private var lastInteractionAt = 0.0
    private var pointerDownScreen: Vector2? = null
    private var pointerDragged = false
    private var cachedInverseProjectionView = org.openrndr.math.Matrix44.IDENTITY
    private var cameraStateReady = false
    private var rotationModel = Matrix44.IDENTITY

    init {
        val grouped = mutableMapOf<Int, MutableList<TrackTile>>()

        for (segment in segments) {
            val trackIndex = labelToTrack[segment.text] ?: continue
            val radius = trackRadius(normalizedTrackIndex(trackIndex))
            val startTile = (segment.start * tilesheet.frameRate).toInt()
            val endTile = (segment.end * tilesheet.frameRate).toInt()

            for (tileIndex in startTile until endTile step TRACK_STEP) {
                val angleDegrees = (tileIndex.toDouble() / tilesheet.size) * 360.0
                val angleRadians = Math.toRadians(angleDegrees)
                val position2D = Polar(angleDegrees, radius).cartesian
                val tangent = Vector3(-sin(angleRadians), 0.0, cos(angleRadians)).normalized
                val radial = Vector3(cos(angleRadians), 0.0, sin(angleRadians)).normalized
                val position = Vector3(position2D.x, 0.0, position2D.y)
                grouped.getOrPut(trackIndex) { mutableListOf() }.add(
                    TrackTile(tileIndex, position, tangent, radial, trackIndex)
                )
            }
        }

        segmentTilesByTrack = grouped.mapValues { (_, trackTiles) -> trackTiles.sortedBy(TrackTile::tileIndex) }
        segmentTiles = segmentTilesByTrack.values.flatten().sortedBy(TrackTile::tileIndex)
        ringWorldPathsByTrack = trackLabels.indices.associateWith { trackIndex ->
            val radius = trackRadius(normalizedTrackIndex(trackIndex))
            (0..240).map { index ->
                val angleDegrees = index / 240.0 * 360.0
                val point = Polar(angleDegrees, radius).cartesian
                Vector3(point.x, 0.0, point.y)
            }
        }
        trackGeometryByTrack = segmentTilesByTrack.mapValues { (_, trackTiles) ->
            vertexBuffer(vertexFormat { position(3); textureCoordinate(2) }, trackTiles.size * 6).also { geometry ->
                geometry.put {
                    for (trackTile in trackTiles) {
                        for ((position, uv) in planarQuadVertices(trackTile, FRAME_WORLD_SCALE, FRAME_Y_OFFSET)) {
                            write(position)
                            write(uv)
                        }
                    }
                }
            }
        }

        lastInteractionAt = program.seconds

        program.mouse.buttonDown.listen { event ->
            if (event.button == MouseButton.LEFT) {
                pointerDownScreen = event.position
                pointerDragged = false
            }
        }

        program.mouse.dragged.listen { event ->
            if (event.button == MouseButton.LEFT) {
                val down = pointerDownScreen
                if (down != null && down.distanceTo(event.position) > CLICK_SCREEN_DRAG_THRESHOLD) {
                    pointerDragged = true
                }
            }
        }

        program.mouse.buttonUp.listen { event ->
            if (event.button != MouseButton.LEFT) {
                clearPointerState()
                return@listen
            }

            val downScreen = pointerDownScreen
            val dragged = pointerDragged
            clearPointerState()

            if (downScreen == null) {
                return@listen
            }

            if (dragged) {
                return@listen
            }

            if (downScreen.distanceTo(event.position) > CLICK_SCREEN_DRAG_THRESHOLD) {
                return@listen
            }

            lastInteractionAt = program.seconds
            val hitTrackIndex = hitTrackIndexScreen(event.position)
            val clickedLabel = hitTrackIndex?.let(trackLabels::get)

            if (hitTrackIndex == null) {
                selectedTrackIndex = null
                manualActivatedTrackLabel = null
                return@listen
            }

            val toggledOff = clickedLabel == activatedTrackLabel
            if (toggledOff) {
                selectedTrackIndex = null
                manualActivatedTrackLabel = null
                return@listen
            }

            manualActivatedTrackLabel = clickedLabel
            selectedTrackIndex = hitTrackIndex
        }
    }

    fun setAutomaticActivation(label: String?) {
        automaticActivatedTrackLabel = label
        automaticTrackIndex = label?.let(labelToTrack::get)
    }

    fun resetSelection() {
        selectedTrackIndex = null
        automaticTrackIndex = null
        manualActivatedTrackLabel = null
        automaticActivatedTrackLabel = null
        lastInteractionAt = program.seconds
        clearPointerState()
    }

    fun draw(time: Double) {
        rotationModel = buildTransform {
            rotate(Vector3.UNIT_Y, time * AUTO_ROTATION_DEGREES_PER_SECOND)
        }
        cachedInverseProjectionView = (program.drawer.projection * program.drawer.view * rotationModel).inversed
        cameraStateReady = true
        updateProjectedRingPaths()
        resetSelectionIfIdle()
        drawTrackLines()
        drawTrackFrames()
        drawRingLegends()
        drawCurrentFrame(time)
        program.drawer.shadeStyle = null
        program.drawer.stroke = null
        program.drawer.fill = ColorRGBa.WHITE
    }

    private fun drawTrackLines() {
        program.drawer.isolated {
            program.drawer.defaults()
            program.drawer.ortho(RenderTarget.active)
            program.drawer.view = Matrix44.IDENTITY
            program.drawer.model = Matrix44.IDENTITY
            program.drawer.fill = null

            for (trackIndex in trackLabels.indices) {
                val projectedPath = ringProjectedPathsByTrack[trackIndex] ?: continue
                val opacity = if (isTrackEmphasized(trackIndex)) DEFAULT_LINE_OPACITY else DIMMED_LINE_OPACITY
                val lineSegments = projectedPath.zipWithNext().map { (start, end) -> LineSegment(start, end) }

                program.drawer.stroke = ColorRGBa.WHITE.opacify(opacity)
                program.drawer.strokeWeight = if (effectiveTrackIndex == trackIndex) {
                    EMPHASIZED_LINE_WEIGHT
                } else {
                    DEFAULT_LINE_WEIGHT
                }
                program.drawer.lineSegments(lineSegments)
            }
        }
    }

    private fun drawTrackFrames() {
        for ((trackIndex, geometry) in trackGeometryByTrack) {
            val opacity = frameOpacity(trackIndex)

            program.drawer.isolated {
                program.drawer.drawStyle.cullTestPass = CullTestPass.ALWAYS
                program.drawer.model = rotationModel
                program.drawer.shadeStyle = textureShader
                program.drawer.fill = ColorRGBa.WHITE.opacify(opacity)
                program.drawer.stroke = null
                program.drawer.vertexBuffer(geometry, DrawPrimitive.TRIANGLES)
            }
        }
    }

    private fun drawCurrentFrame(time: Double) {
        val currentTile = (time * tilesheet.frameRate).toInt()
        val currentEntry = segmentTiles.lastOrNull { it.tileIndex <= currentTile } ?: return
        val opacity = if (isTrackEmphasized(currentEntry.trackIndex)) {
            CURRENT_FRAME_EMPHASIS
        } else {
            CURRENT_FRAME_DIMMED
        }

        currentFrameGeometry.put {
            for ((position, uv) in elevatedQuadVertices(currentEntry, CURRENT_FRAME_SCALE, CURRENT_FRAME_Y_OFFSET)) {
                write(position)
                write(uv)
            }
        }

        program.drawer.isolated {
            program.drawer.drawStyle.cullTestPass = CullTestPass.ALWAYS
            program.drawer.model = rotationModel
            program.drawer.shadeStyle = textureShader
            program.drawer.fill = ColorRGBa.WHITE.opacify(opacity)
            program.drawer.stroke = null
            program.drawer.vertexBuffer(currentFrameGeometry, DrawPrimitive.TRIANGLES)
        }
    }

    private fun drawRingLegends() {
        val legends = ringLegends ?: return
        val projection = program.drawer.projection
        val view = program.drawer.view * rotationModel

        program.drawer.isolated {
            program.drawer.defaults()
            program.drawer.ortho(RenderTarget.active)
            program.drawer.view = Matrix44.IDENTITY
            program.drawer.model = Matrix44.IDENTITY
            program.drawer.shadeStyle = null
            program.drawer.stroke = null
            program.drawer.fontMap = ringLegendFont

            for (trackIndex in trackLabels.indices) {
                val label = trackLabels[trackIndex]
                val legend = legends[label] ?: continue
                val radius = trackRadius(normalizedTrackIndex(trackIndex)) + RING_LEGEND_RADIAL_OFFSET
                val angleDegrees = ringLegendAngleDegrees(trackIndex)
                val position2D = Polar(angleDegrees, radius).cartesian
                val worldPosition = Vector3(position2D.x, RING_LEGEND_Y_OFFSET, position2D.y)
                val screenPosition = project(
                    worldPosition,
                    projection,
                    view,
                    program.drawer.width,
                    program.drawer.height
                ).xy
                val opacity = if (isTrackEmphasized(trackIndex)) 0.92 else 0.22

                program.drawer.fill = ColorRGBa.WHITE.opacify(opacity)
                program.drawer.text(legend, screenPosition.x - legend.length * 4.0, screenPosition.y)
            }
        }
    }

    private fun planarQuadVertices(trackTile: TrackTile, scale: Double, yOffset: Double): List<Pair<Vector3, Vector2>> {
        val right = trackTile.tangent * (0.5 * tilesheet.width.toDouble() * scale)
        val up = Vector3.UNIT_Y * (0.5 * tilesheet.height.toDouble() * scale)
        val center = trackTile.position + trackTile.radial * FRAME_RADIAL_OFFSET + Vector3(0.0, yOffset, 0.0)
        return quadVertices(center, right, up, trackTile.tileIndex)
    }

    private fun elevatedQuadVertices(trackTile: TrackTile, scale: Double, yOffset: Double): List<Pair<Vector3, Vector2>> {
        val right = trackTile.tangent * (0.5 * tilesheet.width.toDouble() * scale)
        val up = Vector3.UNIT_Y * (0.5 * tilesheet.height.toDouble() * scale)
        val center = trackTile.position + trackTile.radial * CURRENT_FRAME_RADIAL_OFFSET + Vector3(0.0, yOffset, 0.0)
        return quadVertices(center, right, up, trackTile.tileIndex)
    }

    private fun quadVertices(
        center: Vector3,
        right: Vector3,
        up: Vector3,
        tileIndex: Int
    ): List<Pair<Vector3, Vector2>> {
        val sx = (tileIndex * tilesheet.width.toDouble()).mod(tilesheet.image.width.toDouble())
        val sy = ((tileIndex * tilesheet.width) / tilesheet.image.width) * tilesheet.height.toDouble()

        val u = sx / tilesheet.image.width.toDouble()
        val v = sy / tilesheet.image.height.toDouble()
        val du = tilesheet.width.toDouble() / tilesheet.image.width.toDouble()
        val dv = tilesheet.height.toDouble() / tilesheet.image.height.toDouble()

        return listOf(
            center - right + up to Vector2(u, 1.0 - (v + dv)),
            center + right + up to Vector2(u + du, 1.0 - (v + dv)),
            center + right - up to Vector2(u + du, 1.0 - v),
            center + right - up to Vector2(u + du, 1.0 - v),
            center - right - up to Vector2(u, 1.0 - v),
            center - right + up to Vector2(u, 1.0 - (v + dv))
        )
    }

    private fun hitTrackIndexScreen(screenPosition: Vector2): Int? {
        var bestTrackIndex: Int? = null
        var bestDistance = Double.POSITIVE_INFINITY

        for (trackIndex in trackLabels.indices) {
            val projectedPath = ringProjectedPathsByTrack[trackIndex] ?: continue
            val distance = distanceToClosedPolyline(screenPosition, projectedPath)
            if (distance < bestDistance) {
                bestDistance = distance
                bestTrackIndex = trackIndex
            }
        }

        return if (bestDistance <= RING_SCREEN_HIT_THRESHOLD) bestTrackIndex else null
    }

    private fun updateProjectedRingPaths() {
        if (!cameraStateReady) {
            return
        }

        val projection = program.drawer.projection
        val view = program.drawer.view * rotationModel

        for ((trackIndex, worldPath) in ringWorldPathsByTrack) {
            ringProjectedPathsByTrack[trackIndex] = worldPath.map { worldPoint ->
                project(worldPoint, projection, view, program.drawer.width, program.drawer.height).xy
            }
        }
    }

    private fun clearPointerState() {
        pointerDownScreen = null
        pointerDragged = false
    }

    private fun normalizedTrackIndex(trackIndex: Int): Double {
        return if (trackLabels.size > 1) trackIndex / (trackLabels.size - 1.0) else 0.0
    }

    private fun trackRadius(normalizedTrackIndex: Double): Double {
        return TRACK_BASE_RADIUS + normalizedTrackIndex * TRACK_RADIUS_STEP
    }

    private fun ringLegendAngleDegrees(trackIndex: Int): Double {
        val presets = listOf(210.0, 248.0, 286.0, 326.0, 18.0, 58.0, 98.0, 138.0)
        return presets.getOrElse(trackIndex) {
            210.0 + trackIndex * (360.0 / trackLabels.size.coerceAtLeast(1))
        }
    }

    private fun distanceToClosedPolyline(point: Vector2, polyline: List<Vector2>): Double {
        if (polyline.size < 2) {
            return Double.POSITIVE_INFINITY
        }

        var bestDistance = Double.POSITIVE_INFINITY
        for (index in polyline.indices) {
            val start = polyline[index]
            val end = polyline[(index + 1) % polyline.size]
            bestDistance = min(bestDistance, distanceToSegment(point, start, end))
        }
        return bestDistance
    }

    private fun distanceToSegment(point: Vector2, start: Vector2, end: Vector2): Double {
        val segment = end - start
        val lengthSquared = segment.squaredLength
        if (lengthSquared <= RAY_EPSILON) {
            return point.distanceTo(start)
        }

        val t = ((point - start).dot(segment) / lengthSquared).coerceIn(0.0, 1.0)
        val projection = start + segment * t
        return point.distanceTo(projection)
    }

    private fun frameOpacity(trackIndex: Int): Double {
        return when {
            effectiveTrackIndex == null -> DEFAULT_FRAME_OPACITY
            effectiveTrackIndex == trackIndex -> EMPHASIZED_FRAME_OPACITY
            else -> DIMMED_FRAME_OPACITY
        }
    }

    private fun isTrackEmphasized(trackIndex: Int): Boolean {
        return effectiveTrackIndex == null || effectiveTrackIndex == trackIndex
    }

    private fun resetSelectionIfIdle() {
        if (manualActivatedTrackLabel != null && program.seconds - lastInteractionAt >= TRACK_SELECTION_TIMEOUT) {
            selectedTrackIndex = null
            manualActivatedTrackLabel = null
        }
    }

    private val effectiveTrackIndex: Int?
        get() = selectedTrackIndex ?: automaticTrackIndex
}
