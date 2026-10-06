package app.mise.data

import android.content.Context
import app.mise.BuildConfig
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.SearchByTextRequest
import kotlinx.coroutines.tasks.await

/** Matched location for a list entry. */
data class PlaceMatch(val placeId: String, val latLng: LatLng, val address: String?)

/** What the bottom sheet shows from Google Maps data. */
data class PlaceDetails(
    val rating: Double?,
    val ratingCount: Int?,
    val summary: String?,
    val address: String?,
    val reviews: List<ReviewSnippet>,
)

data class ReviewSnippet(val author: String, val rating: Double, val text: String)

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
            .builder("$name $city", listOf(Place.Field.ID, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS))
            .setMaxResultCount(1)
            .build()
        val hit = client.searchByText(request).await().places.firstOrNull() ?: return null
        val loc = hit.location ?: return null
        return PlaceMatch(hit.id ?: return null, loc, hit.formattedAddress)
    }

    suspend fun details(googlePlaceId: String): PlaceDetails {
        val fields = listOf(
            Place.Field.RATING, Place.Field.USER_RATING_COUNT, Place.Field.EDITORIAL_SUMMARY,
            Place.Field.FORMATTED_ADDRESS, Place.Field.REVIEWS,
        )
        val p = client.fetchPlace(FetchPlaceRequest.newInstance(googlePlaceId, fields)).await().place
        return PlaceDetails(
            rating = p.rating,
            ratingCount = p.userRatingCount,
            summary = p.editorialSummary,
            address = p.formattedAddress,
            reviews = p.reviews.orEmpty().mapNotNull { r ->
                val text = r.text?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ReviewSnippet(r.authorAttribution.name, r.rating, text)
            },
        )
    }
}
