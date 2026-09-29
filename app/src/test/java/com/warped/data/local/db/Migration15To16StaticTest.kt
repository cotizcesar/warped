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
 * Phase 58 (OG-01): JVM static gate for the v15 to v16 migration.
 *
 * The on-device `MigrationTest` is hardware-gated (no adb in CI/dev
 * containers). This test closes the verification gap JVM-side with zero
 * Android framework, zero Robolectric and zero new dependencies: it asserts
 * the FULL delta (three nullable OG columns on grounded_sources) by reading
 * the exported Room schemas (15.json frozen baseline, 16.json export),
 * capturing the exact execSQL statements out of [MIGRATION_15_16], and
 * cross-checking migration SQL against the exported schema. Mirrors the
 * `Migration14To15StaticTest` precedent.
 *
 * Scope-reduction prohibition: this gate asserts the whole delta, not a subset.
 */
class Migration15To16StaticTest {

    // -- file resolution ----------------------------------------------------

    private fun readRepoFile(vararg candidates: String): String {
        // Classloader attempt first (harmless when schemas are not on the
        // unit-test classpath), then Gradle working-dir relative paths.
        // Under `:app:testDebugUnitTest` the working dir is app/.
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

    // -- JSON navigation (JsonObject only, no @Serializable models) ---------

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
        MIGRATION_15_16.migrate(db)
        return statements
    }

    // -- (1) schema versions -------------------------------------------------

    @Test
    fun `schema versions - 15 json is v15 and 16 json is v16`() {
        assertThat(schemaVersion(schemaJson(15))).isEqualTo(15)
        assertThat(schemaVersion(schemaJson(16))).isEqualTo(16)
    }

    // -- (2) v15 frozen baseline ---------------------------------------------

    @Test
    fun `v15 frozen baseline - no og columns`() {
        val v15 = schemaJson(15)
        assertWithMessage("15.json is the frozen baseline: og_title must never appear")
            .that(v15.contains("og_title")).isFalse()
        assertWithMessage("15.json is the frozen baseline: og_description must never appear")
            .that(v15.contains("og_description")).isFalse()
        assertWithMessage("15.json is the frozen baseline: og_image_url must never appear")
            .that(v15.contains("og_image_url")).isFalse()
    }

    // -- (3) exact v16 delta --------------------------------------------------

    @Test
    fun `exact v16 delta - grounded_sources gains exactly three nullable og columns`() {
        val v15 = entitiesOf(schemaJson(15))
        val v16 = entitiesOf(schemaJson(16))

        // No entity added, none dropped — the delta is columns-only.
        assertThat(v16.keys - v15.keys).isEmpty()
        assertThat(v15.keys - v16.keys).isEmpty()

        // grounded_sources gains exactly {og_title, og_description,
        // og_image_url}; every other entity keeps byte-identical columns.
        for (table in v15.keys) {
            val before = columnNames(v15.getValue(table))
            val after = columnNames(v16.getValue(table))
            if (table == "grounded_sources") {
                assertWithMessage("grounded_sources column delta must be exactly the 3 OG columns")
                    .that(after - before)
                    .containsExactly("og_title", "og_description", "og_image_url")
            } else {
                assertWithMessage("entity %s must keep identical columns between v15 and v16", table)
                    .that(after).containsExactlyElementsIn(before)
            }
        }

        // OG columns: TEXT affinity, nullable, no default — NULL means
        // "no OG captured" (a non-null default would conflate absent/empty).
        val fields = v16.getValue("grounded_sources")
            .getValue("fields").jsonArray
            .map { it.jsonObject }
        for (column in listOf("og_title", "og_description", "og_image_url")) {
            val field = fields.single {
                it.getValue("columnName").jsonPrimitive.content == column
            }
            assertThat(field.getValue("affinity").jsonPrimitive.content).isEqualTo("TEXT")
            assertWithMessage("%s must stay nullable (no notNull:true)", column)
                .that(field["notNull"]?.jsonPrimitive?.content).isNotEqualTo("true")
            assertWithMessage("%s must have no defaultValue (NULL default)", column)
                .that(field.containsKey("defaultValue")).isFalse()
        }
    }

    // -- (4) migration SQL exactness ------------------------------------------

    @Test
    fun `migration SQL exactness - 3 ALTER statements agreeing with exported schema`() {
        assertThat(MIGRATION_15_16.startVersion).isEqualTo(15)
        assertThat(MIGRATION_15_16.endVersion).isEqualTo(16)

        val statements = captureMigrationSql()
        assertWithMessage("MIGRATION_15_16 must execute exactly 3 statements, got: %s", statements)
            .that(statements).hasSize(3)

        val expectedColumns = listOf("og_title", "og_description", "og_image_url")
        statements.zip(expectedColumns).forEach { (sql, column) ->
            assertWithMessage("statement must ALTER grounded_sources: %s", sql)
                .that(
                    sql.contains("ALTER TABLE", ignoreCase = true) &&
                        sql.contains("grounded_sources") &&
                        sql.contains("ADD COLUMN", ignoreCase = true) &&
                        sql.contains(column),
                ).isTrue()
            val upper = sql.uppercase()
            assertWithMessage("statement must not pin NOT NULL: %s", sql)
                .that(upper.contains("NOT NULL")).isFalse()
            assertWithMessage("statement must not pin a DEFAULT: %s", sql)
                .that(upper.contains("DEFAULT")).isFalse()
        }

        // Migration SQL and exported schema agree: every migrated column
        // appears in the 16.json grounded_sources createSql.
        val createSql = entitiesOf(schemaJson(16))
            .getValue("grounded_sources")
            .getValue("createSql").jsonPrimitive.content
        for (column in expectedColumns) {
            assertWithMessage("16.json createSql must carry %s", column)
                .that(createSql.contains(column)).isTrue()
        }
    }

    // -- (5) registration ------------------------------------------------------

    @Test
    fun `registration - version 16 annotation, single 15-16 migration, wired into database builder`() {
        // Room's @Database annotation is CLASS retention, so it is not
        // visible to runtime reflection under unit tests — assert the
        // declared version from the AppDatabase.kt source instead (the
        // 16.json version cross-check already ran in the versions test).
        // Best-effort reflection stays: if a runtime-visible annotation ever
        // exists, it must also read 16.
        AppDatabase::class.java.getAnnotation(Database::class.java)?.let { annotation ->
            assertThat(annotation.version).isEqualTo(16)
        }
        val appDatabaseSource =
            mainSource("com/warped/data/local/db/AppDatabase.kt")
        assertWithMessage("AppDatabase @Database annotation must declare version = 16")
            .that(appDatabaseSource.contains("version = 16")).isTrue()

        val migrationsSource =
            mainSource("com/warped/data/local/db/Migrations.kt")
        val occurrences = "Migration(15, 16)".toRegex(RegexOption.LITERAL)
            .findAll(migrationsSource).count()
        assertWithMessage("Migrations.kt must declare exactly one Migration(15, 16), found %s", occurrences)
            .that(occurrences).isEqualTo(1)

        val moduleSource =
            mainSource("com/warped/di/DatabaseModule.kt")
        assertWithMessage("DatabaseModule.kt must call addMigrations")
            .that(moduleSource.contains("addMigrations")).isTrue()
        val addMigrationsTail = moduleSource.substringAfter("addMigrations")
        assertWithMessage("MIGRATION_15_16 must be wired inside the addMigrations call")
            .that(addMigrationsTail.contains("MIGRATION_15_16")).isTrue()
    }

    // -- (6) entity round-trip -------------------------------------------------

    @Test
    fun `entity round-trip preserves og columns`() {
        val source = GroundedSource(
            url = "https://example.com/page",
            extractedText = "text",
            status = GroundedSourceStatus.OK,
            ogTitle = "OG Title",
            ogDescription = "OG description",
            ogImageUrl = "https://cdn.example.com/img.png",
        )

        val roundTripped = source.toEntity(messageId = 1L, sourceIndex = 0).toDomain()

        assertThat(roundTripped.ogTitle).isEqualTo("OG Title")
        assertThat(roundTripped.ogDescription).isEqualTo("OG description")
        assertThat(roundTripped.ogImageUrl).isEqualTo("https://cdn.example.com/img.png")
        assertThat(roundTripped.url).isEqualTo("https://example.com/page")
    }

    @Test
    fun `entity round-trip keeps null og for legacy rows`() {
        val source = GroundedSource(
            url = "https://example.com/page",
            extractedText = null,
            status = GroundedSourceStatus.OMITIDA,
        )

        val roundTripped = source.toEntity(messageId = 1L, sourceIndex = 1).toDomain()

        assertThat(roundTripped.ogTitle).isNull()
        assertThat(roundTripped.ogDescription).isNull()
        assertThat(roundTripped.ogImageUrl).isNull()
    }
}
