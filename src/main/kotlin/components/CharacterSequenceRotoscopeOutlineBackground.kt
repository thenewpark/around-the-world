package components

import components.rotoscope.AnalyzedFrame
import components.rotoscope.ContourExtractor
import components.rotoscope.MaskProcessor
import components.rotoscope.RotoscopeRenderer
import components.rotoscope.SegmentStyleMapper
import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.shape.Rectangle
import javax.imageio.ImageIO

private const val OUTLINE_BACKGROUND_SEQUENCE_FPS = 24.0
private const val OUTLINE_BACKGROUND_ANALYSIS_CACHE_SIZE = 20
private const val OUTLINE_BACKGROUND_LINE_OPACITY = 0.24
private const val OUTLINE_BACKGROUND_CLIP_INSET_PX = 5.0

class CharacterSequenceRotoscopeOutlineBackground(
    private val program: Program,
    rootDir: String
) {
    private val displaySlotOverrides = mapOf(
        "Robots" to CharacterSequenceSlot(org.openrndr.math.Vector2(0.5, 0.56), 0.352)
    )

    private val sequences = CharacterSequenceCatalog.load(rootDir)
    private val maskProcessor = MaskProcessor()
    private val contourExtractor = ContourExtractor()
    private val rotoscopeRenderer = RotoscopeRenderer(
        font = program.loadFont("data/fonts/default.otf", 16.0)
    )
    private val styleMapper = SegmentStyleMapper()

    private val analysisCache = linkedMapOf<String, AnalyzedFrame>()

    private var activeSelectionLabel: String? = null
    private var selectedAtPlaybackTime = 0.0

    fun draw(selectedLabel: String?, playbackTime: Double) {
        if (selectedLabel != activeSelectionLabel) {
            activeSelectionLabel = selectedLabel
            selectedAtPlaybackTime = playbackTime
        }

        val labels = if (selectedLabel == null || selectedLabel == "All") {
            sequences.keys.filter { it != "All" }
        } else {
            listOf(selectedLabel)
        }

        for (label in labels) {
            val sequence = sequences[label] ?: continue
            val frameIndex = frameIndex(sequence, selectedLabel, playbackTime)
            val analyzedFrame = analyzeFrame(sequence, frameIndex) ?: continue
            val slot = displaySlotOverrides[sequence.label] ?: sequence.slot
            val target = targetRectangle(slot, analyzedFrame)

            drawRotoscopeLines(analyzedFrame, sequence.label, target)
        }
    }

    private fun frameIndex(
        sequence: CharacterSequenceData,
        selectedLabel: String?,
        playbackTime: Double
    ): Int {
        val time = if (selectedLabel == null) {
            playbackTime + sequence.phaseOffsetSeconds
        } else {
            (playbackTime - selectedAtPlaybackTime).coerceAtLeast(0.0)
        }
        return ((time * OUTLINE_BACKGROUND_SEQUENCE_FPS).toInt()).mod(sequence.frames.size)
    }

    private fun analyzeFrame(sequence: CharacterSequenceData, frameIndex: Int): AnalyzedFrame? {
        val file = sequence.frames.getOrNull(frameIndex) ?: return null
        val key = "${sequence.label}:${file.name}:analysis"
        analysisCache[key]?.let { cached ->
            analysisCache.remove(key)
            analysisCache[key] = cached
            return cached
        }

        val image = ImageIO.read(file) ?: return null
        val mask = maskProcessor.process(image)
        val contours = contourExtractor.extract(mask)
        val analyzedFrame = AnalyzedFrame(
            frameIndex = frameIndex,
            sourceWidth = image.width,
            sourceHeight = image.height,
            mask = mask,
            contours = contours
        )
        analysisCache[key] = analyzedFrame
        evictAnalysisCache()
        return analyzedFrame
    }

    private fun drawRotoscopeLines(frame: AnalyzedFrame, label: String, target: Rectangle) {
        val style = styleMapper.styleFor(Segment(label, 0.0, 0.0)).copy(
            outlineLayers = 1,
            strokeWeight = 0.40, // NOTE. STROKE WIDTH
            jitterAmount = 0.2,
            alpha = OUTLINE_BACKGROUND_LINE_OPACITY
        )

        rotoscopeRenderer.drawRotoscopeLayerInBounds(
            drawer = program.drawer,
            frame = frame,
            style = style,
            targetBounds = target,
            clipInsetPx = OUTLINE_BACKGROUND_CLIP_INSET_PX,
            clearBackground = false,
            lineColor = ColorRGBa.WHITE.opacify(0.92)
        )
    }

    private fun targetRectangle(slot: CharacterSequenceSlot, frame: AnalyzedFrame): Rectangle {
        val targetHeight = program.height * slot.heightFactor
        val aspect = frame.sourceWidth.toDouble() / frame.sourceHeight.toDouble()
        val targetWidth = targetHeight * aspect
        val centerX = program.width * slot.center.x
        val centerY = program.height * slot.center.y
        return Rectangle(
            centerX - targetWidth * 0.5,
            centerY - targetHeight * 0.5,
            targetWidth,
            targetHeight
        )
    }

    private fun evictAnalysisCache() {
        while (analysisCache.size > OUTLINE_BACKGROUND_ANALYSIS_CACHE_SIZE) {
            val eldest = analysisCache.entries.first()
            analysisCache.remove(eldest.key)
        }
    }
}
