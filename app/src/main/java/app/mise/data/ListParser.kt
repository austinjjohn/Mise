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
     * A saved place an entry probably already is, judged by name only (the exact check on commit uses Google's
     * place id). Names must match, or one must contain the other once they're 6+ characters.
     */
    fun possibleDuplicate(name: String, places: List<SavedPlace>): SavedPlace? {
        val n = normalize(name)
        if (n.isEmpty()) return null
        return places.firstOrNull { p ->
            listOfNotNull(p.name, p.googleName).map(::normalize).any { other ->
                other == n || (minOf(other.length, n.length) >= 6 && (other.contains(n) || n.contains(other)))
            }
        }
    }
}
