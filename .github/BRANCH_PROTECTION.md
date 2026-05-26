# Branch Protection Setup

This project uses **track-based branches** that map directly to Google Play Store release tracks.

## Branches

| Branch      | Play Store Track | Purpose                        |
|-------------|------------------|--------------------------------|
| `internal`  | Internal testing | Fast iteration, QA builds      |
| `alpha`     | Alpha            | Early external testers         |
| `beta`      | Beta             | Larger group testing           |
| `production`| Production       | Public release                 |
| `main`      | —                | Development trunk (CI only)    |

## Required GitHub Repository Settings

You must manually configure branch protection rules in the GitHub UI. Go to:

**Settings → Branches → Add rule**

Apply the following to `internal`, `alpha`, `beta`, and `production`:

- [x] **Require a pull request before merging**
  - [x] Require approvals: `1`
- [x] **Require status checks to pass before merging**
  - Search for and select: `build`
- [x] **Require branches to be up to date before merging**
- [x] **Restrict pushes that create files larger than 100 MB**
- [x] **Block force pushes**
- [x] **Require linear history** (optional, recommended)

For `main`, the same rules apply but without automatic Play Store release.

## Release Flow

1. Open a PR from `main` → `internal`
2. Merge → CI runs → Release workflow uploads to **Internal** track
3. Promote PR: `internal` → `alpha` → CI runs → uploads to **Alpha**
4. Promote PR: `alpha` → `beta` → CI runs → uploads to **Beta**
5. Promote PR: `beta` → `production` → CI runs → uploads to **Production**

Each merge triggers the `release.yml` workflow automatically.

## Required Secrets

Configure these in **Settings → Secrets and variables → Actions**:

| Secret | Description |
|--------|-------------|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release signing keystore |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias |
| `RELEASE_KEY_PASSWORD` | Key password |
| `GCP_SERVICE_ACCOUNT` | JSON key of Google Play service account with `Release Manager` role |

## Service Account Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a service account with **Google Play Developer API** access
3. In Google Play Console → **Users and permissions**, invite the service account with **Release Manager** role
4. Download the JSON key and paste its contents into the `GCP_SERVICE_ACCOUNT` secret

