package audio

class MultiChannelVorbisTrack(val tracks: List<VorbisTrack>) {

    fun play() {
        tracks.forEach { it.play() }
    }

    fun stop() {
        tracks.forEach { it.stop() }
    }

    fun pause() {
        tracks.forEach { it.pause() }
    }

    fun resume() {
        tracks.forEach { it.resume() }
    }

    fun setPosition(timeInSeconds: Double) {
        tracks.forEach { it.setPosition(timeInSeconds) }
    }

    fun position(): Double {
        return tracks.map { it.position() }.average()
    }
}

fun loadMultiAudio(vararg paths: String): MultiChannelVorbisTrack {
    return MultiChannelVorbisTrack(paths.map { loadAudio(it) })
}
