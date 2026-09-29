package com.warped.data.local.db

import androidx.room.Database
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
 * Phase 53 gap-closure (TOGGLE-03): JVM static gate for the v14 to v15 migration.
 *
 * The on-device `MigrationTest.migrate14To15` is the gold standard but is
 * hardware-gated (no adb in CI/dev containers). This test closes the
 * verification gap JVM-side with zero Android framework, zero Robolectric and
 * zero new dependencies: it asserts the FULL delta (nullable web_override
 * column + grounded_sources table + index + CASCADE FK + registration) by
 * reading the exported Room schemas (14.json frozen baseline, 15.json export),
 * capturing the exact execSQL statements out of [MIGRATION_14_15], and
 * cross-checking migration SQL against the exported schema.
 *
 * Scope-reduction prohibition: this gate asserts the whole delta, not a subset.
 */
class Migration14To15StaticTest {

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
        MIGRATION_14_15.migrate(db)
        return statements
    }

    // -- (1) schema versions -------------------------------------------------

    @Test
    fun `schema versions - 14 json is v14 and 15 json is v15`() {
        assertThat(schemaVersion(schemaJson(14))).isEqualTo(14)
        assertThat(schemaVersion(schemaJson(15))).isEqualTo(15)
    }

    // -- (2) v14 frozen baseline ---------------------------------------------

    @Test
    fun `v14 frozen baseline - no web_override and no grounded_sources`() {
        val v14 = schemaJson(14)
        assertWithMessage("14.json is the frozen baseline: web_override must never appear")
            .that(v14.contains("web_override")).isFalse()
        assertWithMessage("14.json is the frozen baseline: grounded_sources must never appear")
            .that(v14.contains("grounded_sources")).isFalse()
    }

    // -- (3) exact v15 delta --------------------------------------------------

    @Test
    fun `exact v15 delta - one new table, one new column, full grounded_sources shape`() {
        val v14 = entitiesOf(schemaJson(14))
        val v15 = entitiesOf(schemaJson(15))

        // New entity is exactly {grounded_sources} — nothing added, nothing dropped.
        assertThat(v15.keys - v14.keys).containsExactly("grounded_sources")
        assertThat(v14.keys - v15.keys).isEmpty()

        // conversations gains exactly {web_override}; every other entity keeps
        // byte-identical column sets.
        for (table in v14.keys) {
            val before = columnNames(v14.getValue(table))
            val after = columnNames(v15.getValue(table))
            if (table == "conversations") {
                assertWithMessage("conversations column delta must be exactly {web_override}")
                    .that(after - before).containsExactly("web_override")
            } else {
                assertWithMessage("entity %s must keep identical columns between v14 and v15", table)
                    .that(after).containsExactlyElementsIn(before)
            }
        }

        // web_override: INTEGER affinity, nullable, no default — NULL preserves
        // the inherit leg of the tri-state (a non-null default would destroy it).
        val webOverride = v15.getValue("conversations")
            .getValue("fields").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("columnName").jsonPrimitive.content == "web_override" }
        assertThat(webOverride.getValue("affinity").jsonPrimitive.content).isEqualTo("INTEGER")
        assertWithMessage("web_override must stay nullable (no notNull:true)")
            .that(webOverride["notNull"]?.jsonPrimitive?.content).isNotEqualTo("true")
        assertWithMessage("web_override must have no defaultValue (NULL default preserves inherit)")
            .that(webOverride.containsKey("defaultValue")).isFalse()

        // grounded_sources: exact column set, extracted_text the only nullable
        // data column, CASCADE FK, message_id index.
        val grounded = v15.getValue("grounded_sources")
        val fields = grounded.getValue("fields").jsonArray.map { it.jsonObject }
        assertThat(fields.map { it.getValue("columnName").jsonPrimitive.content })
            .containsExactly(
                "id", "message_id", "source_index",
                "resolved_url", "extracted_text", "status",
            ).inOrder()
        val nullable = fields
            .filter { it["notNull"]?.jsonPrimitive?.content != "true" }
            .map { it.getValue("columnName").jsonPrimitive.content }
        assertWithMessage("extracted_text must be the only nullable grounded_sources column")
            .that(nullable).containsExactly("extracted_text")

        val fk = grounded.getValue("foreignKeys").jsonArray.single().jsonObject
        assertThat(fk.getValue("table").jsonPrimitive.content).isEqualTo("messages")
        assertThat(fk.getValue("columns").jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("message_id")
        assertThat(fk.getValue("referencedColumns").jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("id")
        assertThat(fk.getValue("onDelete").jsonPrimitive.content).isEqualTo("CASCADE")

        val index = grounded.getValue("indices").jsonArray.single().jsonObject
        assertThat(index.getValue("name").jsonPrimitive.content)
            .isEqualTo("index_grounded_sources_message_id")
        assertThat(index.getValue("columnNames").jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("message_id")
    }

    // -- (4) migration SQL exactness ------------------------------------------

    @Test
    fun `migration SQL exactness - 3 statements agreeing with exported schema`() {
        assertThat(MIGRATION_14_15.startVersion).isEqualTo(14)
        assertThat(MIGRATION_14_15.endVersion).isEqualTo(15)

        val statements = captureMigrationSql()
        assertWithMessage("MIGRATION_14_15 must execute exactly 3 statements, got: %s", statements)
            .that(statements).hasSize(3)

        val (alter, createTable, createIndex) = statements

        assertWithMessage("statement 1 must add the nullable web_override column: %s", alter)
            .that(alter.contains("ALTER TABLE", ignoreCase = true)).isTrue()
        assertWithMessage("statement 1 must target conversations: %s", alter)
            .that(alter.contains("conversations")).isTrue()
        assertWithMessage("statement 1 must add web_override: %s", alter)
            .that(alter.contains("ADD COLUMN", ignoreCase = true) && alter.contains("web_override"))
            .isTrue()
        val alterUpper = alter.uppercase()
        assertWithMessage("statement 1 must not pin NOT NULL (inherit leg): %s", alter)
            .that(alterUpper.contains("NOT NULL")).isFalse()
        assertWithMessage("statement 1 must not pin a DEFAULT (inherit leg): %s", alter)
            .that(alterUpper.contains("DEFAULT")).isFalse()

        assertWithMessage("statement 2 must create grounded_sources with CASCADE FK: %s", createTable)
            .that(
                createTable.contains("CREATE TABLE", ignoreCase = true) &&
                    createTable.contains("grounded_sources") &&
                    createTable.contains("message_id") &&
                    createTable.contains("CASCADE", ignoreCase = true),
            ).isTrue()

        assertWithMessage("statement 3 must create the message_id index: %s", createIndex)
            .that(
                createIndex.contains("CREATE INDEX", ignoreCase = true) &&
                    createIndex.contains("index_grounded_sources_message_id"),
            ).isTrue()

        // Migration SQL and exported schema agree: every key identifier in the
        // CREATE TABLE statement appears in the 15.json createSql.
        val createSql = entitiesOf(schemaJson(15))
            .getValue("grounded_sources")
            .getValue("createSql").jsonPrimitive.content
        for (identifier in listOf("resolved_url", "extracted_text", "status", "source_index")) {
            assertWithMessage("migration CREATE TABLE must carry %s", identifier)
                .that(createTable.contains(identifier)).isTrue()
            assertWithMessage("15.json createSql must carry %s", identifier)
                .that(createSql.contains(identifier)).isTrue()
        }
    }

    // -- (5) registration ------------------------------------------------------

    @Test
    fun `registration - version 15 annotation, single 14-15 migration, wired into database builder`() {
        // Room's @Database annotation is CLASS retention, so it is not
        // visible to runtime reflection under unit tests — assert the
        // declared version from the AppDatabase.kt source instead (the
        // 15.json version cross-check already ran in the versions test).
        // Best-effort reflection stays: if a runtime-visible annotation ever
        // exists, it must also read >= 15. (Phase 58: the annotation moves
        // forward with each migration; this gate pins the 14→15 link, not
        // the current head version.)
        AppDatabase::class.java.getAnnotation(Database::class.java)?.let { annotation ->
            assertWithMessage("AppDatabase version must never move below 15")
                .that(annotation.version).isAtLeast(15)
        }
        val appDatabaseSource =
            mainSource("com/warped/data/local/db/AppDatabase.kt")
        val declaredVersion = "version = (\\d+)".toRegex()
            .find(appDatabaseSource)?.groupValues?.getOrNull(1)?.toIntOrNull()
        assertWithMessage("AppDatabase @Database annotation must declare a version >= 15, source: %s", appDatabaseSource)
            .that(declaredVersion).isNotNull()
        assertWithMessage("AppDatabase version must never move below 15, found %s", declaredVersion)
            .that(declaredVersion!!).isAtLeast(15)

        val migrationsSource =
            mainSource("com/warped/data/local/db/Migrations.kt")
        val occurrences = "Migration(14, 15)".toRegex(RegexOption.LITERAL)
            .findAll(migrationsSource).count()
        assertWithMessage("Migrations.kt must declare exactly one Migration(14, 15), found %s", occurrences)
            .that(occurrences).isEqualTo(1)

        val moduleSource =
            mainSource("com/warped/di/DatabaseModule.kt")
        assertWithMessage("DatabaseModule.kt must call addMigrations")
            .that(moduleSource.contains("addMigrations")).isTrue()
        val addMigrationsTail = moduleSource.substringAfter("addMigrations")
        assertWithMessage("MIGRATION_14_15 must be wired inside the addMigrations call")
            .that(addMigrationsTail.contains("MIGRATION_14_15")).isTrue()
    }
}
