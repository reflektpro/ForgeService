package com.forgeservice.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.forgeservice.app.data.remote.BayDto
import com.forgeservice.app.data.remote.DashboardStatsDto
import com.forgeservice.app.data.remote.PartDto
import com.forgeservice.app.data.remote.VehicleDto
import com.forgeservice.app.data.remote.WorkOrderDto
import com.forgeservice.app.data.repository.ForgeServiceRepository
import com.forgeservice.app.ui.theme.ForgeServiceTheme
import kotlinx.coroutines.launch

/**
 * ОПЕРАТИВНЫЙ ДАШБОРД (тактический).
 * Отличается от Аналитики: фокус на "что происходит ПРЯМО СЕЙЧАС".
 * - Состояние боксов (занят/свободен)
 * - Активные заказ-наряды с чипами статусов
 * - Быстрый перевод на следующий этап (advance-status)
 * - Срочные алерты по складу
 * - Клик по ЗН -> деталка (если передан navController)
 *
 * Аналитика = стратегическая: метрики за период, топы, PDF-отчёт.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController? = null) {
    ForgeServiceTheme {
        val repository = remember { ForgeServiceRepository() }

        var stats by remember { mutableStateOf<DashboardStatsDto?>(null) }
        var bays by remember { mutableStateOf<List<BayDto>>(emptyList()) }
        var vehicles by remember { mutableStateOf<List<VehicleDto>>(emptyList()) }
        var activeOrders by remember { mutableStateOf<List<WorkOrderDto>>(emptyList()) }
        var lowStockParts by remember { mutableStateOf<List<PartDto>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        val scope = rememberCoroutineScope()

        fun loadDashboard() {
            scope.launch {
                try {
                    isLoading = true
                    error = null
                    stats = repository.getDashboardStats()
                    bays = repository.getBays()
                    vehicles = repository.getVehicles()
                    val allOrders = repository.getWorkOrders()
                    activeOrders = allOrders.filter { wo ->
                        wo.status !in listOf("completed", "cancelled")
                    }.sortedByDescending { it.updated_at }
                    lowStockParts = repository.getParts(lowStockOnly = true)
                } catch (e: Exception) {
                    error = "Не удалось загрузить оперативные данные: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }

        fun advanceOrder(orderId: Int) {
            scope.launch {
                try {
                    repository.advanceStatus(orderId)
                    loadDashboard()
                } catch (e: Exception) {
                    error = "Не удалось сменить статус: ${e.message}"
                }
            }
        }

        LaunchedEffect(Unit) {
            loadDashboard()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header — operational tone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "ForgeService",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Оперативный дашборд • Прямо сейчас",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { loadDashboard() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column // early exit in loading
            }

            if (error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        error!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            val s = stats

            // Оперативные KPI (короткие, актуальные)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallMetricCard(
                    label = "Активных ЗН",
                    value = s?.active_orders?.toString() ?: "—",
                    modifier = Modifier.weight(1f)
                )
                SmallMetricCard(
                    label = "Свободно боксов",
                    value = if (s != null) "${s.bays_total - s.bays_occupied}/${s.bays_total}" else "—",
                    modifier = Modifier.weight(1f)
                )
                SmallMetricCard(
                    label = "Низкий остаток",
                    value = s?.low_stock_parts?.toString() ?: "—",
                    modifier = Modifier.weight(1f),
                    highlight = (s?.low_stock_parts ?: 0) > 0
                )
            }

            Spacer(Modifier.height(20.dp))

            // === БОКСЫ — визуальная загрузка (главное отличие от аналитики) ===
            Text("Состояние боксов", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (bays.isEmpty()) {
                Text("Боксы не загружены", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val occupiedBayIds = activeOrders.mapNotNull { it.assigned_bay_id }.toSet()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bays.forEach { bay ->
                        val orderInBay = activeOrders.find { it.assigned_bay_id == bay.id }
                        val occupied = (bay.current_work_order_id != null) || (bay.id in occupiedBayIds)
                        val woIdForLabel = bay.current_work_order_id ?: orderInBay?.id
                        val statusText = if (occupied && woIdForLabel != null) "ЗАНЯТ • ЗН-$woIdForLabel" else if (occupied) "ЗАНЯТ" else "СВОБОДЕН"

                        // Theme-friendly dark colors (no more light "white" backgrounds)
                        val accent = if (occupied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Colored indicator dot — clearly shows busy vs free on dark theme
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        bay.name,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (occupied) accent else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (bay.description?.isNotBlank() == true) {
                                        Text(bay.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = accent.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        statusText,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // === АКТИВНЫЕ ЗАКАЗ-НАРЯДЫ + БЫСТРЫЕ ДЕЙСТВИЯ ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Активные заказ-наряды", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text("${activeOrders.size} в работе", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))

            if (activeOrders.isEmpty()) {
                Card {
                    Text(
                        "Нет активных заказов. Отлично!",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    activeOrders.take(8).forEach { wo ->
                        val vehicle = vehicles.find { it.id == wo.vehicle_id }
                        val vehicleLabel = if (vehicle != null) {
                            "${vehicle.make} ${vehicle.model} • ${vehicle.license_plate}"
                        } else {
                            "Авто #${wo.vehicle_id}"
                        }

                        val bayName = wo.assigned_bay_id?.let { bid ->
                            bays.find { it.id == bid }?.name ?: "Бокс #$bid"
                        }

                        val (statusLabel, statusColor) = getStatusVisual(wo.status)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                navController?.navigate("work_order/${wo.id}")
                            }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "ЗН-${wo.id}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = statusColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            statusLabel,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                Text(vehicleLabel, fontWeight = FontWeight.Medium)
                                if (bayName != null) {
                                    Text("Бокс: $bayName", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Итого: ${wo.total_cost.toInt()} ₽", style = MaterialTheme.typography.bodySmall)

                                Spacer(Modifier.height(10.dp))

                                // Quick action — главное отличие дашборда
                                val isFinal = wo.status in listOf("ready", "completed", "cancelled")
                                if (!isFinal) {
                                    Button(
                                        onClick = { advanceOrder(wo.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
                                    ) {
                                        Text("Перевести на следующий этап →")
                                    }
                                } else {
                                    Text(
                                        "Готов к выдаче / завершён",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (activeOrders.size > 8) {
                        Text("... и ещё ${activeOrders.size - 8}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // === СРОЧНЫЕ ПРЕДУПРЕЖДЕНИЯ СКЛАД (оперативно, не аналитика) ===
            if (lowStockParts.isNotEmpty()) {
                Text("Срочные запчасти (заканчиваются)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                lowStockParts.take(4).forEach { part ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F1F))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(part.name, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                if (part.part_number != null) Text(part.part_number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "${part.quantity} / мин ${part.min_quantity}",
                                color = Color(0xFFE57373),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (lowStockParts.size > 4) {
                    Text("+ ещё ${lowStockParts.size - 4}", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Перейдите во вкладку «Склад» чтобы сделать приход или списать.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun getStatusVisual(status: String): Pair<String, Color> {
    return when (status) {
        "new" -> "НОВАЯ" to Color(0xFF607D8B)
        "diagnostics" -> "ДИАГНОСТИКА" to Color(0xFF5C6BC0)
        "awaiting_approval" -> "НА СОГЛАСОВАНИЕ" to Color(0xFFAB47BC)
        "in_progress" -> "В РАБОТЕ" to Color(0xFFFF7043)
        "awaiting_parts" -> "ЖДЁТ ЗАПЧАСТИ" to Color(0xFFFFA726)
        "quality_control" -> "КОНТРОЛЬ КАЧЕСТВА" to Color(0xFF26A69A)
        "ready" -> "ГОТОВ" to Color(0xFF66BB6A)
        else -> status.uppercase() to Color(0xFFFF6B35)
    }
}

@Composable
private fun SmallMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = if (highlight)
            CardDefaults.cardColors(containerColor = Color(0xFF2A1F1F))
        else
            CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = if (highlight) Color(0xFFE57373) else MaterialTheme.colorScheme.primary
            )
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}