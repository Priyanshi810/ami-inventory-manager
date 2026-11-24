package com.ami.inventory.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalShipping
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
import com.ami.inventory.data.models.FinishedProduct
import com.ami.inventory.data.models.OrderItem
import com.ami.inventory.data.models.SalesOrder
import com.ami.inventory.data.models.UserRole
import com.ami.inventory.ui.viewmodel.InventoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    inventoryViewModel: InventoryViewModel,
    userRole: UserRole
) {
    val finishedProducts by inventoryViewModel.finishedProducts.collectAsState()
    val salesOrders by inventoryViewModel.salesOrders.collectAsState()
    val isOperating by inventoryViewModel.isOperating.collectAsState()
    val actionMessage by inventoryViewModel.actionMessage.collectAsState()

    var orderNumber by remember { mutableStateOf("ORD-${System.currentTimeMillis() % 100000}") }
    var customerName by remember { mutableStateOf("") }
    val orderItems = remember { mutableStateListOf<OrderItem>() }

    var selectedProductToAdd by remember { mutableStateOf<FinishedProduct?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var quantityText by remember { mutableStateOf("10") }
    var allowAdminOverride by remember { mutableStateOf(false) }

    val categories = listOf("All", "Air Freshener (AF)", "Air Freshener Refill (AFR)", "Car Perfume (CF)", "Car Perfume Refill (CFR)")
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    val filteredProducts = finishedProducts.filter {
        selectedCategory == "All" || it.category.equals(selectedCategory, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Order Dispatch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Fulfill customer orders. Directly subtracts Finished Product inventory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Create Order Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("New Customer Dispatch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = orderNumber,
                        onValueChange = { orderNumber = it },
                        label = { Text("Order / Invoice #") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Customer / Store") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Add Products to this Order:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))

                // Category Chips
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

                Spacer(modifier = Modifier.height(8.dp))

                // Product Dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedProductToAdd?.let { "${it.name} (In stock: ${it.currentStock})" } ?: "Select Finished Product...",
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
                                text = {
                                    Column {
                                        Text("${prod.name} (${prod.sku})", fontWeight = FontWeight.SemiBold)
                                        Text("Available Ready Stock: ${prod.currentStock} units", fontSize = 12.sp, color = if (prod.currentStock > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    }
                                },
                                onClick = {
                                    selectedProductToAdd = prod
                                    expanded = false
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
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val prod = selectedProductToAdd
                            val qty = quantityText.toIntOrNull() ?: 0
                            if (prod != null && qty > 0) {
                                orderItems.add(
                                    OrderItem(
                                        finishedProductId = prod.id,
                                        finishedProductName = prod.name,
                                        quantity = qty
                                    )
                                )
                                selectedProductToAdd = null
                                quantityText = "10"
                            }
                        },
                        enabled = selectedProductToAdd != null && (quantityText.toIntOrNull() ?: 0) > 0,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                if (orderItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Items in this Dispatch (${orderItems.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        orderItems.forEachIndexed { index, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.finishedProductName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Dispatch Qty: ${item.quantity} units", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { orderItems.removeAt(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    if (userRole == UserRole.ADMIN) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = allowAdminOverride,
                                onCheckedChange = { allowAdminOverride = it }
                            )
                            Text("Admin Override (allow negative finished stock)", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            inventoryViewModel.fulfillSalesOrder(
                                orderNumber = orderNumber.trim(),
                                customerName = if (customerName.isNotBlank()) customerName.trim() else "Customer",
                                items = orderItems.toList(),
                                allowNegative = allowAdminOverride
                            ) { success, _ ->
                                if (success) {
                                    orderItems.clear()
                                    orderNumber = "ORD-${System.currentTimeMillis() % 100000}"
                                    customerName = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !isOperating,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isOperating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fulfill & Deduct Finished Stock", fontWeight = FontWeight.Bold)
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

        Spacer(modifier = Modifier.height(20.dp))

        // Recent Dispatched Orders Section
        Text("Recent Dispatches (${salesOrders.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (salesOrders.isEmpty()) {
            Text("No customer orders dispatched yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                salesOrders.forEach { order ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(order.orderNumber, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(sdf.format(Date(order.timestamp)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Customer: ${order.customerName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            order.items.forEach { item ->
                                Text("• ${item.quantity}x ${item.finishedProductName}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
