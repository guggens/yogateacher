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
    val hold_seconds: Int,
)

@Service
class LessonGeneratorService(chatClientBuilder: ChatClient.Builder) {

    private val chatClient = chatClientBuilder.build()
    private val knowledgeBase: String by lazy { loadKnowledgeBase() }

    fun generate(durationMinutes: Int): YogaLesson {
        val systemPrompt = """
            Du bist ein erfahrener Yoga-Lehrer. Du sprichst deine Schülerinnen und Schüler auf Deutsch an.
            Du hast tiefes Wissen über Yoga-Poses, Flows, Philosophie, Anatomie, Pranayama und Meditation.
            
            Hier ist deine gesamte Wissensbasis:
            
            $knowledgeBase
            
            Antworte AUSSCHLIESSLICH mit einem gültigen JSON-Objekt — kein erklärender Text davor oder danach.
            Beachte die Sequenzierungsregeln aus flows.md (Opening → Warm-Up → Building → Peak → Cool-Down → Restorative → Savasana).
        """.trimIndent()

        val userPrompt = """
            Erstelle eine vollständige ${durationMinutes}-minütige Yoga-Stunde auf Deutsch.
            
            Antworte mit einem JSON-Objekt in exakt diesem Format:
            {
              "title": "...",
              "theme": "...",
              "duration_minutes": $durationMinutes,
              "segments": [
                {
                  "phase": "opening|warm_up|standing|peak|cool_down|restorative|savasana",
                  "instruction": "Gesprochene Anweisung auf Deutsch (2-4 Sätze)",
                  "hold_seconds": 30
                }
              ]
            }
            
            Wichtig:
            - Alle "instruction"-Texte auf Deutsch — ruhig, warm, einladend
            - "hold_seconds" ist die Haltedauer in Sekunden nach dem Sprechen der Anweisung
            - Schließe mit einer Meditation oder Savasana ab (mindestens 3 Minuten)
            - Die Summe aller (Sprechzeit + hold_seconds) soll ca. ${durationMinutes * 60} Sekunden ergeben
        """.trimIndent()

        val raw = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .content()
            ?: error("Keine Antwort von Claude erhalten")

        val json = extractJson(raw)
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<YogaLesson>(json)
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
