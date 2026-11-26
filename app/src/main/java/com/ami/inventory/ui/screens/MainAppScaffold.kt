package com.ami.inventory.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ami.inventory.data.models.UserProfile
import com.ami.inventory.data.models.UserRole
import com.ami.inventory.ui.viewmodel.AuthViewModel
import com.ami.inventory.ui.viewmodel.InventoryViewModel

enum class NavigationTab(val label: String, val icon: ImageVector) {
    PRODUCTS("Products", Icons.Default.Category),
    ORDERS("Orders", Icons.Default.LocalShipping),
    PRODUCE("Produce", Icons.Default.PrecisionManufacturing),
    MATERIALS("Materials", Icons.Default.Science),
    OVERVIEW("Overview", Icons.Default.Dashboard)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    userProfile: UserProfile,
    authViewModel: AuthViewModel,
    inventoryViewModel: InventoryViewModel
) {
    var currentTab by remember {
        mutableStateOf(
            when (userProfile.role) {
                UserRole.ADMIN -> NavigationTab.PRODUCTS
                UserRole.PRODUCTION -> NavigationTab.PRODUCE
                UserRole.INVENTORY -> NavigationTab.MATERIALS
            }
        )
    }

    var activeSubScreen by remember { mutableStateOf<String?>(null) } // "BOM" or "LOGS"
    var preselectedProductIdForProduce by remember { mutableStateOf<String?>(null) }

    val availableTabs = remember(userProfile.role) {
        when (userProfile.role) {
            UserRole.ADMIN -> listOf(
                NavigationTab.PRODUCTS,
                NavigationTab.ORDERS,
                NavigationTab.PRODUCE,
                NavigationTab.MATERIALS,
                NavigationTab.OVERVIEW
            )
            UserRole.PRODUCTION -> listOf(
                NavigationTab.PRODUCTS,
                NavigationTab.PRODUCE,
                NavigationTab.MATERIALS
            )
            UserRole.INVENTORY -> listOf(
                NavigationTab.PRODUCTS,
                NavigationTab.ORDERS,
                NavigationTab.MATERIALS
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (activeSubScreen) {
                                "BOM" -> "BOM Configuration"
                                "LOGS" -> "Audit & Stock Logs"
                                else -> "AMI Inventory"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(userProfile.role.name, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = when (userProfile.role) {
                                    UserRole.ADMIN -> MaterialTheme.colorScheme.primaryContainer
                                    UserRole.PRODUCTION -> MaterialTheme.colorScheme.secondaryContainer
                                    UserRole.INVENTORY -> MaterialTheme.colorScheme.tertiaryContainer
                                }
                            )
                        )
                    }
                },
                navigationIcon = {
                    if (activeSubScreen != null) {
                        IconButton(onClick = { activeSubScreen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { authViewModel.signOut() }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (activeSubScreen == null) {
                NavigationBar {
                    availableTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = {
                                currentTab = tab
                                if (tab != NavigationTab.PRODUCE) {
                                    preselectedProductIdForProduce = null
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (activeSubScreen == "BOM") {
                BomConfigScreen(inventoryViewModel = inventoryViewModel)
            } else if (activeSubScreen == "LOGS") {
                StockLogsScreen(inventoryViewModel = inventoryViewModel)
            } else {
                when (currentTab) {
                    NavigationTab.PRODUCTS -> FinishedGoodsScreen(
                        inventoryViewModel = inventoryViewModel,
                        userRole = userProfile.role,
                        onNavigateToProduce = { prodId ->
                            preselectedProductIdForProduce = prodId
                            currentTab = NavigationTab.PRODUCE
                        }
                    )
                    NavigationTab.ORDERS -> OrdersScreen(
                        inventoryViewModel = inventoryViewModel,
                        userRole = userProfile.role
                    )
                    NavigationTab.PRODUCE -> ProductionScreen(
                        inventoryViewModel = inventoryViewModel,
                        userRole = userProfile.role,
                        preselectedProductId = preselectedProductIdForProduce
                    )
                    NavigationTab.MATERIALS -> InventoryScreen(
                        inventoryViewModel = inventoryViewModel,
                        userRole = userProfile.role
                    )
                    NavigationTab.OVERVIEW -> AdminDashboardScreen(
                        userProfile = userProfile,
                        inventoryViewModel = inventoryViewModel,
                        onNavigateToInventory = { currentTab = NavigationTab.MATERIALS },
                        onNavigateToProduction = { currentTab = NavigationTab.PRODUCE },
                        onNavigateToBomConfig = { activeSubScreen = "BOM" },
                        onNavigateToLogs = { activeSubScreen = "LOGS" }
                    )
                }
            }
        }
    }
}
