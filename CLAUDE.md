# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./gradlew build          # compile + assemble
./gradlew bootRun        # run the app (reads env vars for API keys)
```

Requires JDK 25 (`sdk install java 25.0.2-open`). Gradle wrapper included (9.4.1).

Environment variables must be set before running — see `.env.example`:
- `ANTHROPIC_API_KEY` (required) — Claude API key
- `GOOGLE_TTS_API_KEY` (required) — Google Cloud TTS
- `SPOTIFY_CLIENT_ID` / `SPOTIFY_CLIENT_SECRET` (optional) — Spotify Premium needed for playback

There are no tests in this project.

## Architecture

Kotlin Spring Boot CLI app (no web server — `spring.main.web-application-type=none`). Entry point is `Main.kt` with a `CommandLineRunner` REPL.

**Flow:** User types `lesson <minutes>` → `LessonGeneratorService` loads yoga knowledge base from `src/main/resources/yoga/*.md`, sends it as system context to Claude via Spring AI `ChatClient`, gets back structured JSON → deserialized to `YogaLesson`/`LessonSegment` → each segment is spoken aloud via `GoogleTtsService` (REST call to Google Cloud TTS) → audio played through `AudioPlayer` (javax.sound). Spotify playlist runs in background if configured.

**Services:**
- `LessonGeneratorService` (`@Service`) — builds prompts, calls Claude, parses JSON response
- `GoogleTtsService` (`@Service`) — HTTP POST to Google TTS REST API, returns WAV bytes
- `SpotifyAuthService` (`@Service`) — OAuth2 flow, caches refresh token at `~/.yogateacher/spotify_token`
- `SpotifyPlayerService` — **not a Spring bean**; instantiated manually with an access token after OAuth
- `AudioPlayer` — singleton `object`, plays WAV via `javax.sound.sampled`

## Key Conventions

- **German everywhere** — all TTS instructions, Claude prompts, lesson output, and console messages are in German. Keep new content in German.
- **`@param:Value` for Spring injection** — always use `@param:Value("${...}")` on constructor parameters (not bare `@Value`) to avoid Kotlin annotation-target ambiguity (KT-73255).
- **`kotlinx-serialization` for all JSON** — `@Serializable` data classes + `kotlinx.serialization.json.Json`. No Jackson/Gson.
- **`java.net.http.HttpClient` for HTTP** — no Spring WebClient or RestTemplate. All external API calls use JDK HttpClient directly.
- Claude model configured in `src/main/resources/application.properties` (`claude-sonnet-4-6`, 16384 max tokens).
