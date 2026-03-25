package components

import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import java.io.File
import javax.imageio.ImageIO

data class CharacterSequenceSlot(
    val center: Vector2,
    val heightFactor: Double
)

data class CharacterSequenceData(
    val label: String,
    val frames: List<File>,
    val backgroundColor: ColorRGBa,
    val slot: CharacterSequenceSlot,
    val phaseOffsetSeconds: Double
)

object CharacterSequenceCatalog {
    val directoryByLabel = mapOf(
        "All" to "all",
        "Athletes" to "athlete",
        "Mummies" to "mummies",
        "Robots" to "robot",
        "Skeletons" to "skeleton",
        "Disco girls" to "disco girls"
    )

val slotByLabel = mapOf(
    "All" to CharacterSequenceSlot(Vector2(0.5, 0.56), 0.44),
    "Athletes" to CharacterSequenceSlot(Vector2(0.18, 0.24), 0.32),
    "Mummies" to CharacterSequenceSlot(Vector2(0.20, 0.78), 0.336),
    "Robots" to CharacterSequenceSlot(Vector2(0.80, 0.40), 0.368),
    "Skeletons" to CharacterSequenceSlot(Vector2(0.74, 0.74), 0.32),
    "Disco girls" to CharacterSequenceSlot(Vector2(0.80, 0.22), 0.304)
)

    private val phaseOffsetByLabel = mapOf(
        "All" to 0.0,
        "Athletes" to 0.8,
        "Mummies" to 1.6,
        "Robots" to 2.4,
        "Skeletons" to 3.2,
        "Disco girls" to 4.0
    )

    fun load(rootDir: String): Map<String, CharacterSequenceData> {
        return directoryByLabel.mapNotNull { (label, directoryName) ->
            val directory = File(rootDir, directoryName)
            if (!directory.exists() || !directory.isDirectory) {
                return@mapNotNull null
            }

            val frames = directory.listFiles()
                ?.filter { it.isFile && it.name.lowercase().endsWith(".jpg") }
                ?.sortedBy { it.name }
                .orEmpty()

            if (frames.isEmpty()) {
                return@mapNotNull null
            }

            val slot = slotByLabel[label] ?: return@mapNotNull null
            CharacterSequenceData(
                label = label,
                frames = frames,
                backgroundColor = estimateBackgroundColor(frames.first()),
                slot = slot,
                phaseOffsetSeconds = phaseOffsetByLabel[label] ?: 0.0
            )
        }.associateBy { it.label }
    }

    fun estimateBackgroundColor(file: File): ColorRGBa {
        val image = ImageIO.read(file) ?: return ColorRGBa.BLACK
        val margin = minOf(20, image.width / 8, image.height / 8).coerceAtLeast(1)
        val samples = mutableListOf<Int>()
        val corners = listOf(
            0 to 0,
            image.width - margin to 0,
            0 to image.height - margin,
            image.width - margin to image.height - margin
        )

        corners.forEach { (originX, originY) ->
            for (y in originY until originY + margin) {
                for (x in originX until originX + margin) {
                    samples += image.getRGB(x, y)
                }
            }
        }

        val red = samples.map { it shr 16 and 0xff }.average() / 255.0
        val green = samples.map { it shr 8 and 0xff }.average() / 255.0
        val blue = samples.map { it and 0xff }.average() / 255.0
        return ColorRGBa(red, green, blue, 1.0)
    }
}
