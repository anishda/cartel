package app.audioguide.images

import app.audioguide.images.ImageCacheExpiry.Decision
import app.audioguide.images.ImageCacheExpiry.MAX_AGE_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageCacheExpiryTest {
    private val now = 1_800_000_000_000L

    @Test
    fun firstLaunchStartsTheClockWithoutClearing() =
        assertEquals(Decision.Start(now), ImageCacheExpiry.decide(since = null, now = now))

    @Test
    fun keepsACacheYoungerThanAWeek() =
        assertEquals(Decision.Keep, ImageCacheExpiry.decide(since = now - MAX_AGE_MILLIS + 1, now = now))

    @Test
    fun clearsACacheOfAWeekOrMore() {
        assertEquals(Decision.Clear(now), ImageCacheExpiry.decide(since = now - MAX_AGE_MILLIS, now = now))
        assertEquals(Decision.Clear(now), ImageCacheExpiry.decide(since = now - 30 * MAX_AGE_MILLIS, now = now))
    }

    @Test
    fun clearsWhenTheClockWentBackwards() =
        assertEquals(Decision.Clear(now), ImageCacheExpiry.decide(since = now + 1_000, now = now))
}
