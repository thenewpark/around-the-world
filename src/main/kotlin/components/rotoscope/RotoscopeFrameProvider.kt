package components.rotoscope

data class AnalyzedFrame(
    val frameIndex: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val mask: BinaryMask,
    val contours: List<Contour>
)

class RotoscopeFrameProvider(
    private val frameSource: MaskFrameSource,
    private val maskProcessor: MaskProcessor,
    private val contourExtractor: ContourExtractor
) {
    private val analyzedCache = linkedMapOf<Int, AnalyzedFrame>()

    fun get(frameIndex: Int): AnalyzedFrame? {
        val clamped = frameIndex.coerceIn(frameSource.minimumFrameIndex, frameSource.maximumFrameIndex)
        analyzedCache[clamped]?.let { cached ->
            analyzedCache.remove(clamped)
            analyzedCache[clamped] = cached
            return cached
        }

        val image = frameSource.loadBuffered(clamped) ?: return null
        val mask = maskProcessor.process(image)
        val contours = contourExtractor.extract(mask)
        val analyzedFrame = AnalyzedFrame(
            frameIndex = clamped,
            sourceWidth = image.width,
            sourceHeight = image.height,
            mask = mask,
            contours = contours
        )
        analyzedCache[clamped] = analyzedFrame
        evictCache()
        return analyzedFrame
    }

    private fun evictCache() {
        while (analyzedCache.size > RotoscopeConfig.ANALYZED_CACHE_SIZE) {
            val eldestKey = analyzedCache.entries.first().key
            analyzedCache.remove(eldestKey)
        }
    }
}
