# Lesson JSON Format (v2 — Flow-Centric)

This document describes the JSON format used by the yoga lesson generator. Each lesson is a sequence of **flow segments** — named sequences of 1-7 connected asanas, not individual poses.

## Top-Level Structure

```json
{
  "title": "Feuer & Fluss — Kraftvoller Vinyasa Flow",
  "theme": "anregend, auspowernd, intensiv",
  "duration_minutes": 45,
  "segments": [ ... ]
}
```

## Segment (FlowSegment)

Each segment represents a named flow. JSON uses short keys to minimize token usage.

| Key   | Kotlin Property        | Type           | Default | Description                                              |
|-------|------------------------|----------------|---------|----------------------------------------------------------|
| `p`   | `phase`                | String         | —       | `opening\|warm_up\|standing\|peak\|cool_down\|restorative\|savasana` |
| `n`   | `name`                 | String         | —       | Flow name, e.g. "Krieger-Flow" — **spoken via TTS**      |
| `t`   | `transition_seconds`   | Int            | `5`     | Silence before the flow starts (seconds)                 |
| `mod` | `modification`         | String         | `""`    | Easier alternative — displayed as text only, **not spoken** |
| `s`   | `steps`                | List<FlowStep> | —       | 1-7 connected poses for round 1                         |
| `sr`  | `steps_round2`         | List<FlowStep> | `[]`    | Abbreviated steps for rounds 2+ (left side for bilateral) |
| `rel` | `release`              | String         | `""`    | Explicit exit/release instruction after all steps        |
| `rh`  | `release_hold_seconds` | Int            | `0`     | Hold time after release is spoken                        |
| `rep` | `repeat`               | Int            | `1`     | 1=once, 2=bilateral (right/left), 3+=repetitions         |
| `rc`  | `repeat_cue`           | String         | `""`    | Spoken before rounds 2+ (e.g. "Jetzt die linke Seite.") |

Fields with default values are **omitted** from the JSON when they match the default.

### Repetition limits

- Flows with 1-2 steps: `rep` max 4
- Flows with 3+ steps (including Sun Salutations): `rep` max 2

### The `sr` field

When `rep >= 2` and the flow has 3+ steps, `sr` should always be provided:

- **Bilateral (rep=2)**: `sr` contains the left-side instructions with correct side references ("linker Fuß" instead of "rechter Fuß") and shorter instruction text (1 sentence per step).
- **Multi-round (rep=3+)**: `sr` contains abbreviated instructions (just pose name + breath cue). Hold times may be shorter.
- When `sr` is empty or absent, the full `s` steps are used for all rounds (backward compatible).

## Step (FlowStep)

Each step is a single pose within a flow.

| Key | Kotlin Property | Type         | Default | Description                                     |
|-----|-----------------|--------------|---------|------------------------------------------------|
| `i` | `instruction`   | String       | —       | How to enter the pose (2-3 sentences, German)   |
| `h` | `hold_seconds`  | Int          | —       | Hold time after the instruction is spoken        |
| `c` | `cues`          | List<String> | `[]`    | Pure coaching cues during the hold (no release!) |

## Playback Sequence

For each segment, the app executes:

```
1. TRANSITION
   - Spotify: duck volume → switch playlist (if phase changed) → restore volume
   - Sleep transition_seconds (split around playlist switch)

2. FLOW NAME ANNOUNCEMENT
   - TTS speaks segment.name (e.g. "Krieger-Flow")

3. FLOW EXECUTION (repeated `rep` times)
   For each round:
     a. If round > 1: TTS speaks repeat_cue, sleep transition_seconds

     b. Select steps: round 1 uses `s`, rounds 2+ use `sr` (if non-empty, else `s`)

     c. For each step:
        - TTS speaks step.instruction
        - Coaching cues distributed evenly over hold_seconds:
          interval = hold_seconds / (cues.count + 1)
          sleep(interval) → speak(cue) → ... → sleep(interval)
        - If no cues: silent sleep(hold_seconds)

     d. TTS speaks release (if non-empty)
     e. Sleep release_hold_seconds
```

Note: `modification` is **not spoken** — it is only shown in text-only display mode.

## Timing Model

Total lesson time = sum over all segments:

```
segment_time =
    transition_seconds
  + speak_time(name)
  + 1 × (
      sum_over_s_steps(speak_time(instruction) + hold_seconds + speak_time(cues))
    + speak_time(release)
    + release_hold_seconds
    )
  + (repeat - 1) × (
      speak_time(repeat_cue)
    + transition_seconds
    + sum_over_sr_steps(speak_time(instruction) + hold_seconds + speak_time(cues))
    + speak_time(release)
    + release_hold_seconds
    )
```

When `sr` is empty, `sr_steps = s_steps`.

**Speak time estimate**: ~4 seconds per sentence.

### Cue guidelines by hold duration

| hold_seconds | cues count | Notes                          |
|-------------|------------|--------------------------------|
| < 15        | 0          | Rapid flow step                |
| 15-22       | 0-1        | Brief hold                     |
| 22-50       | 1-2        | Standard hold                  |
| > 50        | 2-3        | Long hold (restorative)        |
| Savasana    | 0          | Respect the silence            |

### Hold time guidelines

| Pose type              | hold_seconds range |
|------------------------|--------------------|
| Flow steps (1 breath)  | 2-4s               |
| Short holds (3 breaths)| 8-12s              |
| Standard holds (5 br.) | 15-22s             |
| Long holds (8 breaths) | 25-35s             |
| Restorative holds      | 50-75s             |
| Down Dog in Sun Sal    | 20-22s             |
| Savasana               | 150-240s           |

### Transition guidelines

| Movement type             | transition_seconds |
|---------------------------|-------------------|
| Seamless (Lunge → Warrior) | 2-5              |
| Position change            | 5-8              |
| Major change (Standing → Floor) | 10-15       |

### Transition continuity

Each flow's first step **must** start from the position the previous flow's release left the student in. Forbidden direct transitions:

- Downward Dog ↔ Navasana (Boat) — needs intermediate sitting/forward fold
- Standing → Prone (Locust/Bow) — must come to floor first
- Deep backbend → deep forward fold — neutralize first (knees to chest)
- Headstand → Standing — Child's Pose first

## Session Closing

The last segment (Savasana) release **must** end with closing words: gratitude towards the body and oneself, then Namaste. If absent, the app speaks a fallback closing automatically.

## Examples

### 1. Bilateral Warrior Flow (rep=2, with sr for left side)

```json
{
  "p": "peak",
  "n": "Krieger-Flow",
  "mod": "Hinteres Knie auf dem Boden für sanftere Variante.",
  "s": [
    {"i": "Aus Downward Dog schreite mit dem rechten Fuß nach vorne in Low Lunge — den tiefen Ausfallschritt.", "h": 10},
    {"i": "Hebe dich auf zu Warrior I — dem Krieger Eins. Arme nach oben, Hüften nach vorne.", "h": 18, "c": ["Knie über dem Knöchel"]},
    {"i": "Öffne dich zu Warrior II — dem Krieger Zwei. Arme weit, Blick über die vordere Hand.", "h": 18, "c": ["Schultern tief, Kraft in den Beinen"]},
    {"i": "Strecke dich in Extended Side Angle — den gestreckten Seitwinkel. Unterarm auf den Oberschenkel.", "h": 14, "c": ["Öffne die Brust zum Himmel"]}
  ],
  "sr": [
    {"i": "Linker Fuß nach vorne, Low Lunge.", "h": 8},
    {"i": "Warrior I links — Arme hoch.", "h": 16},
    {"i": "Öffne dich zu Warrior II links.", "h": 16},
    {"i": "Extended Side Angle links.", "h": 12}
  ],
  "rel": "Löse dich, Hände zum Boden, schreite zurück und fließe durch einen Vinyasa.",
  "rh": 15,
  "rep": 2,
  "rc": "Wunderschön. Dasselbe auf der linken Seite."
}
```

**What the student hears:**
1. *(5s silence — transition)*
2. "Krieger-Flow" *(flow name announcement)*
3. **Round 1 (right side):** Low Lunge instruction → 10s hold → Warrior I instruction → 18s hold with cue → Warrior II instruction → 18s hold with cue → Extended Side Angle instruction → 14s hold with cue → "Löse dich..." *(release)* → 15s hold
4. "Wunderschön. Dasselbe auf der linken Seite." *(repeat_cue)* → 5s pause
5. **Round 2 (left side, abbreviated sr):** "Linker Fuß nach vorne, Low Lunge." → 8s → "Warrior I links — Arme hoch." → 16s → ... → release → 15s hold

### 2. Sun Salutation A (rep=2, with abbreviated sr)

```json
{
  "p": "warm_up",
  "n": "Sonnengruß A",
  "t": 3,
  "s": [
    {"i": "Stehe in Tadasana — dem Berg. Hände vor dem Herzen, Füße zusammen.", "h": 5},
    {"i": "Einatmen, Arme nach oben — Urdhva Hastasana.", "h": 3},
    {"i": "Ausatmen, falte dich nach vorne — Uttanasana, die stehende Vorbeuge.", "h": 4},
    {"i": "Einatmen, halber Lift — Ardha Uttanasana. Flacher Rücken, Blick nach vorne.", "h": 3},
    {"i": "Ausatmen, schreite oder springe zurück in Chaturanga Dandasana.", "h": 3},
    {"i": "Einatmen, Upward Dog — der heraufschauende Hund. Brust nach vorne und oben.", "h": 4},
    {"i": "Ausatmen, Downward Dog — der herabschauende Hund. Fünf tiefe Atemzüge.", "h": 22, "c": ["Fersen streben zur Matte"]}
  ],
  "sr": [
    {"i": "Einatmen, Arme hoch.", "h": 2},
    {"i": "Ausatmen, Vorbeuge.", "h": 2},
    {"i": "Einatmen, halber Lift.", "h": 2},
    {"i": "Ausatmen, Chaturanga.", "h": 2},
    {"i": "Einatmen, Upward Dog.", "h": 3},
    {"i": "Ausatmen, Downward Dog. Fünf Atemzüge.", "h": 22, "c": ["Fersen zur Matte"]}
  ],
  "rep": 2,
  "rc": "Einatmen, schreite nach vorne — nächste Runde."
}
```

### 3. Savasana with closing (single pose, no cues)

```json
{
  "p": "savasana",
  "n": "Savasana — Tiefenentspannung",
  "t": 10,
  "s": [
    {
      "i": "Lege dich flach auf den Rücken. Arme neben dem Körper, Handflächen nach oben. Schließe die Augen und lass alles los.",
      "h": 180
    }
  ],
  "rel": "Bewege langsam deine Finger und Zehen. Rolle dich auf die rechte Seite. Komme in deinem Tempo zurück in den Sitz. Hände vor dem Herzen — Danke an deinen Körper für diese Praxis. Danke an dich selbst, dass du dir diese Zeit geschenkt hast. Namaste.",
  "rh": 15
}
```
