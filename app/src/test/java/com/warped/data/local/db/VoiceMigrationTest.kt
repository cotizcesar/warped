package com.warped.data.local.db

import androidx.room.Database
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.warped.data.local.db.entity.MessageEntity
import com.warped.data.local.db.entity.toDomain
import com.warped.domain.model.Role
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Phase 68 (VMSG-06): JVM static gate for the v17 to v18 migration.
 *
 * Mirrors the `Migration16To17StaticTest` precedent: asserts the FULL delta
 * (audio_path + audio_duration_ms + transcript columns on messages) by
 * reading the exported Room schemas (17.json frozen baseline, 18.json
 * export), capturing the exact execSQL statements out of [MIGRATION_17_18],
 * and cross-checking migration SQL against the exported schema. Zero Android
 * framework, zero Robolectric.
 *
 * Scope-reduction prohibition: this gate asserts the whole delta, not a subset.
 */
class VoiceMigrationTest {

    private fun readRepoFile(vararg candidates: String): String {
        val fromClasspath = candidates.firstNotNullOfOrNull { name ->
            javaClass.classLoader?.getResource(name)?.readText()
        }
        if (fromClasspath != null) return fromClasspath
        for (name in candidates) {
            val file = File(name)
            if (file.exists()) return file.readText()
        }
        throw AssertionError(
            "None of the candidate repo files exist: ${candidates.toList()} " +
                "(cwd=${File(".").absolutePath})",
        )
    }

    private fun schemaJson(version: Int): String = readRepoFile(
        "schemas/com.warped.data.local.db.AppDatabase/$version.json",
        "app/schemas/com.warped.data.local.db.AppDatabase/$version.json",
        "com.warped.data.local.db.AppDatabase/$version.json",
    )

    private fun mainSource(relative: String): String = readRepoFile(
        "src/main/java/$relative",
        "app/src/main/java/$relative",
    )

    private fun entitiesOf(schemaText: String) =
        Json.parseToJsonElement(schemaText).jsonObject
            .getValue("database").jsonObject
            .getValue("entities").jsonArray
            .associate { it.jsonObject.getValue("tableName").jsonPrimitive.content to it.jsonObject }

    private fun schemaVersion(schemaText: String): Int =
        Json.parseToJsonElement(schemaText).jsonObject
            .getValue("database").jsonObject
            .getValue("version").jsonPrimitive.int

    private fun columnNames(entity: kotlinx.serialization.json.JsonObject): Set<String> =
        entity.getValue("fields").jsonArray
            .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
            .toSet()

    private fun captureMigrationSql(): List<String> {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()
        every { db.execSQL(capture(statements)) } answers { nothing }
        MIGRATION_17_18.migrate(db)
        return statements
    }

    @Test
    fun `schema versions - 17 json is v17 and 18 json is v18`() {
        assertThat(schemaVersion(schemaJson(17))).isEqualTo(17)
        assertThat(schemaVersion(schemaJson(18))).isEqualTo(18)
    }

    @Test
    fun `v17 frozen baseline - no voice columns on messages`() {
        val v17 = schemaJson(17)
        assertWithMessage("17.json is the frozen baseline: audio_path must never appear")
            .that(v17.contains("audio_path")).isFalse()
        assertWithMessage("17.json is the frozen baseline: audio_duration_ms must never appear")
            .that(v17.contains("audio_duration_ms")).isFalse()
        // NOTE: transcript is a common English word — scope the baseline
        // check to the messages entity instead of the whole file.
        val messagesFields = entitiesOf(v17).getValue("messages")
            .getValue("fields").jsonArray
            .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
        assertWithMessage("17.json messages must not carry transcript")
            .that(messagesFields).doesNotContain("transcript")
    }

    @Test
    fun `exact v18 delta - messages gains exactly the three voice columns`() {
        val v17 = entitiesOf(schemaJson(17))
        val v18 = entitiesOf(schemaJson(18))

        // No entity added, none dropped — the delta is columns-only.
        assertThat(v18.keys - v17.keys).isEmpty()
        assertThat(v17.keys - v18.keys).isEmpty()

        // messages gains exactly {audio_path, audio_duration_ms,
        // transcript}; every other entity keeps byte-identical columns.
        for (table in v17.keys) {
            val before = columnNames(v17.getValue(table))
            val after = columnNames(v18.getValue(table))
            if (table == "messages") {
                assertWithMessage("messages column delta must be exactly the 3 voice columns")
                    .that(after - before)
                    .containsExactly("audio_path", "audio_duration_ms", "transcript")
            } else {
                assertWithMessage("entity %s must keep identical columns between v17 and v18", table)
                    .that(after).containsExactlyElementsIn(before)
            }
        }

        val fields = v18.getValue("messages")
            .getValue("fields").jsonArray
            .map { it.jsonObject }
        // audio_path: TEXT affinity, nullable, no default — NULL means
        // "non-voice message" (a non-null default would conflate absent/empty).
        val pathField = fields.single {
            it.getValue("columnName").jsonPrimitive.content == "audio_path"
        }
        assertThat(pathField.getValue("affinity").jsonPrimitive.content).isEqualTo("TEXT")
        assertWithMessage("audio_path must stay nullable (no notNull:true)")
            .that(pathField["notNull"]?.jsonPrimitive?.content).isNotEqualTo("true")
        assertWithMessage("audio_path must have no defaultValue (NULL default)")
            .that(pathField.containsKey("defaultValue")).isFalse()
        // audio_duration_ms: INTEGER NOT NULL — legacy rows read 0 =
        // unknown duration without a nullable Long in the entity. NOTE: the
        // exported schema carries no defaultValue (Room only records
        // @ColumnInfo defaults, not Kotlin constructor defaults) — the
        // DEFAULT 0 lives in the MIGRATION_17_18 DDL, where SQLite requires
        // it for ADD COLUMN NOT NULL on non-empty tables. Both read 0.
        val durationField = fields.single {
            it.getValue("columnName").jsonPrimitive.content == "audio_duration_ms"
        }
        assertThat(durationField.getValue("affinity").jsonPrimitive.content).isEqualTo("INTEGER")
        assertWithMessage("audio_duration_ms must be NOT NULL")
            .that(durationField["notNull"]?.jsonPrimitive?.content).isEqualTo("true")
        assertWithMessage("exported schema tracks no Kotlin default (DDL owns DEFAULT 0)")
            .that(durationField.containsKey("defaultValue")).isFalse()
        // transcript: TEXT affinity, nullable, no default — NULL means "no
        // transcript yet" (Phase 69 owns the column; Phase 68 never writes it).
        val transcriptField = fields.single {
            it.getValue("columnName").jsonPrimitive.content == "transcript"
        }
        assertThat(transcriptField.getValue("affinity").jsonPrimitive.content).isEqualTo("TEXT")
        assertWithMessage("transcript must stay nullable (no notNull:true)")
            .that(transcriptField["notNull"]?.jsonPrimitive?.content).isNotEqualTo("true")
        assertWithMessage("transcript must have no defaultValue (NULL default)")
            .that(transcriptField.containsKey("defaultValue")).isFalse()
    }

    @Test
    fun `migration SQL exactness - 3 ALTER statements agreeing with exported schema`() {
        assertThat(MIGRATION_17_18.startVersion).isEqualTo(17)
        assertThat(MIGRATION_17_18.endVersion).isEqualTo(18)

        val statements = captureMigrationSql()
        assertWithMessage("MIGRATION_17_18 must execute exactly 3 statements, got: %s", statements)
            .that(statements).hasSize(3)

        val expectedColumns = listOf("audio_path", "audio_duration_ms", "transcript")
        statements.zip(expectedColumns).forEach { (sql, column) ->
            assertWithMessage("statement must ALTER messages: %s", sql)
                .that(
                    sql.contains("ALTER TABLE", ignoreCase = true) &&
                        sql.contains("messages") &&
                        sql.contains("ADD COLUMN", ignoreCase = true) &&
                        sql.contains(column),
                ).isTrue()
        }
        // Only the duration column pins NOT NULL DEFAULT 0.
        val durationSql = statements.single { it.contains("audio_duration_ms") }.uppercase()
        assertWithMessage("duration statement must pin NOT NULL: %s", durationSql)
            .that(durationSql.contains("NOT NULL")).isTrue()
        assertWithMessage("duration statement must pin DEFAULT 0: %s", durationSql)
            .that(durationSql.contains("DEFAULT 0")).isTrue()
        statements.filterNot { it.contains("audio_duration_ms") }.forEach { sql ->
            val upper = sql.uppercase()
            assertWithMessage("statement must not pin NOT NULL: %s", sql)
                .that(upper.contains("NOT NULL")).isFalse()
            assertWithMessage("statement must not pin a DEFAULT: %s", sql)
                .that(upper.contains("DEFAULT")).isFalse()
        }

        // Migration SQL and exported schema agree: every migrated column
        // appears in the 18.json messages createSql.
        val createSql = entitiesOf(schemaJson(18))
            .getValue("messages")
            .getValue("createSql").jsonPrimitive.content
        for (column in expectedColumns) {
            assertWithMessage("18.json createSql must carry %s", column)
                .that(createSql.contains(column)).isTrue()
        }
    }

    @Test
    fun `registration - version 18 annotation, single 17-18 migration, wired into database builder`() {
        AppDatabase::class.java.getAnnotation(Database::class.java)?.let { annotation ->
            assertWithMessage("AppDatabase version must never move below 18")
                .that(annotation.version).isAtLeast(18)
        }
        val appDatabaseSource =
            mainSource("com/warped/data/local/db/AppDatabase.kt")
        val declaredVersion = "version = (\\d+)".toRegex()
            .find(appDatabaseSource)?.groupValues?.getOrNull(1)?.toIntOrNull()
        assertWithMessage("AppDatabase @Database annotation must declare a version")
            .that(declaredVersion).isNotNull()
        assertWithMessage("AppDatabase version must never move below 18, found %s", declaredVersion)
            .that(declaredVersion!!).isAtLeast(18)

        val migrationsSource =
            mainSource("com/warped/data/local/db/Migrations.kt")
        val occurrences = "Migration(17, 18)".toRegex(RegexOption.LITERAL)
            .findAll(migrationsSource).count()
        assertWithMessage("Migrations.kt must declare exactly one Migration(17, 18), found %s", occurrences)
            .that(occurrences).isEqualTo(1)

        val moduleSource =
            mainSource("com/warped/di/DatabaseModule.kt")
        assertWithMessage("DatabaseModule.kt must call addMigrations")
            .that(moduleSource.contains("addMigrations")).isTrue()
        val addMigrationsTail = moduleSource.substringAfter("addMigrations")
        assertWithMessage("MIGRATION_17_18 must be wired inside the addMigrations call")
            .that(addMigrationsTail.contains("MIGRATION_17_18")).isTrue()
    }

    @Test
    fun `entity round-trip preserves voice columns`() {
        val entity = MessageEntity(
            id = 3L,
            conversationId = 42L,
            role = "USER",
            content = "voice note",
            createdAt = 1_700_000_000_000L,
            audioPath = "/voice/vm-1.m4a",
            audioDurationMs = 12_000L,
        )

        val roundTripped = entity.toDomain()

        assertThat(roundTripped.audioPath).isEqualTo("/voice/vm-1.m4a")
        assertThat(roundTripped.audioDurationMs).isEqualTo(12_000L)
        assertThat(roundTripped.transcript).isNull()
    }

    @Test
    fun `entity round-trip keeps null voice for legacy rows`() {
        val entity = MessageEntity(
            id = 4L,
            conversationId = 42L,
            role = "USER",
            content = "plain text",
            createdAt = 1_700_000_000_001L,
        )

        val roundTripped = entity.toDomain()

        assertThat(roundTripped.audioPath).isNull()
        assertThat(roundTripped.audioDurationMs).isEqualTo(0L)
        assertThat(roundTripped.transcript).isNull()
    }

    @Test
    fun `blank stored path degrades to no-player`() {
        val entity = MessageEntity(
            id = 5L,
            conversationId = 42L,
            role = "USER",
            content = "hand-edited row",
            createdAt = 1_700_000_000_002L,
            audioPath = "   ",
            audioDurationMs = 9_999L,
        )

        val roundTripped = entity.toDomain()

        assertThat(roundTripped.audioPath).isNull()
        assertThat(roundTripped.role).isEqualTo(Role.USER)
    }
}
