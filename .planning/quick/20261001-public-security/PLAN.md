# Quick Task: pre-public security review (read-only audit)

## Task (user request, 2026-10-01)
Review the whole repo for vulnerabilities before making it public.
No code fixes in this task — findings + remediations only.

## Method
git-history forensics, tracked-file scan, manifest/permissions vs code
usage, network config, logging paths, hardcoded-secret patterns, test
fixtures, workflow supply-chain, runner trust, API availability
(branch protection blocked: private free plan).
