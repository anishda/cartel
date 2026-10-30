package app.audioguide.packbuilder

import java.nio.file.Path
import kotlin.system.exitProcess

private const val USAGE = """
Fabrique les packs musée de l'app Audioguide à partir de la base Joconde.

Usage : ./gradlew :pack-builder:run --args="[--out <dossier>] [--cache <dossier>] [--museum louvre,orsay]"

  --out      dossier de sortie : <musée>.pack.db et manifest.json   (défaut : packs)
  --cache    cache des réponses de l'API, pour refaire un pack sans réseau   (défaut : build/pack-cache)
  --museum   liste d'identifiants parmi : %s   (défaut : tous)
"""

fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { it[0] to it.getOrNull(1) }
    if ("--help" in options || options.keys.any { it !in setOf("--out", "--cache", "--museum") }) {
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
    val manifest = PackBuilder(JocondeTabularApi(cache), out).buildAll(museums)

    println("\n${manifest.packs.size} pack(s) dans ${out.toAbsolutePath()} :")
    manifest.packs.forEach { println("  ${it.file}  ${it.recordCount} œuvres  ${it.sizeBytes / 1024} Ko  sha256 ${it.sha256.take(12)}…") }
}
