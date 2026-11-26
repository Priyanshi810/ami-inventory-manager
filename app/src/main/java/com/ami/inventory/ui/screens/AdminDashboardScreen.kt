package com.ami.inventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ami.inventory.data.models.UserProfile
import com.ami.inventory.ui.viewmodel.InventoryViewModel

@Composable
fun AdminDashboardScreen(
    userProfile: UserProfile,
    inventoryViewModel: InventoryViewModel,
    onNavigateToInventory: () -> Unit,
    onNavigateToProduction: () -> Unit,
    onNavigateToBomConfig: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val rawMaterials by inventoryViewModel.rawMaterials.collectAsState()
    val finishedProducts by inventoryViewModel.finishedProducts.collectAsState()
    val lowStockMaterials by inventoryViewModel.lowStockMaterials.collectAsState()
    val stockLogs by inventoryViewModel.stockLogs.collectAsState()
    val isOperating by inventoryViewModel.isOperating.collectAsState()
    val actionMessage by inventoryViewModel.actionMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Welcome Header
        Text(
            text = "Welcome, ${userProfile.name}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Role: ${userProfile.role.name} • AMI Inventory Control",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Metrics Grid (2x2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Raw Materials",
                count = "${rawMaterials.size}",
                icon = Icons.Default.Inventory,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToInventory
            )
            MetricCard(
                title = "Low Stock Alerts",
                count = "${lowStockMaterials.size}",
                icon = Icons.Default.Warning,
                color = if (lowStockMaterials.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToInventory
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Finished Goods",
                count = "${finishedProducts.size}",
                icon = Icons.Default.Category,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToProduction
            )
            MetricCard(
                title = "Transactions",
                count = "${stockLogs.size}",
                icon = Icons.Default.History,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToLogs
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Quick Action Buttons
        Text("Quick Navigation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateToProduction,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PrecisionManufacturing, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Produce", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onNavigateToBomConfig,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.SettingsSuggest, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("BOM Config", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onNavigateToLogs,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Audit Logs", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // One-Click Seed / Sync Catalog Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AMI Master Catalog Setup", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Populates or syncs all 120 Finished Products (30 AF, 30 AFR, 30 CF, 30 CFR), 25 Common Materials, 30 Fragrance Concentrates, and all 120 BOM mappings.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { inventoryViewModel.seedSampleAmiData() },
                    enabled = !isOperating
                ) {
                    if (isOperating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (finishedProducts.isEmpty()) "Populate AMI Master Catalog" else "Sync / Update AMI Master Catalog")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Low-Stock Attention List
        Text(
            text = "Critical Reorder Alerts (${lowStockMaterials.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (lowStockMaterials.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("All raw materials have healthy stock levels!", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lowStockMaterials.forEach { mat ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mat.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "Stock: ${mat.currentStock} ${mat.unit} (Min: ${mat.minStockThreshold}) • Lead: ${mat.leadTimeDays}d",
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                "Reorder +${mat.reorderQty}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    count: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(count, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
