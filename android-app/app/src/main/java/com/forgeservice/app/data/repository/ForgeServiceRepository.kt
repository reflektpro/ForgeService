package com.forgeservice.app.data.repository

import com.forgeservice.app.data.remote.ApiClient
import com.forgeservice.app.data.remote.DashboardStatsDto
import com.forgeservice.app.data.remote.WorkOrderDetailDto
import com.forgeservice.app.data.remote.WorkOrderDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Simple repository that talks to the backend.
 * Later we can add Room caching here for offline mode.
 */
class ForgeServiceRepository {

    private val api = ApiClient.api

    suspend fun getDashboardStats(): DashboardStatsDto = withContext(Dispatchers.IO) {
        api.getDashboardStats()
    }

    suspend fun getWorkOrders(status: String? = null, limit: Int = 50): List<WorkOrderDto> = withContext(Dispatchers.IO) {
        api.getWorkOrders(status = status, limit = limit)
    }

    suspend fun getWorkOrderDetail(id: Int): WorkOrderDetailDto = withContext(Dispatchers.IO) {
        api.getWorkOrderDetail(id)
    }

    suspend fun advanceStatus(workOrderId: Int): WorkOrderDto = withContext(Dispatchers.IO) {
        api.advanceWorkOrderStatus(workOrderId)
    }

    suspend fun addWorkItem(workOrderId: Int, request: com.forgeservice.app.data.remote.AddWorkItemRequest) = withContext(Dispatchers.IO) {
        api.addWorkItem(workOrderId, request)
    }

    suspend fun uploadPhoto(
        workOrderId: Int,
        category: String,
        description: String?,
        filePart: MultipartBody.Part
    ): com.forgeservice.app.data.remote.PhotoDto = withContext(Dispatchers.IO) {
        val idBody = workOrderId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val catBody = category.toRequestBody("text/plain".toMediaTypeOrNull())
        val descBody = description?.toRequestBody("text/plain".toMediaTypeOrNull())
        api.uploadPhoto(idBody, catBody, descBody, filePart)
    }

    suspend fun deletePhoto(photoId: Int) = withContext(Dispatchers.IO) {
        api.deletePhoto(photoId)
    }

    suspend fun createPart(part: com.forgeservice.app.data.remote.PartCreate) = withContext(Dispatchers.IO) {
        api.createPart(part)
    }

    suspend fun getParts(
        query: String? = null,
        lowStockOnly: Boolean = false
    ): List<com.forgeservice.app.data.remote.PartDto> = withContext(Dispatchers.IO) {
        api.getParts(query = query, lowStockOnly = lowStockOnly)
    }

    suspend fun addStock(partId: Int, quantity: Int): Map<String, Any> = withContext(Dispatchers.IO) {
        api.addStock(partId, quantity)
    }

    suspend fun subtractStock(partId: Int, quantity: Int): Map<String, Any> = withContext(Dispatchers.IO) {
        api.subtractStock(partId, quantity)
    }

    suspend fun getBays(): List<com.forgeservice.app.data.remote.BayDto> = withContext(Dispatchers.IO) {
        api.getBays()
    }

    suspend fun getVehicles(query: String? = null): List<com.forgeservice.app.data.remote.VehicleDto> = withContext(Dispatchers.IO) {
        api.getVehicles(query = query)
    }

    suspend fun getClients(query: String? = null): List<com.forgeservice.app.data.remote.ClientDto> = withContext(Dispatchers.IO) {
        api.getClients(query = query)
    }

    // TODO: add part usage, create work order, etc.
}