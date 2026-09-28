---
phase: quick-260430-ryn
plan: 01
subsystem: ui
tags: [compose, material3, chat, input-bar, navigation, material-icons-extended]

# Dependency graph
requires: []
provides:
  - ChatGPT-style pill input with Material send/stop icons
  - ChatScreen without TopAppBar, inline ModelSelector above messages
  - Icon-only bottom navigation bar (no text labels)
affects: [chat, navigation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "OutlinedTextField with shape = MaterialTheme.shapes.extraLarge and transparent borders for pill-style input"
    - "NavigationBarItem with label = null (icon-only) using contentDescription for accessibility"
    - "ModelSelector composable inline above LazyColumn (ExposedDropdownMenuBox) rather than in TopAppBar title"

key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/navigation/NavGraph.kt

key-decisions:
  - "Used OutlinedTextFieldDefaults.colors(focusedBorderColor, unfocusedBorderColor) with Color.Transparent for the pill-style input border hiding (not TextFieldDefaults colors, which use different parameter names)"
  - "Used Icons.AutoMirrored.Filled.Send for send button — mirrors correctly on RTL locales"
  - "Kept ModelSelector as ExposedDropdownMenuBox — no refactor needed, just relocated from TopAppBar title to inline Column body"

patterns-established:
  - "ChatInputBar: flat Row with Alignment.Bottom, OutlinedTextField(shape=extraLarge) + IconButton for send/stop"
  - "ChatScreen: Scaffold with bottomBar only (no topBar), ModelSelector in Box above LazyColumn"
  - "NavGraph: NavigationBarItem with null labels and contentDescription on icons for accessibility"

requirements-completed: [CHAT-REDESIGN-01]

# Metrics
duration: ~10min
completed: 2026-04-30
---

# Quick Task 260430-ryn: Chat UI Redesign — Hide TopAppBar, Inline Model Selector

**Modernized chat interface with ChatGPT-style pill input, Material send/stop icons, inline model selector, and icon-only bottom navigation.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-04-30T20:40:00Z
- **Completed:** 2026-04-30T20:50:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Redesigned ChatInputBar from Surface-wrapped row with text icons ("■"/"→") to a flat pill-style `OutlinedTextField` with Material `Icons.Filled.Stop` and `Icons.AutoMirrored.Filled.Send`
- Removed TopAppBar and AssistChip from ChatScreen; moved `ModelSelector` inline above `LazyColumn` as an `ExposedDropdownMenuBox`
- Compacted bottom navigation to icons-only by setting `NavigationBarItem.label = null` while preserving `contentDescription` on icons for accessibility
- Build verified: `./gradlew assembleDebug` succeeds with Java 21

## Task Commits

Each task was committed atomically:

1. **Task 1: Redesign ChatInputBar** — `6a570bb` (feat)
2. **Task 1 fix: Correct color parameter names** — `2d1a13f` (fix)
3. **Task 2: Remove TopAppBar, inline ModelSelector, icon-only navbar** — `b6cb8f7` (feat)

## Files Created/Modified

- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` — Complete redesign: pill-style OutlinedTextField with Material send/stop icons, no Surface wrapper
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` — TopAppBar and AssistChip removed; ModelSelector added inline above LazyColumn
- `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` — NavigationBarItem labels set to null (icons only); redundant Text import removed

## Decisions Made

- Used `OutlinedTextFieldDefaults.colors(focusedBorderColor, unfocusedBorderColor)` with `Color.Transparent` — not `TextFieldDefaults.colors()` which has different parameter names
- Send icon uses `Icons.AutoMirrored.Filled.Send` for correct RTL mirroring on Android
- ModelSelector retained as-is — only relocated from TopAppBar title to inline Column body, no composable signature changes needed

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed `OutlinedTextFieldDefaults.colors()` parameter names**
- **Found during:** Task 2 build verification
- **Issue:** Plan's reference code used `unfocusedIndicatorColor`/`focusedIndicatorColor` which don't exist on `OutlinedTextFieldDefaults.colors()`. Those parameters exist on `TextFieldDefaults.colors()` (not `OutlinedTextFieldDefaults`).
- **Fix:** Changed to `unfocusedBorderColor`/`focusedBorderColor` — the correct parameter names for `OutlinedTextFieldDefaults.colors()`
- **Files modified:** `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt`
- **Verification:** `./gradlew assembleDebug` passes with Java 21
- **Committed in:** `2d1a13f`

**2. [Rule 3 - Blocking] Java 25 incompatible with Gradle 8.13 Kotlin DSL parser**
- **Found during:** Build verification
- **Issue:** System Java is 25.0.2, which Gradle 8.13's Kotlin compiler can't parse (`java.lang.IllegalArgumentException: 25.0.2`). Pre-existing environment issue.
- **Fix:** Used `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` for the build
- **Verification:** Build succeeds with Java 21
- **Note:** Not a code issue — environment configuration. No code change needed.

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both auto-fixes necessary for compilation. No scope creep. Plan executed correctly aside from API parameter name error.

## Issues Encountered

- Java 25.0.2 installed as default JVM but Gradle 8.13 / Kotlin 2.1.10 can't parse its version string. Future setup should ensure `JAVA_HOME` points to Java 17 or 21 in local.properties or environment.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Chat UI redesign complete — ready for verification testing or subsequent phase work
- No blockers

---
*Quick Task: 260430-ryn*
*Completed: 2026-04-30*
