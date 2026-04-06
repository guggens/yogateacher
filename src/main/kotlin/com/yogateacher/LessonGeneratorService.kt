package com.yogateacher

import kotlinx.serialization.Serializable
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service

@Serializable
data class YogaLesson(
    val title: String,
    val theme: String,
    val duration_minutes: Int,
    val segments: List<LessonSegment>,
)

@Serializable
data class LessonSegment(
    val phase: String,
    val instruction: String,
    val modification: String = "",
    val hold_seconds: Int,
    val transition_seconds: Int = 5,
    val cues: List<String> = emptyList(),
)

@Service
class LessonGeneratorService(chatClientBuilder: ChatClient.Builder) {

    private val chatClient = chatClientBuilder.build()
    private val knowledgeBase: String by lazy { loadKnowledgeBase() }
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun toJson(lesson: YogaLesson): String = json.encodeToString(YogaLesson.serializer(), lesson)
    fun fromJson(jsonStr: String): YogaLesson = json.decodeFromString(YogaLesson.serializer(), jsonStr)

    fun generate(durationMinutes: Int, focusArea: String? = null): YogaLesson {
        val systemPrompt = """
            Du bist ein erfahrener Yoga-Lehrer. Du sprichst deine Schülerinnen und Schüler auf Deutsch an.
            Du hast tiefes Wissen über Yoga-Poses, Flows, Philosophie, Anatomie, Pranayama und Meditation.
            
            Hier ist deine gesamte Wissensbasis:
            
            $knowledgeBase
            
            Antworte AUSSCHLIESSLICH mit einem gültigen JSON-Objekt — kein erklärender Text davor oder danach.
            Beachte die Sequenzierungsregeln aus flows.md (Opening → Warm-Up → Building → Peak → Cool-Down → Restorative → Savasana).
            ${focusArea?.let { "Der Schwerpunkt dieser Stunde liegt auf: $it. Wähle Posen und Flows, die besonders diesen Bereich ansprechen." } ?: ""}
        """.trimIndent()

        val userPrompt = """
            Erstelle eine vollständige ${durationMinutes}-minütige Yoga-Stunde auf Deutsch.${focusArea?.let { "\n            Schwerpunkt: $it." } ?: ""}
            
            Antworte mit einem JSON-Objekt in exakt diesem Format:
            {
              "title": "...",
              "theme": "...",
              "duration_minutes": $durationMinutes,
              "segments": [
                {
                  "phase": "opening|warm_up|standing|peak|cool_down|restorative|savasana",
                  "instruction": "Detaillierte Anweisung (3-5 Sätze): Schritt-für-Schritt wie man in die Pose kommt, Ausrichtung, Atmung",
                  "modification": "Leichtere Alternative für Anfänger (1-2 Sätze) — leer lassen bei einfachen Posen",
                  "hold_seconds": 30,
                  "transition_seconds": 5,
                  "cues": ["Kurzer Coaching-Hinweis während des Haltens", "Noch ein Hinweis"]
                }
              ]
            }
            
            Wichtig:
            - Alle Texte auf Deutsch — ruhig, warm, einladend
            - "instruction": Detailliert! Beschreibe Schritt für Schritt, wie man in die Pose kommt. Nicht nur den Namen nennen, sondern anleiten. 3-5 Sätze.
            - "modification": Für anspruchsvolle Posen eine leichtere Variante anbieten ("Oder, wenn das zu intensiv ist: ..."). Bei einfachen Posen (Savasana, Sitzen, Katze/Kuh) leer lassen.
            - "hold_seconds": Reine Haltezeit NACH dem Sprechen der Anweisung. Bedenke: Jeder Satz braucht ca. 3-4 Sekunden zum Sprechen.
            - "transition_seconds": Stille Übergangszeit VOR der Anweisung — Zeit um physisch die Position zu wechseln. 3-5s bei kleinen Wechseln, 8-15s bei großen Wechseln (z.B. Stehen→Boden).
            - "cues": 1-3 kurze Coaching-Hinweise (je 1 Satz), die gleichmäßig verteilt während der Haltezeit gesprochen werden. Enthalten: Ausrichtungshinweise ("Knie über dem Knöchel"), Atemhinweise ("Atme tief in die Dehnung"), Motivation ("Du bist stärker als du denkst"). Bei Savasana/Meditation: cues leer lassen — respektiere die Stille.
            - Die Summe aller (transition_seconds + Sprechzeit + hold_seconds) soll ca. ${durationMinutes * 60} Sekunden ergeben
            - Schließe mit Savasana ab (mindestens 3 Minuten, keine cues — nur Stille)
        """.trimIndent()

        val raw = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .content()
            ?: error("Keine Antwort von Claude erhalten")

        return json.decodeFromString(YogaLesson.serializer(), extractJson(raw))
    }

    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "Kein JSON in der Antwort gefunden:\n$raw" }
        return raw.substring(start, end + 1)
    }

    private fun loadKnowledgeBase(): String {
        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath:yoga/*.md")
        return resources
            .filter { it.filename != "yoga_knowledge_base.md" } // skip the consolidated duplicate
            .sortedBy { it.filename }
            .joinToString("\n\n---\n\n") { res ->
                "## ${res.filename}\n\n${res.inputStream.bufferedReader().readText()}"
            }
    }
}
