package com.warped.data.repository

import com.warped.data.local.db.dao.RemoteEndpointDao
import com.warped.data.local.db.entity.toDomain
import com.warped.data.local.db.entity.toEntity
import com.warped.data.local.security.ApiKeyStore
import com.warped.domain.model.Endpoint
import com.warped.domain.repository.EndpointRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EndpointRepositoryImpl @Inject constructor(
    private val endpointDao: RemoteEndpointDao,
    private val apiKeyStore: ApiKeyStore
) : EndpointRepository {

    override fun observeEndpoints(): Flow<List<Endpoint>> =
        endpointDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getActive(): Endpoint? =
        endpointDao.getActive()?.toDomain()

    override suspend fun saveEndpoint(endpoint: Endpoint): Long {
        val existing = if (endpoint.id != 0L) endpointDao.getById(endpoint.id) else null
        val keyRef = existing?.encryptedApiKeyRef ?: "api_key_${endpoint.id}"
        val id = endpointDao.upsert(endpoint.toEntity(encryptedApiKeyRef = keyRef))
        if (endpoint.isActive) {
            endpointDao.activate(id)
        }
        return id
    }

    override suspend fun deleteEndpoint(endpointId: Long) {
        if (endpointId == 0L) throw IllegalArgumentException("Invalid endpoint ID")
        apiKeyStore.deleteKey(endpointId)
        endpointDao.deleteById(endpointId)
    }

    override suspend fun activateEndpoint(endpointId: Long) {
        endpointDao.deactivateAll()
        endpointDao.activate(endpointId)
    }
}
