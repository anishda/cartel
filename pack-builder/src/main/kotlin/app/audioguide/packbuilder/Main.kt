package app.audioguide.packbuilder

import java.nio.file.Path
import kotlin.system.exitProcess

private const val USAGE = """
Fabrique les packs musée de l'app Audioguide à partir de la base Joconde.

Usage : ./gradlew :pack-builder:run --args="[--out <dossier>] [--cache <dossier>] [--museum louvre,orsay] [--stories yes|no] [--phonemizer \"<commande>\"]"

  --out      dossier de sortie : <musée>.pack.db et manifest.json   (défaut : packs)
  --cache    cache des réponses de l'API, pour refaire un pack sans réseau   (défaut : build/pack-cache)
  --museum   liste d'identifiants parmi : %s   (défaut : tous)
  --phonemizer  commande qui phonétise les textes lus (voir tools/phonemize.py) : les packs gardent les phonèmes pour la
                voix neuronale   (défaut : aucune, packs sans phonèmes)
  --stories  relier les œuvres à Wikidata et reprendre l'introduction Wikipedia (CC BY-SA)   (défaut : yes)
"""

fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { it[0] to it.getOrNull(1) }
    if ("--help" in options || options.keys.any { it !in setOf("--out", "--cache", "--museum", "--stories", "--phonemizer") }) {
        println(USAGE.trimIndent().format(Museums.all.joinToString { it.id }))
        exitProcess(if ("--help" in options) 0 else 2)
    }

    val museums = options["--museum"]?.split(',')?.map { id ->
        Museums.byId(id.trim()) ?: run {
            System.err.println("Musée inconnu : $id (connus : ${Museums.all.joinToString { it.id }})")
            exitProcess(2)
        }
    } ?: Museums.all

    val out = Path.of(options["--out"] ?: "packs")
    val cache = Path.of(options["--cache"] ?: "build/pack-cache")
    val wikimedia = if (options["--stories"] == "no") null else WikimediaApi(cache)
    val phonemizer = options["--phonemizer"]?.let { ExternalPhonemizer(it.trim().split(Regex("\\s+"))) }
    val manifest = PackBuilder(JocondeTabularApi(cache), out, wikimedia = wikimedia, phonemizer = phonemizer).buildAll(museums)

    println("\n${manifest.packs.size} pack(s) dans ${out.toAbsolutePath()} :")
    manifest.packs.forEach { println("  ${it.file}  ${it.recordCount} œuvres  ${it.sizeBytes / 1024} Ko  sha256 ${it.sha256.take(12)}…") }
}
