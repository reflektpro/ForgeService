package com.forgeservice.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgeservice.app.data.remote.ApiClient
import com.forgeservice.app.data.remote.DashboardStatsDto
import com.forgeservice.app.data.remote.WorkOrderDto
import com.forgeservice.app.data.repository.ForgeServiceRepository
import com.forgeservice.app.ui.theme.ForgeServiceTheme
import kotlinx.coroutines.launch

@Composable
fun AnalyticsScreen() {
    ForgeServiceTheme {
        val repository = remember { ForgeServiceRepository() }
        var stats by remember { mutableStateOf<DashboardStatsDto?>(null) }
        var recentCompleted by remember { mutableStateOf<List<WorkOrderDto>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        val scope = rememberCoroutineScope()

        fun loadAnalytics() {
            scope.launch {
                try {
                    isLoading = true
                    error = null
                    stats = repository.getDashboardStats()
                    // Get some completed for "recent"
                    val all = repository.getWorkOrders()
                    recentCompleted = all.filter { it.status == "completed" }.take(5)
                } catch (e: Exception) {
                    error = "Не удалось загрузить аналитику: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }

        LaunchedEffect(Unit) {
            loadAnalytics()
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Аналитика и отчёты", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { loadAnalytics() }) {
                    Icon(Icons.Default.Refresh, "Обновить")
                }
            }
            Text("Стратегический обзор: выручка, топы, закрытые заказы + выгрузка PDF.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            } else if (stats != null) {
                val s = stats!!

                // KPI row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Выручка (30д)", "${s.revenue_month.toInt()} ₽", Modifier.weight(1f))
                    MetricCard("Ср. чек", "${s.avg_check.toInt()} ₽", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Активных ЗН", s.active_orders.toString(), Modifier.weight(1f))
                    MetricCard(
                        "Боксы занято",
                        "${s.bays_occupied} из ${s.bays_total}",
                        Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Low stock alert
                if (s.low_stock_parts > 0) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F1F))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⚠ ${s.low_stock_parts} позиций на складе ниже минимума — проверьте Склад",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Top services (placeholder data)
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("ТОП услуг", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            "ТО-1 / Регламентное" to 14,
                            "Замена колодок (перед)" to 9,
                            "Диагностика ходовой" to 7,
                            "Замена масла + фильтр" to 6,
                            "Свечи зажигания" to 4
                        ).forEach { (name, count) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name)
                                Text("$count заказов")
                            }
                            // Simple bar
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(Color(0xFFFF6B35).copy(alpha = 0.2f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(count / 20f)
                                        .height(8.dp)
                                        .background(Color(0xFFFF6B35))
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Recent completed
                Text("Последние закрытые заказы", fontWeight = FontWeight.SemiBold)
                if (recentCompleted.isNotEmpty()) {
                    Column {
                        recentCompleted.forEach { wo ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("ЗН-${wo.id} • Авто #${wo.vehicle_id}")
                                    Text("${wo.total_cost.toInt()} ₽", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                } else {
                    Text("Нет закрытых в выборке.", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))

                // Report button
                val context = LocalContext.current
                OutlinedButton(
                    onClick = {
                        val url = "${ApiClient.currentBaseUrl}/api/pdf/report?days=30"
                        val request = DownloadManager.Request(Uri.parse(url))
                        request.setTitle("Forge Report")
                        request.setDescription("Period analytics report PDF")
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "forge_report.pdf")
                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сформировать отчёт за период (PDF)")
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}