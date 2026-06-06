package com.warped.domain.repository

import com.warped.domain.model.Endpoint
import kotlinx.coroutines.flow.Flow

interface EndpointRepository {
    fun observeEndpoints(): Flow<List<Endpoint>>
    suspend fun getActive(): Endpoint?
    suspend fun getById(endpointId: Long): Endpoint?
    suspend fun saveEndpoint(endpoint: Endpoint): Long
    suspend fun deleteEndpoint(endpointId: Long)
    suspend fun activateEndpoint(endpointId: Long)
}
