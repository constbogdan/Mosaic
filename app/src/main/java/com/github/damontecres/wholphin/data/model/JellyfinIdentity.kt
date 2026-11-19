package com.github.damontecres.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

/** A user-visible Jellyfin observation, not a durable proof of present-day access. */
@Entity(
    tableName = "jellyfin_identity_items",
    primaryKeys = ["userRowId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = ["rowId"],
            childColumns = ["userRowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userRowId", "mediaType", "tmdbId")],
)
data class JellyfinIdentityItem(
    val userRowId: Int,
    val mediaType: String,
    val tmdbId: Int?,
    val itemId: UUID,
    val libraryId: UUID,
)

/** Presence of this row distinguishes an unscanned user from a completed API scan. */
@Entity(
    tableName = "jellyfin_identity_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = ["rowId"],
            childColumns = ["userRowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class JellyfinIdentitySnapshot(
    @androidx.room.PrimaryKey val userRowId: Int,
    val serverId: UUID,
    val userId: UUID,
    val observedAtEpochMillis: Long,
    val returnedItemCount: Int,
    val missingTmdbCount: Int,
)
