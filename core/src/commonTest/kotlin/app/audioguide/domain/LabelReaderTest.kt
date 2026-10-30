package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LabelReaderTest {
    private class Call(val region: Region?, val scale: Float)

    private class FakeRecognizer(
        val first: RecognizedText,
        val second: RecognizedText? = null,
    ) : TextRecognizer {
        val calls = mutableListOf<Call>()
        override suspend fun recognize(image: ByteArray, region: Region?, scale: Float): Result<RecognizedText> {
            calls += Call(region, scale)
            return if (calls.size == 1) Result.success(first) else Result.success(checkNotNull(second))
        }
    }

    private fun lines(count: Int, height: Float, confidence: Float, imageHeight: Int = 2000, imageWidth: Int = 1500) = RecognizedText(
        (0 until count).map { TextLine("Une ligne de texte numéro $it", 0.2f, 0.2f + it * 0.04f, 0.8f, 0.2f + it * 0.04f + height, confidence) },
        imageWidth, imageHeight,
    )

    @Test
    fun clearBigTextIsReadOnce() = runTest {
        val recognizer = FakeRecognizer(first = lines(10, height = 0.03f, confidence = 0.99f)) // 60 px
        LabelReader(recognizer).read(byteArrayOf(1))
        assertEquals(1, recognizer.calls.size)
    }

    @Test
    fun smallTextIsReadAgainOnTheLabelZoneEnlarged() = runTest {
        val small = lines(10, height = 0.008f, confidence = 0.9f) // 16 px
        val better = lines(12, height = 0.02f, confidence = 1f)
        val recognizer = FakeRecognizer(first = small, second = better)
        val result = LabelReader(recognizer).read(byteArrayOf(1)).getOrThrow()

        assertEquals(2, recognizer.calls.size)
        val second = recognizer.calls[1]
        assertNotNull(second.region)
        assertTrue(second.scale in 2f..3f, "agrandissement ${second.scale}")
        assertEquals(better, result, "la meilleure lecture est gardée")
    }

    @Test
    fun theSecondReadingIsKeptOnlyIfItIsBetter() = runTest {
        val first = lines(10, height = 0.008f, confidence = 0.95f)
        val worse = lines(3, height = 0.02f, confidence = 0.4f)
        val result = LabelReader(FakeRecognizer(first, worse)).read(byteArrayOf(1)).getOrThrow()
        assertEquals(first, result)
    }

    @Test
    fun doubtfulTextIsReadAgainEvenIfBigEnough() = runTest {
        val recognizer = FakeRecognizer(first = lines(10, height = 0.03f, confidence = 0.6f), second = lines(10, height = 0.03f, confidence = 1f))
        LabelReader(recognizer).read(byteArrayOf(1))
        assertEquals(2, recognizer.calls.size)
    }

    @Test
    fun aSmallImageWithNothingReadIsTriedAgainEnlarged() = runTest {
        val empty = RecognizedText(emptyList(), 337, 450)
        val recognizer = FakeRecognizer(first = empty, second = lines(8, 0.02f, 1f))
        val result = LabelReader(recognizer).read(byteArrayOf(1)).getOrThrow()
        assertEquals(2, recognizer.calls.size)
        assertNull(recognizer.calls[1].region)
        assertEquals(8, result.lines.size)
    }

    @Test
    fun aBigImageWithNothingReadIsNotRetried() = runTest {
        val recognizer = FakeRecognizer(first = RecognizedText(emptyList(), 4000, 3000))
        LabelReader(recognizer).read(byteArrayOf(1))
        assertEquals(1, recognizer.calls.size)
    }

    @Test
    fun theEnlargementNeverExceedsTheMaximumSide() = runTest {
        // Image déjà énorme : soit on ne relit pas, soit l'agrandissement est borné par la taille de la zone.
        val big = lines(10, height = 0.002f, confidence = 0.9f, imageHeight = 6000, imageWidth = 4500)
        val recognizer = FakeRecognizer(first = big, second = big)
        LabelReader(recognizer).read(byteArrayOf(1))
        val second = recognizer.calls.getOrNull(1) ?: return@runTest
        val side = 6000f * (second.region?.height ?: 1f) * second.scale
        assertTrue(side <= 3601f, "côté $side")
    }

    @Test
    fun aMidSizeSmallTextIsEnlargedWithinTheLimit() = runTest {
        // 2000 × 1500, texte de 16 px : zone d'environ 1500 × 900, agrandie ×2,75 → côté ~4100 > 3600 : bornée.
        val recognizer = FakeRecognizer(
            first = lines(10, height = 0.008f, confidence = 0.9f),
            second = lines(10, height = 0.02f, confidence = 1f),
        )
        LabelReader(recognizer).read(byteArrayOf(1))
        val second = recognizer.calls[1]
        val region = checkNotNull(second.region)
        val side = maxOf(1500f * region.width, 2000f * region.height) * second.scale
        assertTrue(side <= 3601f, "côté $side")
        assertTrue(second.scale >= 1.3f)
    }

    @Test
    fun aRecognizerFailureIsPassedOn() = runTest {
        val failing = object : TextRecognizer {
            override suspend fun recognize(image: ByteArray, region: Region?, scale: Float) = Result.failure<RecognizedText>(IllegalStateException("boum"))
        }
        assertEquals("boum", LabelReader(failing).read(byteArrayOf(1)).exceptionOrNull()?.message)
    }

    @Test
    fun aFailedSecondPassFallsBackToTheFirstReading() = runTest {
        var calls = 0
        val first = lines(10, height = 0.008f, confidence = 0.9f)
        val recognizer = object : TextRecognizer {
            override suspend fun recognize(image: ByteArray, region: Region?, scale: Float) =
                if (++calls == 1) Result.success(first) else Result.failure(IllegalStateException("mémoire"))
        }
        assertEquals(first, LabelReader(recognizer).read(byteArrayOf(1)).getOrThrow())
    }
}
