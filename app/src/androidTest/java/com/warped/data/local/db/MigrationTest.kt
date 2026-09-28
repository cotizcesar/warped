package com.warped.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate11To12_preservesConversationsAndMessages() {
        val dbName = "migration-test.db"
        // Open the v11 schema and seed a conversation with 50 messages.
        helper.createDatabase(dbName, 11).apply {
            execSQL(
                "INSERT INTO conversations(id, title, created_at, updated_at, provider_type, endpoint_id, model_id, system_prompt) " +
                    "VALUES (1, 'test', 1700000000000, 1700000001000, 'local', 0, 'm', null)"
            )
            repeat(50) { i ->
                execSQL(
                    "INSERT INTO messages(conversation_id, role, content, token_count, created_at, images, stats, reasoning) " +
                        "VALUES (1, 'user', 'msg $i', 5, ${1700000000000L + i}, null, null, null)"
                )
            }
            close()
        }

        // Run the manual migration v11 -> v12 and verify data is intact + new table exists.
        helper.runMigrationsAndValidate(dbName, 12, true, MIGRATION_11_12).use { db ->
            db.query("SELECT COUNT(*) FROM messages WHERE conversation_id = 1").use { cur ->
                cur.moveToFirst()
                assertEquals("All 50 messages must survive migration", 50, cur.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM conversations").use { cur ->
                cur.moveToFirst()
                assertEquals("Conversation must survive migration", 1, cur.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM benchmark_results").use { cur ->
                cur.moveToFirst()
                assertEquals("benchmark_results must start empty", 0, cur.getInt(0))
            }
        }
    }

    // Phase 53 (TOGGLE-03 exit gate): v14 -> v15 carries web_override + grounded_sources.
    // Requires a device/emulator; assembleDebug must run first so 15.json exists.
    @Test
    fun migrate14To15_preservesRowsDefaultsOverrideNullAndCascadesSources() {
        val dbName = "migration-test-14-15.db"
        // Seed at v14: one conversation with a user + assistant message.
        helper.createDatabase(dbName, 14).apply {
            execSQL(
                "INSERT INTO conversations(id, title, created_at, updated_at, provider_type, endpoint_id, model_id, system_prompt) " +
                    "VALUES (1, 'grounding chat', 1700000000000, 1700000001000, 'remote', 0, 'm', null)"
            )
            execSQL(
                "INSERT INTO messages(id, conversation_id, role, content, token_count, created_at, images, stats, reasoning) " +
                    "VALUES (10, 1, 'user', 'https://example.com/a', 5, 1700000000000, null, null, null)"
            )
            execSQL(
                "INSERT INTO messages(id, conversation_id, role, content, token_count, created_at, images, stats, reasoning) " +
                    "VALUES (11, 1, 'ASSISTANT', 'reply', 7, 1700000000001, null, null, null)"
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 15, true, MIGRATION_14_15).use { db ->
            // Pre-existing rows survive.
            db.query("SELECT COUNT(*) FROM conversations").use { cur ->
                cur.moveToFirst()
                assertEquals("Conversation must survive migration", 1, cur.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM messages WHERE conversation_id = 1").use { cur ->
                cur.moveToFirst()
                assertEquals("Both messages must survive migration", 2, cur.getInt(0))
            }
            // Old rows read web_override NULL (= inherit global default-ON).
            db.query("SELECT web_override FROM conversations WHERE id = 1").use { cur ->
                cur.moveToFirst()
                assertTrue("web_override must default NULL on old rows", cur.isNull(0))
            }
            // grounded_sources starts empty.
            db.query("SELECT COUNT(*) FROM grounded_sources").use { cur ->
                cur.moveToFirst()
                assertEquals("grounded_sources must start empty", 0, cur.getInt(0))
            }

            // Insert ok + omitida rows out of order; read-back must follow source_index.
            db.execSQL(
                "INSERT INTO grounded_sources(message_id, source_index, resolved_url, extracted_text, status) " +
                    "VALUES (11, 1, 'https://example.com/skipped', null, 'omitida')"
            )
            db.execSQL(
                "INSERT INTO grounded_sources(message_id, source_index, resolved_url, extracted_text, status) " +
                    "VALUES (11, 0, 'https://example.com/kept', 'page text', 'ok')"
            )
            db.query(
                "SELECT source_index, resolved_url, extracted_text, status FROM grounded_sources " +
                    "WHERE message_id = 11 ORDER BY source_index ASC"
            ).use { cur ->
                cur.moveToFirst()
                assertEquals(0, cur.getInt(0))
                assertEquals("https://example.com/kept", cur.getString(1))
                assertEquals("page text", cur.getString(2))
                assertEquals("ok", cur.getString(3))
                cur.moveToNext()
                assertEquals(1, cur.getInt(0))
                assertEquals("https://example.com/skipped", cur.getString(1))
                assertTrue(cur.isNull(2))
                assertEquals("omitida", cur.getString(3))
            }

            // Boolean? override round-trips null/0/1.
            db.execSQL("UPDATE conversations SET web_override = 1, updated_at = 2 WHERE id = 1")
            db.query("SELECT web_override FROM conversations WHERE id = 1").use { cur ->
                cur.moveToFirst()
                assertEquals(1, cur.getInt(0))
            }
            db.execSQL("UPDATE conversations SET web_override = 0, updated_at = 3 WHERE id = 1")
            db.query("SELECT web_override FROM conversations WHERE id = 1").use { cur ->
                cur.moveToFirst()
                assertEquals(0, cur.getInt(0))
            }
            db.execSQL("UPDATE conversations SET web_override = NULL, updated_at = 4 WHERE id = 1")
            db.query("SELECT web_override FROM conversations WHERE id = 1").use { cur ->
                cur.moveToFirst()
                assertTrue(cur.isNull(0))
            }

            // Deleting the conversation cascades messages into sources transitively.
            db.execSQL("DELETE FROM conversations WHERE id = 1")
            db.query("SELECT COUNT(*) FROM messages").use { cur ->
                cur.moveToFirst()
                assertEquals("Messages must cascade with conversation", 0, cur.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM grounded_sources").use { cur ->
                cur.moveToFirst()
                assertEquals("Sources must cascade transitively", 0, cur.getInt(0))
            }
        }
    }
}
