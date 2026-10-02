package com.warped.data.local.db

import androidx.room.Database
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.warped.data.local.db.entity.toDomain
import com.warped.data.local.db.entity.toEntity
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
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
 * Quick-task (card-snippet): JVM static gate for the v16 to v17
 * migration. Mirrors the `Migration15To16StaticTest` precedent: asserts
 * the FULL delta (one nullable snippet column on grounded_sources) by
 * reading the exported Room schemas (16.json frozen baseline, 17.json
 * export), capturing the exact execSQL statements out of
 * [MIGRATION_16_17], and cross-checking migration SQL against the
 * exported schema. Zero Android framework, zero Robolectric.
 */
class Migration16To17StaticTest {

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
        MIGRATION_16_17.migrate(db)
        return statements
    }

    @Test
    fun `schema versions - 16 json is v16 and 17 json is v17`() {
        assertThat(schemaVersion(schemaJson(16))).isEqualTo(16)
        assertThat(schemaVersion(schemaJson(17))).isEqualTo(17)
    }

    @Test
    fun `v16 frozen baseline - no snippet column`() {
        val v16 = schemaJson(16)
        assertWithMessage("16.json is the frozen baseline: snippet must never appear")
            .that(v16.contains("\"snippet\"")).isFalse()
    }

    @Test
    fun `exact v17 delta - grounded_sources gains exactly the nullable snippet column`() {
        val v16 = entitiesOf(schemaJson(16))
        val v17 = entitiesOf(schemaJson(17))

        assertThat(v17.keys - v16.keys).isEmpty()
        assertThat(v16.keys - v17.keys).isEmpty()

        for (table in v16.keys) {
            val before = columnNames(v16.getValue(table))
            val after = columnNames(v17.getValue(table))
            if (table == "grounded_sources") {
                assertWithMessage("grounded_sources column delta must be exactly the snippet column")
                    .that(after - before)
                    .containsExactly("snippet")
            } else {
                assertWithMessage("entity %s must keep identical columns between v16 and v17", table)
                    .that(after).containsExactlyElementsIn(before)
            }
        }

        val field = v17.getValue("grounded_sources")
            .getValue("fields").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("columnName").jsonPrimitive.content == "snippet" }
        assertThat(field.getValue("affinity").jsonPrimitive.content).isEqualTo("TEXT")
        assertWithMessage("snippet must stay nullable (no notNull:true)")
            .that(field["notNull"]?.jsonPrimitive?.content).isNotEqualTo("true")
        assertWithMessage("snippet must have no defaultValue (NULL default)")
            .that(field.containsKey("defaultValue")).isFalse()
    }

    @Test
    fun `migration SQL exactness - 1 ALTER statement agreeing with exported schema`() {
        assertThat(MIGRATION_16_17.startVersion).isEqualTo(16)
        assertThat(MIGRATION_16_17.endVersion).isEqualTo(17)

        val statements = captureMigrationSql()
        assertWithMessage("MIGRATION_16_17 must execute exactly 1 statement, got: %s", statements)
            .that(statements).hasSize(1)

        val sql = statements.single()
        assertWithMessage("statement must ALTER grounded_sources: %s", sql)
            .that(
                sql.contains("ALTER TABLE", ignoreCase = true) &&
                    sql.contains("grounded_sources") &&
                    sql.contains("ADD COLUMN", ignoreCase = true) &&
                    sql.contains("snippet"),
            ).isTrue()
        val upper = sql.uppercase()
        assertWithMessage("statement must not pin NOT NULL: %s", sql)
            .that(upper.contains("NOT NULL")).isFalse()
        assertWithMessage("statement must not pin a DEFAULT: %s", sql)
            .that(upper.contains("DEFAULT")).isFalse()

        val createSql = entitiesOf(schemaJson(17))
            .getValue("grounded_sources")
            .getValue("createSql").jsonPrimitive.content
        assertWithMessage("17.json createSql must carry snippet")
            .that(createSql.contains("snippet")).isTrue()
    }

    @Test
    fun `registration - version at-least 17 annotation, single 16-17 migration, wired into database builder`() {
        // Phase 68: the head version moved to 18 — this gate pins the
        // 16→17 link, not the current head (mirrors the 15→16 at-least
        // precedent in Migration15To16StaticTest).
        AppDatabase::class.java.getAnnotation(Database::class.java)?.let { annotation ->
            assertWithMessage("AppDatabase version must never move below 17")
                .that(annotation.version).isAtLeast(17)
        }
        val appDatabaseSource =
            mainSource("com/warped/data/local/db/AppDatabase.kt")
        val declaredVersion = "version = (\\d+)".toRegex()
            .find(appDatabaseSource)?.groupValues?.getOrNull(1)?.toIntOrNull()
        assertWithMessage("AppDatabase @Database annotation must declare a version")
            .that(declaredVersion).isNotNull()
        assertWithMessage("AppDatabase version must never move below 17, found %s", declaredVersion)
            .that(declaredVersion!!).isAtLeast(17)

        val migrationsSource =
            mainSource("com/warped/data/local/db/Migrations.kt")
        val occurrences = "Migration(16, 17)".toRegex(RegexOption.LITERAL)
            .findAll(migrationsSource).count()
        assertWithMessage("Migrations.kt must declare exactly one Migration(16, 17), found %s", occurrences)
            .that(occurrences).isEqualTo(1)

        val moduleSource =
            mainSource("com/warped/di/DatabaseModule.kt")
        assertWithMessage("DatabaseModule.kt must call addMigrations")
            .that(moduleSource.contains("addMigrations")).isTrue()
        val addMigrationsTail = moduleSource.substringAfter("addMigrations")
        assertWithMessage("MIGRATION_16_17 must be wired inside the addMigrations call")
            .that(addMigrationsTail.contains("MIGRATION_16_17")).isTrue()
    }

    @Test
    fun `entity round-trip preserves snippet`() {
        val source = GroundedSource(
            url = "https://example.com/page",
            extractedText = "text",
            status = GroundedSourceStatus.OK,
            ogTitle = "OG Title",
            snippet = "Search excerpt.",
        )

        val roundTripped = source.toEntity(messageId = 1L, sourceIndex = 0).toDomain()

        assertThat(roundTripped.snippet).isEqualTo("Search excerpt.")
        assertThat(roundTripped.ogTitle).isEqualTo("OG Title")
    }

    @Test
    fun `entity round-trip keeps null snippet for legacy rows`() {
        val source = GroundedSource(
            url = "https://example.com/page",
            extractedText = null,
            status = GroundedSourceStatus.OMITIDA,
        )

        val roundTripped = source.toEntity(messageId = 1L, sourceIndex = 1).toDomain()

        assertThat(roundTripped.snippet).isNull()
    }
}
