# yogateacher

AI Yoga Trainer playground — a Kotlin CLI app that generates complete yoga lessons with Claude, speaks instructions aloud in German via Google Cloud TTS, and optionally plays a Spotify playlist in the background.

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

Used to synthesise German yoga instructions as audio (`de-DE-Wavenet-C` voice).

| | |
|---|---|
| **API endpoint** | `https://texttospeech.googleapis.com/v1/text:synthesize` |
| **Env variable** | `GOOGLE_TTS_API_KEY` |
| **Free tier** | 1 M WaveNet characters / month |

How to get a key:

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project (or select an existing one)
3. Enable the **Cloud Text-to-Speech API** — [direct link](https://console.cloud.google.com/apis/library/texttospeech.googleapis.com)
4. Go to **APIs & Services → Credentials** — [direct link](https://console.cloud.google.com/apis/credentials)
5. Click **Create Credentials → API key**
6. Copy the key and set `GOOGLE_TTS_API_KEY`

### Anthropic Claude (required)

Used to generate structured yoga lessons via Spring AI (`claude-haiku-4-5` by default, configurable in `application.properties`).

| | |
|---|---|
| **Accessed via** | Spring AI Anthropic starter (not called directly) |
| **Env variable** | `ANTHROPIC_API_KEY` |

How to get a key:

1. Sign up at [console.anthropic.com](https://console.anthropic.com/)
2. Go to **API Keys** and create a new key
3. Set `ANTHROPIC_API_KEY`

### Spotify Web API (optional)

Used to search for and play a yoga/meditation playlist in the background. Requires **Spotify Premium** for playback control.

| | |
|---|---|
| **API endpoints** | Spotify Web API (`api.spotify.com/v1/...`) — search, player, devices |
| **Auth** | OAuth 2.0 Authorization Code flow via local callback server on `localhost:8888` |
| **Env variables** | `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET` |

How to set up:

1. Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Create a new app
3. Under **Settings → Redirect URIs**, add `http://localhost:8888/callback`
4. Copy **Client ID** and **Client Secret**
5. Set `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET`
6. On first run the app opens a browser tab for OAuth approval; the refresh token is cached at `~/.yogateacher/spotify_token` for subsequent runs

The app works fine without Spotify — it simply skips background music if credentials are absent.

## Usage

After starting with `./gradlew bootRun`:

```
lesson <minutes>   Generate and speak a full AI yoga lesson (e.g. "lesson 30")
<any text>         Speak the text via TTS immediately
quit               Exit the app
```
