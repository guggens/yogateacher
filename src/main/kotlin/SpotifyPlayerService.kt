import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

@Serializable
private data class Device(val id: String, val name: String, val is_active: Boolean)

@Serializable
private data class DevicesResponse(val devices: List<Device>)

class SpotifyPlayerService(private val accessToken: String) {

    fun startYogaPlaylist() {
        val playlistUri = searchYogaPlaylist()
        val deviceId = getActiveDevice()
        startPlayback(playlistUri, deviceId)
    }

    private fun searchYogaPlaylist(): String {
        val query = URLEncoder.encode("yoga meditation entspannung", Charsets.UTF_8)
        val request = buildGetRequest("https://api.spotify.com/v1/search?q=$query&type=playlist&limit=1&market=DE")
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Spotify Suche fehlgeschlagen ${response.statusCode()}: ${response.body()}" }

        val body = json.parseToJsonElement(response.body()).jsonObject
        val playlist = body["playlists"]!!.jsonObject["items"]!!.jsonArray.firstOrNull()?.jsonObject
            ?: error("Keine Yoga-Playlist gefunden")

        val name = playlist["name"]!!.jsonPrimitive.content
        val uri = playlist["uri"]!!.jsonPrimitive.content
        println("🎵 Playlist gefunden: $name")
        return uri
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

    private fun buildGetRequest(url: String) = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer $accessToken")
        .GET()
        .build()
}
