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
    /** Nom du fichier image sur Commons (P18), sans « File: ». */
    val imageFile: String? = null,
)

/** Licence et auteur d'un fichier de Commons, tels que Commons les déclare. */
data class ImageCredit(val license: String, val author: String?)

/** Où trouver les œuvres Wikidata d'un musée et les récits Wikipedia. Remplaçable dans les tests. */
interface WikimediaSource {
    fun works(museum: MuseumSpec): List<WikidataWork>

    /** Introduction en texte brut de chaque article de [titles] (clé : le titre demandé). */
    fun intros(titles: List<String>): Map<String, String>

    /** Licence et auteur de chaque fichier de [files] que Commons connaît (clé : le nom demandé). */
    fun credits(files: List<String>): Map<String, ImageCredit>
}

/** Interroge le service SPARQL de Wikidata et l'API de Wikipedia en français, avec cache disque. */
class WikimediaApi(cacheDir: Path) : WikimediaSource {
    private val sparql = CachedHttp(cacheDir.resolve("wikidata"), politenessMillis = 1_000, accept = "application/sparql-results+json")
    private val wikipedia = CachedHttp(cacheDir.resolve("wikipedia"), politenessMillis = 200)
    private val commons = CachedHttp(cacheDir.resolve("commons"), politenessMillis = 200)

    override fun works(museum: MuseumSpec): List<WikidataWork> {
        val qid = museum.wikidataId ?: return emptyList()
        // Toute collection qui fait partie du musée (départements compris), avec le numéro d'inventaire.
        val query = """
            SELECT ?item ?inv ?label ?links ?title ?image (GROUP_CONCAT(DISTINCT ?creatorLabel; separator="|") AS ?creators) WHERE {
              ?item wdt:P195 ?collection . ?collection wdt:P361* wd:$qid .
              ?item wdt:P217 ?inv ; wikibase:sitelinks ?links .
              OPTIONAL { ?item rdfs:label ?label FILTER(LANG(?label) = "fr") }
              OPTIONAL { ?item wdt:P170 ?creator . ?creator rdfs:label ?creatorLabel FILTER(LANG(?creatorLabel) = "fr") }
              OPTIONAL { ?article schema:about ?item ; schema:isPartOf <https://fr.wikipedia.org/> ; schema:name ?title }
              OPTIONAL { ?item wdt:P18 ?image }
            } GROUP BY ?item ?inv ?label ?links ?title ?image
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

    override fun credits(files: List<String>): Map<String, ImageCredit> {
        val result = HashMap<String, ImageCredit>()
        // L'API Commons accepte 50 titres par requête.
        for (batch in files.distinct().sorted().chunked(50)) {
            val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&prop=imageinfo" +
                "&iiprop=extmetadata&iiextmetadatafilter=" + enc("LicenseShortName|Artist") + "&redirects=1&titles=" +
                enc(batch.joinToString("|") { "File:$it" })
            result += parseCredits(commons.get(url), batch)
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
                    imageFile = value("image")?.substringAfterLast('/')?.let { java.net.URLDecoder.decode(it.replace("+", "%2B"), Charsets.UTF_8) },
                )
            }
        }

        /**
         * Licence et auteur des fichiers demandés. Seules les licences libres sont gardées (domaine public, CC0,
         * CC BY et CC BY-SA) ; CC BY et CC BY-SA exigent un auteur.
         */
        internal fun parseCredits(body: String, requested: List<String>): Map<String, ImageCredit> {
            val query = json.parseToJsonElement(body).jsonObject["query"]?.jsonObject ?: return emptyMap()
            val renamed = titleChanges(query)
            val byTitle = query["pages"]?.jsonArray.orEmpty().mapNotNull { page ->
                val o = page.jsonObject
                val meta = o["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject?.get("extmetadata")?.jsonObject
                    ?: return@mapNotNull null
                fun field(name: String) = meta[name]?.jsonObject?.get("value")?.jsonPrimitive?.content
                val license = field("LicenseShortName")?.trim() ?: return@mapNotNull null
                val author = field("Artist")?.let(::plainText)?.takeIf { it.isNotBlank() }
                if (!isFreeLicense(license)) return@mapNotNull null
                if (author == null && license.startsWith("CC BY", ignoreCase = true)) return@mapNotNull null
                o["title"]!!.jsonPrimitive.content to ImageCredit(license, author)
            }.toMap()
            return requested.mapNotNull { file ->
                var resolved = "File:$file"
                repeat(3) { resolved = renamed[resolved] ?: resolved }
                byTitle[resolved]?.let { file to it }
            }.toMap()
        }

        private val FREE_LICENSE = Regex("^(public domain|cc0.*|pdm.*|cc[- ]by(-sa)?[- ]\\d\\.\\d.*)$", RegexOption.IGNORE_CASE)

        internal fun isFreeLicense(license: String) = FREE_LICENSE.matches(license.trim())

        /** Le champ « Artist » de Commons est du HTML : on garde le texte. */
        internal fun plainText(html: String): String =
            html.replace(Regex("<[^>]*>"), "")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ").trim()

        private fun titleChanges(query: JsonObject): Map<String, String> {
            val renamed = HashMap<String, String>()
            for (key in listOf("normalized", "redirects")) {
                query[key]?.jsonArray?.forEach {
                    renamed[it.jsonObject["from"]!!.jsonPrimitive.content] = it.jsonObject["to"]!!.jsonPrimitive.content
                }
            }
            return renamed
        }

        /** Rattache chaque introduction au titre demandé, en suivant les normalisations et redirections. */
        internal fun parseIntros(body: String, requested: List<String>): Map<String, String> {
            val query = json.parseToJsonElement(body).jsonObject["query"]?.jsonObject ?: return emptyMap()
            val renamed = titleChanges(query)
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
