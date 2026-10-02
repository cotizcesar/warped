# Quick Task: public hardening (all audit findings except Blocker 2)

## Task (user: "repara todo menos el bloqueador 2", 2026-10-01)
1. **Blocker 1** — rotate keystore store+key passwords (current
   `warped123` is weak AND in git history). Backup `.jks` first.
   Update `local.properties` + GitHub Secrets. Verify via release
   signing (`bundleRelease`).
2. **Blocker 3** — pin all GH Actions to full SHAs in `ci.yml` /
   `release.yml` + add Dependabot for `github-actions`.
3. **HIGH 4** — remove 4 unused permissions from manifest
   (`ACCESS_FINE/COARSE_LOCATION`, `SCHEDULE_EXACT_ALARM`,
   `RECORD_AUDIO`) + delete dead `AudioRecorder.kt` (zero references,
   unguarded MIC path).
4. Out of scope: Blocker 2 (runner, user handles), cleartext UX note
   (accepted risk, later), branch protection (blocked until public).

## Verification
- `keytool -list` with new passwords; `bundleRelease` signs + installs.
- AAB manifest has no removed permissions (`aapt dump badging`).
- CI green on push (validates pinned SHAs).
- Commit(s) on `main` + push (push authorized; needed for CI proof).
