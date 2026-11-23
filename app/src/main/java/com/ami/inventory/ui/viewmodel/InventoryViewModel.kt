package com.ami.inventory.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ami.inventory.data.models.*
import com.ami.inventory.data.repository.InventoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val repository: InventoryRepository = InventoryRepository()
) : ViewModel() {

    val rawMaterials: StateFlow<List<RawMaterial>> = repository.getRawMaterialsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finishedProducts: StateFlow<List<FinishedProduct>> = repository.getFinishedProductsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val salesOrders: StateFlow<List<SalesOrder>> = repository.getSalesOrdersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockLogs: StateFlow<List<StockLog>> = repository.getStockLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readyMadeLiquids: StateFlow<List<RawMaterial>> = rawMaterials.map { list ->
        list.filter { it.category == "Ready-Made Liquid" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockMaterials: StateFlow<List<RawMaterial>> = rawMaterials.map { list ->
        list.filter { it.isLowStock }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    // --- MANUAL STOCK ADJUSTMENT (MATERIALS & LIQUIDS) ---
    fun stockIn(materialId: String, addedQty: Double, remarks: String) {
        viewModelScope.launch {
            _isOperating.value = true
            try {
                repository.stockInMaterial(materialId, addedQty, remarks)
                _actionMessage.value = "Stock updated successfully!"
            } catch (e: Exception) {
                _actionMessage.value = "Failed to update stock: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    // --- MANUAL STOCK ADJUSTMENT (FINISHED GOODS) ---
    fun updateFinishedStock(productId: String, addedQty: Int, remarks: String) {
        viewModelScope.launch {
            _isOperating.value = true
            try {
                repository.updateFinishedProductStock(productId, addedQty, remarks)
                _actionMessage.value = "Finished goods stock updated!"
            } catch (e: Exception) {
                _actionMessage.value = "Failed to update stock: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    // --- SALES ORDER DISPATCH (DIRECTLY SUBTRACTS FINISHED GOODS) ---
    fun fulfillSalesOrder(
        orderNumber: String,
        customerName: String,
        items: List<OrderItem>,
        allowNegative: Boolean = false,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isOperating.value = true
            val result = repository.fulfillSalesOrder(orderNumber, customerName, items, allowNegative)
            _isOperating.value = false
            if (result.isSuccess) {
                val msg = result.getOrNull() ?: "Order dispatched!"
                _actionMessage.value = msg
                onComplete(true, msg)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Dispatch failed."
                _actionMessage.value = errorMsg
                onComplete(false, errorMsg)
            }
        }
    }

    // --- PRODUCTION ASSEMBLY (DEDUCTS BOM MATERIALS & ADDS FINISHED GOODS) ---
    fun assembleFinishedProduct(
        productId: String,
        quantityToProduce: Int,
        allowOverride: Boolean = false,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isOperating.value = true
            val result = repository.assembleFinishedProduct(productId, quantityToProduce, allowOverride)
            _isOperating.value = false
            if (result.isSuccess) {
                val msg = result.getOrNull() ?: "Production completed!"
                _actionMessage.value = msg
                onComplete(true, msg)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Production failed."
                _actionMessage.value = errorMsg
                onComplete(false, errorMsg)
            }
        }
    }

    // --- LIQUID PREPARATION (MIX READY-MADE LIQUID) ---
    fun mixLiquid(
        liquidType: String,
        fragranceName: String,
        liquidVolumeToMixMl: Double,
        allowOverride: Boolean = false,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isOperating.value = true
            val result = repository.mixReadyMadeLiquid(liquidType, fragranceName, liquidVolumeToMixMl, allowOverride)
            _isOperating.value = false
            if (result.isSuccess) {
                val msg = result.getOrNull() ?: "Liquid mixed successfully!"
                _actionMessage.value = msg
                onComplete(true, msg)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Liquid mixing failed."
                _actionMessage.value = errorMsg
                onComplete(false, errorMsg)
            }
        }
    }

    fun addRawMaterial(material: RawMaterial) {
        viewModelScope.launch {
            _isOperating.value = true
            try {
                repository.addOrUpdateRawMaterial(material)
                _actionMessage.value = "Raw material saved: ${material.name}"
            } catch (e: Exception) {
                _actionMessage.value = "Error: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    suspend fun loadBom(productId: String): BillOfMaterials? {
        return repository.getBomForProduct(productId)
    }

    fun saveBom(bom: BillOfMaterials, onSaved: () -> Unit) {
        viewModelScope.launch {
            _isOperating.value = true
            try {
                repository.saveBom(bom)
                _actionMessage.value = "BOM saved for ${bom.finishedProductName}"
                onSaved()
            } catch (e: Exception) {
                _actionMessage.value = "Failed to save BOM: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    fun seedSampleAmiData() {
        viewModelScope.launch {
            _isOperating.value = true
            try {
                repository.seedInitialAmiData()
                _actionMessage.value = "Master AMI Catalog Seeded (120 Finished Goods, 30 Liquids, 25 Materials & BOMs)!"
            } catch (e: Exception) {
                _actionMessage.value = "Seed Error: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }
}
