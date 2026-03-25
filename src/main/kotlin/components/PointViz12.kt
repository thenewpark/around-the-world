package components

import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.events.Event
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.buildTransform
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.project
import org.openrndr.shape.Rectangle
import org.openrndr.shape.bounds
import kotlin.math.*

class PointViz12(
    val tilesheet: Tilesheet,
    val points: List<Vector3>,
    val program: Program
) {
    companion object {
        const val SPHERE_RADIUS = 60.0
        const val BASE_CARD_SCALE = 0.060
        const val NEIGHBOR_CARD_SCALE = 0.088
        const val CURRENT_CARD_SCALE = 0.128

        const val BASE_OUTWARD_OFFSET = 0.0
        const val NEIGHBOR_OUTWARD_OFFSET = 1.2
        const val CURRENT_OUTWARD_OFFSET = 2.8

        const val BASE_ALPHA = 0.22
        const val NEIGHBOR_ALPHA = 0.68
        const val CURRENT_ALPHA = 1.0

        const val NEIGHBOR_WINDOW = 18
        const val AUTO_ROTATION_DEGREES_PER_SECOND = 9.0
        const val AUTO_TILT_DEGREES = -14.0
        const val AUTO_WOBBLE_DEGREES = 5.0
        const val AUTO_WOBBLE_SPEED = 0.35

        const val CLICK_DISTANCE_THRESHOLD = 16.0

        const val LATITUDE_BAND_HALF_WIDTH = 0.07
        const val LATITUDE_BAND_SCALE_BOOST = 0.026
        const val LATITUDE_BAND_OUTWARD_BOOST = 2.2

        const val TEXT_RADIUS = 72.0
        const val TEXT_SPEED = -12.0
        const val TEXT_STRING = "AROUND THE WORLD "
        const val TEXT_FONT_SIZE = 64.0

        private val GOLDEN_ANGLE = PI * (3.0 - sqrt(5.0))

        fun fibonacciSpherePoints(count: Int, radius: Double): List<Vector3> = List(count) { index ->
            if (count <= 1) return@List Vector3(0.0, radius, 0.0)
            val y = 1.0 - ((index + 0.5) / count) * 2.0
            val radial = sqrt(1.0 - y * y)
            val theta = index * GOLDEN_ANGLE
            Vector3(cos(theta) * radial, y, sin(theta) * radial) * radius
        }
    }

    val seek = Event<SeekEvent>()
    var activeFrameRects = mutableListOf<Pair<Int, Rectangle>>()
    var content = program.drawer.bounds
    var latitudeBandHighlightEnabled = false

    private val duration = tilesheet.size / tilesheet.frameRate

    private val textureShader = shadeStyle {
        fragmentTransform = "x_fill *= texture(p_texture, va_texCoord0);"
        parameter("texture", tilesheet.image)
    }

    private data class SphereTile(
        val tileIndex: Int,
        val position: Vector3,
        val normal: Vector3,
        val right: Vector3,
        val up: Vector3
    )

    private val sphereTiles = points.indices.map { createSphereTile(it) }

    private val font = program.loadFont("data/fonts/PPWatch-Medium.otf", TEXT_FONT_SIZE)

    private val baseGeometry = vertexBuffer(vertexFormat {
        position(3)
        textureCoordinate(2)
    }, points.size * 6)

    private val neighborhoodGeometry = vertexBuffer(vertexFormat {
        position(3)
        textureCoordinate(2)
    }, (NEIGHBOR_WINDOW * 2 + 1) * 6)

    private val currentGeometry = vertexBuffer(vertexFormat {
        position(3)
        textureCoordinate(2)
    }, 6)

    private var animationStartedAt = program.seconds
    private var lastView = Matrix44.IDENTITY
    private var lastProjection = Matrix44.IDENTITY

    init {
        updateBaseGeometry()

        program.mouse.buttonUp.listen { event ->
            if (event.propagationCancelled) return@listen

            activeFrameRects.find { it.second.contains(event.position) }?.let { (index, _) ->
                seek.trigger(SeekEvent(index.toDouble() / tilesheet.size * duration))
                event.cancelPropagation()
                return@listen
            }

            val model = sphereTransform(animationTime())
            val modelView = lastView * model
            
            val (nearestIndex, distance) = points.indices.map { i ->
                i to project(points[i], modelView, lastProjection, program.drawer.width, program.drawer.height).xy.distanceTo(event.position)
            }.minByOrNull { it.second } ?: return@listen

            if (distance < CLICK_DISTANCE_THRESHOLD) {
                seek.trigger(SeekEvent(nearestIndex.toDouble() / points.size * duration))
                event.cancelPropagation()
            }
        }
    }

    fun reset() {
        animationStartedAt = program.seconds
        activeFrameRects.clear()
        content = program.drawer.bounds
        lastView = Matrix44.IDENTITY
        lastProjection = Matrix44.IDENTITY
    }

    fun draw(time: Double) {
        val wrappedTime = wrapTime(time)
        val currentTileIndex = currentFrameIndex(wrappedTime)
        val animationTime = animationTime()

        updateBaseGeometry(currentTileIndex)
        updateNeighborhoodGeometry(currentTileIndex)
        updateCurrentGeometry(currentTileIndex)

        val modelTransform = sphereTransform(animationTime)
        lastView = program.drawer.view
        lastProjection = program.drawer.projection
        
        program.drawer.isolated {
            model = modelTransform
            drawStyle.cullTestPass = CullTestPass.ALWAYS
            shadeStyle = textureShader
            stroke = null

            fill = ColorRGBa.WHITE.opacify(BASE_ALPHA)
            vertexBuffer(baseGeometry, DrawPrimitive.TRIANGLES)

            fill = ColorRGBa.WHITE.opacify(NEIGHBOR_ALPHA)
            vertexBuffer(neighborhoodGeometry, DrawPrimitive.TRIANGLES)

            fill = ColorRGBa.WHITE.opacify(CURRENT_ALPHA)
            vertexBuffer(currentGeometry, DrawPrimitive.TRIANGLES)
        }

        drawRotatingText(animationTime, modelTransform)

        // Update activeFrameRects for interaction
        // These are projected to 2D screen space
        activeFrameRects.clear()
        val view = program.drawer.view
        val projection = program.drawer.projection
        
        // Add neighbor tiles to activeFrameRects
        for (offset in -NEIGHBOR_WINDOW..NEIGHBOR_WINDOW) {
            val tileIndex = floorMod(currentTileIndex + offset, tilesheet.size)
            val tile = sphereTiles[tileIndex]
            
            // We use the same scale/offset as in drawing to define the hit-test rectangle
            val distance = circularDistance(tileIndex, currentTileIndex)
            val emphasis = 1.0 - distance / (NEIGHBOR_WINDOW + 1.0)
            val scale = NEIGHBOR_CARD_SCALE + emphasis * 0.02
            val outwardOffset = NEIGHBOR_OUTWARD_OFFSET + emphasis * 0.8
            
            val center2d = project(tile.position + tile.normal * outwardOffset, view * modelTransform, projection, program.drawer.width, program.drawer.height).xy
            
            val dw = scale * tilesheet.width * 2.0 // Just an estimate for the 2D size
            val dh = scale * tilesheet.height * 2.0
            
            activeFrameRects.add(tileIndex to Rectangle.fromCenter(center2d, dw, dh))
        }
        
        val currentTile = sphereTiles[currentTileIndex]
        val center2d = project(
            currentTile.position + currentTile.normal * CURRENT_OUTWARD_OFFSET,
            view * modelTransform,
            projection,
            program.drawer.width,
            program.drawer.height
        ).xy
        val dw = CURRENT_CARD_SCALE * tilesheet.width * 2.0
        val dh = CURRENT_CARD_SCALE * tilesheet.height * 2.0
        activeFrameRects.add(currentTileIndex to Rectangle.fromCenter(center2d, dw, dh))

        if (activeFrameRects.isNotEmpty()) {
            content = activeFrameRects.map { it.second }.bounds.offsetEdges(200.0)
        }
    }

    private fun wrapTime(time: Double): Double {
        if (duration <= 0.0) return 0.0
        val wrapped = time % duration
        return if (wrapped >= 0.0) wrapped else wrapped + duration
    }

    fun currentFrameIndex(time: Double): Int {
        return floorMod((time * tilesheet.frameRate).toInt(), tilesheet.size)
    }

    private fun updateBaseGeometry(currentTileIndex: Int? = null) {
        val highlightedLatitude = currentTileIndex?.let { sphereTiles[it].normal.y }

        baseGeometry.put {
            sphereTiles.forEach { tile ->
                val weight = if (latitudeBandHighlightEnabled && highlightedLatitude != null) {
                    latitudeBandWeight(tile.normal.y, highlightedLatitude)
                } else {
                    0.0
                }
                val scale = BASE_CARD_SCALE + weight * LATITUDE_BAND_SCALE_BOOST
                val outwardOffset = BASE_OUTWARD_OFFSET + weight * LATITUDE_BAND_OUTWARD_BOOST

                tileQuadVertices(tile, scale, outwardOffset).forEach { (pos, uv) ->
                    write(pos)
                    write(uv)
                }
            }
        }
    }

    private fun updateNeighborhoodGeometry(currentTileIndex: Int) {
        neighborhoodGeometry.put {
            for (offset in -NEIGHBOR_WINDOW..NEIGHBOR_WINDOW) {
                val tileIndex = floorMod(currentTileIndex + offset, tilesheet.size)
                val emphasis = 1.0 - circularDistance(tileIndex, currentTileIndex) / (NEIGHBOR_WINDOW + 1.0)
                val scale = NEIGHBOR_CARD_SCALE + emphasis * 0.02
                val outwardOffset = NEIGHBOR_OUTWARD_OFFSET + emphasis * 0.8

                tileQuadVertices(sphereTiles[tileIndex], scale, outwardOffset).forEach { (pos, uv) ->
                    write(pos)
                    write(uv)
                }
            }
        }
    }

    private fun updateCurrentGeometry(currentTileIndex: Int) {
        currentGeometry.put {
            tileQuadVertices(sphereTiles[currentTileIndex], CURRENT_CARD_SCALE, CURRENT_OUTWARD_OFFSET).forEach { (pos, uv) ->
                write(pos)
                write(uv)
            }
        }
    }

    private fun sphereTransform(time: Double) = buildTransform {
        rotate(Vector3.UNIT_Y, time * AUTO_ROTATION_DEGREES_PER_SECOND)
        rotate(Vector3.UNIT_X, AUTO_TILT_DEGREES + sin(time * AUTO_WOBBLE_SPEED) * AUTO_WOBBLE_DEGREES)
    }

    private fun createSphereTile(index: Int): SphereTile {
        val position = points[index]
        val normal = position.normalized
        val referenceUp = if (abs(normal.dot(Vector3.UNIT_Y)) > 0.94) Vector3.UNIT_Z else Vector3.UNIT_Y
        val right = referenceUp.cross(normal).normalized
        val up = normal.cross(right).normalized
        return SphereTile(index, position, normal, right, up)
    }

    private fun tileQuadVertices(tile: SphereTile, scale: Double, outwardOffset: Double): List<Pair<Vector3, Vector2>> {
        val center = tile.position + tile.normal * outwardOffset
        val right = tile.right * (0.5 * tilesheet.width.toDouble() * scale)
        val up = tile.up * (0.5 * tilesheet.height.toDouble() * scale)

        val sx = (tile.tileIndex * tilesheet.width.toDouble()) % tilesheet.image.width.toDouble()
        val sy = ((tile.tileIndex * tilesheet.width) / tilesheet.image.width) * tilesheet.height.toDouble()

        val u = sx / tilesheet.image.width.toDouble()
        val v = sy / tilesheet.image.height.toDouble()
        val du = tilesheet.width.toDouble() / tilesheet.image.width.toDouble()
        val dv = tilesheet.height.toDouble() / tilesheet.image.height.toDouble()

        return listOf(
            center - right + up to Vector2(u, 1.0 - v),
            center + right + up to Vector2(u + du, 1.0 - v),
            center + right - up to Vector2(u + du, 1.0 - (v + dv)),
            center + right - up to Vector2(u + du, 1.0 - (v + dv)),
            center - right - up to Vector2(u, 1.0 - (v + dv)),
            center - right + up to Vector2(u, 1.0 - v)
        )
    }

    private fun latitudeBandWeight(tileLatitude: Double, highlightedLatitude: Double): Double {
        val distance = abs(tileLatitude - highlightedLatitude)
        if (distance >= LATITUDE_BAND_HALF_WIDTH) {
            return 0.0
        }

        val normalized = 1.0 - (distance / LATITUDE_BAND_HALF_WIDTH)
        return normalized * normalized * (3.0 - 2.0 * normalized)
    }

    private fun circularDistance(a: Int, b: Int): Double {
        val difference = abs(a - b)
        return min(difference, tilesheet.size - difference).toDouble()
    }

    private fun floorMod(value: Int, modulo: Int): Int {
        val result = value % modulo
        return if (result >= 0) result else result + modulo
    }

    private fun animationTime(): Double = program.seconds - animationStartedAt

    private fun drawRotatingText(time: Double, modelTransform: Matrix44) {
        val text = TEXT_STRING
        val radius = TEXT_RADIUS
        val angleStep = 360.0 / text.length
        val rotation = time * TEXT_SPEED

        program.drawer.isolated {
            model = modelTransform * buildTransform { rotate(Vector3.UNIT_Y, rotation) }
            fill = ColorRGBa.WHITE
            fontMap = font

            for (i in text.indices) {
                val angle = i * angleStep
                isolated {
                    rotate(Vector3.UNIT_Y, angle)
                    translate(radius, 0.0, 0.0)
                    rotate(Vector3.UNIT_Y, -90.0)
                    rotate(Vector3.UNIT_Z, 180.0) // Upside down
                    scale(24.0 / TEXT_FONT_SIZE)
                    text(text[i].toString(), 0.0, 0.0)
                }
            }
        }
    }
}
