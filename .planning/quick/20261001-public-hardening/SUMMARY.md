---
status: complete
date: 2026-10-01
branch: main
pushed: true
---

# Summary: public hardening (all findings except Blocker 2)

## Done
1. **Blocker 1 — keystore passwords rotated.** Old `warped123`
   (in git history) replaced with 48-hex-char randoms (store + key).
   Backup of original `.jks` at `/tmp/opencode/warped-release.jks.bak`
   (local machine only). `local.properties` + GitHub Secrets
   (`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`) updated. Verified:
   `bundleRelease` signs clean with new creds. NOTE: leaked old
   passwords are now useless; history purge optional.
2. **Blocker 3 — actions pinned.** All 6 `uses:` in `ci.yml`/`release.yml`
   pinned to full SHAs (comments keep the tag). Added
   `.github/dependabot.yml` (weekly `github-actions`).
3. **HIGH 4 — permissions stripped.** Removed `RECORD_AUDIO`,
   `SCHEDULE_EXACT_ALARM`, `ACCESS_FINE/COARSE_LOCATION` from manifest
   (+ rationale comment); deleted dead `AudioRecorder.kt` (zero refs).
   Verified in merged release manifest: forbidden names appear only in
   the comment; AAB carries INTERNET/NOTIFICATIONS/FGS + standard
   WorkManager-added ones.
4. Pushed to `main` (CI validates pinned SHAs; release ships hardened
   Beta).

## Left to user
- Blocker 2 (self-hosted runner + fork PRs) — explicitly kept by user.
- Branch protection after going public (403 on private free plan).
- Cleartext-LAN accepted risk (later UX note).
- New passwords shown once in chat — store in password manager.
