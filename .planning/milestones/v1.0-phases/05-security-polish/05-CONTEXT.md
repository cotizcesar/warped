# Phase 5: Security Hardening & Polish

## Context

Final phase for the Warped Android project. Implements user-facing data deletion controls and security audit.

### Phase Goal
Provide user-facing data deletion controls (chat history, API keys), ensure all sensitive data is purged, and perform final security review.

### Success Criteria
1. User can delete all chat history from settings and verify conversations/messages are permanently removed
2. User can delete stored API keys individually or all at once, and Keystore entries are purged
3. Deleting chat history or API keys does not affect endpoint configs, local models, or presets

### Implementation Summary

#### New Files
- `app/src/main/java/com/warped/ui/settings/SettingsUiState.kt` — UI state data class
- `app/src/main/java/com/warped/ui/settings/SettingsViewModel.kt` — Settings ViewModel with deletion logic
- `app/src/main/java/com/warped/ui/settings/SettingsScreen.kt` — Settings screen UI

#### Modified Files
- `app/src/main/java/com/warped/ui/navigation/Screen.kt` — Added `Settings` screen route
- `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` — Added Settings composable route

#### Verified Files (no changes needed)
- `app/src/main/java/com/warped/WarpedApplication.kt` — RedactingTree properly filters all sensitive patterns
- `app/src/main/java/com/warped/di/SecurityModule.kt` — Keystore encryption with AES-256-GCM configured
- `app/src/main/res/xml/data_extraction_rules.xml` — Excludes all data from backups/transfers
- `app/src/main/java/com/warped/data/local/security/KeystoreManager.kt` — EncryptedSharedPreferences with hardware-backed keys
- `app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt` — Individual and batch key deletion support

### Security Verification

| Check | Status |
|-------|--------|
| RedactingTree filters api_key | PASS |
| RedactingTree filters Bearer tokens | PASS |
| RedactingTree filters secrets | PASS |
| RedactingTree filters authorization headers | PASS |
| KeystoreManager uses AES-256-GCM | PASS |
| KeystoreManager uses EncryptedSharedPreferences | PASS |
| KeystoreManager uses hardware-backed MasterKey | PASS |
| data_extraction_rules excludes all from backup | PASS |
| ApiKeyStore.deleteKey removes from Keystore | PASS |
| ApiKeyStore.deleteAllKeys batch-removes | PASS |
| KeystoreManager.clearAll() available | PASS |

### Architecture Notes
- SettingsViewModel observes 4 data sources to display counts (chats, endpoints, models, presets)
- Deletion operations are scoped to their specific data sources only
- Chat deletion uses `ChatRepository.deleteAllConversations()`
- API key deletion uses `ApiKeyStore.deleteAllKeys()`/`deleteKey()` which removes from EncryptedSharedPreferences backed by Android Keystore
- Endpoint configs, local models, and presets are NOT affected by chat/key deletion
