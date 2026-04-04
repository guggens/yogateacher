import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

@Serializable
private data class TtsRequest(
    val input: TextInput,
    val voice: VoiceSelectionParams,
    val audioConfig: AudioConfig,
)

@Serializable
private data class TextInput(val text: String)

@Serializable
private data class VoiceSelectionParams(
    val languageCode: String,
    val name: String,
    val ssmlGender: String,
)

@Serializable
private data class AudioConfig(
    val audioEncoding: String,
    val speakingRate: Double,
    val pitch: Double,
)

@Serializable
private data class TtsResponse(val audioContent: String)

@Service
class GoogleTtsService(@param:Value("\${GOOGLE_TTS_API_KEY}") private val apiKey: String) {

    private val client = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun synthesize(text: String): ByteArray {
        val body = json.encodeToString(
            TtsRequest(
                input = TextInput(text),
                voice = VoiceSelectionParams(
                    languageCode = "de-DE",
                    name = "de-DE-Wavenet-C",
                    ssmlGender = "FEMALE",
                ),
                audioConfig = AudioConfig(
                    audioEncoding = "LINEAR16",
                    speakingRate = 0.9,
                    pitch = -1.0,
                ),
            )
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "TTS API error ${response.statusCode()}: ${response.body()}"
        }

        val audioContent = json.decodeFromString<TtsResponse>(response.body()).audioContent
        return Base64.getDecoder().decode(audioContent)
    }
}
