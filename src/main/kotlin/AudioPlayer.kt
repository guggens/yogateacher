import javax.sound.sampled.AudioSystem
import java.io.ByteArrayInputStream

object AudioPlayer {

    fun play(wavBytes: ByteArray) {
        val audioInput = AudioSystem.getAudioInputStream(ByteArrayInputStream(wavBytes))
        val clip = AudioSystem.getClip()
        clip.open(audioInput)
        clip.start()
        // Block until playback finishes, then release the clip
        Thread.sleep(clip.microsecondLength / 1_000 + 300)
        clip.close()
    }
}
