package app.audioguide.favorites

import kotlin.test.Test
import kotlin.test.assertEquals

class IsoDateTest {
    @Test
    fun epochIsFirstOfJanuary1970() = assertEquals("1970-01-01", isoDate(0))

    @Test
    fun knownDates() {
        assertEquals("2026-10-12", isoDate(1_791_763_200_000L)) // 2026-10-12 00:00 UTC
        assertEquals("2000-02-29", isoDate(951_782_400_000L)) // année bissextile
        assertEquals("2024-12-31", isoDate(1_735_689_599_000L)) // dernière seconde de l'année
    }

    @Test
    fun timeOfDayDoesNotChangeTheDate() {
        assertEquals("2026-10-12", isoDate(1_791_763_200_000L + 86_399_999L))
        assertEquals("2026-10-11", isoDate(1_791_763_200_000L - 1))
    }
}
