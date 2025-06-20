package fr.groggy.racecontrol.tv.db.season

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SeasonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(season: SeasonEntity)

    @Query("SELECT * FROM seasons WHERE year = :year")
    fun observeByYear(year: Int): Flow<SeasonEntity?>

}
