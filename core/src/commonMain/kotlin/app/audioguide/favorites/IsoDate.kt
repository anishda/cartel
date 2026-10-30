package app.audioguide.favorites

/** Date du jour au format `2026-10-12` (UTC) pour des millisecondes depuis 1970. */
fun isoDate(epochMillis: Long): String {
    // Algorithme de Howard Hinnant : jours depuis 1970 → date civile.
    val days = epochMillis.floorDiv(86_400_000L) + 719_468
    val era = days.floorDiv(146_097L)
    val dayOfEra = days - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPrime = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthPrime + 2) / 5 + 1
    val month = if (monthPrime < 10) monthPrime + 3 else monthPrime - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
