package com.ami.inventory.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ami.inventory.data.models.BillOfMaterials
import com.ami.inventory.data.models.BomItem
import com.ami.inventory.data.models.FinishedProduct
import com.ami.inventory.data.models.RawMaterial
import com.ami.inventory.ui.viewmodel.InventoryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BomConfigScreen(
    inventoryViewModel: InventoryViewModel
) {
    val finishedProducts by inventoryViewModel.finishedProducts.collectAsState()
    val rawMaterials by inventoryViewModel.rawMaterials.collectAsState()
    val isOperating by inventoryViewModel.isOperating.collectAsState()
    val actionMessage by inventoryViewModel.actionMessage.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedProduct by remember { mutableStateOf<FinishedProduct?>(null) }
    val currentBomItems = remember { mutableStateListOf<BomItem>() }

    var selectedMaterialToAdd by remember { mutableStateOf<RawMaterial?>(null) }
    var qtyRequiredText by remember { mutableStateOf("1.0") }

    fun loadBomForProduct(prod: FinishedProduct) {
        coroutineScope.launch {
            val bom = inventoryViewModel.loadBom(prod.id)
            currentBomItems.clear()
            if (bom != null) {
                currentBomItems.addAll(bom.items)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("BOM Configuration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Define the raw materials consumed when 1 unit of a product is manufactured.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Product Selector Dropdown
        var expandedProduct by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expandedProduct,
            onExpandedChange = { expandedProduct = !expandedProduct }
        ) {
            OutlinedTextField(
                value = selectedProduct?.name ?: "Select Finished Product...",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProduct) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expandedProduct,
                onDismissRequest = { expandedProduct = false }
            ) {
                finishedProducts.forEach { prod ->
                    DropdownMenuItem(
                        text = { Text("${prod.name} (${prod.category})") },
                        onClick = {
                            selectedProduct = prod
                            expandedProduct = false
                            loadBomForProduct(prod)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        selectedProduct?.let { prod ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Add Material to '${prod.name}' BOM", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    var expandedMat by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedMat,
                        onExpandedChange = { expandedMat = !expandedMat }
                    ) {
                        OutlinedTextField(
                            value = selectedMaterialToAdd?.name ?: "Choose Raw Material...",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMat) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedMat,
                            onDismissRequest = { expandedMat = false }
                        ) {
                            rawMaterials.forEach { mat ->
                                DropdownMenuItem(
                                    text = { Text("${mat.name} (${mat.unit})") },
                                    onClick = {
                                        selectedMaterialToAdd = mat
                                        expandedMat = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = qtyRequiredText,
                            onValueChange = { qtyRequiredText = it },
                            label = { Text("Qty Per Unit (${selectedMaterialToAdd?.unit ?: ""})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                val mat = selectedMaterialToAdd
                                val qty = qtyRequiredText.toDoubleOrNull() ?: 0.0
                                if (mat != null && qty > 0) {
                                    val existingIndex = currentBomItems.indexOfFirst { it.materialId == mat.id }
                                    if (existingIndex >= 0) {
                                        currentBomItems[existingIndex] = BomItem(mat.id, mat.name, qty, mat.unit)
                                    } else {
                                        currentBomItems.add(BomItem(mat.id, mat.name, qty, mat.unit))
                                    }
                                    selectedMaterialToAdd = null
                                    qtyRequiredText = "1.0"
                                }
                            },
                            enabled = selectedMaterialToAdd != null && (qtyRequiredText.toDoubleOrNull() ?: 0.0) > 0
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Current BOM Ingredients (Per 1 Unit)", fontWeight = FontWeight.Bold)

            LazyColumn(
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(currentBomItems) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.materialName, fontWeight = FontWeight.SemiBold)
                                Text("${item.quantityRequired} ${item.unit} per finished unit", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { currentBomItems.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    inventoryViewModel.saveBom(
                        BillOfMaterials(
                            finishedProductId = prod.id,
                            finishedProductName = prod.name,
                            items = currentBomItems.toList()
                        )
                    ) {}
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !isOperating,
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isOperating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save BOM for ${prod.name}")
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a finished product above to view or configure its BOM.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
