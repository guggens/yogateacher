import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component

@SpringBootApplication
class YogaTeacherApp

fun main(args: Array<String>) {
    runApplication<YogaTeacherApp>(*args)
}

@Component
class YogaTeacherRunner(
    private val tts: GoogleTtsService,
    private val spotifyAuth: SpotifyAuthService,
    private val lessonGenerator: LessonGeneratorService,
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        println("🧘 Yoga Teacher — deutsche Stimme bereit (de-DE-Wavenet-C)")

        if (spotifyAuth.isConfigured) {
            try {
                val token = spotifyAuth.getAccessToken()
                SpotifyPlayerService(token).startYogaPlaylist()
            } catch (e: Exception) {
                System.err.println("⚠️  Spotify konnte nicht gestartet werden: ${e.message}")
            }
        } else {
            println("ℹ️  Spotify nicht konfiguriert (SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET fehlen)")
        }

        println("\nBefehle: 'lesson <minuten>' startet eine generierte Stunde, 'quit' beendet das Programm.\n")

        while (true) {
            print("> ")
            val line = readLine()?.trim() ?: break
            when {
                line.equals("quit", ignoreCase = true) || line.equals("exit", ignoreCase = true) -> break
                line.startsWith("lesson", ignoreCase = true) -> runLesson(line)
                line.isEmpty() -> continue
                else -> speakInstruction(line)
            }
        }

        println("Namasté 🙏")
    }

    private fun runLesson(command: String) {
        val minutes = command.removePrefix("lesson").trim().toIntOrNull() ?: 30
        println("🌀 Generiere ${minutes}-minütige Yoga-Stunde mit Claude...")
        try {
            val lesson = lessonGenerator.generate(minutes)
            println("✨ ${lesson.title} — ${lesson.theme}\n")

            for (segment in lesson.segments) {
                speakInstruction(segment.instruction)
                if (segment.hold_seconds > 0) {
                    Thread.sleep(segment.hold_seconds * 1000L)
                }
            }
            println("\n🙏 Stunde beendet.")
        } catch (e: Exception) {
            System.err.println("Fehler beim Generieren der Stunde: ${e.message}")
        }
    }

    private fun speakInstruction(text: String) {
        try {
            val audio = tts.synthesize(text)
            AudioPlayer.play(audio)
        } catch (e: Exception) {
            System.err.println("TTS-Fehler: ${e.message}")
        }
    }
}
