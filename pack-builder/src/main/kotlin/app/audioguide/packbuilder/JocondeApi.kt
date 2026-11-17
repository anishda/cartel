package app.audioguide.packbuilder

import app.audioguide.catalog.JocondeRecord
import java.net.URLEncoder
import java.nio.file.Path
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
) : RecordSource {
    private val http = CachedHttp(cacheDir)

    private fun fetch(url: String) = http.get(url)

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

    @Serializable
    internal data class Page(val data: List<JocondeRecord> = emptyList(), val links: Links? = null, val meta: Meta? = null)

    @Serializable
    internal data class Links(val next: String? = null)

    @Serializable
    internal data class Meta(val total: Int? = null)

    companion object {
        const val RESOURCE_ID = "7e3307c2-f2ff-455c-bbca-bb6f11aec7bb"
        private const val BASE = "https://tabular-api.data.gouv.fr/api/resources"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        internal fun parsePage(body: String): Page = json.decodeFromString(Page.serializer(), body)
    }
}
