package com.ami.inventory.data.models

enum class UserRole {
    ADMIN,
    PRODUCTION,
    INVENTORY
}

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.INVENTORY
)

data class RawMaterial(
    val id: String = "",
    val name: String = "",
    val category: String = "Packaging", // "Bottle", "Bottle Cap", "Box", "Packaging", "Raw Material Base", "Fragrance Concentrate", "Ready-Made Liquid", "Paper", "Roll"
    val currentStock: Double = 0.0,
    val unit: String = "pcs", // "ml", "L", "pcs", "roll", "sheets"
    val minStockThreshold: Double = 50.0,
    val reorderQty: Double = 200.0,
    val leadTimeDays: Int = 3
) {
    val isLowStock: Boolean
        get() = currentStock <= minStockThreshold
}

data class FinishedProduct(
    val id: String = "",
    val name: String = "",
    val category: String = "Air Freshener (AF)", // "Air Freshener (AF)", "Air Freshener Refill (AFR)", "Car Perfume (CF)", "Car Perfume Refill (CFR)"
    val sku: String = "",
    val currentStock: Int = 0
)

data class BomItem(
    val materialId: String = "",
    val materialName: String = "",
    val quantityRequired: Double = 0.0,
    val unit: String = "pcs"
)

data class BillOfMaterials(
    val finishedProductId: String = "",
    val finishedProductName: String = "",
    val items: List<BomItem> = emptyList()
)

data class OrderItem(
    val finishedProductId: String = "",
    val finishedProductName: String = "",
    val quantity: Int = 1
)

data class SalesOrder(
    val id: String = "",
    val orderNumber: String = "",
    val customerName: String = "General Customer",
    val status: String = "DISPATCHED", // "DISPATCHED", "PENDING", "CANCELLED"
    val items: List<OrderItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val userEmail: String = "",
    val note: String = ""
)

data class StockLog(
    val id: String = "",
    val materialId: String = "",
    val materialName: String = "",
    val changeQty: Double = 0.0,
    val remainingStock: Double = 0.0,
    val type: String = "PURCHASE_IN", // "PURCHASE_IN", "SALES_DISPATCH", "BOM_ASSEMBLY_DEDUCT", "FINISHED_GOODS_PRODUCED", "LIQUID_MIX_PRODUCED", "MANUAL_ADJUST"
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String = "",
    val userEmail: String = "",
    val remarks: String = ""
)
