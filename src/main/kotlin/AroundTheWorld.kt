import audio.VorbisTrack
import audio.loadMultiAudio
import components.CharacterSequenceRotoscopeOutlineBackground
import components.ObjectDetectionBackground
import components.PointViz12
import components.PointViz15_3D
import components.Pose
import components.PreviewWindow
import components.RadialSegmentViz3DRefined
import components.Segment
import components.Tilesheet
import components.loadPoses
import components.loadSegments
import components.loadTilesheet
import org.openrndr.CharacterEvent
import org.openrndr.Extension
import org.openrndr.ExtensionStage
import org.openrndr.GestureEvents
import org.openrndr.KEY_SPACEBAR
import org.openrndr.KeyEvent
import org.openrndr.KeyEvents
import org.openrndr.MouseButton
import org.openrndr.MouseEvent
import org.openrndr.MouseEvents
import org.openrndr.PinchEvent
import org.openrndr.PointerEvent
import org.openrndr.PointerEvents
import org.openrndr.Program
import org.openrndr.Fullscreen
import org.openrndr.KEY_BACKSPACE
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.CullTestPass
import org.openrndr.draw.DrawPrimitive
import org.openrndr.draw.Drawer
import org.openrndr.draw.MagnifyingFilter
import org.openrndr.draw.MinifyingFilter
import org.openrndr.draw.RenderTarget
import org.openrndr.draw.VertexElementType
import org.openrndr.draw.isolated
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.loadFont
import org.openrndr.draw.renderTarget
import org.openrndr.draw.shadeStyle
import org.openrndr.draw.vertexBuffer
import org.openrndr.draw.vertexFormat
import org.openrndr.events.Event
import org.openrndr.extra.camera.Orbital
import org.openrndr.extra.color.colormatrix.tint
import org.openrndr.extra.shapes.path3d.toPath3D
import org.openrndr.extra.shapes.pose.pose
import org.openrndr.extra.shapes.rectify.rectified
import org.openrndr.extra.textwriter.writer
import org.openrndr.ffmpeg.PlayMode
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import org.openrndr.ffmpeg.loadVideo
import org.openrndr.ffmpeg.probeVideo
import org.openrndr.math.Matrix44
import org.openrndr.math.Polar
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.buildTransform
import org.openrndr.math.transforms.project
import org.openrndr.shape.Circle
import org.openrndr.shape.Path3D
import org.openrndr.shape.Rectangle
import org.openrndr.shape.Segment3D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.tan

private const val SCENE_TRANSITION_DURATION = 0.6
private const val SCREEN_AUDIO_GAIN_ACTIVE = 1.0
private const val SCREEN_AUDIO_GAIN_DIMMED = 0.18

private enum class ScreenAudioStem {
    BASS,
    DRUMS,
    VOCALS,
    OTHER
}

private enum class ScreenBackgroundMode {
    ROTOSCOPE,
    OBJECT_DETECTION
}

private object PointViz19SceneConfig {
    const val RADIUS = 300.0
    const val HUB_TILE_SCALE = 1.0
    const val DATA_DIR = "data/extracted/around-the-world"
    const val SEGMENTS_CSV = "data/segments/segments-people.csv"
    val BACKGROUND_COLOR = ColorRGBa.BLACK
    val LINK_COLOR_BACKGROUND = ColorRGBa.WHITE.opacify(0.6)
    val LINK_COLOR_ACTIVE = ColorRGBa.WHITE
    val TEXT_COLOR = ColorRGBa.WHITE
    const val OPACITY_BACKGROUND_LINKS = 0.05
    const val OPACITY_ACTIVE_LINKS = 0.6
    const val FONT_PATH = "data/fonts/PPWatch-Medium.otf"
    const val FONT_SIZE = 24.0
    const val FOCUS_SHARPNESS = 4.0
    const val FOCUS_SCALE_RANGE = 0.9
    const val FOCUS_BASE_SCALE = 0.1
    val INITIAL_EYE = Vector3(0.0, 400.0, 600.0)
    val LOOK_AT = Vector3.ZERO
    const val FOLLOW_DISTANCE = 350.0
    const val TRACK_SPEED = 0.1
    const val ACTIVE_FRAME_PADDING = 48.0
}

private fun trackOrbitalCameraToActiveFrames(
    camera: Orbital,
    activePositions: List<Vector3>,
    baseEye: Vector3,
    baseLookAt: Vector3,
    minDistance: Double,
    padding: Double,
    blend: Double
) {
    if (activePositions.isEmpty()) {
        return
    }

    val centroid = activePositions.reduce { acc, vector -> acc + vector } / activePositions.size.toDouble()
    val viewOffset = baseEye - baseLookAt
    val viewDirection = if (viewOffset.length > 1E-6) {
        viewOffset.normalized
    } else {
        Vector3.UNIT_Z
    }
    val clusterRadius = activePositions.maxOf { (it - centroid).length } + padding
    val halfFovRadians = Math.toRadians(camera.fov.coerceAtLeast(1.0)) * 0.5
    val fittedDistance = if (halfFovRadians > 1E-6) {
        clusterRadius / tan(halfFovRadians)
    } else {
        minDistance
    }
    val targetEye = centroid + viewDirection * max(minDistance, fittedDistance)

    camera.lookAt = camera.lookAt * (1.0 - blend) + centroid * blend
    camera.eye = camera.eye * (1.0 - blend) + targetEye * blend
}

private val SCREEN_TRACK_TO_STEM = mapOf(
    "Mummies" to ScreenAudioStem.DRUMS,
    "Robots" to ScreenAudioStem.VOCALS,
    "Athletes" to ScreenAudioStem.BASS,
    "Disco girls" to ScreenAudioStem.OTHER,
    "Skeletons" to ScreenAudioStem.OTHER
)

private fun applyScreenAudioMix(
    selectedLabel: String?,
    stemTracks: Map<ScreenAudioStem, VorbisTrack>
) {
    val emphasizedStem = selectedLabel?.let(SCREEN_TRACK_TO_STEM::get)

    stemTracks.forEach { (stem, track) ->
        track.gain = when {
            emphasizedStem == null -> SCREEN_AUDIO_GAIN_ACTIVE
            stem == emphasizedStem -> SCREEN_AUDIO_GAIN_ACTIVE
            else -> SCREEN_AUDIO_GAIN_DIMMED
        }
    }
}

private class SharedSoundtrack(val video: VideoPlayerFFMPEG) {

    init {
        require(instances == 0) { "HIGHLANDER THERE CAN BE ONLY ONE!1" }
        instances++
    }

    private val audio = loadMultiAudio(
        "data/audio/bass.ogg",
        "data/audio/drums.ogg",
        "data/audio/vocals.ogg",
        "data/audio/other.ogg"
    )
    private val stemTracks = mapOf(
        ScreenAudioStem.BASS to audio.tracks[0],
        ScreenAudioStem.DRUMS to audio.tracks[1],
        ScreenAudioStem.VOCALS to audio.tracks[2],
        ScreenAudioStem.OTHER to audio.tracks[3]
    )
    private var started = false

    fun ensurePlaying() {
        if (!started) {
            applyScreenAudioMix(null, stemTracks)
            audio.play()
            started = true
        }
    }

    fun position(): Double = audio.position()


    fun duration(): Double = audio.tracks[0].duration()

    fun seek(timeInSeconds: Double) {
        video.seek(timeInSeconds)
        audio.setPosition(timeInSeconds)
    }

    fun resetMix() {
        applyScreenAudioMix(null, stemTracks)
    }

    fun emphasize(label: String?) {
        applyScreenAudioMix(label, stemTracks)
    }

    companion object {
        var instances = 0
    }
}

private class SceneMouseEvents : MouseEvents {
    override var position = Vector2.ZERO
    override val buttonDown = Event<MouseEvent>()
    override val buttonUp = Event<MouseEvent>()
    override val dragged = Event<MouseEvent>()
    override val moved = Event<MouseEvent>()
    override val scrolled = Event<MouseEvent>()
    override val entered = Event<MouseEvent>()
    override val exited = Event<MouseEvent>()
}

private class SceneKeyEvents : KeyEvents {
    override val keyDown = Event<KeyEvent>()
    override val keyUp = Event<KeyEvent>()
    override val keyRepeat = Event<KeyEvent>()
    override val character = Event<CharacterEvent>()
}

private class ScenePointerEvents : PointerEvents {
    override val pointerDown = Event<PointerEvent>()
    override val pointerUp = Event<PointerEvent>()
    override val moved = Event<PointerEvent>()
    override val cancelled = Event<PointerEvent>()
}

private class SceneGestureEvents : GestureEvents {
    override val pinchStarted = Event<PinchEvent>()
    override val pinchUpdated = Event<PinchEvent>()
    override val pinchEnded = Event<PinchEvent>()
}

private class SceneStageExtension(
    private val stage: ExtensionStage,
    private val userDraw: Program.() -> Unit
) : Extension {
    override var enabled = true

    override fun setup(program: Program) {
        if (stage == ExtensionStage.SETUP) {
            program.userDraw()
        }
    }

    override fun beforeDraw(drawer: Drawer, program: Program) {
        if (stage == ExtensionStage.BEFORE_DRAW) {
            program.userDraw()
        }
    }

    override fun afterDraw(drawer: Drawer, program: Program) {
        if (stage == ExtensionStage.AFTER_DRAW) {
            program.userDraw()
        }
    }
}

private class SceneProgram(private val parent: Program) : Program by parent {
    override val mouse: MouseEvents = SceneMouseEvents()
    override val keyboard: KeyEvents = SceneKeyEvents()
    override val pointers: PointerEvents = ScenePointerEvents()
    override val gestures: GestureEvents = SceneGestureEvents()
    override val extensions = mutableListOf<Extension>()

    override fun <T : Extension> extend(extension: T): T {
        extensions.add(extension)
        extension.setup(this)
        return extension
    }

    override fun <T : Extension> extend(extension: T, configure: T.() -> Unit): T {
        extensions.add(extension)
        extension.configure()
        extension.setup(this)
        return extension
    }

    override fun extend(stage: ExtensionStage, userDraw: Program.() -> Unit) {
        extensions.add(SceneStageExtension(stage, userDraw))
    }

    fun dispatchMouseMoved(event: MouseEvent) {
        (mouse as SceneMouseEvents).position = event.position
        mouse.moved.trigger(event)
    }

    fun dispatchMouseDown(event: MouseEvent) {
        (mouse as SceneMouseEvents).position = event.position
        mouse.buttonDown.trigger(event)
    }

    fun dispatchMouseDragged(event: MouseEvent) {
        (mouse as SceneMouseEvents).position = event.position
        mouse.dragged.trigger(event)
    }

    fun dispatchMouseUp(event: MouseEvent) {
        (mouse as SceneMouseEvents).position = event.position
        mouse.buttonUp.trigger(event)
    }

    fun dispatchMouseScrolled(event: MouseEvent) {
        (mouse as SceneMouseEvents).position = event.position
        mouse.scrolled.trigger(event)
    }

    fun dispatchPointerMoved(event: PointerEvent) {
        pointers.moved.trigger(event)
    }

    fun dispatchPointerDown(event: PointerEvent) {
        pointers.pointerDown.trigger(event)
    }

    fun dispatchPointerUp(event: PointerEvent) {
        pointers.pointerUp.trigger(event)
    }

    fun dispatchPointerCancelled(event: PointerEvent) {
        pointers.cancelled.trigger(event)
    }

    fun dispatchKeyDown(event: KeyEvent) {
        keyboard.keyDown.trigger(event)
    }

    fun dispatchKeyUp(event: KeyEvent) {
        keyboard.keyUp.trigger(event)
    }

    fun dispatchKeyRepeat(event: KeyEvent) {
        keyboard.keyRepeat.trigger(event)
    }

    fun dispatchCharacter(event: CharacterEvent) {
        keyboard.character.trigger(event)
    }

    fun drawWithExtensions(drawContent: () -> Unit) {
        val enabledExtensions = extensions.filter { it.enabled }
        enabledExtensions.forEach { it.beforeDraw(drawer, this) }
        try {
            drawContent()
        } finally {
            enabledExtensions.asReversed().forEach { it.afterDraw(drawer, this) }
        }
    }
}

private interface ScreenScene {
    val name: String
    val program: SceneProgram
    fun activate()
    fun deactivate()
    fun reset()
    fun draw(time: Double)
}

class VideoPointViz3D13(
    val tilesheet: Tilesheet,
    val segments: List<Segment>,
    val path3D: Path3D, val program: Program,
    val videoImage: ColorBuffer,
    val poses: List<Pose>,
    val worldYOffset: Double = 0.0
) {
    val posePath = path3D.rectified().pose(Vector3.UNIT_Y)
    val geometry = vertexBuffer(vertexFormat {
        position(3); textureCoordinate(2); attribute(
        "opacity",
        VertexElementType.FLOAT32
    )
    }, tilesheet.size * 6)
    val numPoints = 100 //(tilesheet.size / 200).coerceAtLeast(1) // Number of points on the path

    val ypositions = DoubleArray(numPoints) { 0.0 }
    val font = program.loadFont("data/fonts/PPWatch-Medium.otf", 20.0)

    val projectedPoints = mutableListOf<Vector2>()
    val sheetPositions = mutableListOf<Vector3>()
    val sheetProjectedPoints = mutableListOf<Vector2>()

    var zoomedIndex = -1
    var zoomFactor = 0.0
    var targetLookAt = Vector3.ZERO
    var targetEye = Vector3(0.0, 90.0, 180.0)

    private var lastZoomedIndex = -1
    private var zoomStartTime = 0.0

    fun reset() {
        ypositions.fill(0.0)
        projectedPoints.clear()
        sheetPositions.clear()
        sheetProjectedPoints.clear()
        zoomedIndex = -1
        zoomFactor = 0.0
        targetLookAt = Vector3.ZERO
        targetEye = Vector3(0.0, 90.0, 180.0)
        lastZoomedIndex = -1
        zoomStartTime = program.seconds
    }

    fun draw(time: Double, currentZoomedIndex: Int = -1, zoom: Double = 0.0) {
        if (currentZoomedIndex != lastZoomedIndex) {
            if (currentZoomedIndex != -1) {
                zoomStartTime = program.seconds
            }
            lastZoomedIndex = currentZoomedIndex
        }

        val activeSegment = segments.find { it.start <= time && time <= it.end }

        val activeLabel = activeSegment?.text ?: ""

        val drawer = program.drawer
        val viewInverse = drawer.view.inversed
        val cameraRight = (viewInverse * Vector4.UNIT_X).xyz
        val cameraUp = (viewInverse * Vector4.UNIT_Y).xyz
        val tiles = tilesheet.image
        val tileWidth = tilesheet.width
        val tileHeight = tilesheet.height

        val duration = tilesheet.size / tilesheet.frameRate

        val delayInSeconds = duration / numPoints

        val anyFrameTriggered = (0 until numPoints).any { i ->
            val delayFactor = tilesheet.frameRate * delayInSeconds
            val frameOffsetConstant = (i * delayFactor).toInt()
            val frameIndexConstant = (frameOffsetConstant).mod(tilesheet.size)
            val frameTime = frameIndexConstant / tilesheet.frameRate
            val frameSegment = segments.find { it.start <= frameTime && frameTime <= it.end }
            val isFrameSegmentTheActiveSegment = frameSegment?.text == activeLabel
            val segmentStartTime = activeSegment?.start ?: 0.0
            val delayPerPoint = 4.0
            val individualTriggerTime = segmentStartTime + (i / numPoints.toDouble()) * delayPerPoint

            isFrameSegmentTheActiveSegment && frameSegment?.text != "All" && time >= individualTriggerTime
        }

        var activeSheets = 0
        geometry.put {
            for (i in 0 until numPoints) {
                val pathT = i / numPoints.toDouble()
                val pose = posePath.pose(pathT)

                // Calculate animated tile index with a delay based on the point index i
                val delayFactor = tilesheet.frameRate * delayInSeconds  // delay in frames between points
                val frameOffset = (time * tilesheet.frameRate + i * delayFactor).toInt()
                val frameIndex = (frameOffset).mod(tilesheet.size)

                val frameOffsetConstant = (i * delayFactor).toInt()
                val frameIndexConstant = (frameOffsetConstant).mod(tilesheet.size)

                val frameTime = frameIndexConstant / tilesheet.frameRate

                val frameSegment = segments.find { it.start <= frameTime && frameTime <= it.end }

                val isFrameSegmentTheActiveSegment = frameSegment?.text == activeLabel

                // Staircase effect: add an offset to the segment trigger based on the point index i
                val delayPerPoint = 4.0 // time in seconds for the full sequence to trigger
                val segmentStartTime = activeSegment?.start ?: 0.0
                val individualTriggerTime = segmentStartTime + (i / numPoints.toDouble()) * delayPerPoint



                if (isFrameSegmentTheActiveSegment && frameSegment?.text != "All" && time >= individualTriggerTime) {

                    ypositions[i] = ypositions[i] * 0.95 + 0.02 * activeSheets
                    activeSheets++
                } else {
                    ypositions[i] = ypositions[i] * 0.95
                }

                val reposition = pose * buildTransform { translate(0.0, ypositions[i] * -5.0, 0.0) }
                val position = (reposition * Vector4(0.0, 0.0, 0.0, 1.0)).xyz + Vector3(0.0, worldYOffset, 0.0)

                val BPM = 121.3
                val beat = (time * BPM / 60.0).mod(1.0)
                val beatFactor = 1.0 + 0.2 * exp(-5.0 * beat)

                // If the sheet is active (triggered and moving up), stop the BPM (set factor to 1.0)
                val beatScale = if (ypositions[i] > 0.1) 1.0 else beatFactor

                var currentScale = 0.1 * beatScale

                var right = Vector3.ZERO
                var up = Vector3.ZERO

                val isSelected = i == currentZoomedIndex

                if (currentZoomedIndex != -1) {
                    if (isSelected) {
                        currentScale = (0.1 * beatScale) * (1.0 - zoom)
                    } else {
                        currentScale = (0.1 * beatScale) * (1.0 - zoom)
                    }
                    right = (reposition * Vector4.UNIT_X).xyz * (0.5 * tilesheet.width.toDouble()) * currentScale
                    up = (reposition * Vector4.UNIT_Y).xyz * (0.5 * tilesheet.height.toDouble()) * currentScale
                } else {
                    right = (reposition * Vector4.UNIT_X).xyz * (0.5 * tilesheet.width.toDouble()) * currentScale
                    up = (reposition * Vector4.UNIT_Y).xyz * (0.5 * tilesheet.height.toDouble()) * currentScale
                }

                val isTriggered =
                    isFrameSegmentTheActiveSegment && frameSegment?.text != "All" && time >= individualTriggerTime
                val targetOpacity = if (isTriggered) 1.0 else (if (anyFrameTriggered) 0.06 else 1.0)
                val currentOpacity = if (currentZoomedIndex != -1) {
                    if (isSelected) (0.09 * (1.0 - zoom)) else (targetOpacity * (1.0 - zoom))
                } else {
                    targetOpacity
                }

                val sx = (frameIndex * tileWidth.toDouble()).mod(tiles.width.toDouble())
                val sy = ((frameIndex * tileWidth) / tiles.width) * tileHeight.toDouble()

                val u = sx / tiles.width.toDouble()
                val v = sy / tiles.height.toDouble()
                val du = tilesheet.width.toDouble() / tiles.width.toDouble()
                val dv = tilesheet.height.toDouble() / tiles.height.toDouble()

                write(position - right + up); write(Vector2(u, 1.0 - (v + dv))); write(currentOpacity.toFloat())
                write(position + right + up); write(Vector2(u + du, 1.0 - (v + dv))); write(currentOpacity.toFloat())
                write(position + right - up); write(Vector2(u + du, 1.0 - (v))); write(currentOpacity.toFloat())

                write(position + right - up); write(Vector2(u + du, 1.0 - (v))); write(currentOpacity.toFloat())
                write(position - right - up); write(Vector2(u, 1.0 - (v))); write(currentOpacity.toFloat())
                write(position - right + up); write(Vector2(u, 1.0 - (v + dv))); write(currentOpacity.toFloat())
            }
        }

        sheetPositions.clear()
        sheetProjectedPoints.clear()
        for (i in 0 until numPoints) {
            val pathT = i / numPoints.toDouble()
            val pose = posePath.pose(pathT)
            val reposition = pose * buildTransform { translate(0.0, ypositions[i] * -5.0, 0.0) }
            val position = (reposition * Vector4(0.0, 0.0, 0.0, 1.0)).xyz + Vector3(0.0, worldYOffset, 0.0)
            sheetPositions.add(position)
            val pp = project(position, drawer.projection, drawer.view * drawer.model, drawer.width, drawer.height).xy
            sheetProjectedPoints.add(pp)
        }

        drawer.isolated {
            drawer.drawStyle.cullTestPass = CullTestPass.ALWAYS
            drawer.shadeStyle = shadeStyle {
                fragmentTransform = """
                    vec4 color = texture(p_texture, va_texCoord0);
                    color.a *= va_opacity;
                    x_fill = color;
                """.trimIndent()
                parameter("texture", tilesheet.image)
            }
            drawer.vertexBuffer(geometry, DrawPrimitive.TRIANGLES)
        }

        if (currentZoomedIndex != -1 && zoom > 0.5) {
            val pathT = currentZoomedIndex / numPoints.toDouble()
            val pose = posePath.pose(pathT)
            val reposition = pose * buildTransform { translate(0.0, ypositions[currentZoomedIndex] * -5.0, 0.0) }
            val position = (reposition * Vector4(0.0, 0.0, 0.0, 1.0)).xyz + Vector3(0.0, worldYOffset, 0.0)

            val delayFactor = tilesheet.frameRate * delayInSeconds
            val frameOffset = (time * tilesheet.frameRate + currentZoomedIndex * delayFactor).toInt()
            val frameIndex = (frameOffset).mod(tilesheet.size)

            val targetScale = 0.5
            val cameraRight = (viewInverse * Vector4.UNIT_X).xyz
            val cameraUp = (viewInverse * Vector4.UNIT_Y).xyz

            val activePoses = poses.filter { it.frameIndex == frameIndex }
            if (activePoses.isNotEmpty()) {
                drawer.isolated {
                    drawer.stroke = ColorRGBa.WHITE
                    drawer.strokeWeight = 1.0

                    activePoses.forEach { p ->
                        val p1 = Vector3(
                            ((p.start.x / 640.0) - 0.5) * tilesheet.width.toDouble() * targetScale,
                            ((p.start.y / 480.0) - 0.5) * tilesheet.height.toDouble() * targetScale * -1.0,
                            0.0
                        )
                        val p2 = Vector3(
                            ((p.end.x / 640.0) - 0.5) * tilesheet.width.toDouble() * targetScale,
                            ((p.end.y / 480.0) - 0.5) * tilesheet.height.toDouble() * targetScale * -1.0,
                            0.0
                        )

                        val worldP1 = position + cameraRight * p1.x + cameraUp * p1.y
                        val worldP2 = position + cameraRight * p2.x + cameraUp * p2.y
                        drawer.lineSegment(worldP1, worldP2)
                    }
                }
            }
        }

        // --- Phrase rendering for zoomed sheet ---
        val phraseMap = mapOf(
            "Robots" to "They move like machines, following a precise and invisible command.",
            "Athletes" to "They push and repeat, caught in an endless cycle of effort.",
            "Disco Girls" to "They glow in the spotlight, but their shine never lasts.",
            "Skeletons" to "They bounce in loops, reminding us death is always in rhythm.",
            "Mummies" to "They drift through time, carrying the slow weight of decay.",
            "All" to "From individual rhythms to perfect synchrony, they reveal how every human, despite their differences, is drawn into the same commanding order of life."
        )

        if (currentZoomedIndex != -1) {
            val phraseDelayFactor = tilesheet.frameRate * delayInSeconds
            val phraseFrameOffset = (currentZoomedIndex * phraseDelayFactor).toInt()
            val phraseFrameIndex = (phraseFrameOffset).mod(tilesheet.size)
            val phraseFrameTime = phraseFrameIndex / tilesheet.frameRate
            val phraseSegment = segments.find { it.start <= phraseFrameTime && phraseFrameTime <= it.end }

            if (phraseSegment != null && phraseMap.containsKey(phraseSegment.text)) {
                val fullPhrase = phraseMap[phraseSegment.text]!!
                val title = if (phraseSegment.text == "All") "All" else phraseSegment.text

                // Typewriter effect logic
                val typewriterSpeed = 30.0 // characters per second
                val elapsed = program.seconds - zoomStartTime
                val charsToShow = (elapsed * typewriterSpeed).toInt().coerceIn(0, fullPhrase.length)
                val displayedPhrase = fullPhrase.substring(0, charsToShow)

                drawer.isolated {
                    // Switch to 2D for screen-centered text
                    drawer.view = Matrix44.IDENTITY
                    drawer.ortho()
                    drawer.model = Matrix44.IDENTITY

                    // Draw Title
                    val titleFont = program.loadFont("data/fonts/PPWatch-Medium.otf", 70.0)
                    drawer.fontMap = titleFont
                    drawer.fill = ColorRGBa.WHITE.opacify(zoom)

                    val centerX = drawer.width * 0.5
                    val titleY = 80.0
                    val titleWidth = writer(drawer) { textWidth(title) }
                    drawer.text(title, centerX - titleWidth * 0.5, titleY)

                    // Draw Phrase (Bigger)
                    val phraseFont = program.loadFont("data/fonts/PPWatch-Medium.otf", 40.0)
                    drawer.fontMap = phraseFont
                    drawer.fill = ColorRGBa.WHITE.opacify(zoom) // Fade in with zoom

                    // Center top of the screen
                    val y = 150.0 // Offset from top

                    writer(drawer) {
                        tracking = 0.0
                        leading = 10.0

                        if (phraseSegment.text == "All") {
                            // Manual split for "All" to ensure it's after "synchrony," and "differences,"

                            val splitMarker1 = "synchrony,"
                            val splitIndex1 = fullPhrase.indexOf(splitMarker1) + splitMarker1.length

                            val splitMarker2 = "differences,"
                            val splitIndex2 = fullPhrase.indexOf(splitMarker2) + splitMarker2.length

                            val line1 = fullPhrase.substring(0, splitIndex1).trim()
                            val line2 = fullPhrase.substring(splitIndex1, splitIndex2).trim()
                            val line3 = fullPhrase.substring(splitIndex2).trim()

                            val d1 = displayedPhrase.take(line1.length)
                            val d2 = if (displayedPhrase.length > line1.length) {
                                displayedPhrase.substring(
                                    line1.length,
                                    minOf(displayedPhrase.length, line1.length + line2.length + 1)
                                ).trimStart()
                            } else ""
                            val d3 = if (displayedPhrase.length > line1.length + line2.length + 1) {
                                displayedPhrase.substring(line1.length + line2.length + 1).trimStart()
                            } else ""

                            val w1 = textWidth(line1)
                            cursor.x = centerX - w1 * 0.5
                            cursor.y = y
                            text(d1)

                            if (d2.isNotEmpty() || charsToShow > line1.length) {
                                val w2 = textWidth(line2)
                                newLine()
                                cursor.x = centerX - w2 * 0.5
                                text(d2)
                            }

                            if (d3.isNotEmpty() || charsToShow > line1.length + line2.length + 1) {
                                val w3 = textWidth(line3)
                                newLine()
                                cursor.x = centerX - w3 * 0.5
                                text(d3)
                            }
                        } else {
                            val tw = textWidth(fullPhrase)
                            cursor.x = centerX - tw * 0.5
                            cursor.y = y
                            text(displayedPhrase)
                        }
                    }
                }
            }
        }

        // Draw legends
        drawer.isolated {
            drawer.fontMap = font
            drawer.fill = ColorRGBa.WHITE

            for (i in 0 until numPoints) {
                val pathT = i / numPoints.toDouble()
                val pose = posePath.pose(pathT)
                val reposition = pose * buildTransform { translate(0.0, ypositions[i] * -5.0, 0.0) }

                val delayFactor = tilesheet.frameRate * delayInSeconds
                val frameOffsetConstant = (i * delayFactor).toInt()
                val frameIndexConstant = (frameOffsetConstant).mod(tilesheet.size)
                val frameTime = frameIndexConstant / tilesheet.frameRate
                val frameSegment = segments.find { it.start <= frameTime && frameTime <= it.end }

                if (frameSegment != null && frameSegment.text != "All") {
                    val isFrameSegmentTheActiveSegment = frameSegment.text == activeLabel
                    val segmentStartTime = activeSegment?.start ?: 0.0
                    val delayPerPoint = 4.0
                    val individualTriggerTime = segmentStartTime + (i / numPoints.toDouble()) * delayPerPoint
                    val isTriggered = isFrameSegmentTheActiveSegment && time >= individualTriggerTime

                    val isSelected = i == currentZoomedIndex

                    val legendScaleFactor = if (currentZoomedIndex == -1) {
                        1.0 - zoom
                    } else {
                        1.0 - zoom
                    }

                    if (isTriggered && legendScaleFactor > 0.001) {
                        drawer.isolated {
                            val textScale = 0.08 * legendScaleFactor
                            drawer.model = reposition * buildTransform {
                                translate(-2.0, -5.5, 0.0)
                                scale(textScale)
                            }
                            drawer.text(frameSegment.text, -frameSegment.text.length * 4.0, 0.0)
                        }
                    }
                }
            }
        }

        // --- Timeline drawing ---
        drawer.isolated {
            val progress = time / duration

            // The timeline circle is in the XZ plane.
            // drawer.circle draws in XY plane, so we rotate it.
            drawer.model = buildTransform {
                rotate(Vector3.UNIT_X, 90.0)
                drawer.strokeWeight = 1.0
            }

            // Draw background circle (thin)
            drawer.stroke = ColorRGBa.WHITE
            drawer.strokeWeight = 0.2
            drawer.fill = null

            val c = Circle(Vector2.ZERO, 50.0)
            drawer.circle(c)

            // Draw time markers
            drawer.fill = ColorRGBa.WHITE
            drawer.stroke = null
            drawer.fontMap = font
            val numMarkers = 12
            for (i in 0 until numMarkers) {
                val angle = i * (360.0 / numMarkers)
                val p = Polar(angle, 52.0).cartesian
                val timeMarker = (angle / 360.0) * duration
                val minutes = (timeMarker / 60).toInt()
                val seconds = (timeMarker % 60).toInt()
                val timeString = String.format("%02d:%02d", minutes, seconds)

                drawer.isolated {
                    drawer.translate(p)
                    drawer.rotate(angle + 90.0) // Rotate so top faces center (CW + adjust)
                    drawer.scale(0.08)
                    drawer.text(timeString, -timeString.length * 4.0, -10.0) // Centered text
                }
            }

            projectedPoints.clear()
            for (i in 0 until 100) {
                val p = Polar(i * 3.6, 50.0).cartesian.vector3()

                val pp = project(p, drawer.projection, drawer.view * drawer.model, drawer.width, drawer.height).xy
                projectedPoints.add(pp)
            }


        }
    }
}

private class CylinderScene(
    parent: Program,
    private val soundtrack: SharedSoundtrack,
    val videoImage: ColorBuffer,
) : ScreenScene {
    override val name = "Cylinder"
    override val program = SceneProgram(parent)

    private val dataDir = "data/extracted/around-the-world/"
    private val cylinderYOffset = 0.0
    private val tileSheet =
        loadTilesheet("$dataDir/around-the-world-tilesheet.csv", "$dataDir/around-the-world-tilesheet.png")

    private val segments = loadSegments("data/segments/segments-people.csv")
    private val poses = loadPoses("${dataDir}around-the-world_poses2.csv")
    private val pointViz3D = VideoPointViz3D13(
        tilesheet = tileSheet,
        segments = segments,
        path3D = Circle(Vector2.ZERO, 32.0).contour.toPath3D().transform(
            buildTransform { rotate(Vector3.UNIT_X, 90.0) }
        ),
        program = program,
        videoImage = videoImage,
        poses = poses,
        worldYOffset = cylinderYOffset
    )
    private val orbital = program.extend(Orbital()) {
        fov = 20.0
        eye = Vector3(0.0, 80.0, 180.0)
    }
    private val initialOrbitalFov = 20.0
    private val duration = tileSheet.size / tileSheet.frameRate
    private val initialOrbitalEye = Vector3(0.0, 80.0, 180.0)
    private val initialTargetLookAt = Vector3.ZERO
    private val initialTargetEye = Vector3(0.0, 90.0, 180.0)

    private var started = false
    private var zoomedIndex = -1
    private var animatingIndex = -1
    private var zoomFactor = 0.0
    private var targetLookAt = initialTargetLookAt
    private var targetEye = initialTargetEye
    private var seekRequested = false


    init {
        program.mouse.buttonDown.listen {
            if (it.button == MouseButton.LEFT && !it.propagationCancelled) {
                if (zoomedIndex != -1) {
                    zoomedIndex = -1
                    targetLookAt = Vector3.ZERO
                    targetEye = Vector3(0.0, 90.0, 180.0)
                    it.cancelPropagation()
                } else {
                    val nearestSheet =
                        pointViz3D.sheetProjectedPoints.minByOrNull { point -> point.distanceTo(it.position) }
                    if (nearestSheet != null && nearestSheet.distanceTo(it.position) < 30.0) {
                        val index = pointViz3D.sheetProjectedPoints.indexOf(nearestSheet)
                        zoomedIndex = index
                        animatingIndex = index
                        targetLookAt = pointViz3D.sheetPositions[index]
                        targetEye = pointViz3D.sheetPositions[index] + Vector3(0.0, 0.0, 20.0)
                        it.cancelPropagation()
                    } else {
                        navigate(it.position)
                    }
                }
            }
        }

        program.mouse.dragged.listen {
            if (it.button == MouseButton.LEFT && !it.propagationCancelled) {
                navigate(it.position)
            }
        }

        program.mouse.buttonUp.listen {
            seekRequested = true
        }
    }

    override fun activate() {
        //soundtrack.ensurePlaying()
        soundtrack.resetMix()

    }

    override fun deactivate() {
        if (started) {
            //    video.pause()
        }
    }

    override fun reset() {
        zoomedIndex = -1
        animatingIndex = -1
        zoomFactor = 0.0
        targetLookAt = initialTargetLookAt
        targetEye = initialTargetEye
        seekRequested = false
        orbital.fov = initialOrbitalFov
        orbital.lookAt = initialTargetLookAt
        orbital.eye = initialOrbitalEye
        pointViz3D.reset()
    }

    override fun draw(time: Double) {
        program.drawWithExtensions {
            //  syncVideoToSoundtrack()
            val smoothingFactor = 0.1

            if (zoomedIndex != -1) {
                zoomFactor = zoomFactor * (1.0 - smoothingFactor) + smoothingFactor
                if (zoomedIndex in pointViz3D.sheetPositions.indices) {
                    targetLookAt = pointViz3D.sheetPositions[zoomedIndex]
                    targetEye = pointViz3D.sheetPositions[zoomedIndex] + Vector3(0.0, 0.0, 20.0)
                }
            } else {
                zoomFactor = zoomFactor * (1.0 - smoothingFactor)
                if (zoomFactor < 0.001) {
                    zoomFactor = 0.0
                    animatingIndex = -1
                }
            }

            orbital.lookAt = orbital.lookAt * (1.0 - smoothingFactor) + targetLookAt * smoothingFactor
            orbital.eye = orbital.eye * (1.0 - smoothingFactor) + targetEye * smoothingFactor

            program.drawer.clear(ColorRGBa.BLACK)
            // video.draw {}
            pointViz3D.draw(time, animatingIndex, zoomFactor)

            program.drawer.defaults()
            val projectedPoints = pointViz3D.projectedPoints
            val duration = tileSheet.size / tileSheet.frameRate

            val playIndex = ((time / duration) * projectedPoints.size)
                .toInt()
                .coerceIn(0, projectedPoints.lastIndex.coerceAtLeast(0))

            if (projectedPoints.isNotEmpty()) {
                program.drawer.fill = ColorRGBa.WHITE
                program.drawer.stroke = null
                program.drawer.circle(projectedPoints[playIndex], 9.0)

                val mousePosition = program.mouse.position
                val nearestPoint = projectedPoints.minByOrNull { point -> point.distanceTo(mousePosition) }
                if (nearestPoint != null && mousePosition.distanceTo(nearestPoint) < 30.0) {
                    if (seekRequested) {
                        val nearestId = projectedPoints.indexOf(nearestPoint)
                        val t = nearestId.toDouble() / projectedPoints.size.toDouble()
                        soundtrack.seek(t * duration)
                        //syncVideoToSoundtrack(force = true)
                        seekRequested = false
                    }
                    program.drawer.circle(nearestPoint, 10.0)
                } else {
                    seekRequested = false
                }
            } else {
                seekRequested = false
            }
        }
    }

    private fun navigate(mousePosition: Vector2) {
        try {
            val camera = orbital.camera
            val rayMethod = orbital::class.members.find { it.name == "ray" }
                ?: camera::class.members.find { it.name == "ray" }

            val ray = if (rayMethod?.parameters?.size == 3) {
                rayMethod.call(orbital, mousePosition, program.drawer.bounds)
            } else {
                rayMethod?.call(camera, mousePosition, program.drawer.bounds)
            }

            if (ray != null) {
                val origin = ray::class.members.find { it.name == "origin" }?.call(ray) as Vector3
                val direction = ray::class.members.find { it.name == "direction" }?.call(ray) as Vector3
                if (abs(direction.y) > 0.00001) {
                    val t = -origin.y / direction.y
                    if (t > 0.0) {
                        val intersection = origin + direction * t
                        val angle = atan2(intersection.z, intersection.x)
                        val normalizedAngle = (angle + 2.0 * PI).mod(2.0 * PI)
                        val targetTime = (normalizedAngle / (2.0 * PI)) * duration

                        soundtrack.seek(targetTime.coerceIn(0.0, duration))
                        //syncVideoToSoundtrack(force = true)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}

private class GridLinkScene(
    parent: Program,
    private val soundtrack: SharedSoundtrack
) : ScreenScene {
    override val name = "Grid Link"
    override val program = SceneProgram(parent)

    private val camera = program.extend(Orbital()) {
        eye = PointViz19SceneConfig.INITIAL_EYE
        lookAt = PointViz19SceneConfig.LOOK_AT
        far = 3000.0
    }
    private val font = program.loadFont(PointViz19SceneConfig.FONT_PATH, PointViz19SceneConfig.FONT_SIZE)
    private val tilesheet = loadTilesheet(
        "${PointViz19SceneConfig.DATA_DIR}/around-the-world-tilesheet.csv",
        "${PointViz19SceneConfig.DATA_DIR}/around-the-world-tilesheet.png"
    )
    private val path3D = Circle(Vector2.ZERO, PointViz19SceneConfig.RADIUS).contour
        .toPath3D()
        .transform(buildTransform { rotate(Vector3.UNIT_X, 90.0) })
    private val pointViz = PointViz15_3D(
        tilesheet,
        path3D,
        program,
        focusSharpness = PointViz19SceneConfig.FOCUS_SHARPNESS,
        focusScaleRange = PointViz19SceneConfig.FOCUS_SCALE_RANGE,
        focusBaseScale = PointViz19SceneConfig.FOCUS_BASE_SCALE,
        zoomFactor = PointViz19SceneConfig.HUB_TILE_SCALE
    )
    private val segments = loadSegments(PointViz19SceneConfig.SEGMENTS_CSV)
    private val remap = pointViz.remap
    private val linksByLabel = segments.groupBy { it.text }.mapValues { (_, segs) ->
        val segmentCenters = segs.map { segment ->
            val midTime = (segment.start + segment.end) / 2.0
            val frameIndex = (midTime * tilesheet.frameRate).toInt().coerceIn(0, remap.size - 1)
            remap[frameIndex]
        }
        if (segmentCenters.size > 1) {
            (segmentCenters + segmentCenters.first()).zipWithNext().map { (p0, p1) ->
                Segment3D(p0, Vector3.ZERO, p1)
            }
        } else {
            emptyList()
        }
    }
    private val allBackgroundLinks = linksByLabel.values.flatten()
    private val frameToLabels = Array(remap.size) { mutableSetOf<String>() }.also { mapping ->
        segments.forEach { segment ->
            val startFrame = (segment.start * tilesheet.frameRate).toInt()
            val endFrame = (segment.end * tilesheet.frameRate).toInt()
            for (frameIndex in startFrame until endFrame) {
                if (frameIndex in mapping.indices) {
                    mapping[frameIndex].add(segment.text)
                }
            }
        }
    }
    private val labelHubs = segments.groupBy { it.text }.mapValues { (_, segs) ->
        segs.fold(Vector3.ZERO) { acc, segment ->
            val midTime = (segment.start + segment.end) / 2.0
            val frameIndex = (midTime * tilesheet.frameRate).toInt().coerceIn(0, remap.size - 1)
            acc + remap[frameIndex]
        } / segs.size.toDouble()
    }
    private val activeFramePadding =
        max(tilesheet.width.toDouble(), tilesheet.height.toDouble()) + PointViz19SceneConfig.ACTIVE_FRAME_PADDING

    private var zoomToFit = false
    private var clickedPosition: Vector2? = null

    init {
        pointViz.seek.listen {
            soundtrack.seek(it.positionInSeconds)
        }

        program.mouse.buttonDown.listen {
            clickedPosition = it.position
        }

        program.keyboard.character.listen {
            when (it.character.lowercaseChar()) {
                'z' -> zoomToFit = !zoomToFit
                'u' -> {
                    zoomToFit = false
                    camera.eye = PointViz19SceneConfig.INITIAL_EYE
                    camera.lookAt = PointViz19SceneConfig.LOOK_AT
                }
            }
        }
    }

    override fun activate() {
        soundtrack.ensurePlaying()
        soundtrack.resetMix()
    }

    override fun deactivate() = Unit

    override fun reset() {
        zoomToFit = false
        clickedPosition = null
        camera.eye = PointViz19SceneConfig.INITIAL_EYE
        camera.lookAt = PointViz19SceneConfig.LOOK_AT
    }

    override fun draw(time: Double) {
        program.drawWithExtensions {
            val playbackTime = soundtrack.position()
            program.drawer.clear(PointViz19SceneConfig.BACKGROUND_COLOR)

            clickedPosition?.let { position ->
                val pickedIdx = pointViz.pick(
                    playbackTime,
                    position,
                    program.drawer.projection,
                    program.drawer.view,
                    program.width.toDouble(),
                    program.height.toDouble()
                )
                if (pickedIdx != null) {
                    soundtrack.seek(pickedIdx.toDouble() / tilesheet.frameRate)
                }
                clickedPosition = null
            }

            val activeIndices = pointViz.activeFrameRects.map { it.tileIndex to it.frameIndex }
            val activeLabels = activeIndices.flatMap { (_, frameIndex) ->
                frameToLabels[frameIndex % remap.size]
            }.toSet()

            program.drawer.fill = null
            program.drawer.stroke = PointViz19SceneConfig.LINK_COLOR_BACKGROUND
                .opacify(PointViz19SceneConfig.OPACITY_BACKGROUND_LINKS)
            for (link in allBackgroundLinks) {
                program.drawer.segment(link)
            }

            for (label in activeLabels) {
                program.drawer.stroke = PointViz19SceneConfig.LINK_COLOR_ACTIVE
                    .opacify(PointViz19SceneConfig.OPACITY_ACTIVE_LINKS)
                linksByLabel[label]?.forEach(program.drawer::segment)

                labelHubs[label]?.let { hubPos ->
                    val representativeIdx = activeIndices.find { (_, frameIndex) ->
                        frameToLabels[frameIndex % remap.size].contains(label)
                    }?.second

                    if (representativeIdx != null) {
                        val tiles = tilesheet.image
                        val tw = tilesheet.width.toDouble()
                        val th = tilesheet.height.toDouble()
                        val sx = (representativeIdx * tw).mod(tiles.width.toDouble())
                        val sy = (representativeIdx * tw.toInt() / tiles.width).toDouble() * th

                        val source = Rectangle(sx, sy, tw, th)
                        val target = Rectangle.fromCenter(
                            Vector2.ZERO,
                            tw * PointViz19SceneConfig.HUB_TILE_SCALE,
                            th * PointViz19SceneConfig.HUB_TILE_SCALE
                        )

                        program.drawer.isolated {
                            program.drawer.translate(hubPos)

                            val view = program.drawer.view
                            val billboard = Matrix44(
                                view.c0r0, view.c0r1, view.c0r2, 0.0,
                                view.c1r0, view.c1r1, view.c1r2, 0.0,
                                view.c2r0, view.c2r1, view.c2r2, 0.0,
                                0.0, 0.0, 0.0, 1.0
                            )
                            program.drawer.model *= billboard
                            program.drawer.scale(1.0, -1.0)
                            program.drawer.image(tiles, source, target)

                            val currentTime =
                                (representativeIdx % tilesheet.size).toDouble() / tilesheet.frameRate
                            program.drawer.fill = PointViz19SceneConfig.TEXT_COLOR
                            program.drawer.fontMap = font

                            writer {
                                box = Rectangle(-200.0, target.y - 35.0, 400.0, 30.0)
                                horizontalAlign = 0.5
                                verticalAlign = 1.0
                                text(formatTimePrecise(currentTime))
                            }
                        }
                    }
                }
            }

            pointViz.draw(playbackTime)

            if (zoomToFit) {
                val trackedActivePositions = pointViz.activeFrameRects.map { activeFrame ->
                    remap[activeFrame.tileIndex % remap.size]
                }
                trackOrbitalCameraToActiveFrames(
                    camera = camera,
                    activePositions = trackedActivePositions,
                    baseEye = PointViz19SceneConfig.INITIAL_EYE,
                    baseLookAt = PointViz19SceneConfig.LOOK_AT,
                    minDistance = PointViz19SceneConfig.FOLLOW_DISTANCE,
                    padding = activeFramePadding,
                    blend = PointViz19SceneConfig.TRACK_SPEED
                )
            }
        }
    }

    private fun formatTimePrecise(seconds: Double): String {
        val minutes = (seconds / 60.0).toInt()
        val wholeSeconds = (seconds % 60.0).toInt()
        val tenths = ((seconds % 1.0) * 10.0).toInt()
        return "${minutes.toString().padStart(2, '0')}:${wholeSeconds.toString().padStart(2, '0')}.$tenths"
    }
}

private class SpiralScene(
    parent: Program,
    private val soundtrack: SharedSoundtrack,
    private val videoImage: ColorBuffer,
) : ScreenScene {
    override val name = "Spiral"
    override val program = SceneProgram(parent)

    private val dataDir = "data/extracted/around-the-world"
    private val tileSheet = loadTilesheet(
        "$dataDir/around-the-world-tilesheet.csv",
        "$dataDir/around-the-world-tilesheet.png"
    )

    private val videoFrameRate = probeVideo("$dataDir/around-the-world.mp4")?.framerate ?: tileSheet.frameRate
    private val segments = loadSegments("data/segments/segments-people.csv")
    private val ringLegends = segments.map { it.text }.distinct().associateWith { it }
    private val orbital = program.extend(Orbital()) {
        fov = 20.0
        eye = Vector3(0.0, 90.0, 180.0)
        lookAt = Vector3.ZERO
    }
    private val initialOrbitalFov = 20.0
    private val initialOrbitalEye = Vector3(0.0, 90.0, 180.0)
    private val initialOrbitalLookAt = Vector3.ZERO
    private val segmentViz = RadialSegmentViz3DRefined(tileSheet, segments, program, orbital, ringLegends)
    private val rotoscopeBackground = CharacterSequenceRotoscopeOutlineBackground(
        program = program,
        rootDir = "data/characters"
    )
    private val objectDetectionBackground = ObjectDetectionBackground(
        program = program,
        boxesCsvPath = "$dataDir/around-the-world-object_boxes-2.csv",
        frameRate = videoFrameRate,
        sourceWidth = videoImage.width,
        sourceHeight = videoImage.height
    )
    private var started = false
    private var lastAudioSelection: String? = null
    private var backgroundMode = ScreenBackgroundMode.ROTOSCOPE

//    private fun syncVideoToSoundtrack(force: Boolean = false) {
//        val targetTime = soundtrack.position().coerceIn(0.0, video.duration)
//        if (force || abs(video.position - targetTime) > 0.05) {
//            video.seek(targetTime)
//        }
//    }

    init {
        program.keyboard.keyDown.listen { event ->
            when (event.name.lowercase()) {
                "1" -> backgroundMode = ScreenBackgroundMode.ROTOSCOPE
                "2" -> backgroundMode = ScreenBackgroundMode.OBJECT_DETECTION
            }
        }
    }

    override fun activate() {
        soundtrack.ensurePlaying()
        if (started) {
//            syncVideoToSoundtrack(force = true)
//            video.resume()
        } else {
//            video.play()
//            syncVideoToSoundtrack(force = true)
            started = true
        }
        lastAudioSelection = null
        soundtrack.emphasize(segmentViz.selectedTrackLabel)
    }

    override fun deactivate() {
        if (started) {
//            video.pause()
        }
    }

    override fun reset() {
        lastAudioSelection = null
        backgroundMode = ScreenBackgroundMode.ROTOSCOPE
        orbital.fov = initialOrbitalFov
        orbital.eye = initialOrbitalEye
        orbital.lookAt = initialOrbitalLookAt
        segmentViz.resetSelection()
    }

    override fun draw(time: Double) {
        program.drawWithExtensions {
//            syncVideoToSoundtrack()
            program.drawer.clear(ColorRGBa.BLACK)

            var hasVideoFrame = false
            videoImage.let {
                hasVideoFrame = true

                val playbackTime = time
                val activeSegment = segments.find { it.start <= playbackTime && playbackTime < it.end }
                segmentViz.setAutomaticActivation(activeSegment?.text)

                when (backgroundMode) {
                    ScreenBackgroundMode.ROTOSCOPE -> {
                        rotoscopeBackground.draw(segmentViz.activatedTrackLabel, playbackTime)
                    }

                    ScreenBackgroundMode.OBJECT_DETECTION -> {
                        objectDetectionBackground.draw(it, playbackTime)
                    }
                }
            }

            val playbackTime = time
            val activeSegment = segments.find { it.start <= playbackTime && playbackTime < it.end }
            if (!hasVideoFrame) {
                segmentViz.setAutomaticActivation(activeSegment?.text)
            }
            segmentViz.draw(playbackTime)

            val selectedLabel = segmentViz.selectedTrackLabel
            if (selectedLabel != lastAudioSelection) {
                soundtrack.emphasize(selectedLabel)
                lastAudioSelection = selectedLabel
            }
        }
    }
}

private class WorldScene(
    parent: Program,
    private val soundtrack: SharedSoundtrack,
    private val onPreviewEnter1: (PreviewWindow) -> Unit = {},
    private val onPreviewEnter2: (PreviewWindow) -> Unit = {},
    private val onPreviewEnter3: (PreviewWindow) -> Unit = {}
) : ScreenScene {
    override val name = "World"
    override val program = SceneProgram(parent)

    val distanceToSphere = 360.0
    private val orbital = program.extend(Orbital()) {
        eye = Vector3(0.0, 10.0, distanceToSphere)
        lookAt = Vector3.ZERO
        fov = 22.0
    }
    private val initialOrbitalFov = 22.0
    private val initialOrbitalEye = Vector3(0.0, 10.0, distanceToSphere)
    private val initialOrbitalLookAt = Vector3.ZERO

    private val dataDir = "data/extracted/around-the-world/"
    private val tileSheet = loadTilesheet(
        "${dataDir}around-the-world-tilesheet.csv",
        "${dataDir}around-the-world-tilesheet.png"
    )

    private val previewWindow = PreviewWindow(
        program = program,
        videoPath = "data/extracted/around-the-world/Delphine-resized.mp4",
        x = 180.0,
        y = 140.0,
        title = "Analysis on Movement",
        directory = "src/main/kotlin/Movement.kt",
        onEnter = onPreviewEnter1
    )

    private val previewWindow2 = PreviewWindow(
        program = program,
        videoPath = "data/extracted/around-the-world/Sprial-resized.mp4",
        x = 1280.0,
        y = 720.0,
        title = "Analysis on Instruments",
        directory = "src/main/kotlin/Instrument.kt",
        onEnter = onPreviewEnter2
    )

    private val previewWindow3 = PreviewWindow(
        program = program,
        videoPath = "data/extracted/around-the-world/cindy.mp4",
        x = 240.0,
        y = 720.0,
        title = "Media Player",
        directory = "src/main/kotlin/Grid-Link.kt",
        onEnter = onPreviewEnter3
    )

    private val spherePoints = PointViz12.fibonacciSpherePoints(tileSheet.size, PointViz12.SPHERE_RADIUS)
    private val pointViz = PointViz12(tileSheet, spherePoints, program).apply {
        latitudeBandHighlightEnabled = true
    }

    init {
        pointViz.seek.listen {
            soundtrack.seek(it.positionInSeconds)
        }
    }

    override fun activate() {
        soundtrack.ensurePlaying()
        soundtrack.resetMix()
    }

    override fun deactivate() = Unit

    override fun reset() {
        orbital.fov = initialOrbitalFov
        orbital.eye = initialOrbitalEye
        orbital.lookAt = initialOrbitalLookAt
        pointViz.reset()
        previewWindow.reset()
        previewWindow2.reset()
        previewWindow3.reset()
    }

    override fun draw(time: Double) {
        program.drawWithExtensions {
            program.drawer.clear(ColorRGBa.BLACK)
            pointViz.draw(soundtrack.position())
            previewWindow.draw(program.drawer)
            previewWindow2.draw(program.drawer)
            previewWindow3.draw(program.drawer)
        }
    }
}

fun main() {
    application {
        configure {
            width = 1920
            height = 1080
            fullscreen = Fullscreen.CURRENT_DISPLAY_MODE
        }

        program {
            var activeSceneIndex = 0
            var previousSceneIndex: Int? = null
            var transitionStartedAt = 0.0

            lateinit var scenes: List<ScreenScene>

            val details = probeVideo("data/extracted/around-the-world/around-the-world.mp4")

            val video = loadVideo(
                "data/extracted/around-the-world/around-the-world.mp4",
                mode = PlayMode.VIDEO
            )

            val soundtrack = SharedSoundtrack(video)


            val videoRt = renderTarget(details!!.width, details.height) {
                colorBuffer()
            }


            fun transitionToScene(nextSceneIndex: Int) {
                if (previousSceneIndex != null || nextSceneIndex == activeSceneIndex) {
                    return
                }

                previousSceneIndex = activeSceneIndex
                scenes[activeSceneIndex].deactivate()
                activeSceneIndex = nextSceneIndex
                scenes[activeSceneIndex].activate()
                transitionStartedAt = seconds
            }

            scenes = listOf(
                WorldScene(
                    this,
                    soundtrack,
                    onPreviewEnter1 = { transitionToScene(1) }, // Movement
                    onPreviewEnter2 = { transitionToScene(2) }, // Instruments
                    onPreviewEnter3 = { transitionToScene(3) }  // Media Player
                ),
                CylinderScene(this, soundtrack, videoRt.colorBuffer(0)),
                SpiralScene(this, soundtrack, videoRt.colorBuffer(0)),
                GridLinkScene(this, soundtrack),
            )
            val sceneTargets = scenes.associateWith {
                renderTarget(width, height, contentScale = RenderTarget.active.contentScale) {
                    colorBuffer()
                    depthBuffer()
                }
            }

            scenes[activeSceneIndex].activate()

            fun currentInputScene(): ScreenScene {
                return scenes[activeSceneIndex]
            }

            fun restartExperience() {
                soundtrack.seek(0.0)
                soundtrack.resetMix()
                scenes.forEach(ScreenScene::reset)
                previousSceneIndex = null
                transitionStartedAt = seconds
                scenes[activeSceneIndex].activate()
            }

            keyboard.keyDown.listen { event ->
                if (event.key == KEY_SPACEBAR && !event.propagationCancelled) {
                    val nextSceneIndex = (activeSceneIndex + 1) % scenes.size
                    transitionToScene(nextSceneIndex)
                    event.cancelPropagation()
                } else if (event.key == KEY_BACKSPACE && !event.propagationCancelled) {
                    transitionToScene(0)
                    event.cancelPropagation()
                } else if (event.name.lowercase() == "r" && !event.propagationCancelled) {
                    restartExperience()
                    event.cancelPropagation()
                } else if (!event.propagationCancelled) {
                    currentInputScene().program.dispatchKeyDown(event)
                }
            }

            keyboard.keyUp.listen { event ->
                if (!event.propagationCancelled) {
                    currentInputScene().program.dispatchKeyUp(event)
                }
            }

            keyboard.keyRepeat.listen { event ->
                if (!event.propagationCancelled) {
                    currentInputScene().program.dispatchKeyRepeat(event)
                }
            }

            keyboard.character.listen { event ->
                if (!event.propagationCancelled) {
                    currentInputScene().program.dispatchCharacter(event)
                }
            }

            mouse.moved.listen { event ->
                currentInputScene().program.dispatchMouseMoved(event)
            }

            mouse.buttonDown.listen { event ->
                if (!event.propagationCancelled) {
                    currentInputScene().program.dispatchMouseDown(event)
                }
            }

            mouse.dragged.listen { event ->
                if (!event.propagationCancelled) {
                    currentInputScene().program.dispatchMouseDragged(event)
                }
            }

            mouse.buttonUp.listen { event ->
                if (!event.propagationCancelled) {
                    currentInputScene().program.dispatchMouseUp(event)
                }
            }

            mouse.scrolled.listen { event ->
                if (!event.propagationCancelled) {
                    currentInputScene().program.dispatchMouseScrolled(event)
                }
            }

            pointers.moved.listen { event ->
                currentInputScene().program.dispatchPointerMoved(event)
            }

            pointers.pointerDown.listen { event ->
                currentInputScene().program.dispatchPointerDown(event)
            }

            pointers.pointerUp.listen { event ->
                currentInputScene().program.dispatchPointerUp(event)
            }

            pointers.cancelled.listen { event ->
                currentInputScene().program.dispatchPointerCancelled(event)
            }


            // start playing video and sound as late as possible
            video.play()
            soundtrack.ensurePlaying()


            var lastSeek = 0.0
            extend {

                if (soundtrack.position() > soundtrack.duration() - 1.0 && seconds - lastSeek > 1.0) {

                    soundtrack.seek(0.0)
                    lastSeek = seconds
                }


                drawer.clear(ColorRGBa.BLACK)
                drawer.isolatedWithTarget(videoRt) {
                    drawer.ortho(videoRt)
                    video.draw(drawer)

                }

                val activeScene = scenes[activeSceneIndex]
                val activeTarget = sceneTargets.getValue(activeScene)
                renderSceneToTarget(soundtrack.position(), activeScene, activeTarget)

                val fromIndex = previousSceneIndex
                if (fromIndex == null) {
                    compositeTarget(activeTarget, 1.0)
                } else {
                    val progress = ((seconds - transitionStartedAt) / SCENE_TRANSITION_DURATION)
                        .coerceIn(0.0, 1.0)
                    val previousTarget = sceneTargets.getValue(scenes[fromIndex])
                    compositeTarget(previousTarget, 1.0 - progress)
                    compositeTarget(activeTarget, progress)

                    if (progress >= 1.0) {
                        previousSceneIndex = null
                    }
                }
            }
        }
    }
}

private fun Program.renderSceneToTarget(time: Double, scene: ScreenScene, target: RenderTarget) {
    drawer.isolatedWithTarget(target) {
        drawer.clear(ColorRGBa.TRANSPARENT)
        scene.draw(time)
    }
}

private fun Program.compositeTarget(target: RenderTarget, opacity: Double) {
    if (opacity <= 0.0) {
        return
    }

    val colorBuffer = target.colorBuffer(0)
    colorBuffer.filterMin = MinifyingFilter.LINEAR
    colorBuffer.filterMag = MagnifyingFilter.LINEAR

    drawer.isolated {
        drawer.defaults()
        drawer.drawStyle.colorMatrix = tint(ColorRGBa.WHITE.opacify(opacity))
        drawer.image(colorBuffer)
    }
}
