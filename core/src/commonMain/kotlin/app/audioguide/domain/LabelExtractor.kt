package app.audioguide.domain

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Le cartel retrouvé dans une photo : titres au-dessus des colonnes, puis le texte de chaque colonne. */
data class ExtractedLabel(
    /** Titres qui surplombent les colonnes (vide s'il n'y a qu'une colonne). */
    val header: String,
    /** Le texte de chaque colonne, de gauche à droite ; paragraphes séparés par une ligne vide. */
    val columns: List<String>,
    /** Lignes de l'OCR écartées (bruit, logos, texte étranger au cartel). */
    val ignoredLines: Int,
    /**
     * Les lignes retenues, une par ligne, dans l'ordre où l'OCR les voit (de haut en bas, puis de gauche à droite).
     * Pour séparer les langues d'un cartel bilingue dont les colonnes n'ont pas été reconnues : les lignes des deux
     * langues s'y alternent, ce que [text] a déjà recollé en paragraphes.
     */
    val lineText: String = "",
) {
    val text: String
        get() = (listOf(header) + columns).filter { it.isNotBlank() }.joinToString("\n\n")

    val isEmpty: Boolean get() = text.isBlank()
}

/**
 * Isole le cartel dans ce que l'OCR a lu de toute la photo : écarte le bruit (logos, filigranes, numéros de
 * téléphone, texte vertical, lignes douteuses), retient la zone de texte principale (la plus fournie, la plus
 * centrée), reconnaît les colonnes, et reconstitue les paragraphes d'après l'interligne.
 *
 * Tout se calcule sur les positions : on ne se fie pas à la hauteur des lignes seule, qui varie du simple au triple
 * quand la photo est prise de biais.
 */
object LabelExtractor {
    private const val MIN_CONFIDENCE = 0.35f
    private const val TINY_TEXT_RATIO = 0.4f
    private const val TINY_TEXT_MAX_CHARS = 25
    private const val TINY_SHORT_RATIO = 0.55f
    private const val TINY_SHORT_MAX_CHARS = 12
    private const val TINY_SHORT_CONFIDENCE = 0.8f
    private const val FRAGMENT_GAP = 1.5f
    private const val TITLE_MAX_CHARS = 40
    private const val MAX_STACK_GAP = 3.5f
    private const val MAX_COLUMN_GAP = 2.5f
    private const val SECONDARY_REGION_RATIO = 0.4f
    private const val SECONDARY_REGION_MIN_CHARS = 100
    private const val PARAGRAPH_PITCH_RATIO = 1.55f
    private const val SENTENCE_END = ".:;!?…»\")"

    private val urlOrEmail = Regex("""https?://|www\.|\S+@\S+\.\S+""", RegexOption.IGNORE_CASE)
    private val phone = Regex("""\+?\d[\d .\-]{7,}\d""")

    private class Box(
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val confidence: Float,
        /** Nombre de lignes de l'OCR que cette ligne regroupe. */
        val sources: Int = 1,
    ) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
    }

    fun extract(recognized: RecognizedText): ExtractedLabel {
        val w = recognized.imageWidth.coerceAtLeast(1).toFloat()
        val h = recognized.imageHeight.coerceAtLeast(1).toFloat()
        val all = recognized.lines
            .map { Box(it.text.trim(), it.left * w, it.top * h, it.right * w, it.bottom * h, it.confidence) }
            .filter { it.text.isNotEmpty() }

        val readable = all.filter(::isReadable)
        if (readable.isEmpty()) return ExtractedLabel("", emptyList(), ignoredLines = all.size)

        val body = bodyHeight(readable)
        val selected = chooseRegions(cluster(readable), w, h).flatten()
        val (header, columns) = splitColumns(selected, body)

        // Les morceaux d'une même ligne se recollent colonne par colonne (jamais d'une colonne à l'autre), puis les
        // petits textes isolés (logos, légendes) sont écartés.
        fun prepare(flow: List<Box>) = dropTiny(mergeFragments(flow), body)
        val headerBoxes = prepare(header)
        val columnBoxes = columns.map(::prepare).filter { it.isNotEmpty() }
        val kept = (headerBoxes + columnBoxes.flatten()).sumOf { it.sources }
        return ExtractedLabel(
            header = flow(headerBoxes),
            columns = columnBoxes.map(::flow).filter { it.isNotBlank() },
            ignoredLines = all.size - kept,
            lineText = (headerBoxes + columnBoxes.flatten())
                .sortedWith(compareBy({ it.centerY }, { it.left }))
                .joinToString("\n") { it.text },
        )
    }

    /** Écarte ce qui n'est manifestement pas du texte de cartel. */
    private fun isReadable(box: Box): Boolean {
        if (box.confidence < MIN_CONFIDENCE) return false
        val compact = box.text.filterNot { it.isWhitespace() }
        val alnum = compact.count { it.isLetterOrDigit() }
        if (alnum < 2 || alnum < compact.length * 0.5) return false
        if (urlOrEmail.containsMatchIn(box.text)) return false
        if (phone.find(box.text)?.value?.count { it.isDigit() }?.let { it >= 9 } == true) return false
        // Texte vertical (titre de tranche, rotation) : plus haut que large.
        if (box.text.length >= 3 && box.width < box.height) return false
        return true
    }

    /** Recolle les morceaux d'une même ligne que l'OCR a coupée (« pour » « ancêtres » « des »). */
    private fun mergeFragments(boxes: List<Box>): List<Box> {
        val parent = IntArray(boxes.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val a = boxes[i]; val b = boxes[j]
            val minH = min(a.height, b.height)
            val overlapY = min(a.bottom, b.bottom) - max(a.top, b.top)
            val gapX = max(a.left, b.left) - min(a.right, b.right)
            if (overlapY >= 0.6f * minH && gapX >= -0.1f * minH && gapX <= FRAGMENT_GAP * max(a.height, b.height)) {
                parent[find(i)] = find(j)
            }
        }
        return boxes.indices.groupBy(::find).values.map { members ->
            val parts = members.map { boxes[it] }.sortedBy { it.left }
            Box(
                text = parts.joinToString(" ") { it.text },
                left = parts.minOf { it.left }, top = parts.minOf { it.top },
                right = parts.maxOf { it.right }, bottom = parts.maxOf { it.bottom },
                confidence = parts.sumOf { it.confidence.toDouble() * it.text.length }.toFloat() / parts.sumOf { it.text.length },
                sources = parts.sumOf { it.sources },
            )
        }
    }

    /** Petits textes isolés : logos, légendes, mentions. Une ligne longue n'est jamais écartée pour sa taille. */
    private fun dropTiny(boxes: List<Box>, body: Float): List<Box> = boxes.filterNot { box ->
        (box.height < TINY_TEXT_RATIO * body && box.text.length <= TINY_TEXT_MAX_CHARS) ||
            (box.height < TINY_SHORT_RATIO * body && box.text.length <= TINY_SHORT_MAX_CHARS && box.confidence < TINY_SHORT_CONFIDENCE)
    }

    private fun bodyHeight(boxes: List<Box>): Float {
        val long = boxes.filter { it.text.length >= 15 }.map { it.height }
        return median(long.ifEmpty { boxes.map { it.height } })
    }

    /** Regroupe les lignes voisines : empilées (même zone de texte) ou côte à côte (colonnes). */
    private fun cluster(boxes: List<Box>): List<List<Box>> {
        val parent = IntArray(boxes.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val a = boxes[i]; val b = boxes[j]
            val hMax = max(a.height, b.height)
            val overlapY = min(a.bottom, b.bottom) - max(a.top, b.top)
            val gapY = max(0f, max(a.top, b.top) - min(a.bottom, b.bottom))
            val overlapX = min(a.right, b.right) - max(a.left, b.left)
            val gapX = max(0f, max(a.left, b.left) - min(a.right, b.right))
            val sameRow = overlapY >= 0.5f * min(a.height, b.height)
            val linked = if (sameRow) gapX <= MAX_COLUMN_GAP * hMax else overlapX > 0 && gapY <= MAX_STACK_GAP * hMax
            if (linked) parent[find(i)] = find(j)
        }
        return boxes.indices.groupBy(::find).values.map { members -> members.map { boxes[it] } }
    }

    /** La zone la plus fournie et la plus centrée, plus toute autre zone presque aussi importante. */
    private fun chooseRegions(regions: List<List<Box>>, width: Float, height: Float): List<List<Box>> {
        fun chars(region: List<Box>) = region.sumOf { it.text.length }
        fun score(region: List<Box>): Float {
            val total = chars(region).toFloat()
            val cx = region.sumOf { (it.centerX * it.text.length).toDouble() }.toFloat() / total
            val cy = region.sumOf { (it.centerY * it.text.length).toDouble() }.toFloat() / total
            val centrality = 1f - min(1f, hypot(cx / width - 0.5f, cy / height - 0.5f) / 0.7f)
            return total * (0.6f + 0.4f * centrality)
        }
        val best = regions.maxBy(::score)
        return regions.filter { it === best || (chars(it) >= SECONDARY_REGION_MIN_CHARS && score(it) >= SECONDARY_REGION_RATIO * score(best)) }
            .sortedBy { region -> region.minOf { it.left } }
    }

    /** Sépare les titres et les colonnes. Une seule colonne : pas de titres à part. */
    private fun splitColumns(boxes: List<Box>, body: Float): Pair<List<Box>, List<List<Box>>> {
        val unionLeft = boxes.minOf { it.left }
        val unionRight = boxes.maxOf { it.right }
        val narrow = boxes.filter { it.width <= 0.65f * (unionRight - unionLeft) }
        val split = (if (narrow.size >= 6) findGutter(narrow, body) else null)
            ?: return emptyList<Box>() to listOf(boxes)

        val left = narrow.filter { it.centerX < split }
        val right = narrow.filter { it.centerX >= split }
        val leftChars = left.sumOf { it.text.length }
        val rightChars = right.sumOf { it.text.length }
        if (left.size < 3 || right.size < 3 || min(leftChars, rightChars) < 0.15f * (leftChars + rightChars)) {
            return emptyList<Box>() to listOf(boxes)
        }

        // Haut des colonnes : la première ligne qui ne traverse pas la gouttière.
        val firstColumnTop = narrow.filter { it.right <= split || it.left >= split }.minOfOrNull { it.top } ?: narrow.minOf { it.top }
        val header = mutableListOf<Box>()
        val leftColumn = mutableListOf<Box>()
        val rightColumn = mutableListOf<Box>()
        for (box in boxes) {
            val crossing = box.left < split && box.right > split
            when {
                crossing && box.bottom <= firstColumnTop + 0.5f * body -> header += box
                box.centerX < split -> leftColumn += box
                else -> rightColumn += box
            }
        }
        return header to listOf(leftColumn, rightColumn)
    }

    /**
     * Position de la plus large bande verticale quasi vide entre deux groupes de lignes, ou `null`. On compte, pour
     * chaque abscisse, combien de lignes la couvrent ; quelques lignes qui traversent la gouttière (un sous-titre
     * centré) ne la comblent pas : le seuil est un cinquième du maximum.
     */
    private fun findGutter(narrow: List<Box>, body: Float): Float? {
        val from = narrow.minOf { it.left }
        val to = narrow.maxOf { it.right }
        val binWidth = max(1f, (to - from) / 400f)
        val bins = IntArray(((to - from) / binWidth).toInt() + 1)
        for (box in narrow) {
            val first = ((box.left - from) / binWidth).toInt().coerceIn(0, bins.lastIndex)
            val last = ((box.right - from) / binWidth).toInt().coerceIn(0, bins.lastIndex)
            for (i in first..last) bins[i]++
        }
        val emptyLimit = max(1, bins.max() / 5)

        var best: IntRange? = null
        var start = -1
        for (i in bins.indices) {
            val empty = bins[i] <= emptyLimit
            if (empty && start < 0) start = i
            if (!empty && start >= 0) {
                // Une bande vide doit être encadrée de texte des deux côtés.
                if (start > 0 && (best == null || (i - start) > best.count())) best = start until i
                start = -1
            }
        }
        val run = best ?: return null
        if (run.count() * binWidth < max(0.6f * body, 4f)) return null
        return from + (run.first + run.last + 1) / 2f * binWidth
    }

    /** Remet les lignes d'un flux dans l'ordre de lecture et les assemble en paragraphes d'après l'interligne. */
    private fun flow(boxes: List<Box>): String {
        if (boxes.isEmpty()) return ""
        val lines = boxes.sortedWith(compareBy({ it.centerY }, { it.left }))
        // On mesure sur le centre des lignes : en perspective, le haut des boîtes varie avec leur inclinaison.
        val steps = lines.zipWithNext { a, b -> b.centerY - a.centerY }.filter { it > 0 }
        // L'interligne du texte courant est le plus petit et le plus fréquent : on le lit au 30e percentile,
        // la médiane étant tirée vers le haut par les titres et les blancs entre paragraphes.
        val pitch = if (steps.isEmpty()) 0f else steps.sorted()[(steps.size * 3 / 10).coerceAtMost(steps.lastIndex)]

        val paragraphs = mutableListOf<MutableList<Box>>()
        for ((i, line) in lines.withIndex()) {
            val gap = i > 0 && pitch > 0 && line.centerY - lines[i - 1].centerY > PARAGRAPH_PITCH_RATIO * pitch
            // Une ligne qui commence par une minuscule, après une phrase qui n'est pas finie, prolonge le paragraphe :
            // un blanc plus large (ligne un peu décalée, photo de biais) ne coupe pas une phrase en deux.
            val continuesSentence = i > 0 && line.text.first().isLowerCase() && lines[i - 1].text.last() !in SENTENCE_END
            val startsNew = i == 0 || (gap && !continuesSentence)
            if (startsNew) paragraphs += mutableListOf(line) else paragraphs.last() += line
        }

        val texts = paragraphs.flatMap(::splitAtHeadings).map { paragraph ->
            paragraph.fold("") { text, line ->
                when {
                    text.isEmpty() -> line.text
                    // Mot coupé en fin de ligne : « pein- » + « ture ».
                    text.endsWith("-") && text.length > 3 && line.text.first().isLowerCase() -> text.dropLast(1) + line.text
                    else -> "$text ${line.text}"
                }
            }
        }
        return mergeTitles(texts).joinToString("\n\n")
    }

    /**
     * Un intertitre au milieu d'un paragraphe (« La bonne heure dans le bon sens ») : ligne courte sans ponctuation,
     * après une phrase finie et avant une ligne qui commence par une majuscule. Il devient un paragraphe à lui seul,
     * pour que la voix marque une pause au lieu d'enchaîner le titre et la phrase suivante.
     */
    private fun splitAtHeadings(lines: List<Box>): List<List<Box>> {
        val parts = mutableListOf(mutableListOf<Box>())
        for ((i, line) in lines.withIndex()) {
            val previous = lines.getOrNull(i - 1)
            val next = lines.getOrNull(i + 1)
            val heading = previous != null && next != null &&
                previous.text.last() in SENTENCE_END &&
                line.text.length <= TITLE_MAX_CHARS && line.text.first().isUpperCase() && line.text.last().isLetterOrDigit() &&
                next.text.first().isUpperCase()
            if (heading) {
                parts += mutableListOf(line)
                parts += mutableListOf<Box>()
            } else {
                parts.last() += line
            }
        }
        return parts.filter { it.isNotEmpty() }
    }

    /** Des lignes de titre courtes, sans point final, qui se suivent (nom, dates, métier) ne font qu'un bloc. */
    private fun mergeTitles(paragraphs: List<String>): List<String> {
        fun isTitle(text: String) = text.length <= TITLE_MAX_CHARS && text.last() !in ".:;!?…"
        val merged = mutableListOf<String>()
        for (paragraph in paragraphs) {
            if (merged.isNotEmpty() && isTitle(merged.last()) && isTitle(paragraph)) {
                merged[merged.lastIndex] = merged.last() + " " + paragraph
            } else {
                merged += paragraph
            }
        }
        return merged
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }
}
