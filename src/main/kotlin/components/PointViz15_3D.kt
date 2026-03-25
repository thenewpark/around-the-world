package components

import org.openrndr.Program
import org.openrndr.draw.*
import org.openrndr.events.Event
import org.openrndr.extra.shapes.pose.pose
import org.openrndr.extra.shapes.rectify.rectified
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.shape.Path3D
import org.openrndr.shape.Rectangle
import kotlin.math.abs
import kotlin.math.exp

data class ActiveFrame(val tileIndex: Int, val frameIndex: Int, val rect: Rectangle)

class PointViz15_3D(
    val tilesheet: Tilesheet,
    path3D: Path3D,
    val program: Program,
    val focusSharpness: Double = 4.0,
    val focusScaleRange: Double = 0.9,
    val focusBaseScale: Double = 0.1,
    val zoomFactor: Double = 1.0
) {
    /** Event for external seeking/synchronization */
    val seek = Event<SeekEvent>()

    // --- Pre-calculation for Path-based Layout ---
    private val rectifiedPath = path3D.rectified()
    private val posePath = rectifiedPath.pose(Vector3.UNIT_Y)

    val numPoints = tilesheet.size

    /** Static center positions for each tile in the circular grid */
    val remap = (0 until numPoints).map { i ->
        (posePath.pose(i / numPoints.toDouble()) * Vector4(0.0, 0.0, 0.0, 1.0)).let { Vector3(it.x, it.y, it.z) }
    }

    /** Pre-calculated orientation unit vectors (right and up) for each tile */
    private val orientations = (0 until numPoints).map { i ->
        val pose = posePath.pose(i / numPoints.toDouble())
        val right = (pose * Vector4(1.0, 0.0, 0.0, 0.0)).let { v4 -> Vector3(v4.x, v4.y, v4.z) }
        val up = (pose * Vector4(0.0, 1.0, 0.0, 0.0)).let { v4 -> Vector3(v4.x, v4.y, v4.z) }
        Pair(right, up)
    }

    /** Vertex buffer for rendering all tiles efficiently */
    private val geometry = vertexBuffer(vertexFormat {
        position(3)
        textureCoordinate(2)
    }, numPoints * 6)

    /** Keeps track of which frames are currently enlarged and active */
    var activeFrameRects = mutableListOf<ActiveFrame>()


    var trackMode = false

    /**
     * Updates and draws the 3D grid of animated tiles.
     * @param time The current audio playback time in seconds.
     */
    fun draw(time: Double) {
        val drawer = program.drawer

        drawer.isolated {


            if (trackMode) {
                drawer.view = Matrix44.IDENTITY
                drawer.model = Matrix44.IDENTITY
            }

            activeFrameRects.clear()
            val tiles = tilesheet.image
            val tileWidth = tilesheet.width.toDouble()
            val tileHeight = tilesheet.height.toDouble()

            // Cache constants to avoid repeated member access in the loop
            val halfTW = 0.5 * tileWidth * zoomFactor
            val halfTH = 0.5 * tileHeight * zoomFactor
            val invTilesW = 1.0 / tiles.width.toDouble()
            val invTilesH = 1.0 / tiles.height.toDouble()
            val frameRate = tilesheet.frameRate

            geometry.put {
                for (it in 0 until numPoints) {
                    // Determine which video frame to show for this tile (looping animation)
                    val candidate = it.toDouble()
                    val iit = it + (it + time * frameRate).toInt().mod(250)

                    // Calculate scale (dt) based on distance from the current audio playhead
                    val dt = exp(focusSharpness * -abs(time - candidate / frameRate)) * focusScaleRange + focusBaseScale

                    // Use pre-calculated orientation and position
                    val (uRight, uUp) = orientations[it]
                    val position = remap[it]

                    // Scaled vectors for quad corners
                    val right = uRight * (halfTW * dt)
                    val up = uUp * (halfTH * dt)

                    // Texture coordinates calculation
                    val sx = (iit * tileWidth).mod(tiles.width.toDouble())
                    val sy = ((iit * tileWidth.toInt()) / tiles.width).toDouble() * tileHeight

                    val u = sx * invTilesW
                    val v = sy * invTilesH
                    val du = tileWidth * invTilesW
                    val dv = tileHeight * invTilesH

                    // Construct 6 vertices (2 triangles) per tile
                    write(position - right + up); write(Vector2(u, 1.0 - v))
                    write(position + right + up); write(Vector2(u + du, 1.0 - v))
                    write(position + right - up); write(Vector2(u + du, 1.0 - (v + dv)))

                    write(position + right - up); write(Vector2(u + du, 1.0 - (v + dv)))
                    write(position - right - up); write(Vector2(u, 1.0 - (v + dv)))
                    write(position - right + up); write(Vector2(u, 1.0 - v))

                    // Add to active set if significantly enlarged
                    if (dt > 0.5) {
                        activeFrameRects.add(ActiveFrame(it, iit, Rectangle.EMPTY))
                    }
                }
            }

            program.drawer.isolated {
                // Ensure tiles are visible from both sides if needed, but here we prioritize culling ALWAYS for performance
                program.drawer.drawStyle.cullTestPass = CullTestPass.ALWAYS
                program.drawer.shadeStyle = shadeStyle {
                    fragmentTransform = """
                    vec4 color = texture(p_texture, va_texCoord0); 
                    if (color.a < 0.1) discard; // Handle transparency in tilesheet
                    x_fill = color;
                """.trimIndent()
                    parameter("texture", tilesheet.image)
                }
                program.drawer.vertexBuffer(geometry, DrawPrimitive.TRIANGLES)
            }
        }
    }

    /**
     * Finds the index of the tile at the given screen position.
     * Uses the current focus/scale state to determine the clickable area of each tile.
     * 
     * @param time Current playback time (to calculate tile scales).
     * @param screenPos Mouse position in screen coordinates.
     * @param projection Current projection matrix.
     * @param view Current view matrix.
     * @param width Viewport width.
     * @param height Viewport height.
     * @return The index of the clicked tile, or null if none found.
     */
    fun pick(
        time: Double,
        screenPos: Vector2,
        projection: Matrix44,
        view: Matrix44,
        width: Double,
        height: Double
    ): Int? {
        val tileWidth = tilesheet.width.toDouble()
        val halfTW = 0.5 * tileWidth * zoomFactor
        val frameRate = tilesheet.frameRate

        var bestIdx: Int? = null
        var minDistance = Double.MAX_VALUE

        // Iterate through all tiles and find the one whose projected center is closest
        // while also being within its scaled clickable radius.
        for (i in 0 until numPoints) {
            val position = remap[i]
            val dt = exp(focusSharpness * -abs(time - i.toDouble() / frameRate)) * focusScaleRange + focusBaseScale

            // Project center to screen space
            val p = projection * (view * Vector4(position.x, position.y, position.z, 1.0))
            if (p.w <= 0) continue // Behind camera

            val ndc = Vector3(p.x / p.w, p.y / p.w, p.z / p.w)
            val screen = Vector2((ndc.x + 1.0) * 0.5 * width, (1.0 - ndc.y) * 0.5 * height)

            val dist = (screen - screenPos).length

            // Calculate a clickable radius based on the tile's size (dt)
            // Even small tiles should have a minimum clickable area (e.g. 15 pixels)
            val clickableRadius = (halfTW * dt).coerceAtLeast(15.0)

            if (dist < clickableRadius && dist < minDistance) {
                minDistance = dist
                bestIdx = i
            }
        }
        return bestIdx
    }
}
