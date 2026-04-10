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
    @SerialName("sr") val steps_round2: List<FlowStep> = emptyList(),
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

            KREATIVITÄT: Mindestens 1 Segment pro Stunde soll einen ungewöhnlichen oder selten unterrichteten Flow enthalten —
            z.B. Chandra Namaskar (Mondgruß), Skandasana-Flow, Camatkarasana-Sequenz (Wild Thing), Fallen Triangle,
            Twisted Chair Flow, Eidechsen-Flow (Lizard Variations), oder einen Flow aus themed_flows.md / extended_flows.md.
            Nicht immer die gleichen Standard-Flows wiederholen.

            JSON-FORMAT mit kompakten Schlüsseln (Felder mit Standardwert WEGLASSEN):
            {
              "title": "...",
              "theme": "...",
              "duration_minutes": $durationMinutes,
              "segments": [
                {
                  "p": "opening|warm_up|standing|peak|cool_down|restorative|savasana",
                  "n": "Flow-Name — wird laut angesagt, kurz und natürlich klingend",
                  "t": 5,
                  "mod": "Leichtere Variante (1-2 Sätze) — weglassen bei einfachen Flows",
                  "s": [
                    {"i": "Anweisung für diese Pose im Flow (2-3 Sätze)", "h": 20, "c": ["Coaching-Cue"]},
                    {"i": "Nächste Pose im Flow", "h": 15}
                  ],
                  "sr": [
                    {"i": "Kurze Anweisung für Runde 2+", "h": 15}
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
            - "n": Flow-Name (required). Wird laut über TTS angesagt — kurz, natürlich klingend (z.B. "Sonnengruß A", "Krieger-Tanz")
            - "t": Übergangszeit in Sekunden VOR dem Flow (Standard: 5, weglassen wenn 5)
            - "mod": Modification — leichtere Variante (Standard: "", weglassen wenn leer). Wird NICHT gesprochen, nur als Text angezeigt.
            - "s": Steps-Array mit 1-7 Poses (required) — vollständige Anweisungen für Runde 1
              - "i": Anweisung — Eintritt in die Pose, 2-3 Sätze, BEIDE Namen (englisch/Sanskrit + deutsch)
              - "h": Haltezeit in Sekunden NACH dem Sprechen
              - "c": Coaching-Cues während des Haltens (Standard: [], weglassen wenn leer). NUR Coaching — KEINE Release/Transition-Anweisungen!
            - "sr": Steps für Runde 2+ (Standard: [], weglassen wenn leer). Kürzere, schnellere Anweisungen für Wiederholungsrunden.
              Wenn rep >= 2 UND der Flow 3+ Steps hat: IMMER "sr" bereitstellen.
              Für bilateral (rep=2): sr enthält korrekte Seitenreferenzen (links statt rechts) UND kürzere Anweisungen (1 Satz pro Step).
              Für Wiederholungen (rep=3+): sr enthält nur Posenname + Atemanweisung, keine detaillierten Beschreibungen. Haltezeiten dürfen kürzer sein.
              Wenn "sr" fehlt, werden die vollständigen "s" Steps für alle Runden verwendet.
            - "rel": Release-Anweisung nach allen Steps (Standard: "", weglassen wenn leer)
            - "rh": Haltezeit nach Release in Sekunden (Standard: 0, weglassen wenn 0)
            - "rep": Wiederholungen (Standard: 1, weglassen wenn 1). 2 = bilateral (rechts/links), 3+ = Wiederholungen
            - "rc": Ansage vor Runde 2+ (Standard: "", weglassen wenn rep=1)

            BEISPIEL — Bilateraler Krieger-Flow (rep=2, mit sr für linke Seite):
            {"p":"peak","n":"Krieger-Flow","mod":"Hinteres Knie auf dem Boden für sanftere Variante.",
             "s":[
               {"i":"Aus Downward Dog schreite mit dem rechten Fuß nach vorne in Low Lunge — den tiefen Ausfallschritt.","h":10},
               {"i":"Hebe dich auf zu Warrior I — dem Krieger Eins. Arme nach oben, Hüften nach vorne.","h":18,"c":["Knie über dem Knöchel"]},
               {"i":"Öffne dich zu Warrior II — dem Krieger Zwei. Arme weit, Blick über die vordere Hand.","h":18,"c":["Schultern tief, Kraft in den Beinen"]},
               {"i":"Strecke dich in Extended Side Angle — den gestreckten Seitwinkel. Unterarm auf den Oberschenkel.","h":14,"c":["Öffne die Brust zum Himmel"]}
             ],
             "sr":[
               {"i":"Linker Fuß nach vorne, Low Lunge.","h":8},
               {"i":"Warrior I links — Arme hoch.","h":16},
               {"i":"Öffne dich zu Warrior II links.","h":16},
               {"i":"Extended Side Angle links.","h":12}
             ],
             "rel":"Löse dich, Hände zum Boden, schreite zurück und fließe durch einen Vinyasa.","rh":15,
             "rep":2,"rc":"Wunderschön. Dasselbe auf der linken Seite."}

            BEISPIEL — Sonnengruß A (rep=2, mit sr für verkürzte Runde):
            {"p":"warm_up","n":"Sonnengruß A","t":3,
             "s":[
               {"i":"Stehe in Tadasana — dem Berg. Hände vor dem Herzen, Füße zusammen.","h":5},
               {"i":"Einatmen, Arme nach oben — Urdhva Hastasana.","h":3},
               {"i":"Ausatmen, falte dich nach vorne — Uttanasana, die stehende Vorbeuge.","h":4},
               {"i":"Einatmen, halber Lift — Ardha Uttanasana. Flacher Rücken, Blick nach vorne.","h":3},
               {"i":"Ausatmen, schreite oder springe zurück in Chaturanga Dandasana.","h":3},
               {"i":"Einatmen, Upward Dog — der heraufschauende Hund. Brust nach vorne und oben.","h":4},
               {"i":"Ausatmen, Downward Dog — der herabschauende Hund. Fünf tiefe Atemzüge.","h":22,"c":["Fersen streben zur Matte"]}
             ],
             "sr":[
               {"i":"Einatmen, Arme hoch.","h":2},
               {"i":"Ausatmen, Vorbeuge.","h":2},
               {"i":"Einatmen, halber Lift.","h":2},
               {"i":"Ausatmen, Chaturanga.","h":2},
               {"i":"Einatmen, Upward Dog.","h":3},
               {"i":"Ausatmen, Downward Dog. Fünf Atemzüge.","h":22,"c":["Fersen zur Matte"]}
             ],
             "rep":2,"rc":"Einatmen, schreite nach vorne — nächste Runde."}

            BEISPIEL — Savasana (einzelne Pose, mit Abschluss):
            {"p":"savasana","n":"Savasana — Tiefenentspannung","t":10,
             "s":[{"i":"Lege dich flach auf den Rücken. Arme neben dem Körper, Handflächen nach oben. Schließe die Augen und lass alles los.","h":180}],
             "rel":"Bewege langsam deine Finger und Zehen. Rolle dich auf die rechte Seite. Komme in deinem Tempo zurück in den Sitz. Hände vor dem Herzen — Danke an deinen Körper für diese Praxis. Danke an dich selbst, dass du dir diese Zeit geschenkt hast. Namaste.","rh":15}

            REGELN:
            - Alle Texte auf Deutsch — ruhig, warm, einladend
            - "n" (name): Wird laut angesagt. Kurz und natürlich — kein "Neuer Flow:" Präfix.
            - "i" (instruction): 2-3 Sätze. Nenne BEIDE Namen (englisch/Sanskrit + deutsch). NUR Eintritt in die Pose — keine Release-Anweisungen hier.
            - "c" (cues): REINE Coaching-Hinweise (Ausrichtung, Atmung, Kraft). NIEMALS Release/Transition!
              * h < 15: keine cues (schneller Flow-Schritt)
              * h 15-22: 0-1 cues
              * h 22-50: 1-2 cues
              * h > 50: 2-3 cues
              * Savasana/Meditation: KEINE cues — respektiere die Stille
            - "rel" (release): Explizite Exit-Anweisung am Ende des Flows. Wird NACH allen Steps gesprochen.
            - "rep" + "rc": Für bilaterale Flows (rep=2): rechte Seite zuerst in "s", linke Seite in "sr", rc kündigt die linke Seite an. Für Wiederholungen (rep=3+): rc kündigt die nächste Runde an.

            WIEDERHOLUNGSLIMITS:
            - Einzelposen oder kurze Sequenzen (1-2 Steps): rep maximal 4
            - Vollständige Flows (3+ Steps, inkl. Sonnengrüße): rep maximal 2

            NAHTLOSE ÜBERGÄNGE:
            - Der erste Step jedes Flows MUSS von der Position ausgehen, in der die Release-Anweisung des vorherigen Flows den Schüler hinterlassen hat.
            - Wenn der vorherige Flow mit Downward Dog endet, muss der nächste Flow von Downward Dog ausgehen.
            - Der allererste Flow beginnt von Sukhasana/Stehen (Opening-Phase).
            - VERBOTENE DIREKTE ÜBERGÄNGE (diese Posen erfordern Zwischenschritte):
              * Downward Dog → Navasana (Boat): Erst durch Vorbeuge/Sitzen, dann Boat
              * Navasana → Downward Dog: Erst Hände zum Boden, Plank, dann Downward Dog
              * Stehende Pose → Bauchpose (Locust/Bow): Erst zum Boden kommen
              * Tiefe Rückbeuge → sofort tiefe Vorbeuge: Erst neutralisieren (Knie zur Brust)
              * Kopfstand → Stehen: Erst Child's Pose, dann aufrichten
            - Überprüfe JEDEN Übergang: Kann der Schüler physisch von der End-Position zur Start-Position gelangen?

            ABSCHLUSS: Die Release-Anweisung ("rel") des letzten Savasana-Segments MUSS mit Abschlussworten enden:
            Dankbarkeit an den Körper und sich selbst, dann Namaste. Beispiel: "...Danke an deinen Körper für diese Praxis. Danke an dich selbst. Hände vor dem Herzen. Namaste."

            TIMING:
            - Gesamtzeit ≈ ${durationMinutes * 60}s. Formel pro Segment:
              t + Sprechzeit(n) + 1 × (Summe s-Steps(Sprechzeit i + h + Sprechzeit c) + Sprechzeit rel + rh) + (rep-1) × (Sprechzeit rc + t + Summe sr-Steps(Sprechzeit i + h + Sprechzeit c) + Sprechzeit rel + rh)
              Jeder Satz ≈ 4 Sekunden Sprechzeit. Wenn sr leer: sr-Steps = s-Steps.
            - HALTEZEITEN (lieber etwas kürzer — der Fluss lebt von Bewegung):
              * Flow-Schritte (1 Atemzug): h = 2-4s
              * Kurze Halte (3 Atemzüge): h = 8-12s
              * Standard-Halte (5 Atemzüge): h = 15-22s (NICHT über 25s)
              * Lange Halte (8 Atemzüge): h = 25-35s
              * Restorative Halte: h = 50-75s (NICHT über 90s)
              * Downward Dog in Sonnengrüßen: h = 20-22s
              * Savasana: h = 150-240s (unverändert)
            - t (transition): Nahtlose Übergänge 2-5s, Positionswechsel 5-8s, große Wechsel (Stehen→Boden) 10-15s
            - Opening: Meditation, Atemarbeit (8-10% der Zeit)
            - Warm-Up: Sonnengrüße, Cat-Cow (15-18%)
            - Standing/Peak: Vinyasa-Flows mit 3-7 verbundenen Asanas (40-50%)
            - Cool-Down: Langsamer, fließende Sequenzen (10-15%)
            - Restorative: Längere Holds 50-75s, wenige cues (5-9%)
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
