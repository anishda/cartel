package app.audioguide.packbuilder

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

/**
 * Requêtes GET avec cache disque, nouvel essai et pause de politesse entre deux appels.
 * Le cache permet de refaire un pack sans réseau, et de ne pas réinterroger les services publics.
 */
class CachedHttp(
    private val cacheDir: Path,
    private val politenessMillis: Long = 150,
    private val accept: String = "application/json",
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /** Télécharge [url], ou relit la réponse déjà en cache. */
    fun get(url: String): String {
        Files.createDirectories(cacheDir)
        val cached = cacheDir.resolve(sha256(url).take(32) + ".json")
        if (Files.exists(cached)) return Files.readString(cached)

        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(90))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", accept)
                    .GET().build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200) {
                    Files.writeString(cached, response.body())
                    Thread.sleep(politenessMillis)
                    return response.body()
                }
                lastError = IllegalStateException("HTTP ${response.statusCode()} pour $url")
            } catch (e: java.io.IOException) {
                lastError = e
            }
            Thread.sleep(2_000L * (attempt + 1))
        }
        throw IllegalStateException("Échec après $MAX_ATTEMPTS essais : $url", lastError)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_ATTEMPTS = 4

        // Les services Wikimedia demandent un User-Agent qui identifie l'outil.
        const val USER_AGENT = "cartel-pack-builder/0.2 (https://github.com/anishda/cartel)"
    }
}
