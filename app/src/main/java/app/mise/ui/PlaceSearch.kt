package app.mise.ui

import app.mise.data.SavedPlace

/**
 * Searches everything the user has entered about a place: name, cuisine, dishes, notes, and location.
 * Every word in [query] has to match somewhere ("thai columbia" finds Thai places in Columbia).
 *
 * Returns null when the place doesn't match. Otherwise a short reason such as "Dish: Crab fritters"
 * for the first match outside the name, or "" when only the name matched.
 */
fun searchReason(place: SavedPlace, dishes: List<String>, query: String): String? {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    var reason: String? = null
    for (word in words) {
        fun has(text: String?) = text?.contains(word, ignoreCase = true) == true
        val found: String? = when {
            has(place.name) -> ""
            has(place.cuisine) -> "Cuisine: ${place.cuisine}"
            dishes.any { has(it) } -> "Dish: ${dishes.first { has(it) }}"
            has(place.notes) -> "Note: ${place.notes!!.lineSequence().firstOrNull { has(it) } ?: place.notes}"
            has(place.city) || has(place.address) -> "Location: ${place.city.ifBlank { place.address.orEmpty() }}"
            else -> return null
        }
        if (reason.isNullOrEmpty()) reason = found
    }
    return reason ?: ""
}
