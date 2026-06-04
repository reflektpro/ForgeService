package com.forgeservice.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgeservice.app.R
import com.forgeservice.app.data.remote.ClientDto
import com.forgeservice.app.data.remote.VehicleDto
import com.forgeservice.app.data.repository.ForgeServiceRepository
import com.forgeservice.app.ui.theme.ForgeServiceTheme
import kotlinx.coroutines.launch

/**
 * Экран автомобилей + мощный поиск.
 * Реальные данные + клиент-сайд фильтр по марке/модели/номеру/VIN/клиенту.
 * Красивые карточки с иконкой авто (ic_car), клиентом и пробегом.
 * В будущем — таймлайн истории + создание ЗН прямо из карточки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen() {
    ForgeServiceTheme {
        val repository = remember { ForgeServiceRepository() }

        var vehicles by remember { mutableStateOf<List<VehicleDto>>(emptyList()) }
        var clientMap by remember { mutableStateOf<Map<Int, ClientDto>>(emptyMap()) }
        var searchQuery by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        val scope = rememberCoroutineScope()

        fun loadVehicles() {
            scope.launch {
                try {
                    isLoading = true
                    error = null
                    vehicles = repository.getVehicles()
                    val clients = repository.getClients()
                    clientMap = clients.associateBy { it.id }
                } catch (e: Exception) {
                    error = "Не удалось загрузить автомобили: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }

        LaunchedEffect(Unit) {
            loadVehicles()
        }

        // Мощный клиент-сайд поиск (включая имя клиента) — лучше чем только серверный по авто
        val filteredVehicles = remember(searchQuery, vehicles, clientMap) {
            if (searchQuery.isBlank()) {
                vehicles
            } else {
                val q = searchQuery.lowercase()
                vehicles.filter { v ->
                    val client = clientMap[v.client_id]
                    listOf(
                        v.make,
                        v.model,
                        v.license_plate,
                        v.vin ?: "",
                        client?.full_name ?: "",
                        client?.phone ?: ""
                    ).any { it.lowercase().contains(q) }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header consistent with other tabs (icon refresh)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Автомобили", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${vehicles.size} авто в базе",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { loadVehicles() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Мощный поиск
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск по VIN, гос.номеру, марке, модели, клиенту") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                error != null -> {
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
                }
                filteredVehicles.isEmpty() -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (searchQuery.isNotBlank()) "Ничего не найдено по запросу" else "Нет автомобилей в базе",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (searchQuery.isNotBlank()) {
                                TextButton(onClick = { searchQuery = "" }) { Text("Очистить поиск") }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn {
                        items(filteredVehicles, key = { it.id }) { vehicle ->
                            VehicleCard(
                                vehicle = vehicle,
                                client = clientMap[vehicle.client_id]
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(vehicle: VehicleDto, client: ClientDto?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_car),
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${vehicle.make} ${vehicle.model}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        vehicle.license_plate,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    "${vehicle.year} г.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                val clientLabel = client?.let { "${it.full_name} • ${it.phone}" } ?: "Клиент #${vehicle.client_id}"
                Text(clientLabel, fontWeight = FontWeight.Medium)

                Text(
                    "Пробег: ${formatMileage(vehicle.current_mileage)} км",
                    style = MaterialTheme.typography.bodySmall
                )

                if (!vehicle.vin.isNullOrBlank()) {
                    Text(
                        "VIN: ${vehicle.vin}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatMileage(mileage: Int): String {
    if (mileage <= 0) return "0"
    return mileage.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
}