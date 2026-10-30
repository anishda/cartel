package app.audioguide.favorites

/** Export des favoris en Markdown, pour les ranger dans un vault Obsidian (une section par musée). */
object FavoritesExport {
    /**
     * [exportedOn] : date lisible (`2026-10-12`). Les libellés sont ceux de la langue de l'app.
     */
    fun markdown(
        favorites: List<Favorite>,
        exportedOn: String,
        title: String = "Favoris — Audioguide",
        exportedLabel: String = "Exporté le",
        cartelsLabel: String = "Cartels scannés",
    ): String {
        val out = StringBuilder()
        out.append("# ").append(title).append("\n\n_").append(exportedLabel).append(' ').append(exportedOn).append("_\n")

        val byMuseum = favorites.filter { it.museumName != null }.groupBy { it.museumName!! }
            .toList().sortedBy { it.first.lowercase() }
        val cartels = favorites.filter { it.museumName == null }

        for ((museum, items) in byMuseum) section(out, museum, items)
        if (cartels.isNotEmpty()) section(out, cartelsLabel, cartels)
        return out.toString()
    }

    private fun section(out: StringBuilder, heading: String, items: List<Favorite>) {
        out.append("\n## ").append(heading).append("\n")
        for (favorite in items.sortedBy { it.createdAt }) {
            out.append("\n### ").append(favorite.title).append("\n")
            favorite.subtitle?.let { out.append(it).append("\n") }
            favorite.note?.let { note ->
                out.append("\n").append(note.lines().joinToString("\n") { "> $it" }).append("\n")
            }
            if (favorite.kind == FavoriteKind.Cartel) {
                out.append("\n```\n").append(favorite.body).append("\n```\n")
            }
        }
    }
}
