package app.audioguide.platform

import app.audioguide.domain.TextTranslator
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** ML Kit Translation : le modèle de chaque langue est téléchargé une fois (environ 30 Mo), puis tout reste hors ligne. */
class MlKitTextTranslator : TextTranslator {
    override val isAvailable = true

    override fun supports(from: String, to: String) =
        from != to && TranslateLanguage.fromLanguageTag(from) != null && TranslateLanguage.fromLanguageTag(to) != null

    override suspend fun translate(text: String, from: String, to: String): Result<String> = runCatching {
        val source = requireNotNull(TranslateLanguage.fromLanguageTag(from)) { "Unsupported language: $from" }
        val target = requireNotNull(TranslateLanguage.fromLanguageTag(to)) { "Unsupported language: $to" }
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build(),
        )
        try {
            // Sans le modèle, la traduction échoue : on le télécharge au besoin (réseau nécessaire cette seule fois).
            translator.downloadModelIfNeeded().await()
            translator.translate(text).await()
        } finally {
            translator.close()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
}
