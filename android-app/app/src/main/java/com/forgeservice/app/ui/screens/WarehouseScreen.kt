package com.forgeservice.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgeservice.app.data.remote.PartCreate
import com.forgeservice.app.data.remote.PartDto
import com.forgeservice.app.data.repository.ForgeServiceRepository
import com.forgeservice.app.ui.theme.ForgeServiceTheme
import kotlinx.coroutines.launch

@Composable
fun WarehouseScreen() {
    ForgeServiceTheme {
        val repository = remember { ForgeServiceRepository() }
        var parts by remember { mutableStateOf<List<PartDto>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var searchQuery by remember { mutableStateOf("") }
        var lowStockOnly by remember { mutableStateOf(false) }

        // Dialog states
        var showAddStockDialog by remember { mutableStateOf(false) }
        var showSubtractStockDialog by remember { mutableStateOf(false) }
        var selectedPartForStock by remember { mutableStateOf<PartDto?>(null) }
        var stockQuantity by remember { mutableStateOf("10") }

        var showCreatePartDialog by remember { mutableStateOf(false) }
        var newPartName by remember { mutableStateOf("") }
        var newPartArticle by remember { mutableStateOf("") }
        var newPartQty by remember { mutableStateOf("5") }
        var newPartMin by remember { mutableStateOf("2") }
        var newPartSell by remember { mutableStateOf("1500") }
        var newPartSupplier by remember { mutableStateOf("") }

        val scope = rememberCoroutineScope()

        fun loadParts() {
            scope.launch {
                try {
                    isLoading = true
                    error = null
                    parts = repository.getParts(
                        query = searchQuery.takeIf { it.isNotBlank() },
                        lowStockOnly = lowStockOnly
                    )
                } catch (e: Exception) {
                    error = "Ошибка загрузки склада: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }

        LaunchedEffect(Unit) {
            loadParts()
        }

        // Auto refresh on search/filter change (debounce not critical for demo)
        LaunchedEffect(searchQuery, lowStockOnly) {
            loadParts()
        }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Склад запчастей", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { loadParts() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
            }
            Text(
                "Учёт остатков, приход товаров, автоматическое списание при добавлении работ.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // Search and filters
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск по названию или артикулу") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = lowStockOnly,
                    onClick = { lowStockOnly = !lowStockOnly },
                    label = { Text("Только заканчивающиеся") }
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Всего позиций: ${parts.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            } else if (parts.isEmpty()) {
                Text("Запчасти не найдены. Добавьте первую.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn {
                    items(parts) { part ->
                        PartCard(
                            part = part,
                            onAddStock = {
                                selectedPartForStock = part
                                stockQuantity = "10"
                                showAddStockDialog = true
                            },
                            onSubtractStock = { p ->
                                selectedPartForStock = p
                                stockQuantity = "1"
                                showSubtractStockDialog = true
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // FAB for new part
        Box(Modifier.fillMaxSize()) {
            FloatingActionButton(
                onClick = { showCreatePartDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, "Добавить запчасть")
            }
        }

        // Add stock dialog
        if (showAddStockDialog && selectedPartForStock != null) {
            val part = selectedPartForStock!!
            AlertDialog(
                onDismissRequest = { showAddStockDialog = false },
                title = { Text("Приход запчастей") },
                text = {
                    Column {
                        Text("${part.name} (текущий остаток: ${part.quantity})")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = stockQuantity,
                            onValueChange = { stockQuantity = it.filter { c -> c.isDigit() } },
                            label = { Text("Количество для прихода") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                val qty = stockQuantity.toIntOrNull() ?: 1
                                repository.addStock(part.id, qty)
                                showAddStockDialog = false
                                loadParts()
                            } catch (e: Exception) {
                                // error handled in parent or toast, for demo just close
                                showAddStockDialog = false
                            }
                        }
                    }) { Text("Принять") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddStockDialog = false }) { Text("Отмена") }
                }
            )
        }

        // Subtract stock dialog
        if (showSubtractStockDialog && selectedPartForStock != null) {
            val part = selectedPartForStock!!
            AlertDialog(
                onDismissRequest = { showSubtractStockDialog = false },
                title = { Text("Списать запчасти (продали)") },
                text = {
                    Column {
                        Text("${part.name} (текущий остаток: ${part.quantity})")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = stockQuantity,
                            onValueChange = { stockQuantity = it.filter { c -> c.isDigit() } },
                            label = { Text("Количество для списания") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                val qty = stockQuantity.toIntOrNull() ?: 1
                                repository.subtractStock(part.id, qty)
                                showSubtractStockDialog = false
                                loadParts()
                            } catch (e: Exception) {
                                showSubtractStockDialog = false
                            }
                        }
                    }) { Text("Списать") }
                },
                dismissButton = {
                    TextButton(onClick = { showSubtractStockDialog = false }) { Text("Отмена") }
                }
            )
        }

        // Create new part dialog
        if (showCreatePartDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePartDialog = false },
                title = { Text("Новая запчасть") },
                text = {
                    Column {
                        OutlinedTextField(value = newPartName, onValueChange = { newPartName = it }, label = { Text("Название") })
                        OutlinedTextField(value = newPartArticle, onValueChange = { newPartArticle = it }, label = { Text("Артикул (опц.)") })
                        Row {
                            OutlinedTextField(value = newPartQty, onValueChange = { newPartQty = it.filter(Char::isDigit) }, label = { Text("Нач. кол-во") }, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(value = newPartMin, onValueChange = { newPartMin = it.filter(Char::isDigit) }, label = { Text("Мин. остаток") }, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = newPartSell, onValueChange = { newPartSell = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Цена продажи") })
                        OutlinedTextField(value = newPartSupplier, onValueChange = { newPartSupplier = it }, label = { Text("Поставщик (опц.)") })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                val create = PartCreate(
                                    name = newPartName.ifBlank { "Новая запчасть" },
                                    part_number = newPartArticle.takeIf { it.isNotBlank() },
                                    quantity = newPartQty.toIntOrNull() ?: 5,
                                    min_quantity = newPartMin.toIntOrNull() ?: 2,
                                    sell_price = newPartSell.toDoubleOrNull() ?: 1000.0,
                                    supplier = newPartSupplier.takeIf { it.isNotBlank() }
                                )
                                repository.createPart(create)
                                showCreatePartDialog = false
                                // reset form
                                newPartName = ""; newPartArticle = ""; newPartQty = "5"; newPartMin = "2"; newPartSell = "1500"; newPartSupplier = ""
                                loadParts()
                            } catch (e: Exception) {
                                showCreatePartDialog = false
                            }
                        }
                    }) { Text("Создать") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePartDialog = false }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
private fun PartCard(part: PartDto, onAddStock: () -> Unit, onSubtractStock: (PartDto) -> Unit) {
    val isLow = (part.is_low_stock == true) || (part.quantity <= part.min_quantity)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isLow)
            CardDefaults.cardColors(containerColor = Color(0xFF2A1F1F))
        else
            CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(part.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                if (isLow) Text("⚠ НИЗКИЙ ОСТАТОК", color = Color(0xFFE57373), style = MaterialTheme.typography.labelSmall)
            }
            part.part_number?.let { Text("Арт: $it", style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Остаток: ${part.quantity} ${part.unit}", fontWeight = FontWeight.Medium)
                    Text("Мин: ${part.min_quantity} ${part.unit}", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Продажа: ${part.sell_price.toInt()} ₽", fontWeight = FontWeight.Medium)
                    part.supplier?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(onClick = onAddStock, modifier = Modifier.fillMaxWidth()) {
                Text("Приход запчастей")
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { onSubtractStock(part) }, modifier = Modifier.fillMaxWidth()) {
                Text("Списать (продали)")
            }
        }
    }
}