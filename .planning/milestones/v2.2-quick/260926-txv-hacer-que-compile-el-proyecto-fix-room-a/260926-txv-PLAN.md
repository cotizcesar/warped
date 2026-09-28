---
id: 260926-txv
title: Hacer que compile el proyecto - fix Room auto-migration sin schemas
status: planned
---

# Plan: fix compilación Room

## Diagnóstico
- `./gradlew :app:assembleDebug` falla en `:app:kspDebugKotlin`:
  `Schema '11.json' / '12.json' required for migration was not found`.
- `AppDatabase` v13 declara `AutoMigration(11->12, 12->13)` pero `app/schemas/` está en `.gitignore` y localmente solo existe `13.json`.
- En checkout limpio es imposible compilar: Room KSP exige los schemas de origen/destino de cada AutoMigration.

## Cambios (3 ficheros + test + gitignore)
1. `data/local/db/Migrations.kt`: añadir `MIGRATION_11_12` (CREATE TABLE benchmark_results + 2 índices, SQL copiado de 13.json) y `MIGRATION_12_13` (DROP INDEX `index_messages_conversation_id` + CREATE INDEX compuesto).
2. `data/local/db/AppDatabase.kt`: quitar `AutoMigration` import y `autoMigrations`, dejar `version = 13, exportSchema = true`.
3. `di/DatabaseModule.kt`: registrar `MIGRATION_11_12, MIGRATION_12_13` en `addMigrations(...)`.
4. `src/androidTest/.../MigrationTest.kt`: pasar migraciones manuales explícitas a `runMigrationsAndValidate` (ya no hay automigración que descubrir).
5. `.gitignore`: quitar la línea `app/schemas` para que los schemas se versionen y no se repita el problema.

## Verificación
- `./gradlew :app:assembleDebug` compila OK.
- `./gradlew :app:kspDebugKotlin` sin error de schemas.
