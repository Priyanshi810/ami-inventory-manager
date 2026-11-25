package com.ami.inventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ami.inventory.data.models.RawMaterial
import com.ami.inventory.data.models.UserRole
import com.ami.inventory.ui.viewmodel.InventoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    inventoryViewModel: InventoryViewModel,
    userRole: UserRole
) {
    val rawMaterials by inventoryViewModel.rawMaterials.collectAsState()
    val isOperating by inventoryViewModel.isOperating.collectAsState()
    val actionMessage by inventoryViewModel.actionMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var materialForStockIn by remember { mutableStateOf<RawMaterial?>(null) }
    var showAddMaterialDialog by remember { mutableStateOf(false) }

    val categories = listOf("All", "Ready-Made Liquid", "Bottle", "Bottle Cap", "Box", "Packaging", "Raw Material Base", "Fragrance Concentrate", "Paper", "Roll")

    val filteredList = rawMaterials.filter {
        (selectedCategory == "All" || it.category.equals(selectedCategory, ignoreCase = true)) &&
                (it.name.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true))
    }

    Scaffold(
        floatingActionButton = {
            if (userRole == UserRole.ADMIN || userRole == UserRole.INVENTORY) {
                FloatingActionButton(
                    onClick = { showAddMaterialDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Material")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search raw materials...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action message snack-style alert
            actionMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { inventoryViewModel.clearActionMessage() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No raw materials found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredList, key = { it.id }) { material ->
                        RawMaterialCard(
                            material = material,
                            onStockInClick = { materialForStockIn = material }
                        )
                    }
                }
            }
        }
    }

    // --- DIALOG: STOCK ADJUSTMENT (ADD OR DEDUCT) ---
    materialForStockIn?.let { mat ->
        var qtyText by remember { mutableStateOf("") }
        var isAddition by remember { mutableStateOf(true) }
        var remarksText by remember { mutableStateOf("Supplier Delivery / Stock adjustment") }

        AlertDialog(
            onDismissRequest = { materialForStockIn = null },
            title = { Text("Update Stock - ${mat.name}") },
            text = {
                Column {
                    Text("Category: ${mat.category} • Current Stock: ${mat.currentStock} ${mat.unit}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isAddition,
                            onClick = { isAddition = true },
                            label = { Text("+ Add Stock") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isAddition,
                            onClick = { isAddition = false },
                            label = { Text("- Deduct Stock") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Quantity (${mat.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = remarksText,
                        onValueChange = { remarksText = it },
                        label = { Text("Remarks / PO# / Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = qtyText.toDoubleOrNull() ?: 0.0
                        if (qty > 0) {
                            val finalQty = if (isAddition) qty else -qty
                            inventoryViewModel.stockIn(mat.id, finalQty, remarksText)
                            materialForStockIn = null
                        }
                    },
                    enabled = (qtyText.toDoubleOrNull() ?: 0.0) > 0 && !isOperating
                ) {
                    Text("Save Stock")
                }
            },
            dismissButton = {
                TextButton(onClick = { materialForStockIn = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- DIALOG: CREATE RAW MATERIAL ---
    if (showAddMaterialDialog) {
        var name by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf("Packaging") }
        var initialStock by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("pcs") }
        var minThreshold by remember { mutableStateOf("50") }
        var reorderQty by remember { mutableStateOf("200") }
        var leadTimeDays by remember { mutableStateOf("3") }

        AlertDialog(
            onDismissRequest = { showAddMaterialDialog = false },
            title = { Text("Add Raw Material") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Material Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Category (Liquid, Packaging, etc)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = initialStock, onValueChange = { initialStock = it }, label = { Text("Initial Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit (ml, pcs)") }, modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = minThreshold, onValueChange = { minThreshold = it }, label = { Text("Min Level") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = leadTimeDays, onValueChange = { leadTimeDays = it }, label = { Text("Lead Time (Days)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            inventoryViewModel.addRawMaterial(
                                RawMaterial(
                                    name = name.trim(),
                                    category = cat.trim(),
                                    currentStock = initialStock.toDoubleOrNull() ?: 0.0,
                                    unit = unit.trim(),
                                    minStockThreshold = minThreshold.toDoubleOrNull() ?: 10.0,
                                    reorderQty = reorderQty.toDoubleOrNull() ?: 50.0,
                                    leadTimeDays = leadTimeDays.toIntOrNull() ?: 3
                                )
                            )
                            showAddMaterialDialog = false
                        }
                    },
                    enabled = name.isNotBlank() && !isOperating
                ) {
                    Text("Save Material")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMaterialDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RawMaterialCard(
    material: RawMaterial,
    onStockInClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (material.isLowStock) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = material.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Category: ${material.category} • Lead time: ${material.leadTimeDays}d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (material.isLowStock) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("LOW STOCK", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${material.currentStock} ${material.unit}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (material.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Min: ${material.minStockThreshold} ${material.unit} | Reorder: ${material.reorderQty}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onStockInClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Stock In (+)", fontSize = 13.sp)
                }
            }
        }
    }
}
