package app.mise.data

enum class EntryKind { Cuisine, Dish, Note }

/** One line of a pasted list, before the user reviews it. [text] is the parenthetical, filed under [kind]. */
data class ParsedEntry(
    val id: Int,
    val name: String,
    val location: String,
    val kind: EntryKind = EntryKind.Cuisine,
    val text: String = "",
)

/**
 * Parses lists like:
 *   -- Columbia, MD --
 *   Roggenart (Brunch)
 *   Busboys and Poets (Get Crab Fritters)
 * "-- X --" lines set the location for the lines below. A trailing "(...)" is the cuisine, dish or note;
 * [guessKind] picks a starting point and the review screen lets the user change it.
 */
object ListParser {
    private val header = Regex("""^-{2,}\s*(.+?)\s*-{2,}$""")
    private val bullet = Regex("""^([•*]|-(?!-))\s+""")
    private val trailingParen = Regex("""^(.*?)\s*\(([^()]*)\)\s*$""")
    private val dishVerb = Regex("""^(get|try|order|have)\s+(.+)""", RegexOption.IGNORE_CASE)
    private val noteStart = Regex("""^(must|go\b|with\b|fixed|reservation|bring|ask|only|closed|remember)""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, defaultLocation: String): List<ParsedEntry> {
        var location = defaultLocation
        val out = mutableListOf<ParsedEntry>()
        for (rawLine in raw.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val h = header.matchEntire(line)
            if (h != null) {
                location = h.groupValues[1]
                continue
            }
            val body = line.replace(bullet, "")
            val m = trailingParen.matchEntire(body)
            val name = (m?.groupValues?.get(1) ?: body).trim()
            if (name.isEmpty()) continue
            val (kind, text) = guessKind(m?.groupValues?.get(2)?.trim().orEmpty())
            out += ParsedEntry(out.size, name, location, kind, text)
        }
        return out
    }

    fun guessKind(paren: String): Pair<EntryKind, String> {
        if (paren.isBlank()) return EntryKind.Cuisine to ""
        dishVerb.matchEntire(paren)?.let { return EntryKind.Dish to it.groupValues[2] }
        if (noteStart.containsMatchIn(paren)) return EntryKind.Note to paren
        return EntryKind.Cuisine to paren
    }

    fun splitDishes(text: String): List<String> = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    /**
     * A saved place an entry probably already is, as a heads-up before importing (the exact check on commit
     * uses Google's place id). Needs the name to match (equal, or one contains the other once 6+ characters)
     * AND the location to be the same area, so same-named places in different cities are not flagged.
     * An entry with no location can't be told apart by area, so only its name is compared.
     */
    fun possibleDuplicate(name: String, location: String, places: List<SavedPlace>): SavedPlace? {
        val n = normalize(name)
        if (n.isEmpty()) return null
        return places.firstOrNull { p ->
            val sameName = listOfNotNull(p.name, p.googleName).map(::normalize).any { other ->
                other == n || (minOf(other.length, n.length) >= 6 && (other.contains(n) || n.contains(other)))
            }
            sameName && sameArea(location, p)
        }
    }

    /** True when [location] (like "Columbia, MD") is where [place] is, going by its saved city and Google address. */
    private fun sameArea(location: String, place: SavedPlace): Boolean {
        val loc = normalize(location)
        if (loc.isEmpty()) return true
        val city = normalize(place.city)
        val address = normalize(place.address.orEmpty())
        if (city.isEmpty() && address.isEmpty()) return true // nothing to compare with
        return (city.isNotEmpty() && (city.contains(loc) || loc.contains(city))) || address.contains(loc)
    }
}
