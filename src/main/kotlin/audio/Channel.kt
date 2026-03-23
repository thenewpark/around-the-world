package audio

import org.openrndr.KEY_ARROW_LEFT
import org.openrndr.KEY_ARROW_RIGHT
import org.openrndr.KEY_SPACEBAR
import org.openrndr.Program

open class Channel() {
    open fun setPosition(seconds: Double) {

    }

    open fun setPitch(pitch: Double) {

    }

    open fun setVolume(volume: Double) {

    }

    open fun getPosition(): Double {
        return 0.0
    }

    open fun play(loop: Boolean) {
    }

    open fun stop() {

    }
    open fun pause() {
    }

    open fun resume() {
    }
}

class DummyChannel(val program: Program) : Channel() {
    var timeOffset = 0.0
    val oldClock = program.clock
    val newClock = {  if (paused) pauseTime else oldClock() - timeOffset }
    var paused = false
    var pauseTime = 0.0

    init {
        program.keyboard.keyDown.listen {
            if (it.key == KEY_ARROW_RIGHT) {
                timeOffset -= 1.0
            }
            if (it.key == KEY_ARROW_LEFT) {
                timeOffset += 1.0
            }

            if (it.key == KEY_SPACEBAR) {
                if (!paused) {
                    pauseTime = newClock()
                } else {
                    timeOffset = oldClock() - pauseTime
                }
                paused = !paused
            }
        }
        program.clock = newClock
    }

    override fun setPosition(seconds: Double) {
        timeOffset = oldClock() - seconds
        pauseTime = seconds
    }
}

class VorbisChannel(val vorbisTrack: VorbisTrack) : Channel() {

    var offset = 0.0
    var paused = false

    override fun setPosition(seconds: Double) {
        vorbisTrack.setPosition(seconds)
        offset = System.currentTimeMillis() / 1000.0 - seconds
    }

    override fun setPitch(pitch: Double) {
        vorbisTrack.pitch = pitch
    }

    override fun setVolume(volume: Double) {
        vorbisTrack.gain = volume
    }

    override fun getPosition(): Double {
        val trackPosition = vorbisTrack.position()
        val clockPosition = System.currentTimeMillis() / 1000.0 - offset

        if (paused || vorbisTrack.pitch != 1.0) {
            return trackPosition
        }

        val dt = trackPosition - clockPosition
        val tooMuch = 1.0/60.0
        if (dt < -tooMuch) {
            offset += tooMuch/ 2.0
        }
        if (dt > tooMuch) {
            offset -= tooMuch / 2.0
        }

        return clockPosition
    }

    override fun play(loop: Boolean) {
        offset = System.currentTimeMillis() / 1000.0
        vorbisTrack.play(loop =  loop)
    }

    override fun pause() {
        paused = true
        vorbisTrack.pause()
    }

    override fun resume() {
        paused = false
        offset = System.currentTimeMillis() / 1000.0 - vorbisTrack.position()
        vorbisTrack.resume()
    }

    override fun stop() {
        vorbisTrack.stop()
    }
}


fun Program.playMusic(
    path: String,
    timescale: Double = 1.0,
    scrubbable: Boolean = true,
    loop: Boolean = true,
    dummy: Boolean = false,
    playNow: Boolean = true
): Channel {

    if (!dummy) {
        println("starting music now")
        val track = VorbisTrack(path)
        val channel = VorbisChannel(track).apply {
            if (playNow) {
                play(loop)
            }
        }
        var pitch = 1.0
        var volume = 1.0
        var paused = false
        if (scrubbable) {
            keyboard.keyDown.listen {
                if (it.key == KEY_ARROW_RIGHT) {
                    channel.setPosition(channel.getPosition() + timescale)
                }
                if (it.key == KEY_ARROW_LEFT) {
                    channel.setPosition((channel.getPosition() - timescale).coerceAtLeast(0.0))
                }

                if (it.key == KEY_SPACEBAR) {
                    paused = !paused
                    if (paused) {
                        channel.pause()
                    } else {
                        channel.resume()
                    }
                }
            }
            keyboard.character.listen {
                if (it.character == 'q') {
                    pitch /= 2.0
                    channel.setPitch(pitch)
                }
                if (it.character == 'w') {
                    pitch *= 2.0
                    channel.setPitch(pitch)
                }
                if (it.character == 'm') {
                    volume = 1.0 - volume
                    channel.setVolume(volume)
                }
                if (it.character == 'p') {
                    paused = !paused
                    if (paused) {
                        channel.pause()
                    } else {
                        channel.resume()
                    }
                }
            }
            clock = { channel.getPosition() }
        }
        return channel
    } else {
        return Channel()
    }
}