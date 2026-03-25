package components

import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.draw.RenderTarget
import org.openrndr.draw.isolated
import org.openrndr.draw.loadFont
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.ffmpeg.PlayMode
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import org.openrndr.ffmpeg.loadVideo
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle

class PreviewWindow(
    val program: Program,
    video: VideoPlayerFFMPEG? = null,
    val videoPath: String = "",
    var x: Double,
    var y: Double,
    val width: Double = 320.0,
    val height: Double = 240.0,
    val title: String = "Preview",
    val directory: String = "",
    val targetApp: (() -> Unit)? = null,
    val onEnter: (PreviewWindow) -> Unit = {}
) {
    private var videoInternal: VideoPlayerFFMPEG? = video
    private val initialX = x
    private val initialY = y

    private val videoDelegate = lazy {
        videoInternal ?: if (videoPath.isNotEmpty()) {
            program.loadVideo(videoPath, mode = PlayMode.VIDEO).apply {
                play()
            }
        } else {
            throw IllegalArgumentException("PreviewWindow: Either video or videoPath must be provided")
        }
    }
    val video: VideoPlayerFFMPEG by videoDelegate

    val resolvedTargetApp: (() -> Unit)? by lazy {
        targetApp ?: resolveTargetApp(directory)
    }

    private fun resolveTargetApp(path: String): (() -> Unit)? {
        if (path.isEmpty()) return null

        val cleanPath = path.removePrefix("src/main/kotlin/").removeSuffix(".kt").removePrefix("/")
        val fqcn = cleanPath.replace("/", ".").replace("-", "_") + "Kt"
        val functionName = cleanPath.substringAfterLast("/").replaceFirstChar { it.lowercase() }

        return runCatching {
            val clazz = Class.forName(fqcn)
            val methods = listOf(
                { clazz.getDeclaredMethod(functionName) },
                { clazz.getDeclaredMethod("main") },
                { clazz.getDeclaredMethod("main", Array<String>::class.java) }
            )

            for (methodGetter in methods) {
                runCatching { methodGetter() }.getOrNull()?.let { method ->
                    return {
                        if (method.parameterCount == 0) method.invoke(null)
                        else method.invoke(null, emptyArray<String>())
                    }
                }
            }
            null
        }.getOrElse {
            println("[DEBUG_LOG] PreviewWindow: Error resolving $path: ${it.message}")
            null
        }
    }

    var isDragging = false
    private var dragOffset = Vector2.ZERO

    // NOTE. Window Title Area Height
    private val titleBarHeight = 46.0 // 32.0
    private val font =
        program.loadFont("data/fonts/PPWatch-Medium.otf", 24.0, contentScale = 2.0)
    private val directoryFont =
        program.loadFont("data/fonts/PPWatch-Medium.otf", 14.0, contentScale = 2.0)

    init {
        program.mouse.buttonDown.listen {
            if (Rectangle(x, y - titleBarHeight, width, titleBarHeight).contains(it.position)) {
                isDragging = true
                dragOffset = it.position - Vector2(x, y)
                it.cancelPropagation()
            } else if (Rectangle(x, y, width, height).contains(it.position)) {
                it.cancelPropagation()
            }
        }

        program.mouse.dragged.listen {
            if (isDragging) {
                x = it.position.x - dragOffset.x
                y = it.position.y - dragOffset.y
                it.cancelPropagation()
            }
        }

        program.mouse.buttonUp.listen {
            if (isDragging) {
                isDragging = false
                it.cancelPropagation()
            } else if (Rectangle(x, y, width, height).contains(it.position)) {
                onEnter(this)
                it.cancelPropagation()
            }
        }
    }

    var lastSeek = 0.0

    fun reset() {
        x = initialX
        y = initialY
        isDragging = false
        dragOffset = Vector2.ZERO
        lastSeek = 0.0

        if (videoDelegate.isInitialized()) {
            video.seek(0.0)
        }
    }

    fun draw(drawer: Drawer) {
        drawer.isolated {
            drawer.defaults()
            drawer.translate(x, y)

            // Window Frame & Content
            drawer.stroke = ColorRGBa.WHITE
            drawer.fill = ColorRGBa.WHITE
            drawer.rectangle(0.0, 0.0, this@PreviewWindow.width, this@PreviewWindow.height)

            if (video.position > video.duration - 1.0 && program.seconds - lastSeek > 1.0) {
                video.seek(0.0)
                lastSeek = program.seconds
            }

            //video.draw(drawer, 0.0, 0.0, this@PreviewWindow.width, this@PreviewWindow.height)
            video.draw {
                drawer.imageFit(it, Rectangle(0.0, 0.0, this@PreviewWindow.width, this@PreviewWindow.height))
            }

            // Title Bar
            drawer.fill = ColorRGBa.WHITE
            drawer.rectangle(0.0, -titleBarHeight, this@PreviewWindow.width, titleBarHeight)

            // Title Text
            drawer.fill = ColorRGBa.BLACK
            drawer.fontMap = font
            val yPosOfTitleText = -24.0
            drawer.text(title, 8.0, if (directory.isEmpty()) -10.0 else yPosOfTitleText)

            // Directory Text
            if (directory.isNotEmpty()) {
                drawer.fill = ColorRGBa.BLACK.opacify(0.8)
                drawer.fontMap = directoryFont
                // Note. y position
                val yPosOfDirectoryText = -8.0
                drawer.text(directory, 8.0, yPosOfDirectoryText)
            }

            // Outline]
            drawer.strokeWeight = 0.8
            drawer.fill = ColorRGBa.TRANSPARENT
            drawer.rectangle(0.0, -titleBarHeight, this@PreviewWindow.width, this@PreviewWindow.height + titleBarHeight)
        }
    }
}
