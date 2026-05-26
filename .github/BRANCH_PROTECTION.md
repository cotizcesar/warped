# Branch Protection Setup

This project uses **track-based branches** that map directly to Google Play Store release tracks.

## Branches

| Branch      | Play Store Track | Purpose                        |
|-------------|------------------|--------------------------------|
| `beta`      | Beta             | Pre-release testing            |
| `production`| Production       | Public release                 |

## Required GitHub Repository Settings

You must manually configure branch protection rules in the GitHub UI. Go to:

**Settings → Branches → Add rule**

Apply the following to `beta` and `production`:

- [x] **Require a pull request before merging**
  - [x] Require approvals: `1`
- [x] **Require status checks to pass before merging**
  - Search for and select: `build`
- [x] **Require branches to be up to date before merging**
- [x] **Block force pushes**
- [x] **Require linear history** (optional, recommended)

## Release Flow

1. Open a PR from `beta` → `production`
2. Merge → CI runs → Release workflow uploads to **Production** track
3. Or push to `beta` → CI runs → uploads to **Beta** track

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
