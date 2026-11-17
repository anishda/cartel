package app.audioguide.packbuilder

import java.net.URLEncoder
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Une œuvre décrite dans Wikidata, rattachée à une collection du musée par son numéro d'inventaire (P217). */
data class WikidataWork(
    /** « Q12418 ». */
    val id: String,
    val inventoryNo: String,
    /** Libellé français, s'il existe. */
    val label: String?,
    /** Créateurs (P170), libellés français. */
    val creators: List<String>,
    /** Nombre d'articles Wikimedia sur l'œuvre : mesure de notoriété. */
    val sitelinks: Int,
    /** Titre de l'article Wikipedia en français, s'il existe. */
    val frwikiTitle: String?,
)

/** Où trouver les œuvres Wikidata d'un musée et les récits Wikipedia. Remplaçable dans les tests. */
interface WikimediaSource {
    fun works(museum: MuseumSpec): List<WikidataWork>

    /** Introduction en texte brut de chaque article de [titles] (clé : le titre demandé). */
    fun intros(titles: List<String>): Map<String, String>
}

/** Interroge le service SPARQL de Wikidata et l'API de Wikipedia en français, avec cache disque. */
class WikimediaApi(cacheDir: Path) : WikimediaSource {
    private val sparql = CachedHttp(cacheDir.resolve("wikidata"), politenessMillis = 1_000, accept = "application/sparql-results+json")
    private val wikipedia = CachedHttp(cacheDir.resolve("wikipedia"), politenessMillis = 200)

    override fun works(museum: MuseumSpec): List<WikidataWork> {
        val qid = museum.wikidataId ?: return emptyList()
        // Toute collection qui fait partie du musée (départements compris), avec le numéro d'inventaire.
        val query = """
            SELECT ?item ?inv ?label ?links ?title (GROUP_CONCAT(DISTINCT ?creatorLabel; separator="|") AS ?creators) WHERE {
              ?item wdt:P195 ?collection . ?collection wdt:P361* wd:$qid .
              ?item wdt:P217 ?inv ; wikibase:sitelinks ?links .
              OPTIONAL { ?item rdfs:label ?label FILTER(LANG(?label) = "fr") }
              OPTIONAL { ?item wdt:P170 ?creator . ?creator rdfs:label ?creatorLabel FILTER(LANG(?creatorLabel) = "fr") }
              OPTIONAL { ?article schema:about ?item ; schema:isPartOf <https://fr.wikipedia.org/> ; schema:name ?title }
            } GROUP BY ?item ?inv ?label ?links ?title
        """.trimIndent()
        val body = sparql.get("https://query.wikidata.org/sparql?query=" + enc(query))
        return parseWorks(body)
    }

    override fun intros(titles: List<String>): Map<String, String> {
        val result = HashMap<String, String>()
        // L'API ne rend les introductions que par lots de 20 articles.
        for (batch in titles.distinct().sorted().chunked(20)) {
            val url = "https://fr.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&prop=extracts" +
                "&exintro=1&explaintext=1&exsectionformat=plain&redirects=1&titles=" + enc(batch.joinToString("|"))
            result += parseIntros(wikipedia.get(url), batch)
        }
        return result
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

        internal fun parseWorks(body: String): List<WikidataWork> {
            val bindings = json.parseToJsonElement(body).jsonObject["results"]!!.jsonObject["bindings"]!!.jsonArray
            return bindings.map { it.jsonObject }.map { b ->
                fun value(name: String) = (b[name] as? JsonObject)?.get("value")?.jsonPrimitive?.content
                WikidataWork(
                    id = value("item")!!.substringAfterLast('/'),
                    inventoryNo = value("inv")!!,
                    label = value("label"),
                    creators = value("creators")?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
                    sitelinks = value("links")?.toIntOrNull() ?: 0,
                    frwikiTitle = value("title"),
                )
            }
        }

        /** Rattache chaque introduction au titre demandé, en suivant les normalisations et redirections. */
        internal fun parseIntros(body: String, requested: List<String>): Map<String, String> {
            val query = json.parseToJsonElement(body).jsonObject["query"]?.jsonObject ?: return emptyMap()
            val renamed = HashMap<String, String>()
            for (key in listOf("normalized", "redirects")) {
                query[key]?.jsonArray?.forEach {
                    val from = it.jsonObject["from"]!!.jsonPrimitive.content
                    val to = it.jsonObject["to"]!!.jsonPrimitive.content
                    renamed[from] = to
                }
            }
            val byTitle = query["pages"]?.jsonArray.orEmpty().mapNotNull { page ->
                val o = page.jsonObject
                val extract = o["extract"]?.jsonPrimitive?.content ?: return@mapNotNull null
                o["title"]!!.jsonPrimitive.content to extract
            }.toMap()
            return requested.mapNotNull { title ->
                var resolved = title
                repeat(3) { resolved = renamed[resolved] ?: resolved }
                byTitle[resolved]?.let { title to it }
            }.toMap()
        }
    }
}
