# Copilot Instructions

## Project

`yogateacher` is an AI Yoga Trainer playground — a Kotlin JVM CLI app that speaks yoga pose instructions aloud in German using Google Cloud Text-to-Speech, plays a Spotify yoga/meditation playlist in the background, and generates complete AI yoga lessons via Claude (Spring AI).

## Architecture

- **`Main.kt`** — `@SpringBootApplication` + `@Component CommandLineRunner` entry point: init Spotify, start playlist, then enter REPL
- **`LessonGeneratorService.kt`** — `@Service`; loads yoga knowledge base from classpath, calls Claude via Spring AI `ChatClient`, returns a structured `YogaLesson`
- **`GoogleTtsService.kt`** — `@Service`; HTTP POST to Google Cloud TTS REST API; returns decoded WAV bytes
- **`AudioPlayer.kt`** — plays WAV `ByteArray` via `javax.sound.sampled` (singleton object)
- **`SpotifyAuthService.kt`** — `@Service`; OAuth2 Authorization Code flow; stores refresh token at `~/.yogateacher/spotify_token`
- **`SpotifyPlayerService.kt`** — plain class (instantiated with access token); searches for yoga playlist and starts playback via Spotify Web API
- **`src/main/resources/yoga/*.md`** — yoga knowledge base (poses, flows, philosophy, anatomy, pranayama, meditations, teaching language)

TTS voice: `de-DE-Wavenet-C` (female, calm). Rate `0.9`, pitch `-1.0`.  
Claude model: `claude-haiku-4-5` (configurable via `application.properties`).  
Spotify is optional — the app runs without it if credentials are absent.

## Stack

- Kotlin 2.3.20 · JVM 25 (SDKMAN: `25.0.2-open`)
- Gradle 9.4.1 (Kotlin DSL)
- Spring Boot 3.4.13 · Spring AI 1.1.4 (Anthropic/Claude)
- `kotlinx-serialization-json:1.10.0`
- `javax.sound.sampled` + `java.net.http.HttpClient` + `com.sun.net.httpserver.HttpServer` (all stdlib/JDK)

## Commands

```bash
./gradlew build          # compile + assemble fat JAR
./gradlew bootRun        # run via Spring Boot (reads env vars)
```

## Environment Variables

See `.env.example` for full details. Set before running:

```bash
export GOOGLE_TTS_API_KEY=...          # required — Google Cloud TTS API key
export ANTHROPIC_API_KEY=...           # required — Claude API key (console.anthropic.com)
export SPOTIFY_CLIENT_ID=...           # optional — Spotify Developer app client ID
export SPOTIFY_CLIENT_SECRET=...       # optional — Spotify Developer app client secret
```

## REPL Commands

```
lesson <minutes>   Generate and speak a full AI yoga lesson (e.g. "lesson 30")
<any text>         Speak the text via TTS immediately
quit               Exit the app
```

## AI Lesson Generation

`LessonGeneratorService` loads all `classpath:yoga/*.md` files as system context, then prompts Claude to generate a structured JSON lesson (`YogaLesson` / `LessonSegment`) for a given duration. Each segment has a German spoken instruction and a hold duration in seconds.

The Claude model and max-tokens are configured in `src/main/resources/application.properties`.

## Key Conventions

**`@param:Value` for Spring injection in Kotlin** — always use `@param:Value(...)` (not bare `@Value`) on constructor parameters to avoid KT-73255 annotation-target ambiguity:
```kotlin
@Service
class GoogleTtsService(@param:Value("\${GOOGLE_TTS_API_KEY}") private val apiKey: String)
```
Optional env vars use an empty-string default: `@param:Value("\${SPOTIFY_CLIENT_ID:}")`.

**`kotlinx-serialization` for all JSON** — the project uses `@Serializable` data classes + `kotlinx.serialization.json.Json`, not Jackson or Gson. Every HTTP request/response body is serialized this way.

**`java.net.http.HttpClient` for HTTP** — no Spring WebClient or RestTemplate. All external API calls (TTS, Spotify) use JDK `HttpClient` directly.

**`SpotifyPlayerService` is not a Spring bean** — it is instantiated manually in `Main.kt` with an access token after OAuth completes. Only `SpotifyAuthService` is `@Service`.

**German everywhere** — all TTS instructions, lesson segments, system/user prompts to Claude, and console output are in German. Keep new instructions in German.

**No tests** — there are no unit or integration tests in this project.

## Spotify Setup (one-time)

1. Create a free app at https://developer.spotify.com/dashboard
2. Add redirect URI `http://localhost:8888/callback` in the app settings
3. Requires Spotify Premium for playback control
4. First run opens a browser tab for OAuth approval; refresh token is cached at `~/.yogateacher/spotify_token`
