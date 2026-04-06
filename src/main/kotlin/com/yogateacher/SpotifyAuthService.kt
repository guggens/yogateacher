package com.yogateacher

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.ServerSocket
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val tokenFile: Path = Path.of(System.getProperty("user.home"), ".yogateacher", "spotify_token")
private val json = Json { ignoreUnknownKeys = true }
private val client = HttpClient.newHttpClient()

@Serializable
private data class TokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Int,
)

@Service
class SpotifyAuthService(
    @param:Value("\${SPOTIFY_CLIENT_ID:}") private val clientId: String,
    @param:Value("\${SPOTIFY_CLIENT_SECRET:}") private val clientSecret: String,
) {
    val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()
    private val redirectUri = "http://127.0.0.1:8888/callback"
    private val scopes = "user-read-playback-state user-modify-playback-state"

    fun getAccessToken(): String {
        val stored = loadStoredRefreshToken()
        return if (stored != null) {
            refreshAccessToken(stored)
        } else {
            authorizeAndFetchTokens()
        }
    }

    private fun authorizeAndFetchTokens(): String {
        val authUrl = "https://accounts.spotify.com/authorize?" +
            "client_id=$clientId" +
            "&response_type=code" +
            "&redirect_uri=${URLEncoder.encode(redirectUri, Charsets.UTF_8)}" +
            "&scope=${URLEncoder.encode(scopes, Charsets.UTF_8)}"

        println("🎵 Öffne Browser für Spotify-Anmeldung...")
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(authUrl))
        } else {
            println("Öffne manuell: $authUrl")
        }

        val code = ServerSocket(8888).use { server ->
            server.soTimeout = 120_000
            server.accept().use { socket ->
                val request = socket.getInputStream().bufferedReader().readLine() ?: ""
                val responseBody = "<html><body><h2>Spotify verbunden! Dieses Fenster kann geschlossen werden.</h2></body></html>"
                socket.getOutputStream().write(
                    "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${responseBody.length}\r\nConnection: close\r\n\r\n$responseBody".toByteArray()
                )
                // parse code from "GET /callback?code=XXX HTTP/1.1"
                Regex("code=([^& ]+)").find(request)?.groupValues?.get(1)
                    ?: error("Kein Autorisierungscode in der Antwort gefunden")
            }
        }
        return exchangeCodeForTokens(code)
    }

    private fun exchangeCodeForTokens(code: String): String {
        val body = "grant_type=authorization_code&code=$code&redirect_uri=${URLEncoder.encode(redirectUri, Charsets.UTF_8)}"
        val response = postToTokenEndpoint(body)
        saveRefreshToken(response.refresh_token!!)
        return response.access_token
    }

    private fun refreshAccessToken(refreshToken: String): String {
        val body = "grant_type=refresh_token&refresh_token=$refreshToken"
        return try {
            val response = postToTokenEndpoint(body)
            if (response.refresh_token != null) saveRefreshToken(response.refresh_token)
            response.access_token
        } catch (e: Exception) {
            println("⚠️  Token-Refresh fehlgeschlagen, starte Anmeldung neu...")
            tokenFile.toFile().delete()
            authorizeAndFetchTokens()
        }
    }

    private fun postToTokenEndpoint(body: String): TokenResponse {
        val credentials = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://accounts.spotify.com/api/token"))
            .header("Authorization", "Basic $credentials")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Spotify Token-Fehler ${response.statusCode()}: ${response.body()}" }
        return json.decodeFromString(response.body())
    }

    private fun loadStoredRefreshToken(): String? {
        if (!tokenFile.exists()) return null
        return tokenFile.readText().trim().ifBlank { null }
    }

    private fun saveRefreshToken(token: String) {
        tokenFile.parent.createDirectories()
        tokenFile.writeText(token)
    }
}
