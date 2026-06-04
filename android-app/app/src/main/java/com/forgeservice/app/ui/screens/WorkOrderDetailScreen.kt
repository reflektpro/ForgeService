package com.forgeservice.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.forgeservice.app.data.remote.AddWorkItemRequest
import com.forgeservice.app.data.remote.ApiClient
import com.forgeservice.app.data.remote.WorkOrderDetailDto
import com.forgeservice.app.data.remote.WorkOrderItemDto
import com.forgeservice.app.data.repository.ForgeServiceRepository
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Самый важный экран проекта — детальный просмотр Заказ-наряда.
 *
 * Функционал:
 * - Инфо по авто и клиенту
 * - Список работ + добавление новой работы (диалог)
 * - Списанные запчасти
 * - Фотофиксация с категориями и удалением
 * - Смена статуса с advance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderDetailScreen(
    workOrderId: Int,
    onBack: () -> Unit
) {
    val repository = remember { ForgeServiceRepository() }
    var detail by remember { mutableStateOf<WorkOrderDetailDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isAdvancing by remember { mutableStateOf(false) }

    // For "add work item" dialog
    var showAddWorkDialog by remember { mutableStateOf(false) }
    var newWorkDesc by remember { mutableStateOf("") }
    var newWorkHours by remember { mutableStateOf("1.0") }
    var newWorkPrice by remember { mutableStateOf("2500") }

    // For photo capture and upload
    var isUploadingPhoto by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var tempPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedPhotoCategory by remember { mutableStateOf("intake") }

    // WebSocket manager for real-time (reused instance)
    val wsManager = remember { com.forgeservice.app.data.remote.WebSocketManager() }

    val tabTitles = listOf("Инфо", "Работы", "Запчасти", "Фото")
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    // Helper to create temp uri for camera
    fun createTempPhotoUri(): android.net.Uri {
        val photoFile = File(context.getExternalFilesDir("Pictures"), "forge_photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    // Helper to copy uri content to file for upload
    fun getFileFromUri(context: android.content.Context, uri: android.net.Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    fun loadDetail() {
        scope.launch {
            try {
                isLoading = true
                error = null
                detail = repository.getWorkOrderDetail(workOrderId)
            } catch (e: Exception) {
                error = e.localizedMessage
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(workOrderId) {
        loadDetail()

        // Connect to WebSocket for real-time updates on this work order
        // When server broadcasts (e.g. another client adds item or changes status), we reload
        try {
            wsManager.connectToWorkOrder(
                baseUrl = ApiClient.currentBaseUrl,
                workOrderId = workOrderId
            ) { _ ->
                // Any message means something changed - reload detail
                scope.launch {
                    try {
                        detail = repository.getWorkOrderDetail(workOrderId)
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
    }

    DisposableEffect(workOrderId) {
        onDispose {
            try { wsManager.disconnect() } catch (_: Exception) { }
        }
    }

    // Camera launcher for taking photo
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            scope.launch {
                isUploadingPhoto = true
                try {
                    val file = getFileFromUri(context, tempPhotoUri!!)
                    if (file != null && file.exists()) {
                        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                        repository.uploadPhoto(
                            workOrderId = workOrderId,
                            category = selectedPhotoCategory,
                            description = "Фото сделано в приложении",
                            filePart = body
                        )
                        // refresh
                        detail = repository.getWorkOrderDetail(workOrderId)
                    } else {
                        error = "Не удалось получить файл фото"
                    }
                } catch (e: Exception) {
                    error = "Ошибка загрузки фото: ${e.localizedMessage}"
                } finally {
                    isUploadingPhoto = false
                    tempPhotoUri = null
                }
            }
        } else {
            tempPhotoUri = null
        }
    }

    // Dialog for adding a new work item
    if (showAddWorkDialog) {
        AlertDialog(
            onDismissRequest = { showAddWorkDialog = false },
            title = { Text("Добавить работу") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newWorkDesc,
                        onValueChange = { newWorkDesc = it },
                        label = { Text("Описание работы") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(
                            value = newWorkHours,
                            onValueChange = { newWorkHours = it },
                            label = { Text("Нормочасы") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = newWorkPrice,
                            onValueChange = { newWorkPrice = it },
                            label = { Text("Цена за час") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val hours = newWorkHours.toDoubleOrNull() ?: 1.0
                                val price = newWorkPrice.toDoubleOrNull() ?: 0.0
                                repository.addWorkItem(
                                    workOrderId,
                                    AddWorkItemRequest(
                                        description = newWorkDesc.ifBlank { "Новая работа" },
                                        hours = hours,
                                        price_per_hour = price
                                    )
                                )
                                detail = repository.getWorkOrderDetail(workOrderId)
                                newWorkDesc = ""
                                newWorkHours = "1.0"
                                newWorkPrice = "2500"
                                showAddWorkDialog = false
                            } catch (e: Exception) {
                                error = "Не удалось добавить работу: ${e.message}"
                            }
                        }
                    }
                ) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showAddWorkDialog = false }) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заказ-наряд #$workOrderId — ${tabTitles[selectedTab]}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                error != null -> {
                    Text("Ошибка: $error", Modifier.padding(16.dp))
                }
                detail != null -> {
                    val wo = detail!!

                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        TabRow(selectedTabIndex = selectedTab) {
                            tabTitles.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(title) }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        when (selectedTab) {
                            0 -> {
                                // Инфо tab
                                Card {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("Автомобиль", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            "${wo.vehicle?.make} ${wo.vehicle?.model} ${wo.vehicle?.year} • ${wo.vehicle?.license_plate}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text("Клиент", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            "${wo.vehicle?.client?.full_name} • ${wo.vehicle?.client?.phone}",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        if (!wo.intake_notes.isNullOrBlank()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text("Приёмка", style = MaterialTheme.typography.labelSmall)
                                            Text(wo.intake_notes, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // Status + quick action (moved to info for demo)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Card {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("Статус", style = MaterialTheme.typography.labelSmall)
                                            Text(
                                                wo.status.replace('_', ' ').uppercase(),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isAdvancing = true
                                                try {
                                                    repository.advanceStatus(workOrderId)
                                                    detail = repository.getWorkOrderDetail(workOrderId)
                                                } catch (e: Exception) {
                                                    error = "Не удалось сменить статус: ${e.message}"
                                                } finally {
                                                    isAdvancing = false
                                                }
                                            }
                                        },
                                        enabled = !isAdvancing && wo.status != "completed"
                                    ) {
                                        if (isAdvancing) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("Следующий этап →")
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // Financials
                                Card {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("Финансы", fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Работы: ${wo.labor_cost.toInt()} ₽")
                                        Text("Запчасти: ${wo.parts_cost.toInt()} ₽")
                                        Text(
                                            "ИТОГО к оплате: ${wo.total_cost.toInt()} ₽",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }

                            1 -> {
                                // Работы tab
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Работы", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                    IconButton(onClick = { showAddWorkDialog = true }) {
                                        Icon(Icons.Default.Add, contentDescription = "Добавить работу")
                                    }
                                }

                                if (wo.items.isNotEmpty()) {
                                    wo.items.forEach { item ->
                                        WorkItemCard(item)
                                    }
                                } else {
                                    Text("Работ пока нет", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            2 -> {
                                // Запчасти tab
                                Text("Списанные запчасти", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                if (wo.parts_usages.isNotEmpty()) {
                                    wo.parts_usages.forEach { usage ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(usage.part?.name ?: "Запчасть #${usage.part_id}")
                                                Text("×${usage.quantity}  •  ${usage.price_at_time.toInt()} ₽")
                                            }
                                        }
                                    }
                                } else {
                                    Text("Запчасти не списаны", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            3 -> {
                                // Фото tab + camera, categories, delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Фотофиксация", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                    Button(
                                        onClick = {
                                            val uri = createTempPhotoUri()
                                            tempPhotoUri = uri
                                            takePhotoLauncher.launch(uri)
                                        },
                                        enabled = !isUploadingPhoto
                                    ) {
                                        if (isUploadingPhoto) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Снять фото")
                                        }
                                    }
                                }

                                // Simple category selector for the photo
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("intake" to "Приёмка", "defect" to "Дефект", "during" to "В процессе", "after" to "После").forEach { (cat, label) ->
                                        FilterChip(
                                            selected = selectedPhotoCategory == cat,
                                            onClick = { selectedPhotoCategory = cat },
                                            label = { Text(label) }
                                        )
                                    }
                                }

                                if (wo.photos.isNotEmpty()) {
                                    wo.photos.forEach { photo ->
                                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            Column(Modifier.padding(8.dp)) {
                                                AsyncImage(
                                                    model = "${ApiClient.currentBaseUrl}/photos/${photo.file_path}",
                                                    contentDescription = photo.description,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(120.dp)
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("📷 ${photo.category} • ${photo.taken_at.take(16)}")
                                                    IconButton(onClick = {
                                                        scope.launch {
                                                            try {
                                                                repository.deletePhoto(photo.id)
                                                                detail = repository.getWorkOrderDetail(workOrderId)
                                                            } catch (e: Exception) {
                                                                error = "Не удалось удалить фото: ${e.message}"
                                                            }
                                                        }
                                                    }) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Удалить фото", tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                                if (!photo.description.isNullOrBlank()) {
                                                    Text(photo.description, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        "Фото пока нет. Используй кнопку выше чтобы снять и загрузить.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                    }
                }
            }
        }
    }
}

@Composable
private fun WorkItemCard(item: WorkOrderItemDto) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(item.description, fontWeight = FontWeight.Medium)
            Text(
                "${item.total_price.toInt()} ₽  •  ${item.hours} н/ч" +
                        if (item.status == "done") "  ✓ Выполнено" else "",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}