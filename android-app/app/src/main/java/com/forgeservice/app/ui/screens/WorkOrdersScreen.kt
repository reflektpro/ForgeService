package com.forgeservice.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.forgeservice.app.R
import com.forgeservice.app.data.remote.WorkOrderDto
import com.forgeservice.app.data.repository.ForgeServiceRepository
import com.forgeservice.app.ui.theme.ForgeServiceTheme
import kotlinx.coroutines.launch

/**
 * Список заказ-нарядов с реальными данными из сервера.
 * Клик по карточке → детальный экран.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrdersScreen(navController: NavController) {
    ForgeServiceTheme {
        val repository = remember { ForgeServiceRepository() }
        var orders by remember { mutableStateOf<List<WorkOrderDto>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        val scope = rememberCoroutineScope()

        fun loadOrders() {
            scope.launch {
                try {
                    isLoading = true
                    error = null
                    orders = repository.getWorkOrders()
                } catch (e: Exception) {
                    error = "Ошибка загрузки: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }

        LaunchedEffect(Unit) {
            loadOrders()
        }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Заказ-наряды", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { loadOrders() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn {
                    items(orders) { order ->
                        WorkOrderCard(
                            order = order,
                            onClick = {
                                navController.navigate("work_order/${order.id}")
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkOrderCard(order: WorkOrderDto, onClick: () -> Unit) {
    val statusColor = when (order.status) {
        "completed" -> Color(0xFF4CAF50)
        "in_progress" -> Color(0xFFFF9800)
        "ready" -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_assignment),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = statusColor
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "ЗН-${order.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        order.status.uppercase(),
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text("Автомобиль #${order.vehicle_id}")
                Text("Стоимость: ${order.total_cost.toInt()} ₽")

                if (order.assigned_bay_id != null) {
                    Text("Бокс: ${order.assigned_bay_id}")
                }
            }
        }
    }
}