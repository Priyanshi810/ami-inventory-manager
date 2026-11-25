package com.ami.inventory.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Science
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
import com.ami.inventory.data.models.BillOfMaterials
import com.ami.inventory.data.models.FinishedProduct
import com.ami.inventory.data.models.UserRole
import com.ami.inventory.ui.viewmodel.InventoryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionScreen(
    inventoryViewModel: InventoryViewModel,
    userRole: UserRole,
    preselectedProductId: String? = null
) {
    val finishedProducts by inventoryViewModel.finishedProducts.collectAsState()
    val rawMaterials by inventoryViewModel.rawMaterials.collectAsState()
    val isOperating by inventoryViewModel.isOperating.collectAsState()
    val actionMessage by inventoryViewModel.actionMessage.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedModeTab by remember { mutableStateOf(0) } // 0: Assemble Products, 1: Mix Bulk Liquid

    // --- ASSEMBLY STATE ---
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedProduct by remember { mutableStateOf<FinishedProduct?>(null) }
    var assembleQtyText by remember { mutableStateOf("50") }
    var allowAdminOverride by remember { mutableStateOf(false) }
    var currentBom by remember { mutableStateOf<BillOfMaterials?>(null) }

    // --- LIQUID MIXING STATE ---
    val fragranceOils = remember(rawMaterials) {
        rawMaterials.filter { it.category == "Fragrance Concentrate" }
    }
    var selectedFragranceName by remember { mutableStateOf("Aqua Marine") }
    var mixVolumeMlText by remember { mutableStateOf("5000") }

    val categories = listOf("All", "Air Freshener (AF)", "Air Freshener Refill (AFR)", "Car Perfume (CF)", "Car Perfume Refill (CFR)")

    val filteredProducts = finishedProducts.filter {
        selectedCategory == "All" || it.category.equals(selectedCategory, ignoreCase = true)
    }

    LaunchedEffect(preselectedProductId, finishedProducts) {
        if (!preselectedProductId.isNullOrBlank()) {
            val matched = finishedProducts.find { it.id == preselectedProductId }
            if (matched != null) {
                selectedProduct = matched
                selectedModeTab = 0
                coroutineScope.launch {
                    currentBom = inventoryViewModel.loadBom(matched.id)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Manufacturing & Production",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Assemble ready-to-sell Finished Goods via BOM or mix Bulk Ready-Made Liquid.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Production Mode Tabs
        TabRow(
            selectedTabIndex = selectedModeTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedModeTab == 0,
                onClick = { selectedModeTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PrecisionManufacturing, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1. Assemble Finished Goods")
                    }
                }
            )
            Tab(
                selected = selectedModeTab == 1,
                onClick = { selectedModeTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("2. Mix Bulk Liquid")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // TAB 1: ASSEMBLE FINISHED PRODUCTS (BOM)
        // ==========================================
        if (selectedModeTab == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Select Product to Assemble", fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Product Dropdown
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedProduct?.let { "${it.name} (Current: ${it.currentStock} units)" } ?: "Choose Finished Product...",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            filteredProducts.forEach { prod ->
                                DropdownMenuItem(
                                    text = { Text("${prod.name} (${prod.sku})") },
                                    onClick = {
                                        selectedProduct = prod
                                        expanded = false
                                        coroutineScope.launch {
                                            currentBom = inventoryViewModel.loadBom(prod.id)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = assembleQtyText,
                        onValueChange = { assembleQtyText = it },
                        label = { Text("Quantity of Finished Units to Produce") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // BOM Preview
            selectedProduct?.let { prod ->
                val qtyToProduce = assembleQtyText.toIntOrNull() ?: 0

                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "BOM Consumption Preview ($qtyToProduce units)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Deducts Ready-Made Liquid + Bottles + Caps + Boxes. Adds +$qtyToProduce to Finished Goods.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val bom = currentBom
                        if (bom != null && bom.items.isNotEmpty()) {
                            bom.items.forEach { item ->
                                val totalNeeded = item.quantityRequired * qtyToProduce
                                val currentMat = rawMaterials.find { it.id == item.materialId }
                                val available = currentMat?.currentStock ?: 0.0
                                val hasEnough = available >= totalNeeded

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.materialName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            text = "Need: $totalNeeded ${item.unit} | Stock: $available ${item.unit}",
                                            fontSize = 12.sp,
                                            color = if (hasEnough) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Text(
                                        text = if (hasEnough) "AVAILABLE" else "LOW",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (hasEnough) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                    )
                                }
                                Divider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        } else {
                            Text("Loading BOM formula...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (userRole == UserRole.ADMIN) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = allowAdminOverride,
                                    onCheckedChange = { allowAdminOverride = it }
                                )
                                Text("Admin Override (allow negative material stock)", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (qtyToProduce > 0) {
                                    inventoryViewModel.assembleFinishedProduct(
                                        productId = prod.id,
                                        quantityToProduce = qtyToProduce,
                                        allowOverride = allowAdminOverride
                                    ) { success, _ ->
                                        if (success) {
                                            assembleQtyText = "50"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = qtyToProduce > 0 && !isOperating,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isOperating) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.PrecisionManufacturing, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Assemble & Add +$qtyToProduce to Finished Goods", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // TAB 2: MIX BULK READY-MADE LIQUID
        // ==========================================
        if (selectedModeTab == 1) {
            var selectedLiquidType by remember { mutableStateOf("AF/AFR") } // "AF/AFR" or "CF/CFR"

            val isAirFreshener = selectedLiquidType == "AF/AFR"
            val oilPercent = if (isAirFreshener) 6.0 else 15.0
            val sarvPercent = if (isAirFreshener) 62.67 else 42.50
            val waterPercent = if (isAirFreshener) 31.33 else 42.50

            val volumeMl = mixVolumeMlText.toDoubleOrNull() ?: 0.0
            val oilNeeded = volumeMl * (oilPercent / 100.0)
            val sarvNeeded = volumeMl * (sarvPercent / 100.0)
            val waterNeeded = volumeMl * (waterPercent / 100.0)

            val targetLiquidDisplayName = if (isAirFreshener) "Ready-Made Liquid (AF/AFR) - $selectedFragranceName" else "Ready-Made Liquid (CF/CFR) - $selectedFragranceName"

            val sarvStock = rawMaterials.find { it.id == "mat_base_sarvonol" }?.currentStock ?: 0.0
            val waterStock = rawMaterials.find { it.id == "mat_base_heavy_water" }?.currentStock ?: 0.0

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Mix Bulk Ready-Made Liquid", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Produces specific liquid formulation for filling AF (200ml) or CF (35ml) units.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Liquid Type Formulation Selector
                    Text("Select Formulation Line:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedLiquidType == "AF/AFR",
                            onClick = { selectedLiquidType = "AF/AFR" },
                            label = { Text("Air Freshener (AF / AFR)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedLiquidType == "CF/CFR",
                            onClick = { selectedLiquidType = "CF/CFR" },
                            label = { Text("Car Perfume (CF / CFR)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fragrance Selector
                    Text("Select Fragrance Scent:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))

                    val scentNames = listOf(
                        "Aqua Marine", "Crystal Water", "Himalayan Ice", "Indian Ocean", "Sea Salt",
                        "Amber Bakhoor", "Arabian Nights", "Majestic Oud", "Sands of Arabia", "White Desert",
                        "French Rose", "Hibiscus Flora", "Lavender Bliss", "Lily Gardenia", "Orchid Flora",
                        "Blueberry Burst", "Green Apple", "Lemon Zest", "Pineapple Crust", "Strawberry Bliss",
                        "Camphor (Kapur)", "First Rainn", "Lemongrass", "Organic", "Sacred Sandal",
                        "Cherry Blossom", "Floral Fantasy", "Morning Fresh", "Vanilla Bliss", "Velvet Musk"
                    )

                    var expandedFrag by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedFrag,
                        onExpandedChange = { expandedFrag = !expandedFrag }
                    ) {
                        OutlinedTextField(
                            value = selectedFragranceName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFrag) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedFrag,
                            onDismissRequest = { expandedFrag = false }
                        ) {
                            scentNames.forEach { scent ->
                                DropdownMenuItem(
                                    text = { Text(scent) },
                                    onClick = {
                                        selectedFragranceName = scent
                                        expandedFrag = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = mixVolumeMlText,
                        onValueChange = { mixVolumeMlText = it },
                        label = { Text("Volume to Mix (ml) e.g., 5000 = 5 Litres") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Recipe Formula Box
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Exact Formulation Breakdown:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(if (isAirFreshener) "AF Formula" else "CF Formula", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Text("• Fragrance Oil ($selectedFragranceName): ${String.format("%.1f", oilNeeded)} ml ($oilPercent%)", fontSize = 12.sp)
                            Text("• Sarvonol: ${String.format("%.1f", sarvNeeded)} ml ($sarvPercent%) [Stock: $sarvStock ml]", fontSize = 12.sp)
                            Text("• Heavy Water: ${String.format("%.1f", waterNeeded)} ml ($waterPercent%) [Stock: $waterStock ml]", fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("➔ Adds +$volumeMl ml to '$targetLiquidDisplayName'", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }

                    if (userRole == UserRole.ADMIN) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = allowAdminOverride,
                                onCheckedChange = { allowAdminOverride = it }
                            )
                            Text("Admin Override (allow negative material stock)", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (volumeMl > 0) {
                                inventoryViewModel.mixLiquid(
                                    liquidType = selectedLiquidType,
                                    fragranceName = selectedFragranceName,
                                    liquidVolumeToMixMl = volumeMl,
                                    allowOverride = allowAdminOverride
                                ) { success, _ ->
                                    if (success) {
                                        mixVolumeMlText = "5000"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = volumeMl > 0 && !isOperating,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isOperating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Science, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mix & Add +$volumeMl ml to Liquid Stock", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Action Message
        actionMessage?.let { msg ->
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (msg.contains("Insufficient") || msg.contains("Error") || msg.contains("Failed"))
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (msg.contains("Insufficient")) Icons.Default.Warning else Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { inventoryViewModel.clearActionMessage() }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
