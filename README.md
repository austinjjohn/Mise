# Mise

Your food list, pinned. Android app (Kotlin · Jetpack Compose · Material 3 Expressive).

## Setup
1. Install Android Studio (current stable). Open this folder; let it sync (it will generate the Gradle wrapper jar; accept any suggested version bumps).
2. Google Cloud Console: create a project, enable **Maps SDK for Android** and **Places API (New)**, create an API key.
   Restrict it to package `app.mise` + your debug SHA-1 (`./gradlew signingReport`).
3. `cp local.properties.example local.properties` and paste the key.
4. Enable USB debugging on the Pixel 8 Pro and Run.

## Structure
- `data/Db.kt` – Room: `SavedPlace`, `DishNote`
- `data/PlacesRepository.kt` – all Google Places calls (match list entries → exact pin; fetch rating/summary/reviews)
- `ui/MainViewModel.kt` – state + import logic
- `ui/MapScreen.kt` – map, markers, my-location, bottom sheet host
- `ui/PlaceSheet.kt` – Google gist + dish bullet list
- `ui/ImportScreen.kt` – paste list → match
- `ui/theme/Theme.kt` – M3 Expressive theme (dynamic color)
