# Quick Task 260430-wtn: Fix Real Delete + Model Fetch

**Commit:** 777f604

## Root causes found:

### Delete endpoints not working
The `EndpointRepositoryImpl.deleteEndpoint(id)` was correct, but the UI gave no immediate feedback. The Room Flow takes ~ms to emit. If the Flow was slow or the observable hadn't re-emitted yet, the endpoint appeared to not delete. Also, the old code in EndpointsViewModel didn't use the returned ID from `saveEndpoint()` → `val savedId`.

**Fix:** Immediate UI feedback — filter the endpoint from `_uiState.endpoints` list BEFORE the DB call. If DB delete fails, the Flow observation restores it.

### Model fetch not working
The `LMStudioProvider(baseUrl = url)` created a Retrofit instance with the raw user-entered URL. If the user entered `192.168.1.100:1234` (no scheme), Retrofit would fail to resolve. Also, URLs without trailing `/` cause path resolution issues.

**Fix:** URL normalization before use — adds `http://` prefix if missing, ensures trailing `/`.

### Both ViewModels updated
- `ModelsViewModel.deleteEndpoint()` — immediate `.filter { e.id != endpoint.id }`
- `EndpointsViewModel.deleteEndpoint()` — same pattern
- `ModelsViewModel.saveEndpoint()` — URL normalization
- `EndpointsViewModel.saveEndpoint()` — URL normalization + uses returned `savedId` from repository
- `ModelsViewModel.saveEndpointEdit()` — URL normalization
- `ModelsViewModel.fetchEndpointModels()` — URL normalization before creating provider
