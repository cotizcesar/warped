# Deferred Items — Phase 29

Items discovered during plan execution that are out of scope.

## Pre-existing Compilation Blockers

These prevent `./gradlew :app:compileDebugKotlin` from passing but are NOT caused by plan 29-03 changes.

### 1. Missing fields in SettingsUiState
- **File:** `app/src/main/java/com/warped/ui/settings/SettingsUiState.kt`
- **Missing:** `advancedParams: GenerationParameters`, `toolStates: List<ToolState>`, `enabledToolIds: Set<String>`, `selectedTab: SettingsTab`
- **Impact:** SettingsViewModel and SettingsScreen fail to compile (references to these fields)
- **Source:** Pre-existing — fields removed during previous refactoring without updating consumers

### 2. Missing Operator modifiers (SettingsScreen.kt:453-454)
- **File:** `app/src/main/java/com/warped/ui/settings/SettingsScreen.kt`
- **Issue:** `component1()` and `component2()` extension functions missing `operator` modifier
- **Impact:** Build failure
- **Source:** Pre-existing

### 3. Missing ToolState data class
- **Resolved:** Created `ToolState.kt` (Rule 3 auto-fix) in plan 29-03 commit 88fe7b8
