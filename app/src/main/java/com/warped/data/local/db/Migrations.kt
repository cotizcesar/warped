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
        db.execSQL("ALTER TABLE local_models ADD COLUMN model_format TEXT NOT NULL DEFAULT 'GGUF'")
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
