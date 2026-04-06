package com.yogateacher

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val json = Json { ignoreUnknownKeys = true }
private val client = HttpClient.newHttpClient()

private val PHASE_QUERIES = mapOf(
    "opening"     to "yoga morning meditation",
    "warm_up"     to "yoga warm up flow",
    "standing"    to "yoga vinyasa flow",
    "peak"        to "yoga power flow",
    "cool_down"   to "yoga cool down",
    "restorative" to "yin yoga",
    "savasana"    to "yoga savasana meditation",
)

@Serializable
private data class Device(val id: String, val name: String, val is_active: Boolean)

@Serializable
private data class DevicesResponse(val devices: List<Device>)

class SpotifyPlayerService(private val accessToken: String) {

    private var cachedDeviceId: String? = null

    fun startYogaPlaylist() {
        val playlistUri = searchPlaylist(PHASE_QUERIES["opening"] ?: "yoga meditation entspannung") ?: return
        cachedDeviceId = getActiveDevice()
        startPlayback(playlistUri, cachedDeviceId)
    }

    fun prefetchPhasePlaylists(phases: Set<String>): Map<String, String> {
        cachedDeviceId = getActiveDevice()
        val cache = mutableMapOf<String, String>()
        for (phase in phases) {
            val query = PHASE_QUERIES[phase] ?: "yoga meditation entspannung"
            val uri = searchPlaylist(query)
            if (uri != null) {
                cache[phase] = uri
            } else {
                println("⚠️  Keine Playlist für Phase '$phase' gefunden, übersprungen.")
            }
        }
        return cache
    }

    fun switchToPhase(phase: String, cache: Map<String, String>) {
        val uri = cache[phase] ?: return
        val deviceId = cachedDeviceId ?: getActiveDevice().also { cachedDeviceId = it }
        startPlayback(uri, deviceId)
    }

    fun getCurrentVolume(): Int? {
        val request = buildGetRequest("https://api.spotify.com/v1/me/player")
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) return null
            val playback = json.parseToJsonElement(response.body()).jsonObject
            val device = playback["device"]?.takeIf { it !is JsonNull }?.jsonObject ?: return null
            device["volume_percent"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.toIntOrNull()
        } catch (e: Exception) {
            System.err.println("⚠️  Volume-Abfrage fehlgeschlagen: ${e.message}")
            null
        }
    }

    fun setVolume(volumePercent: Int) {
        val deviceId = cachedDeviceId ?: return
        val clampedVolume = volumePercent.coerceIn(0, 100)
        val url = "https://api.spotify.com/v1/me/player/volume?volume_percent=$clampedVolume&device_id=$deviceId"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $accessToken")
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build()
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..204) {
                System.err.println("⚠️  Volume-Änderung fehlgeschlagen: ${response.statusCode()}")
            }
        } catch (e: Exception) {
            System.err.println("⚠️  Volume-Änderung fehlgeschlagen: ${e.message}")
        }
    }

    private fun searchPlaylist(query: String): String? {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8)
        val request = buildGetRequest("https://api.spotify.com/v1/search?q=$encoded&type=playlist&limit=5&market=DE")
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return null

        return try {
            val body = json.parseToJsonElement(response.body()).jsonObject
            val playlistsEl = body["playlists"]?.takeIf { it !is JsonNull } ?: return null
            val items = playlistsEl.jsonObject["items"]?.takeIf { it !is JsonNull }?.jsonArray
                ?.filter { it !is JsonNull } ?: return null
            if (items.isEmpty()) return null
            val playlist = items.random().jsonObject
            val name = playlist["name"]!!.jsonPrimitive.content
            val uri = playlist["uri"]!!.jsonPrimitive.content
            println("🎵 Playlist gefunden: $name")
            uri
        } catch (e: Exception) {
            System.err.println("Spotify-Suche fehlgeschlagen ('$query'): ${e.message}")
            null
        }
    }

    private fun getActiveDevice(): String? {
        val request = buildGetRequest("https://api.spotify.com/v1/me/player/devices")
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return null

        val devices = json.decodeFromString<DevicesResponse>(response.body()).devices
        if (devices.isEmpty()) {
            println("⚠️  Kein aktives Spotify-Gerät gefunden. Bitte öffne Spotify auf deinem Computer.")
            return null
        }
        val device = devices.firstOrNull { it.is_active } ?: devices.first()
        println("🔊 Gerät: ${device.name}")
        return device.id
    }

    private fun startPlayback(contextUri: String, deviceId: String?) {
        // Enable shuffle before starting playback
        setShuffle(true, deviceId)

        val url = if (deviceId != null) {
            "https://api.spotify.com/v1/me/player/play?device_id=$deviceId"
        } else {
            "https://api.spotify.com/v1/me/player/play"
        }
        val body = """{"context_uri":"$contextUri","offset":{"position":0},"position_ms":0}"""
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..204) {
            "Spotify Wiedergabe fehlgeschlagen ${response.statusCode()}: ${response.body()}"
        }
        println("▶️  Musik läuft!")
    }

    private fun setShuffle(state: Boolean, deviceId: String?) {
        val url = buildString {
            append("https://api.spotify.com/v1/me/player/shuffle?state=$state")
            if (deviceId != null) append("&device_id=$deviceId")
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $accessToken")
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build()
        try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: Exception) { /* shuffle is best-effort */ }
    }

    private fun buildGetRequest(url: String) = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer $accessToken")
        .GET()
        .build()
}
