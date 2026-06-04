package com.forgeservice.app.data.remote

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.*

data class PartCreate(
    val name: String,
    val part_number: String? = null,
    val unit: String = "шт",
    val quantity: Int = 0,
    val min_quantity: Int = 2,
    val purchase_price: Double = 0.0,
    val sell_price: Double = 0.0,
    val supplier: String? = null,
    val notes: String? = null
)

/**
 * Retrofit API interface for ForgeService backend.
 * Matches the FastAPI endpoints.
 */
interface ForgeServiceApi {

    // ==================== DASHBOARD ====================
    @GET("api/dashboard/stats")
    suspend fun getDashboardStats(): DashboardStatsDto

    // ==================== WORK ORDERS ====================
    @GET("api/work-orders")
    suspend fun getWorkOrders(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 100
    ): List<WorkOrderDto>

    @GET("api/work-orders/{id}")
    suspend fun getWorkOrderDetail(@Path("id") id: Int): WorkOrderDetailDto

    @POST("api/work-orders")
    suspend fun createWorkOrder(@Body request: CreateWorkOrderRequest): WorkOrderDto

    @PATCH("api/work-orders/{id}")
    suspend fun updateWorkOrder(
        @Path("id") id: Int,
        @Body request: UpdateWorkOrderRequest
    ): WorkOrderDto

    @POST("api/work-orders/{id}/advance-status")
    suspend fun advanceWorkOrderStatus(@Path("id") id: Int): WorkOrderDto

    // Add work item
    @POST("api/work-orders/{id}/items")
    suspend fun addWorkItem(
        @Path("id") workOrderId: Int,
        @Body item: AddWorkItemRequest
    ): WorkOrderItemDto

    // Add part usage
    @POST("api/work-orders/{id}/parts")
    suspend fun addPartUsage(
        @Path("id") workOrderId: Int,
        @Body usage: AddPartUsageRequest
    ): WorkOrderPartUsageDto

    // ==================== PARTS / WAREHOUSE ====================
    @GET("api/parts")
    suspend fun getParts(
        @Query("q") query: String? = null,
        @Query("low_stock_only") lowStockOnly: Boolean = false
    ): List<PartDto>

    @POST("api/parts/{id}/add-stock")
    suspend fun addStock(
        @Path("id") partId: Int,
        @Query("quantity") quantity: Int
    ): Map<String, Any>

    @POST("api/parts/{id}/subtract-stock")
    suspend fun subtractStock(
        @Path("id") partId: Int,
        @Query("quantity") quantity: Int
    ): Map<String, Any>

    @POST("api/parts")
    suspend fun createPart(@Body part: PartCreate): PartDto

    // ==================== PHOTOS ====================
    @Multipart
    @POST("api/photos/upload")
    suspend fun uploadPhoto(
        @Part("work_order_id") workOrderId: RequestBody,
        @Part("category") category: RequestBody,
        @Part("description") description: RequestBody?,
        @Part file: MultipartBody.Part
    ): PhotoDto

    @GET("api/photos/work-order/{workOrderId}")
    suspend fun getPhotosForWorkOrder(@Path("workOrderId") workOrderId: Int): List<PhotoDto>

    @DELETE("api/photos/{photoId}")
    suspend fun deletePhoto(@Path("photoId") photoId: Int)

    // ==================== BAYS ====================
    @GET("api/bays")
    suspend fun getBays(): List<BayDto>

    // ==================== VEHICLES (for reference) ====================
    @GET("api/vehicles")
    suspend fun getVehicles(@Query("q") query: String? = null): List<VehicleDto>

    // ==================== CLIENTS ====================
    @GET("api/clients")
    suspend fun getClients(@Query("q") query: String? = null): List<ClientDto>
}

// ==================== DTOs (matching backend responses) ====================

data class DashboardStatsDto(
    val active_orders: Int,
    val today_completed: Int,
    val revenue_month: Double,
    val avg_check: Double,
    val low_stock_parts: Int,
    val bays_occupied: Int,
    val bays_total: Int
)

data class WorkOrderDto(
    val id: Int,
    val vehicle_id: Int,
    val status: String,
    val intake_mileage: Int?,
    val intake_notes: String?,
    val labor_cost: Double,
    val parts_cost: Double,
    val total_cost: Double,
    val discount: Double,
    val notes: String?,
    val assigned_bay_id: Int?,
    val created_at: String,
    val updated_at: String,
    val completed_at: String?
)

data class WorkOrderDetailDto(
    val id: Int,
    val vehicle_id: Int,
    val status: String,
    val intake_mileage: Int?,
    val intake_notes: String?,
    val labor_cost: Double,
    val parts_cost: Double,
    val total_cost: Double,
    val discount: Double,
    val notes: String?,
    val assigned_bay_id: Int?,
    val created_at: String,
    val updated_at: String,
    val completed_at: String?,
    val vehicle: VehicleWithClientDto?,
    val items: List<WorkOrderItemDto> = emptyList(),
    val parts_usages: List<WorkOrderPartUsageDto> = emptyList(),
    val photos: List<PhotoDto> = emptyList()
)

data class VehicleWithClientDto(
    val id: Int,
    val make: String,
    val model: String,
    val year: Int,
    val license_plate: String,
    val client: ClientDto?
)

data class ClientDto(
    val id: Int,
    val full_name: String,
    val phone: String
)

data class WorkOrderItemDto(
    val id: Int,
    val work_order_id: Int,
    val description: String,
    val hours: Double,
    val price_per_hour: Double,
    val total_price: Double,
    val status: String,
    val employee_id: Int?
)

data class WorkOrderPartUsageDto(
    val id: Int,
    val work_order_id: Int,
    val part_id: Int,
    val quantity: Int,
    val price_at_time: Double,
    val part: PartDto?
)

data class PhotoDto(
    val id: Int,
    val work_order_id: Int,
    val file_path: String,
    val category: String,
    val description: String?,
    val taken_at: String
)

data class PartDto(
    val id: Int,
    val name: String,
    val part_number: String?,
    val unit: String,
    val quantity: Int,
    val min_quantity: Int,
    val sell_price: Double,
    val purchase_price: Double = 0.0,
    val supplier: String? = null,
    val notes: String? = null,
    val is_low_stock: Boolean? = null
)

data class BayDto(
    val id: Int,
    val name: String,
    val description: String?,
    val is_active: Boolean,
    val current_work_order_id: Int?
)

data class VehicleDto(
    val id: Int,
    val make: String,
    val model: String,
    val year: Int,
    val license_plate: String,
    val vin: String? = null,
    val current_mileage: Int,
    val client_id: Int,
    val photo_path: String? = null
)

// Request DTOs
data class CreateWorkOrderRequest(
    val vehicle_id: Int,
    val intake_mileage: Int? = null,
    val intake_notes: String? = null,
    val notes: String? = null
)

data class UpdateWorkOrderRequest(
    val status: String? = null,
    val intake_notes: String? = null,
    val notes: String? = null,
    val assigned_bay_id: Int? = null
)

data class AddWorkItemRequest(
    val description: String,
    val hours: Double,
    val price_per_hour: Double,
    val employee_id: Int? = null
)

data class AddPartUsageRequest(
    val part_id: Int,
    val quantity: Int
)