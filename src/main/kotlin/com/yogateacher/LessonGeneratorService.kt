package com.yogateacher

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service

@Serializable
data class YogaLesson(
    val title: String,
    val theme: String,
    val duration_minutes: Int,
    val segments: List<FlowSegment>,
)

@Serializable
data class FlowSegment(
    @SerialName("p") val phase: String,
    @SerialName("n") val name: String,
    @SerialName("t") val transition_seconds: Int = 5,
    @SerialName("mod") val modification: String = "",
    @SerialName("s") val steps: List<FlowStep>,
    @SerialName("rel") val release: String = "",
    @SerialName("rh") val release_hold_seconds: Int = 0,
    @SerialName("rep") val repeat: Int = 1,
    @SerialName("rc") val repeat_cue: String = "",
)

@Serializable
data class FlowStep(
    @SerialName("i") val instruction: String,
    @SerialName("h") val hold_seconds: Int,
    @SerialName("c") val cues: List<String> = emptyList(),
)

@Service
class LessonGeneratorService(chatClientBuilder: ChatClient.Builder) {

    private val chatClient = chatClientBuilder.build()
    private val knowledgeBase: String by lazy { loadKnowledgeBase() }
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = true }

    fun toJson(lesson: YogaLesson): String = json.encodeToString(YogaLesson.serializer(), lesson)
    fun fromJson(jsonStr: String): YogaLesson = json.decodeFromString(YogaLesson.serializer(), jsonStr)

    fun generate(durationMinutes: Int, focusArea: String? = null): YogaLesson {
        val systemPrompt = """
            Du bist ein erfahrener Vinyasa Yoga-Lehrer. Du sprichst deine Schülerinnen und Schüler auf Deutsch an.
            Du hast tiefes Wissen über Yoga-Poses, Vinyasa Flows, Philosophie, Anatomie, Pranayama und Meditation.

            DEIN STIL: Du leitest fließende, zusammenhängende Yoga-Stunden aus FLOWS von 3-7 verbundenen Asanas — minimal Stop-and-Go, maximal FLUSS. Keine isolierten Einzelposen.

            Hier ist deine gesamte Wissensbasis:

            $knowledgeBase

            Antworte AUSSCHLIESSLICH mit einem gültigen JSON-Objekt — kein erklärender Text davor oder danach.
            Beachte die Sequenzierungsregeln aus flows.md (Opening → Warm-Up → Building → Peak → Cool-Down → Restorative → Savasana).
            ${focusArea?.let { "Der Schwerpunkt dieser Stunde liegt auf: $it. Wähle Posen und Flows, die besonders diesen Bereich ansprechen und sanft mit den anderen zusammenhängen." } ?: ""}
        """.trimIndent()

        val userPrompt = """
            Erstelle eine vollständige ${durationMinutes}-minütige Vinyasa-Yoga-Stunde auf Deutsch.${focusArea?.let { "\nSchwerpunkt: $it." } ?: ""}

            STRUKTUR: Jedes Segment ist ein BENANNTER FLOW aus 1-7 verbundenen Asanas (steps), NICHT eine einzelne Pose.
            Denke in Flows: Krieger-Flow (Low Lunge → Warrior I → Warrior II → Extended Side Angle), Sonnengruß, Rückbeuge-Flow, etc.

            JSON-FORMAT mit kompakten Schlüsseln (Felder mit Standardwert WEGLASSEN):
            {
              "title": "...",
              "theme": "...",
              "duration_minutes": $durationMinutes,
              "segments": [
                {
                  "p": "opening|warm_up|standing|peak|cool_down|restorative|savasana",
                  "n": "Flow-Name",
                  "t": 5,
                  "mod": "Leichtere Variante (1-2 Sätze) — weglassen bei einfachen Flows",
                  "s": [
                    {"i": "Anweisung für diese Pose im Flow (2-3 Sätze)", "h": 20, "c": ["Coaching-Cue"]},
                    {"i": "Nächste Pose im Flow", "h": 15}
                  ],
                  "rel": "Explizite Release/Exit-Anweisung nach dem Flow",
                  "rh": 10,
                  "rep": 2,
                  "rc": "Jetzt die linke Seite."
                }
              ]
            }

            SCHLÜSSEL:
            - "p": Phase (required)
            - "n": Flow-Name (required)
            - "t": Übergangszeit in Sekunden VOR dem Flow (Standard: 5, weglassen wenn 5)
            - "mod": Modification (Standard: "", weglassen wenn leer)
            - "s": Steps-Array mit 1-7 Poses (required)
              - "i": Anweisung — Eintritt in die Pose, 2-3 Sätze, BEIDE Namen (englisch/Sanskrit + deutsch)
              - "h": Haltezeit in Sekunden NACH dem Sprechen
              - "c": Coaching-Cues während des Haltens (Standard: [], weglassen wenn leer). NUR Coaching — KEINE Release/Transition-Anweisungen!
            - "rel": Release-Anweisung nach allen Steps (Standard: "", weglassen wenn leer)
            - "rh": Haltezeit nach Release in Sekunden (Standard: 0, weglassen wenn 0)
            - "rep": Wiederholungen (Standard: 1, weglassen wenn 1). 2 = bilateral (rechts/links), 3+ = Wiederholungen
            - "rc": Ansage vor Runde 2+ (Standard: "", weglassen wenn rep=1)

            BEISPIEL — Bilateraler Krieger-Flow (rep=2):
            {"p":"peak","n":"Krieger-Flow","mod":"Hinteres Knie auf dem Boden für sanftere Variante.",
             "s":[
               {"i":"Aus Downward Dog schreite mit dem rechten Fuß nach vorne in Low Lunge — den tiefen Ausfallschritt.","h":10},
               {"i":"Hebe dich auf zu Warrior I — dem Krieger Eins. Arme nach oben, Hüften nach vorne.","h":20,"c":["Knie über dem Knöchel"]},
               {"i":"Öffne dich zu Warrior II — dem Krieger Zwei. Arme weit, Blick über die vordere Hand.","h":20,"c":["Schultern tief, Kraft in den Beinen"]},
               {"i":"Strecke dich in Extended Side Angle — den gestreckten Seitwinkel. Unterarm auf den Oberschenkel.","h":15,"c":["Öffne die Brust zum Himmel"]}
             ],
             "rel":"Löse dich, Hände zum Boden, schreite zurück und fließe durch einen Vinyasa.","rh":15,
             "rep":2,"rc":"Wunderschön. Dasselbe auf der linken Seite."}

            BEISPIEL — Sonnengruß A (rep=3):
            {"p":"warm_up","n":"Sonnengruß A","t":3,
             "s":[
               {"i":"Stehe in Tadasana — dem Berg. Hände vor dem Herzen, Füße zusammen.","h":5},
               {"i":"Einatmen, Arme nach oben — Urdhva Hastasana.","h":3},
               {"i":"Ausatmen, falte dich nach vorne — Uttanasana, die stehende Vorbeuge.","h":5},
               {"i":"Einatmen, halber Lift — Ardha Uttanasana. Flacher Rücken, Blick nach vorne.","h":3},
               {"i":"Ausatmen, schreite oder springe zurück in Chaturanga Dandasana.","h":3},
               {"i":"Einatmen, Upward Dog — der heraufschauende Hund. Brust nach vorne und oben.","h":5},
               {"i":"Ausatmen, Downward Dog — der herabschauende Hund. Fünf tiefe Atemzüge.","h":25,"c":["Drücke den Boden aktiv weg, Fersen streben zur Matte"]}
             ],
             "rep":3,"rc":"Einatmen, schreite nach vorne — nächste Runde."}

            BEISPIEL — Savasana (einzelne Pose):
            {"p":"savasana","n":"Savasana — Tiefenentspannung","t":10,
             "s":[{"i":"Lege dich flach auf den Rücken. Arme neben dem Körper, Handflächen nach oben. Schließe die Augen und lass alles los.","h":180}],
             "rel":"Bewege langsam deine Finger und Zehen. Rolle dich auf die rechte Seite.","rh":15}

            REGELN:
            - Alle Texte auf Deutsch — ruhig, warm, einladend
            - "i" (instruction): 2-3 Sätze. Nenne BEIDE Namen (englisch/Sanskrit + deutsch). NUR Eintritt in die Pose — keine Release-Anweisungen hier.
            - "c" (cues): REINE Coaching-Hinweise (Ausrichtung, Atmung, Kraft). NIEMALS Release/Transition!
              * h < 15: keine cues (schneller Flow-Schritt)
              * h 15-30: 0-1 cues
              * h 30-60: 1-2 cues
              * h > 60: 2-3 cues
              * Savasana/Meditation: KEINE cues — respektiere die Stille
            - "rel" (release): Explizite Exit-Anweisung am Ende des Flows. Wird NACH allen Steps gesprochen.
            - "rep" + "rc": Für bilaterale Flows (rep=2): rechte Seite zuerst in den Steps, rc kündigt die linke Seite an. Für Wiederholungen (rep=3+): rc kündigt die nächste Runde an.

            TIMING:
            - Gesamtzeit ≈ ${durationMinutes * 60}s. Formel pro Segment:
              t + (Sprechzeit mod) + rep × (Summe aller Steps(Sprechzeit i + h + Sprechzeit c) + Sprechzeit rel + rh) + (rep-1) × (Sprechzeit rc + t)
              Jeder Satz ≈ 4 Sekunden Sprechzeit.
            - t (transition): Nahtlose Übergänge 2-5s, Positionswechsel 5-8s, große Wechsel (Stehen→Boden) 10-15s
            - Opening: Meditation, Atemarbeit (8-10% der Zeit)
            - Warm-Up: Sonnengrüße, Cat-Cow (15-18%)
            - Standing/Peak: Vinyasa-Flows mit 3-7 verbundenen Asanas (40-50%)
            - Cool-Down: Langsamer, fließende Sequenzen (10-15%)
            - Restorative: Längere Holds 60-90s, wenige cues (5-9%)
            - Savasana: Mindestens 3 Minuten, KEINE cues, nur Stille
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
