---
status: complete
date: 2026-10-01
branch: main
pushed: false
---

# Summary: branch restructure (beta → main, drop production)

## Done (local only, NOT pushed)
- Deleted local `production` (was `cbca244f`; its 3 beta-merge commits
  PR #1-3 vanish as topology, code content preserved in beta ancestry).
- Renamed local `beta` → `main` (only local branch now).
- `ci.yml`: triggers `[main]`; gradle cache ref → `refs/heads/main`.
- `release.yml`: trigger `[main]`; track hardcoded to `beta` (same
  destination beta had; prod promotion now manual in Play Console).
- `BRANCH_PROTECTION.md`: rewritten for single-branch flow.
- YAML validated. Committed on `main`, no push.

## Remote steps left for the USER
1. `git push origin main`
2. Set `main` as default branch (GitHub Settings or
   `gh repo edit --default-branch main`).
3. `git push origin --delete beta production` (after step 2).
4. `git fetch --prune` to clean stale remote-tracking refs.
5. Move branch protection rules beta/production → `main`.
6. Note: `origin/pr-1-merge` remote branch left untouched.
