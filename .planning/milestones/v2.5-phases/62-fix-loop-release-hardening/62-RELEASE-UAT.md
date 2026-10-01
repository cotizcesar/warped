---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  gap_snapshot: "unknown::scenarios=0"
---

# 62 Release UAT (REL-01 closeout)

Explicit human/device items — nothing silent. Checkboxes flip to ✅ with
evidence (who/when/device) as each completes.

## App-function UAT (final artifact)

- [ ] **Leg 1B model-B switch** (from 61 baseline): second complete model
  download (~3 GB) + load/switch/unload tour leg. Carry-over.
- [ ] **G-59-01 16 KB chat turn**: closes automatically if the agent-run
  16 KB release smoke (see evidence file) completes a local turn with zero
  native failures; otherwise retry on a healthy 16 KB system.
- [ ] **Phase 60 follow-ups** (5): 3-button nav visuals; light-theme visuals
  (blocked — no theme toggle, product work); Play Console target-API
  warnings after uploading the 36/36 AAB; foldable posture; quota-pressure
  platform stop (user-cancel path verified, quota-stop path unit-tested).
- [ ] **Standing release-UAT smokes** (STATE.md): DEL-06, WEB-05/06, THEME-01,
  v2.3 MIG-01/WEB-07/WEB-08, v2.4 WEB-09/10/11.
- [ ] **Zero-leak replay (optional)**: debug-LeakCanary tour replay on the
  final tree if desired — baseline (6/6 clean) + 932-green regressions
  already cover it.

## Dashboard UAT (human reads — never fabricated)

- [ ] **Play Console pre-launch report**: confirm no 16 KB / target-API /
    alignment warnings on AAB `08255216…`.
- [ ] **Play Console target-API status**: 36/36 acknowledged, no warnings.

## Resolved tonight (for the record, no action)

- 16 KB boot + PAGE_SIZE 16384 + release install Success on 16KB image.
- Release launch clean on physical Pixel 8 (zero FATAL, sqlcipher ok).
- Catalog order/badges verified on-device screenshots; chat gating verified
  in code + 900-green suite; 270m text chat working on hardware.
