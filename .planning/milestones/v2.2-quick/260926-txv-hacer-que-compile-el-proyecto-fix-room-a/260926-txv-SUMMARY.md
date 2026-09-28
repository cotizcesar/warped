---
id: 260926-txv
title: Hacer que compile el proyecto - fix Room auto-migration sin schemas
status: complete
---

# Summary: fix compilación Room

## Causa
`./gradlew :app:assembleDebug` fallaba en `:app:kspDebugKotlin`:
`Schema '11.json' / '12.json' required for migration was not found`.
`AppDatabase` v13 declaraba `AutoMigration(11->12, 12->13)` pero `app/schemas/` estaba en `.gitignore` y solo existía `13.json` local. En checkout limpio es imposible compilar.

## Cambios
- `data/local/db/Migrations.kt`: nuevas `MIGRATION_11_12` (CREATE TABLE `benchmark_results` + 2 índices, SQL de 13.json) y `MIGRATION_12_13` (DROP `index_messages_conversation_id` + CREATE índice compuesto).
- `data/local/db/AppDatabase.kt`: eliminadas `AutoMigration` / `autoMigrations`; `version = 13, exportSchema = true` se mantiene.
- `di/DatabaseModule.kt`: registradas `MIGRATION_11_12, MIGRATION_12_13` en `addMigrations()`.
- `src/androidTest/.../MigrationTest.kt`: usa `MIGRATION_11_12` explícita en `runMigrationsAndValidate`.
- `.gitignore`: eliminada la línea `app/schemas` para versionar schemas y evitar regresión.
- `app/schemas/.../13.json`: ahora versionado (regenerado por el build).

## Verificación
- `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL** (26s, solo warnings de deprecación preexistentes).
- No se tocó `gradle.properties` (cambio local preexistente `org.gradle.tooling.parallel` dejado fuera del commit).
- `MigrationTest` runtime sigue pendiente de dispositivo real (igual que antes).
