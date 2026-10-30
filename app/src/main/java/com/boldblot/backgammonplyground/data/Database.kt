package com.boldblot.backgammonplyground.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert

object MatchStatus {
    const val IN_PROGRESS = 0
    const val FINISHED = 1
    const val ABANDONED = 2
}

/** One match, or one money session (length 0). */
@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val length: Int,
    val cpuLevel: String,
    val useCube: Boolean,
    val jacoby: Boolean,
    val crawford: Boolean,
    val humanScore: Int = 0,
    val cpuScore: Int = 0,
    /** 0 = human, 1 = computer, null while unfinished (or a money session). */
    val winner: Int? = null,
    val status: Int = MatchStatus.IN_PROGRESS,
    val games: Int = 0,
    /** Rating before/after the match (matches only). */
    val eloBefore: Double? = null,
    val eloAfter: Double? = null,
    /** JSON of PerformanceTotals for the human and the computer. */
    val humanPerformance: String? = null,
    val cpuPerformance: String? = null,
    /** False if the player excluded this match from statistics (and the rating). Schema 3. */
    @ColumnInfo(defaultValue = "1") val inStats: Boolean = true,
    /** True once the move-by-move records of its games were deleted (results kept). Schema 3. */
    @ColumnInfo(defaultValue = "0") val recordsCleared: Boolean = false,
    /** Imported from a .mat file (schema 3): the opponent is a name ([Opponent.imported]), not a computer level. */
    @ColumnInfo(defaultValue = "0") val imported: Boolean = false,
    /** Imported: the opponent's mistakes go on the blunder list too (schema 3). */
    @ColumnInfo(defaultValue = "0") val blundersBoth: Boolean = false,
)

/** A finished game, with its full record for review and its summary for statistics. */
@Entity(
    tableName = "games",
    indices = [Index("matchId")],
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val number: Int,
    val finishedAt: Long,
    val winner: Int,
    val winType: String,
    val points: Int,
    /** JSON of GameSummary. */
    val summary: String,
    /** JSON of the final GameState (decisions with analysis). */
    val record: String,
    /** Score before this game and match length, for display. */
    val scoreBeforeHuman: Int,
    val scoreBeforeCpu: Int,
)

/**
 * A position in My positions (schema 2): an XGID with the player who decides at the bottom
 * (see core/SavedPositions.kt), and with [doubled] their opponent has doubled.
 */
@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val xgid: String,
    val doubled: Boolean = false,
    /** Money game with beavers allowed. */
    val beavers: Boolean = false,
    val note: String = "",
    /** Where it came from: "Blunder list", "Game review", "Analyze Position", "Imported". */
    val source: String = "",
    val savedAt: Long,
    /** JSON of PositionAnalysis; null until analyzed. */
    val analysis: String? = null,
    /** JSON of MyAnswer; null = the engine's best is the right answer. */
    val myAnswer: String? = null,
    /** The last quiz answer: null = not tried yet. */
    val lastRight: Boolean? = null,
    /** Its labels' keys, one per line ([com.boldblot.backgammonplyground.core.Labels]; schema 3). */
    @ColumnInfo(defaultValue = "") val labels: String = "",
)

/** A category of My positions, in the player's order ([sort]). */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sort: Int = 0,
    /** A folder linked to a blunder list filter ([Labels.encode]; "" = no filter); null = an ordinary folder (schema 3). */
    val filter: String? = null,
    /** Linked folders: new blunders that pass the filter are added at once (schema 3). */
    @ColumnInfo(defaultValue = "0") val autoAdd: Boolean = false,
)

/** A position's place in a category (a position can be in several). */
@Entity(tableName = "position_categories", primaryKeys = ["positionId", "categoryId"], indices = [Index("categoryId")])
data class PositionCategoryEntity(val positionId: Long, val categoryId: Long)

/** A category with its number of positions. */
data class CategoryCount(val id: Long, val name: String, val sort: Int, val count: Int, val filter: String? = null, val autoAdd: Boolean = false)

/** A position's labels ([PositionEntity.labels]). */
data class PositionLabels(val id: Long, val labels: String)

/** A game by its match and number. */
data class GameKey(val matchId: Long, val number: Int)

/** The match in progress, so it can be resumed after the app is closed. */
@Entity(tableName = "current_match")
data class CurrentMatchEntity(
    @PrimaryKey val slot: Int = 0,
    val json: String,
)

/**
 * One of the human's blunders, saved for study until the player removes it. Kept when its
 * match is deleted; one row per decision (matchId, gameNumber, decisionIndex).
 */
@Entity(
    tableName = "blunders",
    indices = [Index(value = ["matchId", "gameNumber", "decisionIndex"], unique = true)],
)
data class BlunderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val gameNumber: Int,
    /** Index into the game's decisions. */
    val decisionIndex: Int,
    val playedAt: Long,
    val matchLength: Int,
    val cpuLevel: String,
    /** Error after the 1-point-match factor. */
    val error: Float,
    /** JSON of the DecisionRecord, turned so that its player is at the bottom. */
    val record: String,
    /** The opponent's mistake (imported matches; schema 3). */
    @ColumnInfo(defaultValue = "0") val opponent: Boolean = false,
)

@Dao
interface MatchDao {
    @Insert
    suspend fun insertMatch(m: MatchEntity): Long

    @Upsert
    suspend fun upsertMatch(m: MatchEntity)

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun match(id: Long): MatchEntity?

    @Query("SELECT * FROM matches WHERE (status != ${MatchStatus.IN_PROGRESS} OR games > 0) AND recordsCleared = 0 ORDER BY startedAt DESC")
    suspend fun history(): List<MatchEntity>

    /** Finished matches (not money sessions) that count for the rating, oldest first. */
    @Query("SELECT * FROM matches WHERE status = ${MatchStatus.FINISHED} AND length > 0 AND inStats = 1 ORDER BY finishedAt ASC")
    suspend fun ratedMatchesChronological(): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE status = ${MatchStatus.FINISHED} AND length > 0 ORDER BY finishedAt ASC")
    suspend fun finishedMatchesChronological(): List<MatchEntity>

    @Query("UPDATE matches SET inStats = :inStats WHERE id = :id")
    suspend fun setInStats(id: Long, inStats: Boolean)

    @Query("UPDATE matches SET inStats = 0 WHERE status != ${MatchStatus.IN_PROGRESS}")
    suspend fun excludeFinishedFromStats()

    @Query("UPDATE matches SET eloBefore = :before, eloAfter = :after WHERE id = :id")
    suspend fun setRating(id: Long, before: Double?, after: Double?)

    /** Deletes the game records (not the summaries) of finished matches started before [before]. */
    @Query("UPDATE games SET record = '' WHERE matchId IN (SELECT id FROM matches WHERE status != ${MatchStatus.IN_PROGRESS} AND startedAt < :before)")
    suspend fun clearRecordsBefore(before: Long)

    @Query("UPDATE matches SET recordsCleared = 1 WHERE status != ${MatchStatus.IN_PROGRESS} AND startedAt < :before")
    suspend fun markRecordsClearedBefore(before: Long)

    @Transaction
    suspend fun clearRecords(before: Long) {
        clearRecordsBefore(before)
        markRecordsClearedBefore(before)
    }

    @Query("SELECT * FROM matches")
    suspend fun allMatches(): List<MatchEntity>

    @Insert
    suspend fun insertGame(g: GameEntity): Long

    @Update
    suspend fun updateGame(g: GameEntity)

    @Update
    suspend fun updateBlunder(b: BlunderEntity)

    @Query("SELECT * FROM games WHERE matchId = :matchId ORDER BY number")
    suspend fun games(matchId: Long): List<GameEntity>

    @Query("SELECT * FROM games WHERE matchId = :matchId AND number = :number")
    suspend fun game(matchId: Long, number: Int): GameEntity?

    /** Games with decisions waiting for the background analysis ("pending":true in the record). */
    @Query("SELECT matchId, number FROM games WHERE record LIKE '%\"pending\":true%' ORDER BY finishedAt")
    suspend fun gamesWithPending(): List<GameKey>

    @Query("SELECT id, matchId, number, finishedAt, winner, winType, points, summary, '' AS record, scoreBeforeHuman, scoreBeforeCpu FROM games")
    suspend fun allGameSummaries(): List<GameEntity>

    @Query("SELECT * FROM current_match WHERE slot = 0")
    suspend fun current(): CurrentMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCurrent(c: CurrentMatchEntity)

    @Query("DELETE FROM current_match")
    suspend fun clearCurrent()

    @Query("DELETE FROM games")
    suspend fun deleteAllGames()

    @Query("DELETE FROM matches WHERE status != ${MatchStatus.IN_PROGRESS}")
    suspend fun deleteFinishedMatches()

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun deleteMatch(id: Long)

    @Query("DELETE FROM games WHERE matchId = :id")
    suspend fun deleteGamesOf(id: Long)

    @Transaction
    suspend fun deleteMatchWithGames(id: Long) {
        deleteGamesOf(id)
        deleteMatch(id)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlunder(b: BlunderEntity): Long

    @Query("SELECT * FROM blunders ORDER BY playedAt DESC, matchId DESC, gameNumber DESC, decisionIndex DESC")
    suspend fun blunders(): List<BlunderEntity>

    @Query("DELETE FROM blunders WHERE id = :id")
    suspend fun deleteBlunder(id: Long)

    @Query("DELETE FROM blunders")
    suspend fun deleteAllBlunders()

    @Query("DELETE FROM blunders WHERE id IN (:ids)")
    suspend fun deleteBlunders(ids: List<Long>)

    @Query("SELECT * FROM blunders WHERE id = :id")
    suspend fun blunder(id: Long): BlunderEntity?
}

@Dao
interface PositionDao {
    @Insert
    suspend fun insert(p: PositionEntity): Long

    @Update
    suspend fun update(p: PositionEntity)

    @Query("SELECT * FROM positions WHERE id = :id")
    suspend fun position(id: Long): PositionEntity?

    @Query("SELECT * FROM positions ORDER BY savedAt DESC")
    suspend fun all(): List<PositionEntity>

    @Query("SELECT p.* FROM positions p JOIN position_categories c ON c.positionId = p.id WHERE c.categoryId = :categoryId ORDER BY p.savedAt DESC")
    suspend fun inCategory(categoryId: Long): List<PositionEntity>

    @Query("SELECT DISTINCT p.* FROM positions p JOIN position_categories c ON c.positionId = p.id WHERE c.categoryId IN (:ids)")
    suspend fun inCategories(ids: List<Long>): List<PositionEntity>

    @Query("SELECT * FROM positions WHERE xgid = :xgid AND doubled = :doubled LIMIT 1")
    suspend fun find(xgid: String, doubled: Boolean): PositionEntity?

    @Query("DELETE FROM positions WHERE id = :id")
    suspend fun deletePosition(id: Long)

    @Query("SELECT c.id, c.name, c.sort, (SELECT COUNT(*) FROM position_categories pc WHERE pc.categoryId = c.id) AS count, c.filter, c.autoAdd FROM categories c ORDER BY c.sort, c.id")
    suspend fun categories(): List<CategoryCount>

    @Insert
    suspend fun insertCategory(c: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    @Query("UPDATE categories SET autoAdd = :on WHERE id = :id")
    suspend fun setAutoAdd(id: Long, on: Boolean)

    @Query("UPDATE positions SET labels = :labels WHERE id = :id")
    suspend fun setLabels(id: Long, labels: String)

    @Query("SELECT id, labels FROM positions WHERE labels != ''")
    suspend fun allLabels(): List<PositionLabels>

    @Query("UPDATE categories SET sort = :sort WHERE id = :id")
    suspend fun setSort(id: Long, sort: Int)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("SELECT categoryId FROM position_categories WHERE positionId = :positionId")
    suspend fun categoriesOf(positionId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(l: PositionCategoryEntity)

    @Query("DELETE FROM position_categories WHERE positionId = :positionId AND categoryId = :categoryId")
    suspend fun unlink(positionId: Long, categoryId: Long)

    @Query("DELETE FROM position_categories WHERE positionId = :positionId")
    suspend fun unlinkPosition(positionId: Long)

    @Query("DELETE FROM position_categories WHERE categoryId = :categoryId")
    suspend fun unlinkCategory(categoryId: Long)

    @Query("SELECT COUNT(*) FROM positions p WHERE NOT EXISTS (SELECT 1 FROM position_categories c WHERE c.positionId = p.id)")
    suspend fun uncategorized(): Int

    @Query("SELECT * FROM positions p WHERE NOT EXISTS (SELECT 1 FROM position_categories c WHERE c.positionId = p.id) ORDER BY savedAt DESC")
    suspend fun uncategorizedPositions(): List<PositionEntity>
}

@Database(
    entities = [
        MatchEntity::class, GameEntity::class, CurrentMatchEntity::class, BlunderEntity::class,
        PositionEntity::class, CategoryEntity::class, PositionCategoryEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun matches(): MatchDao
    abstract fun positions(): PositionDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "plyground.db").build()
    }
}
