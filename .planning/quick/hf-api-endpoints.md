# Hugging Face Hub API — Model Browsing & Download Endpoints

Source: `https://huggingface.co/.well-known/openapi.md` (parsed from OpenAPI 3.1.0 spec)
Base URL: `https://huggingface.co`
Auth: `Authorization: Bearer <token>` (optional; required only for gated models)

---

## 1. Model Listing / Search

> **NOT in this OpenAPI spec** — these are well-documented in the HF Hub API docs.

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/models` | List/search models with filters: `search`, `author`, `filter` (by task/library), `sort` (downloads/likes/lastModified), `direction`, `limit`, `full` |
| `GET` | `/api/models/{namespace}/{repo}` | Get model metadata including **`siblings`** field (array of `{rfilename, size, blobId}` — the file list). Also returns `pipeline_tag`, `tags`, `config`, `cardData`, `downloads`, `likes`, `lastModified`, `private`, `gated` |

### Key response fields for DTO (`GET /api/models`)
```
items[] → id, modelId (author/name), pipeline_tag, tags[], downloads, likes, lastModified, private, gated, cardData
```

### Key response fields for DTO (`GET /api/models/{id}`)
```
id, modelId, pipeline_tag, tags[], siblings[] → {rfilename, size, blobId}, safetensors{parameters, total}, config, cardData, downloads, likes, lastModified, private, gated
```

---

## 2. File Listing (Tree)

| Method | Path | Summary |
|--------|------|---------|
| `GET` | `/api/models/{namespace}/{repo}/tree/{rev}/{path}` | List folder content with pagination |

**Params:**
| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| namespace | path | yes | string | HF user/org |
| repo | path | yes | string | Model repo name |
| rev | path | yes | string | Branch/tag/commit (e.g. `main`) |
| path | path | yes | string | Folder path (use `""` for root) |
| expand | query | no | — | Expand entries |
| recursive | query | no | — | Recursive listing |
| limit | query | no | integer | Page size |
| cursor | query | no | string | Pagination cursor |

**Response 200** — `array` of:
| Field | Type | DTO Target |
|-------|------|------------|
| `type` | `"file" \| "directory" \| "unknown"` | `ModelFile.isDirectory` |
| `oid` | string | `ModelFile.oid` |
| `size` | integer | `ModelFile.sizeBytes` |
| `lfs.oid` | string | `ModelFile.lfsOid` |
| `lfs.size` | integer | `ModelFile.lfsSize` |
| `path` | string | `ModelFile.path` (relative) |

---

## 3. File Info (Bulk)

| Method | Path | Summary |
|--------|------|---------|
| `POST` | `/api/models/{namespace}/{repo}/paths-info/{rev}` | Get info for specific paths (up to 2000) |

**Request Body:** `{ "paths": ["file1.gguf", "file2.gguf"] }`

**Params:** `namespace`, `repo`, `rev` (path, required)

**Response 200** — `array` of:
| Field | Type | DTO Target |
|-------|------|------------|
| `type` | `"file" \| "directory"` | `ModelFile.isDirectory` |
| `oid` | string | `ModelFile.oid` |
| `size` | number | `ModelFile.sizeBytes` |
| `path` | string | `ModelFile.path` |
| `lfs.pointerSize` | number | `ModelFile.lfsSize` |
| `xetHash` | string | XET hash (if applicable) |
| `lastCommit.id` | string | — |
| `lastCommit.title` | string | — |

---

## 4. File Resolve / Download

> The canonical HF download URL pattern. Returns a **302/307 redirect** to the actual CDN URL (CloudFront/LFS/XET).

| Method | Path | Notes |
|--------|------|-------|
| `GET` | `/{namespace}/{repo}/resolve/{rev}/{path}` | **Primary** — simplest URL, works for all repo types |
| `GET` | `/api/resolve-cache/models/{namespace}/{repo}/{rev}/{path}` | Alternate (API subdomain) — same behavior |

**Params:** `namespace`, `repo`, `rev`, `path` (all path, required)
**Headers:** `Range: bytes=0-` (for resume), `Accept: application/vnd.xet-fileinfo+json` (for XET metadata only)

**Responses:**
| Code | Meaning |
|------|---------|
| 200 | XET file metadata (JSON) — only if `Accept: application/vnd.xet-fileinfo+json` |
| 302 | Redirect to CloudFront CDN → **follow redirect to download** |
| 304 | Not modified (ETag match) |
| 307 | Redirect to XET endpoint |

**200 response (XET info mode):**
| Field | Type | DTO Target |
|-------|------|------------|
| `hash` | string | `DownloadInfo.hash` |
| `refreshUrl` | string | XET auth URL |
| `reconstructionUrl` | string | XET reconstruction URL |
| `etag` | string | `DownloadInfo.etag` — cache/conditional request |
| `size` | number | `DownloadInfo.sizeBytes` |

> **For actual download:** Set `Accept: application/octet-stream` (or omit), follow the 302 redirect, stream the response body. Use `Range` header for pause/resume.

---

## 5. LFS File List

| Method | Path | Summary |
|--------|------|---------|
| `GET` | `/api/models/{namespace}/{repo}/lfs-files` | List XET/LFS files for a repo |

**Params:** `namespace`, `repo` (path, required), `cursor` (query), `limit` (query, int), `xet` (query)

**Response 200** — `array` of LFS file objects (includes `pusher`, `oid`, `size`, `pointerSize`, etc.)

> Use case: Filter to only LFS-stored files (GGUF models are always LFS). Avoids paginating through entire tree.

---

## 6. References (Branches & Tags)

| Method | Path | Summary |
|--------|------|---------|
| `GET` | `/api/models/{namespace}/{repo}/refs` | List branches, tags, converts, PRs |

**Params:** `namespace`, `repo` (path, required), `include_prs` (query, optional)

**Response 200:**
| Field | Type | DTO Target |
|-------|------|------------|
| `branches[]` | `[{name, ref, targetCommit}]` | — |
| `tags[]` | `[{name, ref, targetCommit}]` | — |
| `converts[]` | array | — |
| `pullRequests[]` | array | — |

> Use case: Resolve the default branch (`main`) or let users pick a specific revision.

---

## 7. Model Tags

| Method | Path | Summary |
|--------|------|---------|
| `GET` | `/api/models-tags-by-type` | Get all model tags grouped by type |

**Params:** `type` (query, optional) — restrict to one: `pipeline_tag`, `library`, `language`, `license`, etc.

**Response 200:** Object keyed by tag type, values are `[{id, label, type}]`

> Use case: Build filter UI (task type, library, language, license dropdowns).

---

## DTO Mapping Summary

| HF Field | Warped DTO | Source Endpoint |
|----------|------------|-----------------|
| `id` (e.g. `meta-llama/Llama-3-8B`) | `HuggingFaceModel.id` | `GET /api/models`, `GET /api/models/{id}` |
| `modelId` | `HuggingFaceModel.modelId` | ditto |
| `pipeline_tag` | `HuggingFaceModel.pipelineTag` | ditto |
| `tags[]` | `HuggingFaceModel.tags[]` | ditto |
| `downloads` | `HuggingFaceModel.downloads` | ditto |
| `likes` | `HuggingFaceModel.likes` | ditto |
| `lastModified` | `HuggingFaceModel.lastModified` | ditto |
| `private` / `gated` | `HuggingFaceModel.isPrivate` / `isGated` | ditto |
| `siblings[].rfilename` | `ModelFile.path` | `GET /api/models/{id}` |
| `siblings[].size` | `ModelFile.sizeBytes` | ditto |
| `siblings[].blobId` | `ModelFile.blobId` | ditto |
| `tree[].type` | `ModelFile.isDirectory` | `GET .../tree/{rev}/{path}` |
| `tree[].oid` | `ModelFile.oid` | ditto |
| `tree[].size` | `ModelFile.sizeBytes` | ditto |
| `tree[].lfs.oid` | `ModelFile.lfsOid` | ditto |
| `resolve.{hash, size, etag}` | `DownloadInfo.{hash, sizeBytes, etag}` | `GET .../resolve/{rev}/{path}` |

## Download Flow

```
1. GET /api/models/{namespace}/{repo}
   → extract siblings[], filter by .rfilename ending in .gguf
   → pick desired file from siblings[*].rfilename

2. GET /{namespace}/{repo}/resolve/main/{filename.gguf}
   → OkHttp follows 302 redirect to CDN
   → stream response body to disk with progress tracking
   → use Range header for pause/resume
   → Authorization: Bearer <token> for gated models
```
