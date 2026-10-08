package app.mise.data

import android.content.Context
import app.mise.BuildConfig
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.SearchByTextRequest
import kotlinx.coroutines.tasks.await

/** Matched location for a list entry. */
data class PlaceMatch(val placeId: String, val latLng: LatLng, val address: String?, val cuisine: String?)

/** What the sheet shows from Google Maps data: just the star rating. */
data class PlaceDetails(val rating: Double?, val ratingCount: Int?)

/** One autocomplete row for the location search. */
data class Suggestion(val placeId: String, val primary: String, val secondary: String?)

/** Thin wrapper over Places SDK (New). All Google-specific code lives here so it's easy to swap. */
class PlacesRepository(context: Context) {
    private val client by lazy {
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context.applicationContext, BuildConfig.MAPS_API_KEY)
        }
        Places.createClient(context.applicationContext)
    }

    /** Resolve "Name" + vague "City" into one precise place. Returns null if nothing found. */
    suspend fun match(name: String, city: String): PlaceMatch? {
        val request = SearchByTextRequest
            .builder("$name $city", listOf(Place.Field.ID, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS, Place.Field.PRIMARY_TYPE_DISPLAY_NAME, Place.Field.TYPES))
            .setMaxResultCount(1)
            .build()
        val hit = client.searchByText(request).await().places.firstOrNull() ?: return null
        val loc = hit.location ?: return null
        return PlaceMatch(hit.id ?: return null, loc, hit.formattedAddress, cuisineOf(hit))
    }

    /** Turns Google's place type ("Thai Restaurant", "ramen_restaurant", "Bakery") into a short cuisine label. */
    private fun cuisineOf(p: Place): String? {
        val primary = p.primaryTypeDisplayName?.replace(Regex("\\s*restaurant$", RegexOption.IGNORE_CASE), "")?.trim()
        if (!primary.isNullOrBlank()) return primary
        return p.placeTypes?.firstOrNull { it.endsWith("_restaurant") && it != "restaurant" }
            ?.removeSuffix("_restaurant")?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
    }

    /** Just the type fields, for filling in cuisine on places that don't have one. */
    suspend fun cuisine(googlePlaceId: String): String? {
        val fields = listOf(Place.Field.PRIMARY_TYPE_DISPLAY_NAME, Place.Field.TYPES)
        return cuisineOf(client.fetchPlace(FetchPlaceRequest.newInstance(googlePlaceId, fields)).await().place)
    }

    suspend fun details(googlePlaceId: String): PlaceDetails {
        val fields = listOf(Place.Field.RATING, Place.Field.USER_RATING_COUNT)
        val p = client.fetchPlace(FetchPlaceRequest.newInstance(googlePlaceId, fields)).await().place
        return PlaceDetails(rating = p.rating, ratingCount = p.userRatingCount)
    }

    // One session token per search: autocomplete keystrokes + the final place lookup are billed as one.
    private var token = AutocompleteSessionToken.newInstance()

    suspend fun suggest(query: String, near: LatLng?): List<Suggestion> {
        val b = FindAutocompletePredictionsRequest.builder().setQuery(query).setSessionToken(token)
        near?.let { b.setLocationBias(CircularBounds.newInstance(it, 50_000.0)) }
        return client.findAutocompletePredictions(b.build()).await().autocompletePredictions.map {
            Suggestion(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null)?.toString())
        }
    }

    /** Resolve a picked suggestion to a name and coordinates. */
    suspend fun locate(placeId: String): Pair<String, LatLng>? {
        val req = FetchPlaceRequest.builder(placeId, listOf(Place.Field.DISPLAY_NAME, Place.Field.LOCATION))
            .setSessionToken(token).build()
        val p = client.fetchPlace(req).await().place
        token = AutocompleteSessionToken.newInstance()
        return (p.displayName ?: "") to (p.location ?: return null)
    }
}
