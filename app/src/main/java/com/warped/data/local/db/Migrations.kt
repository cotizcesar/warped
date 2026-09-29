package com.warped.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN images TEXT")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN stats TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_models ADD COLUMN model_format TEXT NOT NULL DEFAULT 'LITERTLM'")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS download_checkpoints (
                model_id TEXT NOT NULL PRIMARY KEY,
                file_name TEXT NOT NULL,
                file_url TEXT NOT NULL,
                total_bytes INTEGER NOT NULL DEFAULT 0,
                downloaded_bytes INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE presets ADD COLUMN model_format TEXT NOT NULL DEFAULT 'LITERTLM'")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN reasoning TEXT")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_models ADD COLUMN param_temperature REAL NOT NULL DEFAULT 0.7")
        db.execSQL("ALTER TABLE local_models ADD COLUMN param_top_p REAL NOT NULL DEFAULT 0.9")
        db.execSQL("ALTER TABLE local_models ADD COLUMN param_top_k INTEGER NOT NULL DEFAULT 40")
        db.execSQL("ALTER TABLE local_models ADD COLUMN param_repeat_penalty REAL NOT NULL DEFAULT 1.1")
        db.execSQL("ALTER TABLE local_models ADD COLUMN param_max_tokens INTEGER NOT NULL DEFAULT 2048")
        db.execSQL("ALTER TABLE local_models ADD COLUMN param_context_size INTEGER NOT NULL DEFAULT 4096")
        db.execSQL("ALTER TABLE local_models ADD COLUMN param_seed INTEGER NOT NULL DEFAULT -1")
    }
}

// PERF-09: composite index on messages(conversation_id, created_at).
// Manual migrations replace the former AutoMigration(11->12, 12->13):
// app/schemas/ is not versioned, so Room KSP cannot resolve AutoMigration
// schemas on a clean checkout (11.json/12.json missing -> kspDebugKotlin fails).

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `benchmark_results` " +
                "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`modelId` TEXT NOT NULL, `configHash` TEXT NOT NULL, " +
                "`initTimeMs` INTEGER NOT NULL, `prefillTokPerSec` REAL NOT NULL, " +
                "`decodeTokPerSec` REAL NOT NULL, `peakMemoryBytes` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_benchmark_results_modelId` ON `benchmark_results` (`modelId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_benchmark_results_createdAt` ON `benchmark_results` (`createdAt`)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_messages_conversation_id`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_messages_conversation_id_created_at` " +
                "ON `messages` (`conversation_id`, `created_at`)"
        )
    }
}

// local-empty-response follow-up (2026-09-28): repair assistant rows poisoned by the
// old parseThinkBlocks "no tags -> everything is reasoning" fallback (fixed in 839f268).
// Those rows have empty content with the full reply stored in reasoning and no think
// markers; move it back to content so history renders as normal bubbles. Rows carrying
// genuine tagged reasoning (<think>/<channel>) are untouched, as are non-empty answers.
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE messages SET content = reasoning, reasoning = NULL " +
                "WHERE role = 'ASSISTANT' " +
                "AND (content IS NULL OR content = '') " +
                "AND reasoning IS NOT NULL AND reasoning != '' " +
                "AND reasoning NOT LIKE '%<think>%' " +
                "AND reasoning NOT LIKE '%</think>%' " +
                "AND reasoning NOT LIKE '%<channel%' " +
                "AND reasoning NOT LIKE '%</channel%'"
        )
    }
}

// Phase 53 (SRC-01/TOGGLE-01/TOGGLE-03): ONE migration carrying both DDLs —
// no multi-step, no second migration. web_override stays nullable with no
// default so existing rows read NULL (= inherit global default-ON); a non-null
// default would destroy the inherit leg of the tri-state.
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN web_override INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `grounded_sources` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`message_id` INTEGER NOT NULL, " +
                "`source_index` INTEGER NOT NULL, " +
                "`resolved_url` TEXT NOT NULL, " +
                "`extracted_text` TEXT, " +
                "`status` TEXT NOT NULL, " +
                "FOREIGN KEY(`message_id`) REFERENCES `messages`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_grounded_sources_message_id` " +
                "ON `grounded_sources` (`message_id`)"
        )
    }
}

// Phase 58 (OG-01): three nullable OG columns on grounded_sources.
// NULL means no OG captured (pre-58 rows, plain/markdown sources, Tavily
// rows). No index (no query filters on OG columns), no backfill — mirrors
// the MIGRATION_14_15 ALTER-only shape.
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE grounded_sources ADD COLUMN og_title TEXT")
        db.execSQL("ALTER TABLE grounded_sources ADD COLUMN og_description TEXT")
        db.execSQL("ALTER TABLE grounded_sources ADD COLUMN og_image_url TEXT")
    }
}
