package components.rotoscope

import org.openrndr.color.ColorRGBa

object RotoscopeConfig {
    const val DISPLAY_WIDTH = 1280
    const val DISPLAY_HEIGHT = 720

    const val FRAME_RATE = 24.0
    const val INITIAL_PLAYBACK_TIME = 1.0
    const val IMAGE_CACHE_SIZE = 12
    const val PREVIEW_CACHE_SIZE = 6
    const val ANALYZED_CACHE_SIZE = 18

    const val MASK_SAMPLE_STEP = 4
    const val MASK_THRESHOLD = 0.06
    const val MIN_COMPONENT_AREA = 18

    const val OUTLINE_LAYERS = 4
    const val STROKE_WEIGHT = 1.35
    const val JITTER_AMOUNT = 2.2
    const val CONTOUR_MIN_POINTS = 4

    const val DEBUG_MARGIN = 24.0
    const val SEEK_STEP_SECONDS = 1.0

    val BACKGROUND_COLOR = ColorRGBa.WHITE
    val LINE_COLOR = ColorRGBa.BLACK
    val DEBUG_TEXT_COLOR = ColorRGBa.BLACK.opacify(0.82)
    val DEBUG_PANEL_COLOR = ColorRGBa.WHITE.opacify(0.88)
    val MASK_FOREGROUND_COLOR = ColorRGBa.BLACK.opacify(0.92)
}
