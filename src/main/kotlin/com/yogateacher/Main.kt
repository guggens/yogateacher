package com.yogateacher

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

    private var spotifyPlayer: SpotifyPlayerService? = null

    override fun run(vararg args: String?) {
        println("🧘 Yoga Teacher — deutsche Stimme bereit (de-DE-Chirp3-HD)")

        if (spotifyAuth.isConfigured) {
            try {
                val token = spotifyAuth.getAccessToken()
                spotifyPlayer = SpotifyPlayerService(token)
                spotifyPlayer!!.startYogaPlaylist()
            } catch (e: Exception) {
                System.err.println("⚠️  Spotify konnte nicht gestartet werden: ${e.message}")
                spotifyPlayer = null
            }
        } else {
            println("ℹ️  Spotify nicht konfiguriert (SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET fehlen)")
        }

        val commandLine = args.filterNotNull().joinToString(" ").trim()
        if (commandLine.isNotEmpty()) {
            // Non-interactive: run the single command and exit
            dispatch(commandLine)
            println("Namasté 🙏")
            return
        }

        println("\nBefehle:")
        println("  lesson <minuten> [fokus]  — Stunde generieren, speichern und abspielen")
        println("  play <datei>              — Gespeicherte Stunde abspielen (z.B. 'play lessons/2026-04-06_10-00-00.json')")
        println("  test <minuten> [fokus]    — Nur Text ausgeben, nicht abspielen")
        println("  quit                      — Beenden\n")

        while (true) {
            print("> ")
            val line = readLine()?.trim() ?: break
            when {
                line.equals("quit", ignoreCase = true) || line.equals("exit", ignoreCase = true) -> break
                line.isEmpty() -> continue
                else -> dispatch(line)
            }
        }

        println("Namasté 🙏")
    }

    private fun dispatch(line: String) {
        val lower = line.lowercase()
        when {
            lower.startsWith("test") -> runLesson(line.drop(4).trim(), textOnly = true)
            lower.startsWith("play") -> playFromFile(line.drop(4).trim())
            lower.startsWith("lesson") -> runLesson(line.drop(6).trim())
            else -> speakInstruction(line)
        }
    }

    private fun runLesson(args: String, textOnly: Boolean = false) {
        val parts = args.split("\\s+".toRegex(), limit = 2)
        val minutes = parts.firstOrNull()?.toIntOrNull() ?: 30
        val focusArea = if (parts.size > 1 && parts[0].toIntOrNull() != null) parts[1] else null
        val focusInfo = focusArea?.let { " (Fokus: $it)" } ?: ""
        println("🌀 Generiere ${minutes}-minütige Yoga-Stunde${focusInfo} mit Claude...")
        try {
            val lesson = lessonGenerator.generate(minutes, focusArea)
            val savedPath = saveLesson(lesson, minutes, focusArea)
            println("✨ ${lesson.title} — ${lesson.theme}")
            println("💾 Gespeichert: $savedPath\n")
            playOrPrintLesson(lesson, textOnly)
        } catch (e: Exception) {
            System.err.println("Fehler beim Generieren der Stunde: ${e.message}")
        }
    }

    private fun playFromFile(filename: String) {
        val file = if (filename.isEmpty()) {
            // Pick the most recent lesson if no filename given
            File("lessons").listFiles { f -> f.extension == "json" }
                ?.maxByOrNull { it.lastModified() }
                ?: run { System.err.println("Keine Stunden im Ordner 'lessons' gefunden."); return }
        } else {
            File(filename)
        }
        if (!file.exists()) { System.err.println("Datei nicht gefunden: ${file.path}"); return }
        try {
            val lesson = lessonGenerator.fromJson(file.readText())
            println("▶️  ${lesson.title} — ${lesson.theme}\n")
            playOrPrintLesson(lesson, textOnly = false)
        } catch (e: Exception) {
            System.err.println("Fehler beim Laden der Stunde: ${e.message}")
        }
    }

    private fun saveLesson(lesson: YogaLesson, minutes: Int, focusArea: String?): String {
        val dir = File("lessons").apply { mkdirs() }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val focusPart = focusArea?.trim()?.replace("\\s+".toRegex(), "-") ?: ""
        val name = listOfNotNull(timestamp, "${minutes}min", focusPart.ifEmpty { null }).joinToString("_")
        val file = File(dir, "$name.json")
        file.writeText(lessonGenerator.toJson(lesson))
        return file.path
    }

    private fun playOrPrintLesson(lesson: YogaLesson, textOnly: Boolean) {
        if (!textOnly) {
            speakInstruction(lesson.title)
            speakInstruction(lesson.theme)
            speakInstruction("${lesson.duration_minutes} Minuten")
        }

        val cache: Map<String, String> = if (!textOnly && spotifyPlayer != null) {
            val phases = lesson.segments.map { it.phase }.toSet()
            println("Lade Playlists für ${phases.size} Phasen vor...")
            try {
                spotifyPlayer!!.prefetchPhasePlaylists(phases)
            } catch (e: Exception) {
                System.err.println("⚠️  Playlist-Vorladung fehlgeschlagen: ${e.message}")
                emptyMap()
            }
        } else emptyMap()

        var currentPhase: String? = null
        for (segment in lesson.segments) {
            if (textOnly) {
                printSegment(segment)
            } else {
                playSegment(segment, currentPhase, cache)
                currentPhase = segment.phase
            }
        }
        println("\n🙏 Stunde beendet.")
    }

    private fun playSegment(segment: LessonSegment, previousPhase: String?, cache: Map<String, String>) {
        val phaseChanged = segment.phase != previousPhase && spotifyPlayer != null && cache.isNotEmpty()

        // 1. Capture current volume and duck before playlist switch
        var previousVolume: Int? = null
        if (phaseChanged) {
            previousVolume = spotifyPlayer!!.getCurrentVolume()
            spotifyPlayer!!.setVolume(30)
        }

        // 2. First half of transition — current music continues (ducked) while moving
        if (segment.transition_seconds > 0) {
            Thread.sleep(segment.transition_seconds * 500L)
        }

        // 3. Switch playlist mid-transition
        if (phaseChanged) {
            try {
                spotifyPlayer!!.switchToPhase(segment.phase, cache)
            } catch (e: Exception) {
                System.err.println("⚠️  Playlist-Wechsel fehlgeschlagen (${segment.phase}): ${e.message}")
            }
        }

        // 4. Second half of transition — new music plays while settling into position
        if (segment.transition_seconds > 0) {
            Thread.sleep(segment.transition_seconds * 500L)
        }

        // 5. Restore volume
        if (phaseChanged && previousVolume != null) {
            spotifyPlayer!!.setVolume(previousVolume)
        }

        // 6. Main instruction
        speakInstruction(segment.instruction)

        // 7. Modification for difficult poses
        if (segment.modification.isNotBlank()) {
            speakInstruction(segment.modification)
        }

        // 8. Coaching cues evenly spaced during hold, or silent hold if no cues
        if (segment.hold_seconds > 0) {
            if (segment.cues.isNotEmpty()) {
                val intervalMs = (segment.hold_seconds * 1000L) / (segment.cues.size + 1)
                for (cue in segment.cues) {
                    Thread.sleep(intervalMs)
                    speakInstruction(cue)
                }
                Thread.sleep(intervalMs)
            } else {
                Thread.sleep(segment.hold_seconds * 1000L)
            }
        }
    }

    private fun printSegment(segment: LessonSegment) {
        if (segment.transition_seconds > 0) {
            println("[${segment.phase}] ⏳ ${segment.transition_seconds}s Übergang")
        } else {
            println("[${segment.phase}]")
        }
        println(segment.instruction)
        if (segment.modification.isNotBlank()) {
            println("  💡 ${segment.modification}")
        }
        for ((i, cue) in segment.cues.withIndex()) {
            val atSeconds = (segment.hold_seconds * (i + 1)) / (segment.cues.size + 1)
            println("  📌 \"$cue\" (nach ${atSeconds}s)")
        }
        if (segment.hold_seconds > 0) {
            println("  ⏱ ${segment.hold_seconds}s Halten")
        }
        println()
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
