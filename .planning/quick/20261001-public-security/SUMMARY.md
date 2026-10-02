---
status: complete
date: 2026-10-01
pushed: false
---

# Pre-public security review — findings (2026-10-01)

## BLOCKER 1 — Keystore passwords in git history (fix BEFORE public)
`local.properties` was committed before `00bd5940` untracked it. History
contains: `warped123` (store+key) AND a 32-hex pair. Verified just now:
**current passwords ARE `warped123`** (weak + leaked).
Remediation: `keytool -storepasswd` + `-keypasswd` to strong randoms,
update `local.properties` + GitHub Secrets
(`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`), verify
`bundleRelease` signs. Optional hardening: purge file from history
(BFG/filter-repo, rewrites hashes). Rotation alone neutralizes the leak
(the `.jks` itself was never committed).

## BLOCKER 2 — Fork PRs execute on self-hosted runner
`ci.yml` runs `pull_request` on `runs-on: [self-hosted, android]`
(private machine). On a public repo any fork can open a PR → arbitrary
code on that runner (persistence, cache poisoning; secrets aren't
exposed to forks but the machine is). Remediation (pick one): run PR
jobs on `ubuntu-latest`, or gate PR workflow to non-forks
(`github.event.pull_request.head.repo.full_name == github.repository`),
and/or keep the "require approval for fork workflows" setting on.

## BLOCKER 3 — Unpinned third-party release action
`r0adkll/upload-google-play@v1` (floats on a tag) runs with the Release
Manager GCP SA + signed AAB in `release.yml`. Tag-move = exfiltration
risk. Remediation: pin ALL actions to full commit SHAs
(`checkout`, `setup-java`, `setup-android`, `setup-gradle`,
`upload-artifact`, `upload-google-play`), audit `r0adkll` source at the
pinned SHA; enable Dependabot for action updates.

## HIGH 4 — Sensitive permissions declared but unused
`ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`: zero code usage.
`SCHEDULE_EXACT_ALARM`: zero alarm code (`BenchmarkScheduler` clean).
`RECORD_AUDIO`: `AudioRecorder` is DEAD code (no references) with no
runtime-permission flow; `MediaRecorder.start()` unguarded would throw
`SecurityException` if ever wired. Remediation: delete all four
declarations from the manifest (re-add audio later WITH proper
`RequestPermission` flow). Also removes Play sensitive-permission
justification friction.

## ACCEPTED RISK 5 — Global cleartext permitted
`network_security_config.xml` sets `cleartextTrafficPermitted="true"` at
base level (documented ENDPT-06: LAN LLM servers are HTTP-only, per-IP
allowlist impossible). Acceptable for this product, but public users
should be warned in Settings when an endpoint URL is remote-HTTP
(MITM). No Play violation.

## INFO (verified clean, no action)
- Branch protection API 403s on private free plan — apply the prepared
  `protection.json` (PR + 1 approval, no force-push) right AFTER going
  public. Note: GitHub forbids self-approval, so keep `enforce_admins:
  false` or solo PRs become unmergeable.
- No hardcoded keys in `src`/`test`/`scripts`/`.planning` (only
  `tvly-secret` redaction fixtures). No private keys/GCP JSON in any
  revision (only the passwords above).
- Logging: Timber planted only in DEBUG (`RedactingTree`); zero direct
  `android.util.Log` in app code.
- Attack surface: no WebView/FileProvider/deep-links; only launcher
  activity exported; startup provider not exported.
- `allowBackup=false` + extraction rules exclude everything. R8 full
  mode on. 1.7.1 licensing kill-switch gone from current code (verified).
- `app/keystore/`, `local.properties` properly gitignored and untracked
  today.

## Recommended implementation order
Rotate passwords → strip permissions → pin actions + gate PR runner →
public → branch protection → (later) cleartext UX note.
