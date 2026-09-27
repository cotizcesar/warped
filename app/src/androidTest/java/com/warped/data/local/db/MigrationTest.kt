package com.warped.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
}
