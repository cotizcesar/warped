package com.warped.data.repository

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.db.entity.GroundedSourceEntity
import com.warped.data.local.db.entity.toDomain
import com.warped.data.local.db.entity.toEntity
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import org.junit.jupiter.api.Test

/**
 * Phase 53 (SRC-02): hydration mapper contract for the `grounded_sources`
 * table. Entity rows (ok with text, omitida with null text, unknown status
 * strings) map to domain details preserving `source_index` order, with unknown
 * stored statuses defaulting to omitida (drop-unknown, T-53-01).
 *
 * Pure mapper coverage — no Room database needed, JVM-runnable.
 */
class GroundedSourceHydrationTest {

    @Test
    fun `rows map to domain preserving source_index order`() {
        val rows = listOf(
            GroundedSourceEntity(
                messageId = 7L,
                sourceIndex = 0,
                resolvedUrl = "https://a.example/uno",
                extractedText = "Texto uno.",
                status = "ok",
            ),
            GroundedSourceEntity(
                messageId = 7L,
                sourceIndex = 1,
                resolvedUrl = "https://dead.example/x",
                extractedText = null,
                status = "omitida",
            ),
            GroundedSourceEntity(
                messageId = 7L,
                sourceIndex = 2,
                resolvedUrl = "https://c.example/tres",
                extractedText = "Texto tres.",
                status = "ok",
            ),
        )

        val details = rows.map { it.toDomain() }

        assertThat(details.map { it.url }).containsExactly(
            "https://a.example/uno",
            "https://dead.example/x",
            "https://c.example/tres",
        ).inOrder()
        assertThat(details[0].extractedText).isEqualTo("Texto uno.")
        assertThat(details[0].status).isEqualTo(GroundedSourceStatus.OK)
        assertThat(details[1].extractedText).isNull()
        assertThat(details[1].status).isEqualTo(GroundedSourceStatus.OMITIDA)
    }

    @Test
    fun `unknown stored status defaults to omitida`() {
        val row = GroundedSourceEntity(
            messageId = 7L,
            sourceIndex = 0,
            resolvedUrl = "https://weird.example/x",
            extractedText = "Texto.",
            status = "partial",
        )

        assertThat(row.toDomain().status).isEqualTo(GroundedSourceStatus.OMITIDA)
    }

    @Test
    fun `domain toEntity carries row id, index, and storage status`() {
        val ok = GroundedSource(
            url = "https://a.example/uno",
            extractedText = "Texto uno.",
            status = GroundedSourceStatus.OK,
        )
        val skipped = GroundedSource(
            url = "https://dead.example/x",
            extractedText = null,
            status = GroundedSourceStatus.OMITIDA,
        )

        val okEntity = ok.toEntity(messageId = 42L, sourceIndex = 0)
        val skippedEntity = skipped.toEntity(messageId = 42L, sourceIndex = 1)

        assertThat(okEntity.messageId).isEqualTo(42L)
        assertThat(okEntity.sourceIndex).isEqualTo(0)
        assertThat(okEntity.status).isEqualTo("ok")
        assertThat(okEntity.extractedText).isEqualTo("Texto uno.")
        assertThat(skippedEntity.sourceIndex).isEqualTo(1)
        assertThat(skippedEntity.status).isEqualTo("omitida")
        assertThat(skippedEntity.extractedText).isNull()
    }
}
