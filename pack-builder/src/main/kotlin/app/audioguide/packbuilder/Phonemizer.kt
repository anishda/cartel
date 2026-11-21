package app.audioguide.packbuilder

import java.util.concurrent.TimeUnit

/** Transforme des segments de texte français en phonèmes (une phrase par ligne dans le résultat). */
fun interface Phonemizer {
    fun phonemize(texts: List<String>): List<String>
}

/**
 * Lance une commande qui lit un segment par ligne sur son entrée et rend une ligne de phonèmes par segment, les
 * phrases séparées par des tabulations (`tools/phonemize.py`).
 */
class ExternalPhonemizer(private val command: List<String>) : Phonemizer {
    override fun phonemize(texts: List<String>): List<String> {
        if (texts.isEmpty()) return emptyList()
        require(texts.none { '\n' in it }) { "Un segment ne peut pas contenir de saut de ligne" }
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        // La commande répond ligne à ligne : on écrit dans un fil à part pour ne pas bloquer sur un tampon plein.
        val writer = Thread {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { out -> texts.forEach { out.write(it); out.write("\n") } }
        }.also { it.start() }
        val lines = process.inputStream.bufferedReader(Charsets.UTF_8).readLines()
        writer.join()
        check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "Le phonétiseur a échoué : ${command.joinToString(" ")}" }
        check(lines.size == texts.size) { "Le phonétiseur a rendu ${lines.size} lignes pour ${texts.size} segments" }
        return lines.map { it.replace('\t', '\n') }
    }
}
