package com.yogateacher

import java.io.File

object AudioPlayer {

    fun play(wavBytes: ByteArray) {
        val tmp = File.createTempFile("yoga-tts-", ".wav").also { it.deleteOnExit() }
        tmp.writeBytes(wavBytes)
        ProcessBuilder("paplay", tmp.absolutePath)
            .inheritIO()
            .start()
            .waitFor()
        tmp.delete()
    }
}
