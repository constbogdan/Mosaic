package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.damontecres.wholphin.data.model.JellyfinIdentityItem
import com.github.damontecres.wholphin.data.model.JellyfinIdentitySnapshot

data class JellyfinIdentityRead(
    val snapshot: JellyfinIdentitySnapshot,
    val items: List<JellyfinIdentityItem>,
)

@Dao
interface JellyfinIdentityDao {
    @Query("SELECT * FROM jellyfin_identity_snapshots WHERE userRowId = :userRowId")
    suspend fun snapshot(userRowId: Int): JellyfinIdentitySnapshot?

    @Query(
        "SELECT * FROM jellyfin_identity_items WHERE userRowId = :userRowId AND mediaType = :mediaType AND tmdbId = :tmdbId ORDER BY itemId",
    )
    suspend fun matches(
        userRowId: Int,
        mediaType: String,
        tmdbId: Int,
    ): List<JellyfinIdentityItem>

    @Query("SELECT * FROM jellyfin_identity_items WHERE userRowId = :userRowId ORDER BY itemId")
    suspend fun all(userRowId: Int): List<JellyfinIdentityItem>

    /** Keep the marker and candidate rows in the same snapshot generation. */
    @Transaction
    suspend fun find(
        userRowId: Int,
        mediaType: String,
        tmdbId: Int,
    ): JellyfinIdentityRead? {
        val marker = snapshot(userRowId) ?: return null
        return JellyfinIdentityRead(marker, matches(userRowId, mediaType, tmdbId))
    }

    @Query("DELETE FROM jellyfin_identity_items WHERE userRowId = :userRowId")
    suspend fun deleteItems(userRowId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<JellyfinIdentityItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: JellyfinIdentitySnapshot)

    /** One Room transaction: readers see the old generation or the full replacement. */
    @Transaction
    suspend fun replace(
        snapshot: JellyfinIdentitySnapshot,
        items: List<JellyfinIdentityItem>,
    ) {
        require(items.all { it.userRowId == snapshot.userRowId })
        require(items.map { it.itemId }.toSet().size == items.size)
        deleteItems(snapshot.userRowId)
        insertItems(items)
        upsertSnapshot(snapshot)
    }
}
