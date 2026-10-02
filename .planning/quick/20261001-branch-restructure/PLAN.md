# Quick Task: branch restructure (beta → main, drop production)

## Task (user request, 2026-10-01)
- Delete `production` branch (local; remote deletion left to user).
- Rename `beta` → `main` (local).
- `main` deploys where `beta` deploys now: Play **Beta** track.
  Promotion to prod becomes manual in Play Console.

## Changes
1. `git branch -D production` (its 3 merge commits PR #1-3 vanish; code
   content already in beta ancestry — reported to user).
2. `git branch -m beta main`.
3. `.github/workflows/ci.yml`: triggers `[beta, production]` → `[main]`
   (push + PR); gradle cache `cache-read-only` ref `production` → `main`.
4. `.github/workflows/release.yml`: trigger → `[main]`; track step →
   hardcoded `beta` + comment (manual prod promotion).
5. `.github/BRANCH_PROTECTION.md`: single-branch doc (`main` → Beta
   track, manual promote to Production).

## Verification
- `git branch -a` shows only `main` locally.
- `grep` no stale `beta`/`production` refs in `.github/`.
- Commit on `main`, NO push (standing user constraint).

## Remote steps for the USER (not done here)
- `git push origin main`
- Set `main` as default branch (GitHub UI / `gh repo edit`).
- `git push origin --delete beta production`; `git fetch --prune`.
- Move branch protection rules beta/production → main.
