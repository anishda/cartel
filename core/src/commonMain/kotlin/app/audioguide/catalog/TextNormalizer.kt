package app.audioguide.catalog

/**
 * Forme de recherche d'un texte : minuscules, sans accents, tout ce qui n'est pas une lettre ou un chiffre
 * devient une espace simple. Utilisée à l'identique à la fabrication des packs et à la recherche.
 */
fun normalizeForSearch(text: String): String {
    val out = StringBuilder(text.length)
    var lastWasSpace = true
    for (original in text) {
        for (c in foldAccent(original.lowercaseChar())) {
            if (c.isLetterOrDigit()) {
                out.append(c)
                lastWasSpace = false
            } else if (!lastWasSpace) {
                out.append(' ')
                lastWasSpace = true
            }
        }
    }
    return out.toString().trimEnd()
}

/** Numéro d'inventaire comparable : « RF 1954-23 » et « rf195423 » donnent la même chose. */
fun normalizeInventory(inventory: String): String =
    buildString { inventory.lowercase().forEach { if (it.isLetterOrDigit()) append(it) } }

private fun foldAccent(c: Char): String = when (c) {
    'à', 'á', 'â', 'ã', 'ä', 'å', 'ā' -> "a"
    'ç', 'ć', 'č' -> "c"
    'è', 'é', 'ê', 'ë', 'ē', 'ě' -> "e"
    'ì', 'í', 'î', 'ï', 'ī' -> "i"
    'ñ', 'ń' -> "n"
    'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō' -> "o"
    'ù', 'ú', 'û', 'ü', 'ū' -> "u"
    'ý', 'ÿ' -> "y"
    'š', 'ś' -> "s"
    'ž', 'ź', 'ż' -> "z"
    'œ' -> "oe"
    'æ' -> "ae"
    'ß' -> "ss"
    else -> c.toString()
}
