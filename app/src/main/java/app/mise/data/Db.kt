package app.mise.data

import android.content.Context
import androidx.room.*
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
)

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

    @Query("SELECT COUNT(*) FROM SavedPlace WHERE LOWER(name) = LOWER(:name) AND LOWER(city) = LOWER(:city)")
    suspend fun countDuplicates(name: String, city: String): Int

    @Insert suspend fun insert(place: SavedPlace): Long
    @Update suspend fun update(place: SavedPlace)
    @Delete suspend fun delete(place: SavedPlace)

    @Query("SELECT * FROM DishNote WHERE placeId = :placeId ORDER BY tried, id")
    fun observeNotes(placeId: Long): Flow<List<DishNote>>

    @Insert suspend fun insertNote(note: DishNote)
    @Update suspend fun updateNote(note: DishNote)
    @Delete suspend fun deleteNote(note: DishNote)
}

@Database(entities = [SavedPlace::class, DishNote::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): PlaceDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "mise.db")
                .build().also { instance = it }
        }
    }
}
