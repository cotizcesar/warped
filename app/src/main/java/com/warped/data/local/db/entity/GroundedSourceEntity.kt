package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 53 (SRC-01/TOGGLE-03): persisted grounded-source rows scoped to the
 * assistant message that used them. Conversation delete cascades messages into
 * sources transitively via the messages FK chain — no manual cleanup.
 *
 * Only fetcher-resolved post-redirect http/https URLs are stored in
 * [resolvedUrl]; raw user-pasted text never reaches this table (T-53-02).
 */
@Entity(
    tableName = "grounded_sources",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["message_id"])],
)
data class GroundedSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "source_index") val sourceIndex: Int,
    @ColumnInfo(name = "resolved_url") val resolvedUrl: String,
    @ColumnInfo(name = "extracted_text") val extractedText: String? = null,
    /** "ok" or "omitida"; unknown stored values map to omitida on read (T-53-01). */
    @ColumnInfo(name = "status") val status: String,
    /**
     * Phase 58 (OG-01): nullable OpenGraph columns, no backfill default.
     * NULL means no OG captured (pre-58 rows, plain/markdown sources, legacy keyed-provider rows).
     */
    @ColumnInfo(name = "og_title") val ogTitle: String? = null,
    @ColumnInfo(name = "og_description") val ogDescription: String? = null,
    @ColumnInfo(name = "og_image_url") val ogImageUrl: String? = null,
    /**
     * Quick-task (card-snippet): sanitized search excerpt for the card
     * description fallback. NULL for omitida/fetch rows and pre-snippet
     * history (same absent-semantics as the OG columns).
     */
    @ColumnInfo(name = "snippet") val snippet: String? = null,
)
