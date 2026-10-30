package app.audioguide.packbuilder

import app.audioguide.catalog.JocondeRecord
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Où trouver les notices d'un musée. Remplaçable dans les tests. */
interface RecordSource {
    fun records(museum: MuseumSpec, onPage: (loaded: Int, total: Int?) -> Unit = { _, _ -> }): List<JocondeRecord>

    /** Date de dernière mise à jour du jeu de données source, si connue. */
    fun sourceUpdatedAt(): String?
}

/**
 * Lit la base Joconde par l'API tabulaire de data.gouv.fr (filtrée et paginée : pas besoin du CSV de 1,2 Go).
 * Les réponses sont gardées dans [cacheDir] pour pouvoir refaire un pack sans réseau.
 */
class JocondeTabularApi(
    private val cacheDir: Path,
    private val resourceId: String = RESOURCE_ID,
    private val pageSize: Int = 200,
    private val politenessMillis: Long = 150,
) : RecordSource {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()

    override fun records(museum: MuseumSpec, onPage: (Int, Int?) -> Unit): List<JocondeRecord> {
        val byReference = LinkedHashMap<String, JocondeRecord>()
        for (domain in museum.domains) {
            var url: String? = firstPageUrl(museum, domain)
            var total: Int? = null
            var loaded = 0
            while (url != null) {
                val page = parsePage(fetch(url))
                page.data.forEach { record -> record.reference?.let { byReference.putIfAbsent(it, record) } }
                loaded += page.data.size
                total = page.meta?.total ?: total
                onPage(loaded, total)
                url = page.links?.next
            }
        }
        return byReference.values.toList()
    }

    override fun sourceUpdatedAt(): String? = runCatching {
        val body = fetch("https://www.data.gouv.fr/api/1/datasets/${JocondeSource.DATASET_ID}/")
        json.parseToJsonElement(body).jsonObject["last_update"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun firstPageUrl(museum: MuseumSpec, domain: String): String {
        fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
        return "$BASE/$resourceId/data/?Code_Museofile__exact=${enc(museum.museofileCode)}" +
            "&Domaine__contains=${enc(domain)}&page_size=$pageSize&page=1"
    }

    /** Télécharge [url], ou relit la réponse déjà en cache. */
    private fun fetch(url: String): String {
        Files.createDirectories(cacheDir)
        val cached = cacheDir.resolve(sha256(url).take(32) + ".json")
        if (Files.exists(cached)) return Files.readString(cached)

        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "audioguide-musees-pack-builder/0.1")
                    .header("Accept", "application/json")
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
            Thread.sleep(1_000L * (attempt + 1))
        }
        throw IllegalStateException("Échec après $MAX_ATTEMPTS essais : $url", lastError)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    @Serializable
    internal data class Page(val data: List<JocondeRecord> = emptyList(), val links: Links? = null, val meta: Meta? = null)

    @Serializable
    internal data class Links(val next: String? = null)

    @Serializable
    internal data class Meta(val total: Int? = null)

    companion object {
        const val RESOURCE_ID = "7e3307c2-f2ff-455c-bbca-bb6f11aec7bb"
        private const val BASE = "https://tabular-api.data.gouv.fr/api/resources"
        private const val MAX_ATTEMPTS = 4
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        internal fun parsePage(body: String): Page = json.decodeFromString(Page.serializer(), body)
    }
}
