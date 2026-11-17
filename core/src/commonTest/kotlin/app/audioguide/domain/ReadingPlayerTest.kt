package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class ReadingPlanTest {
    private val text = "Première phrase du premier paragraphe. Deuxième phrase.\n\nSecond paragraphe, une seule phrase."

    @Test
    fun paragraphsBecomeSeparateSegmentsWithAPauseBetweenThem() {
        val plan = ReadingPlan.build(text, 1f)
        assertEquals(2, plan.segments.size)
        assertEquals("Première phrase du premier paragraphe. Deuxième phrase.", plan.segments[0])
        val speaking = (plan.segments[0].length / ReadingPlan.CHARS_PER_SECOND * 1000).toLong()
        assertEquals(speaking + ReadingPlan.PARAGRAPH_PAUSE_MILLIS, plan.durationMillis(0))
        assertEquals(plan.durationMillis(0), plan.startMillis(1))
    }

    @Test
    fun aFasterRateShortensTheEstimate() {
        assertTrue(ReadingPlan.build(text, 1.5f).totalMillis < ReadingPlan.build(text, 1f).totalMillis)
    }

    @Test
    fun longParagraphsAreCutAtSentences() {
        val sentence = "Une phrase d'environ soixante caractères, pas un de plus."
        val plan = ReadingPlan.build(List(12) { sentence }.joinToString(" "), 1f)
        assertTrue(plan.segments.size > 1)
        assertTrue(plan.segments.all { it.length <= 330 && it.endsWith(".") }, plan.segments.toString())
    }

    @Test
    fun indexAtFindsTheSegmentAndClampsToTheEnds() {
        val plan = ReadingPlan.build(text, 1f)
        assertEquals(0, plan.indexAt(0))
        assertEquals(1, plan.indexAt(plan.startMillis(1)))
        assertEquals(1, plan.indexAt(plan.totalMillis + 10_000))
    }

    @Test
    fun formatsAClock() {
        assertEquals("0:00", formatClock(0))
        assertEquals("1:05", formatClock(65_000))
        assertEquals("12:30", formatClock(750_000))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingPlayerTest {
    private class FakeSynth : SpeechSynthesizer {
        override val isSpeaking = MutableStateFlow(false)
        override val engineReady = MutableStateFlow(true)
        val spoken = mutableListOf<String>()
        var stops = 0
        private var done: (() -> Unit)? = null
        override fun voiceStatus(languageTag: String) = VoiceStatus.Ready
        override fun setRate(rate: Float) = Unit
        override fun voices(languageTag: String) = emptyList<VoiceOption>()
        override fun setPreferredVoice(languageTag: String, voiceId: String?) = Unit
        override fun speak(text: String, languageTag: String, onDone: (() -> Unit)?) { spoken += text; done = onDone }
        override fun stop() { stops++; done = null }
        /** Le moteur a fini de lire le segment en cours. */
        fun finish() { done?.also { done = null }?.invoke() }
    }

    private val synth = FakeSynth()
    private var clock = 0L
    private val text = "Un.\n\nDeux.\n\nTrois."

    private fun TestScope.player() = ReadingPlayer(synth, CoroutineScope(StandardTestDispatcher(testScheduler)), { clock }).also {
        it.load(ReadingPlan.build(text, 1f), "fr-FR")
    }

    @Test
    fun playsEverySegmentInOrderThenGoesIdle() = runTest {
        val player = player()
        player.play()
        assertEquals(listOf("Un."), synth.spoken)
        synth.finish(); testScheduler.advanceUntilIdle()
        synth.finish(); testScheduler.advanceUntilIdle()
        assertEquals(listOf("Un.", "Deux.", "Trois."), synth.spoken)
        assertEquals(PlayerStatus.Playing, player.state.value.status)
        synth.finish(); testScheduler.advanceUntilIdle()
        assertEquals(PlayerStatus.Idle, player.state.value.status)
    }

    @Test
    fun pauseStopsTheVoiceAndResumeRestartsTheSameSegment() = runTest {
        val player = player()
        player.play()
        synth.finish(); testScheduler.advanceUntilIdle()
        player.pause()
        assertEquals(PlayerStatus.Paused, player.state.value.status)
        // Une fin de lecture tardive d'un segment interrompu ne fait pas avancer.
        synth.finish(); testScheduler.advanceUntilIdle()
        assertEquals(listOf("Un.", "Deux."), synth.spoken)
        player.resume()
        assertEquals(listOf("Un.", "Deux.", "Deux."), synth.spoken)
        assertEquals(PlayerStatus.Playing, player.state.value.status)
    }

    @Test
    fun seekJumpsToTheSegmentAtThatTime() = runTest {
        val player = player()
        val plan = ReadingPlan.build(text, 1f)
        player.play()
        player.seekTo(plan.startMillis(2) + 10)
        assertEquals("Trois.", synth.spoken.last())
        player.pause()
        player.seekTo(0)
        assertEquals(0, player.positionMillis())
        assertEquals(PlayerStatus.Paused, player.state.value.status)
        player.resume()
        assertEquals("Un.", synth.spoken.last())
    }

    @Test
    fun positionGrowsWithTheClockButStaysInsideTheSegment() = runTest {
        val player = player()
        player.play()
        clock = 300
        assertEquals(300, player.positionMillis())
        clock = 1_000_000
        assertTrue(player.positionMillis() < ReadingPlan.build(text, 1f).durationMillis(0))
    }

    @Test
    fun measuredDurationsRefineTheTotal() = runTest {
        val player = player()
        val before = player.state.value.totalMillis
        player.play()
        clock = 10 // le segment a été lu bien plus vite que l'estimation
        synth.finish(); testScheduler.advanceUntilIdle()
        assertTrue(player.state.value.totalMillis < before)
    }

    @Test
    fun stopGoesBackToIdleAtTheStart() = runTest {
        val player = player()
        player.play()
        player.stop()
        assertEquals(PlayerStatus.Idle, player.state.value.status)
        assertEquals(0, player.positionMillis())
    }
}
