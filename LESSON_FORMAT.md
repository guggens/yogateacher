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
| `n`   | `name`                 | String         | —       | Flow name, e.g. "Krieger-Flow", "Sonnengruß A"          |
| `t`   | `transition_seconds`   | Int            | `5`     | Silence before the flow starts (seconds)                 |
| `mod` | `modification`         | String         | `""`    | Easier alternative, announced once before the flow       |
| `s`   | `steps`                | List<FlowStep> | —       | 1-7 connected poses executed in sequence                 |
| `rel` | `release`              | String         | `""`    | Explicit exit/release instruction after all steps        |
| `rh`  | `release_hold_seconds` | Int            | `0`     | Hold time after release is spoken                        |
| `rep` | `repeat`               | Int            | `1`     | 1=once, 2=bilateral (right/left), 3+=repetitions         |
| `rc`  | `repeat_cue`           | String         | `""`    | Spoken before rounds 2+ (e.g. "Jetzt die linke Seite.") |

Fields with default values are **omitted** from the JSON when they match the default.

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

2. MODIFICATION (if non-empty)
   - TTS speaks the modification once

3. FLOW EXECUTION (repeated `rep` times)
   For each round:
     a. If round > 1: TTS speaks repeat_cue, sleep transition_seconds

     b. For each step:
        - TTS speaks step.instruction
        - Coaching cues distributed evenly over hold_seconds:
          interval = hold_seconds / (cues.count + 1)
          sleep(interval) → speak(cue) → ... → sleep(interval)
        - If no cues: silent sleep(hold_seconds)

     c. TTS speaks release (if non-empty)
     d. Sleep release_hold_seconds
```

## Timing Model

Total lesson time = sum over all segments:

```
segment_time =
    transition_seconds
  + speak_time(modification)
  + repeat × (
      sum_over_steps(speak_time(instruction) + hold_seconds + speak_time(cues))
    + speak_time(release)
    + release_hold_seconds
    )
  + (repeat - 1) × (speak_time(repeat_cue) + transition_seconds)
```

**Speak time estimate**: ~4 seconds per sentence.

### Cue guidelines by hold duration

| hold_seconds | cues count | Notes                          |
|-------------|------------|--------------------------------|
| < 15        | 0          | Rapid flow step                |
| 15-30       | 0-1        | Brief hold                     |
| 30-60       | 1-2        | Standard hold                  |
| > 60        | 2-3        | Long hold (restorative)        |
| Savasana    | 0          | Respect the silence            |

### Transition guidelines

| Movement type             | transition_seconds |
|---------------------------|-------------------|
| Seamless (Lunge → Warrior) | 2-5              |
| Position change            | 5-8              |
| Major change (Standing → Floor) | 10-15       |

## Examples

### 1. Bilateral Warrior Flow (rep=2)

```json
{
  "p": "peak",
  "n": "Krieger-Flow",
  "mod": "Hinteres Knie auf dem Boden für sanftere Variante.",
  "s": [
    {
      "i": "Aus Downward Dog schreite mit dem rechten Fuß nach vorne in Low Lunge — den tiefen Ausfallschritt.",
      "h": 10
    },
    {
      "i": "Hebe dich auf zu Warrior I — dem Krieger Eins. Arme nach oben, Hüften nach vorne.",
      "h": 20,
      "c": ["Knie über dem Knöchel"]
    },
    {
      "i": "Öffne dich zu Warrior II — dem Krieger Zwei. Arme weit, Blick über die vordere Hand.",
      "h": 20,
      "c": ["Schultern tief, Kraft in den Beinen"]
    },
    {
      "i": "Strecke dich in Extended Side Angle — den gestreckten Seitwinkel. Unterarm auf den Oberschenkel.",
      "h": 15,
      "c": ["Öffne die Brust zum Himmel"]
    }
  ],
  "rel": "Löse dich, Hände zum Boden, schreite zurück und fließe durch einen Vinyasa.",
  "rh": 15,
  "rep": 2,
  "rc": "Wunderschön. Dasselbe auf der linken Seite."
}
```

**What the student hears:**
1. *(5s silence — transition)*
2. "Hinteres Knie auf dem Boden..." *(modification)*
3. **Round 1 (right side):** Low Lunge instruction → 10s hold → Warrior I instruction → 20s hold with cue → Warrior II instruction → 20s hold with cue → Extended Side Angle instruction → 15s hold with cue → "Löse dich..." *(release)* → 15s hold
4. "Wunderschön. Dasselbe auf der linken Seite." *(repeat_cue)* → 5s pause
5. **Round 2 (left side):** same steps repeated → release → 15s hold

**Estimated time:** 5 + 4 + 2 × (4+10 + 8+20+4 + 8+20+4 + 8+15+4 + 4+15) + (4+5) ≈ **270s (4.5 min)**

### 2. Sun Salutation A (rep=3)

```json
{
  "p": "warm_up",
  "n": "Sonnengruß A",
  "t": 3,
  "s": [
    {"i": "Stehe in Tadasana — dem Berg. Hände vor dem Herzen, Füße zusammen.", "h": 5},
    {"i": "Einatmen, Arme nach oben — Urdhva Hastasana.", "h": 3},
    {"i": "Ausatmen, falte dich nach vorne — Uttanasana, die stehende Vorbeuge.", "h": 5},
    {"i": "Einatmen, halber Lift — Ardha Uttanasana. Flacher Rücken, Blick nach vorne.", "h": 3},
    {"i": "Ausatmen, schreite oder springe zurück in Chaturanga Dandasana.", "h": 3},
    {"i": "Einatmen, Upward Dog — der heraufschauende Hund. Brust nach vorne und oben.", "h": 5},
    {
      "i": "Ausatmen, Downward Dog — der herabschauende Hund. Fünf tiefe Atemzüge.",
      "h": 25,
      "c": ["Drücke den Boden aktiv weg, Fersen streben zur Matte"]
    }
  ],
  "rep": 3,
  "rc": "Einatmen, schreite nach vorne — nächste Runde."
}
```

**Estimated time:** 3 + 3 × (7×4 + 5+3+5+3+3+5+25 + 4) + 2 × (4+3) ≈ **230s (3.8 min)**

### 3. Savasana (single pose, no cues)

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
  "rel": "Bewege langsam deine Finger und Zehen. Rolle dich auf die rechte Seite.",
  "rh": 15
}
```

**Estimated time:** 10 + 12 + 180 + 8 + 15 = **225s (3.75 min)**
