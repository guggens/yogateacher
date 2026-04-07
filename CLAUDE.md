# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./gradlew build          # compile + assemble
./gradlew bootRun        # interactive REPL
./gradlew bootRun --args="lesson 45 anregend, intensiv"   # non-interactive, exits after lesson
./gradlew bootRun --args="test 30"                         # text-only, no TTS/Spotify
./gradlew bootRun --args="play lessons/2026-04-07_45min.json"  # replay saved lesson
```

Requires JDK 25 (`sdk install java 25.0.2-open`). Gradle wrapper included (9.4.1).

API keys go in `src/main/resources/application-local.properties` (gitignored). The `local` profile is active by default (`spring.profiles.active=local` in `application.properties`). Required keys:
- `ANTHROPIC_API_KEY` — Claude API
- `GOOGLE_TTS_API_KEY` — Google Cloud TTS (voice: `de-DE-Chirp3-HD`)
- `SPOTIFY_CLIENT_ID` / `SPOTIFY_CLIENT_SECRET` — optional; Spotify Premium required for playback

There are no tests in this project.

## Architecture

Kotlin Spring Boot CLI app (no web server — `spring.main.web-application-type=none`). Entry point is `Main.kt` with a `CommandLineRunner`. When CLI args are present it runs non-interactively and exits; otherwise it starts a REPL.

**Lesson generation flow:** `lesson <minutes> [focus]` → `LessonGeneratorService` loads all `src/main/resources/yoga/*.md` files as system context, calls Claude via Spring AI `ChatClient`, gets back structured JSON → deserializes to `YogaLesson`/`FlowSegment`/`FlowStep` → saves to `lessons/<timestamp>_<minutes>min_<focus>.json` → plays each flow via `GoogleTtsService` (Google Cloud TTS REST → WAV bytes) → `AudioPlayer` (javax.sound.sampled).

**Flow-centric data model** (see `LESSON_FORMAT.md` for full reference): Each segment is a named flow of 1-7 connected asanas (`FlowStep`), not a single pose. Segments support `repeat` (2=bilateral right/left, 3+=repetitions) with `repeat_cue` spoken between rounds, and an explicit `release` instruction after all steps complete. JSON uses short `@SerialName` keys (`p`, `n`, `s`, `i`, `h`, `c`, `rel`, `rh`, `rep`, `rc`) to save output tokens; `encodeDefaults = false` omits default-valued fields.

**Spotify phase switching** (`SpotifyPlayerService`): Playlists are prefetched per phase before playback starts. On phase change: volume is ducked to 30%, first half of `transition_seconds` elapses, playlist switches, second half elapses, volume restores. `SpotifyPlayerService` is **not a Spring bean** — instantiated manually in `Main.kt` after OAuth completes.

**Services:**
- `LessonGeneratorService` (`@Service`) — builds prompts, calls Claude, parses JSON; data classes `YogaLesson`/`FlowSegment`/`FlowStep` defined here
- `GoogleTtsService` (`@Service`) — HTTP POST to Google TTS REST API, returns WAV bytes
- `SpotifyAuthService` (`@Service`) — OAuth2 PKCE flow, caches refresh token at `~/.yogateacher/spotify_token`
- `SpotifyPlayerService` — manual instantiation; searches Spotify by phase query strings (`PHASE_QUERIES` map), controls playback and volume
- `AudioPlayer` — singleton `object`, plays WAV via `javax.sound.sampled`

## Key Conventions

- **German everywhere** — all TTS text, Claude prompts, lesson output, and console messages are in German. Keep new content in German.
- **`@param:Value` for Spring injection** — always use `@param:Value("${...}")` on constructor parameters (not bare `@Value`) to avoid Kotlin annotation-target ambiguity (KT-73255).
- **`kotlinx-serialization` for all JSON** — `@Serializable` data classes + `kotlinx.serialization.json.Json`. No Jackson/Gson.
- **`java.net.http.HttpClient` for HTTP** — no Spring WebClient or RestTemplate. All external API calls use JDK HttpClient directly.
- Claude model configured in `src/main/resources/application.properties` (`claude-sonnet-4-6`, 16384 max tokens).
