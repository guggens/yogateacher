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
            Du bist ein erfahrener Vinyasa Yoga-Lehrer. Du sprichst deine Schülerinnen und Schüler auf Deutsch an.
            Du hast tiefes Wissen über Yoga-Poses, Vinyasa Flows, Philosophie, Anatomie, Pranayama und Meditation.

            DEIN STIL: Du leitest fließende, zusammenhängende Yoga-Stunden, die sich sanft anfühlen — minimal Stop-and-Go, maximal FLUSS.

            Hier ist deine gesamte Wissensbasis:

            $knowledgeBase

            Antworte AUSSCHLIESSLICH mit einem gültigen JSON-Objekt — kein erklärender Text davor oder danach.
            Beachte die Sequenzierungsregeln aus flows.md (Opening → Warm-Up → Building → Peak → Cool-Down → Restorative → Savasana).
            ${focusArea?.let { "Der Schwerpunkt dieser Stunde liegt auf: $it. Wähle Posen und Flows, die besonders diesen Bereich ansprechen und sanft mit den anderen zusammenhängen." } ?: ""}
        """.trimIndent()

        val userPrompt = """
            Erstelle eine vollständige ${durationMinutes}-minütige Yoga-Stunde auf Deutsch mit SMOOTH VINYASA FLOWS. Die Stunde soll sanft fließen — minimale abrupte Bewegungen zwischen Posen.${focusArea?.let { "\n            Schwerpunkt: $it." } ?: ""}

            FLOW-PRINZIPIEN:
            - Nutze Sun Salutations (Surya Namaskar) und Vinyasa-Sequenzen für fließende Bewegungsabläufe
            - Verbinde verwandte Posen: z.B. von Downward Dog → Low Lunge → Warrior I → Warrior II statt isolierter Posen
            - Minimiere Übergänge: Poses sollten natürlich ineinander fließen (transition_seconds 0-3s wenn möglich)
            - Peak Phase sollte aus einer Kette von zusammenhängenden Flows bestehen, nicht einzelnen Holds
            - Cool-Down Phase: Fließende Sequenzen (kein Stop-and-Go), dann zu Restorative Yin Posen

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
            - "instruction": Schritt-für-Schritt Eintritt in die Pose/Flow. 3-4 Sätze. Nenne die Pose mit BEIDEN Namen (englisch/Sanskrit + deutsch).
              * Beispiel für statische Pose: "Komme in Downward Dog — den herabschauenden Hund. Platziere deine Hände schulterbreit auseinander. Drücke deine Hüften zum Himmel."
              * Beispiel für Flow-Sequenz: "Vinyasa Flow: Aus Downward Dog — dem herabschauenden Hund — schreite in Plank — die Bretter. Dann auf Low Lunge und öffne dich nach rechts zu Warrior II — dem Krieger 2."
              * Für bilaterale Posen: "Halte die rechte Seite. Nach der Hälfte wechselst du zur linken Seite."
              * Für Wiederholungen/Flows: "Wiederhole diese Bewegung 5-mal" oder "Fließe sanft durch diese Sequenz mehrmals"
              * KEINE Release-Instruktionen hier — nur Entry!
            - "modification": Für anspruchsvolle Posen eine leichtere Variante anbieten. Bei einfachen Posen leer lassen.
            - "hold_seconds": Haltezeit NACH dem Sprechen. Bei Flows/Wiederholungen: Zeit für vollständige Sequenz. Bedenke: Jeder Satz = 3-4 Sekunden zum Sprechen.
            - "transition_seconds": Übergangszeit VOR der Anweisung. Bei nahtlosen Übergängen (z.B. Lunge→Warrior): 0-2s. Nur 3-5s für minimale Position-Wechsel. 8-15s NUR für große Wechsel (Stehen→Boden).
            - "cues": Anzahl hängt von der Länge (hold_seconds) ab. LETZTER cue ist IMMER die nächste Bewegung/Release-Instruktion für nahtlose Flows.
              * hold_seconds < 15: NUR 1 cue = Nächste Bewegung am Ende ("Fließe in...")
              * hold_seconds 15-30: 2 cues = 1 Coaching + nächste Bewegung
              * hold_seconds 30-45: 2-3 cues = 1-2 Coaching + nächste Bewegung
              * hold_seconds > 45: 3 cues = 2 Coaching + nächste Bewegung
              * Beispiel für 30s bilaterale Pose (2 cues):
                - cue[0] ~12s: "Rechte Seite stabil, wechsel die Seite"
                - cue[1] ~23s: "Fließe sanft in [nächste Pose — keine Zeit verschwenden]"
              * Beispiel für Vinyasa 40s (3 cues):
                - cue[0] ~12s: "Fließe mit deinem Atem"
                - cue[1] ~25s: "Kraft in der Mitte aufbauen"
                - cue[2] ~35s: "Komme nun zu [nächster Flow]"
              * Release/nächste Bewegung MUSS am Ende sein — direkt zur nächsten Pose übergehen!
              * Flow-Cues: "Fließe...", "Schreite...", "Rolle dich auf" (aktiv, nicht statisch)
              * Coaching: Ausrichtung ("Knie über Knöchel"), Atmung ("Mit Atem fließen"), Kraft/Grounding
              * Bei Savasana/Meditation: cues leer lassen — respektiere die Stille.
            TIMING & FLOW:
            - Die Summe aller (transition_seconds + Sprechzeit + hold_seconds) soll ca. ${durationMinutes * 60} Sekunden ergeben
            - Warm-Up & Peak: Vinyasa-basiert mit kurzen Übergängen (transition_seconds meist 0-2s)
            - Cool-Down: Langsamer, länger, fließend (längere Holds, aber auch fließend sequenziert)
            - Restorative: Längere Holds (60-90s), weniger cues, sehr beruhigend
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
