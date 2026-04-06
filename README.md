# yogateacher

AI Yoga Trainer — a Kotlin CLI app that generates complete Vinyasa yoga lessons with Claude AI, speaks German instructions aloud via Google Cloud TTS, and plays phase-aware Spotify playlists in the background. Commands can be run interactively or via CLI arguments.

## Prerequisites

- JDK 25 (e.g. via SDKMAN: `sdk install java 25.0.2-open`)
- Gradle 9.x (included via wrapper)

## Quick Start

```bash
# 1. Copy and fill in your API keys
cp .env.example .env
source .env

# 2. Or put keys in the Spring Boot local profile
#    (already gitignored — see src/main/resources/application-local.properties)

# 3. Run
./gradlew bootRun
```

## API Keys & Setup

### Google Cloud Text-to-Speech (required)

Converts all yoga instructions and cues to natural-sounding German audio (`de-DE-Chirp3-HD` voice, female). The app speaks:
- Lesson title and theme at the start
- Each segment's instruction, modification, and coaching cues
- All text is automatically synthesized and played through the system speaker

| | |
|---|---|
| **API endpoint** | `https://texttospeech.googleapis.com/v1/text:synthesize` |
| **Env variable** | `GOOGLE_TTS_API_KEY` |
| **Free tier** | 1 M characters / month |

How to get a key:

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project (or select an existing one)
3. Enable the **Cloud Text-to-Speech API** — [direct link](https://console.cloud.google.com/apis/library/texttospeech.googleapis.com)
4. Go to **APIs & Services → Credentials** — [direct link](https://console.cloud.google.com/apis/credentials)
5. Click **Create Credentials → API key**
6. Copy the key and set `GOOGLE_TTS_API_KEY`

### Anthropic Claude (required)

Generates personalized yoga lesson sequences in real time. Given a duration and optional focus area, Claude:
- Designs a complete lesson structure (Opening → Warm-up → Standing → Peak → Cool-down → Restorative → Savasana)
- Writes detailed instructions for each pose/flow with entry, alignment, and release guidance
- Provides coaching cues and modifications for all skill levels
- Ensures smooth Vinyasa transitions and realistic timing

Uses `claude-opus-4-6` by default (configurable in `application.properties`), accessed via Spring AI.

| | |
|---|---|
| **Accessed via** | Spring AI Anthropic starter |
| **Env variable** | `ANTHROPIC_API_KEY` |
| **Model** | `claude-opus-4-6` (can be changed in `application.properties`) |

How to get a key:

1. Sign up at [console.anthropic.com](https://console.anthropic.com/)
2. Go to **API Keys** and create a new key
3. Set `ANTHROPIC_API_KEY`

### Spotify Web API (optional, requires **Spotify Premium**)

Plays phase-aware playlists during yoga sequences. The app:
- Searches Spotify for playlists matched to each lesson phase (opening, warm-up, standing, peak, cool-down, restorative, savasana)
- Randomly selects from top 5 results for variety between lessons
- Automatically switches playlists when the phase changes
- Smoothly ducks and crossfades volume during transitions (listen in full-volume speakers for best effect)
- Enables shuffle within each playlist for more variety

**Important:** The `PUT /v1/me/player/play` endpoint (used for playback control) is **Premium-only**. Free Spotify accounts can browse but cannot play via the Web API.

| | |
|---|---|
| **API endpoints** | Spotify Web API (`api.spotify.com/v1/...`) — search, player, devices, shuffle, volume |
| **Auth** | OAuth 2.0 Authorization Code flow via local callback server on `localhost:8888` |
| **Env variables** | `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET` |
| **Requirement** | Spotify Premium account + active Spotify client (e.g., open Spotify on your computer) |

How to set up:

1. Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Create a new app
3. Under **Settings → Redirect URIs**, add `http://localhost:8888/callback`
4. Copy **Client ID** and **Client Secret**
5. Set `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET`
6. On first run the app opens a browser tab for OAuth approval; the refresh token is cached at `~/.yogateacher/spotify_token` for subsequent runs

**The app works fine without Spotify** — it simply skips background music if credentials are absent or if you don't have Premium.

## Usage

### Interactive Mode

```bash
./gradlew bootRun
```

Then type commands at the prompt:

```
lesson <minutes> [focus]    Generate and play a full AI lesson
                            E.g.: "lesson 60 Intensiv, Nacken, Schultern"
                            (without focus: "lesson 30")

play [filename]             Play a saved lesson from the lessons/ folder
                            E.g.: "play lessons/2026-04-06_10-00-00.json"
                            (without filename: plays most recent)

test <minutes> [focus]      Generate and print lesson as text (no audio/music)
                            E.g.: "test 30"

<any text>                  Speak arbitrary German text via TTS
quit / exit                 Exit the app
```

### Non-Interactive / CLI Mode

Pass commands directly as arguments:

```bash
# Generate and play a 60-minute intense neck/shoulders lesson
./gradlew bootRun --args="lesson 60 Intensiv, Nacken, Schultern"

# Generate and print a 30-minute lesson as text
./gradlew bootRun --args="test 30"

# Play the most recent saved lesson
./gradlew bootRun --args="play"
```

### Lesson Files

- Lessons are saved as JSON in the `lessons/` directory
- Filename format: `YYYY-MM-DD_HH-mm-ss_XXmin_[focus].json`
- Each lesson is fully self-contained (sequences, timings, instructions) and can be replayed without Claude
