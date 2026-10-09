package app.mise.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** A restaurant/food spot from the user's list. lat/lng/googlePlaceId are null until matched via Places. */
@Entity
data class SavedPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val city: String,
    val googlePlaceId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String? = null,
    val cuisine: String? = null,
    val notes: String? = null,
    /** True once we've asked Google for this place's type, so auto-filled cuisine is only tried once. */
    val typeChecked: Boolean = false,
    /** The business's real name from Google Maps; shown instead of whatever the user typed. */
    val googleName: String? = null,
)

/** Name to show for a place: Google's real name when known, otherwise what the user entered. */
val SavedPlace.displayName: String get() = googleName?.takeIf { it.isNotBlank() } ?: name

/** One bullet in a place's list: a dish tried (tried = true) or wanted (tried = false). */
@Entity(
    foreignKeys = [ForeignKey(SavedPlace::class, ["id"], ["placeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("placeId")],
)
data class DishNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val text: String,
    val tried: Boolean = false,
)

@Dao
interface PlaceDao {
    @Query("SELECT * FROM SavedPlace ORDER BY name")
    fun observePlaces(): Flow<List<SavedPlace>>

    @Query("SELECT * FROM SavedPlace WHERE lat IS NULL")
    suspend fun unmatched(): List<SavedPlace>

    @Query("SELECT * FROM SavedPlace WHERE googlePlaceId = :googlePlaceId LIMIT 1")
    suspend fun findByGoogleId(googlePlaceId: String): SavedPlace?

    @Query(
        "SELECT * FROM SavedPlace WHERE (LOWER(name) = LOWER(:name) OR LOWER(googleName) = LOWER(:name)) " +
            "AND LOWER(city) = LOWER(:city) LIMIT 1",
    )
    suspend fun findByNameAndCity(name: String, city: String): SavedPlace?

    @Query("SELECT * FROM SavedPlace WHERE googlePlaceId IS NOT NULL AND (googleName IS NULL OR typeChecked = 0)")
    suspend fun needsGoogleData(): List<SavedPlace>

    @Query("SELECT * FROM DishNote")
    fun observeAllDishes(): Flow<List<DishNote>>

    @Insert suspend fun insert(place: SavedPlace): Long
    @Update suspend fun update(place: SavedPlace)
    @Delete suspend fun delete(place: SavedPlace)

    @Query("SELECT * FROM DishNote WHERE placeId = :placeId ORDER BY tried, id")
    fun observeNotes(placeId: Long): Flow<List<DishNote>>

    @Insert suspend fun insertNote(note: DishNote)
    @Update suspend fun updateNote(note: DishNote)
    @Delete suspend fun deleteNote(note: DishNote)
}

@Database(entities = [SavedPlace::class, DishNote::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): PlaceDao

    companion object {
        @Volatile private var instance: AppDb? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE SavedPlace ADD COLUMN cuisine TEXT")
                db.execSQL("ALTER TABLE SavedPlace ADD COLUMN notes TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE SavedPlace ADD COLUMN typeChecked INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE SavedPlace ADD COLUMN googleName TEXT")
            }
        }

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "mise.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
