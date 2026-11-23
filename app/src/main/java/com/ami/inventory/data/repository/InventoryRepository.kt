package com.ami.inventory.data.repository

import com.ami.inventory.data.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class InventoryRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    fun signOut() {
        auth.signOut()
    }

    // --- USER PROFILE & ROLE ---
    suspend fun getUserProfile(uid: String): UserProfile? {
        val doc = firestore.collection("users").document(uid).get().await()
        return if (doc.exists()) {
            val roleStr = doc.getString("role") ?: UserRole.INVENTORY.name
            UserProfile(
                uid = uid,
                name = doc.getString("name") ?: "",
                email = doc.getString("email") ?: "",
                role = runCatching { UserRole.valueOf(roleStr) }.getOrDefault(UserRole.INVENTORY)
            )
        } else null
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val map = mapOf(
            "uid" to profile.uid,
            "name" to profile.name,
            "email" to profile.email,
            "role" to profile.role.name
        )
        firestore.collection("users").document(profile.uid).set(map).await()
    }

    // --- RAW MATERIALS & READY-MADE LIQUIDS FLOW ---
    fun getRawMaterialsFlow(): Flow<List<RawMaterial>> = callbackFlow {
        val listener = firestore.collection("raw_materials")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    RawMaterial(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        category = doc.getString("category") ?: "General",
                        currentStock = doc.getDouble("currentStock") ?: 0.0,
                        unit = doc.getString("unit") ?: "pcs",
                        minStockThreshold = doc.getDouble("minStockThreshold") ?: 10.0,
                        reorderQty = doc.getDouble("reorderQty") ?: 50.0,
                        leadTimeDays = doc.getLong("leadTimeDays")?.toInt() ?: 3
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addOrUpdateRawMaterial(material: RawMaterial) {
        val col = firestore.collection("raw_materials")
        val docRef = if (material.id.isBlank()) col.document() else col.document(material.id)
        val data = mapOf(
            "name" to material.name,
            "category" to material.category,
            "currentStock" to material.currentStock,
            "unit" to material.unit,
            "minStockThreshold" to material.minStockThreshold,
            "reorderQty" to material.reorderQty,
            "leadTimeDays" to material.leadTimeDays
        )
        docRef.set(data).await()
    }

    suspend fun stockInMaterial(materialId: String, addedQty: Double, remarks: String) {
        val docRef = firestore.collection("raw_materials").document(materialId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val current = snapshot.getDouble("currentStock") ?: 0.0
            val materialName = snapshot.getString("name") ?: "Material"
            val newStock = current + addedQty
            transaction.update(docRef, "currentStock", newStock)

            // Log stock in
            val logRef = firestore.collection("stock_logs").document()
            transaction.set(
                logRef, mapOf(
                    "materialId" to materialId,
                    "materialName" to materialName,
                    "changeQty" to addedQty,
                    "remainingStock" to newStock,
                    "type" to if (addedQty >= 0) "PURCHASE_IN" else "MANUAL_ADJUST",
                    "timestamp" to System.currentTimeMillis(),
                    "userId" to (currentUserId ?: ""),
                    "userEmail" to (currentUserEmail ?: ""),
                    "remarks" to remarks
                )
            )
        }.await()
    }

    // --- FINISHED PRODUCTS FLOW ---
    fun getFinishedProductsFlow(): Flow<List<FinishedProduct>> = callbackFlow {
        val listener = firestore.collection("finished_products")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    FinishedProduct(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        category = doc.getString("category") ?: "General",
                        sku = doc.getString("sku") ?: "",
                        currentStock = doc.getLong("currentStock")?.toInt() ?: 0
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateFinishedProductStock(productId: String, addedQty: Int, remarks: String) {
        val docRef = firestore.collection("finished_products").document(productId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val current = snapshot.getLong("currentStock")?.toInt() ?: 0
            val prodName = snapshot.getString("name") ?: "Product"
            val newStock = current + addedQty
            transaction.update(docRef, "currentStock", newStock)

            val logRef = firestore.collection("stock_logs").document()
            transaction.set(
                logRef, mapOf(
                    "materialId" to productId,
                    "materialName" to prodName,
                    "changeQty" to addedQty.toDouble(),
                    "remainingStock" to newStock.toDouble(),
                    "type" to "FINISHED_GOODS_ADJUST",
                    "timestamp" to System.currentTimeMillis(),
                    "userId" to (currentUserId ?: ""),
                    "userEmail" to (currentUserEmail ?: ""),
                    "remarks" to remarks
                )
            )
        }.await()
    }

    // --- SALES ORDERS / DISPATCH (DIRECTLY SUBTRACTS FINISHED PRODUCTS) ---
    fun getSalesOrdersFlow(): Flow<List<SalesOrder>> = callbackFlow {
        val listener = firestore.collection("sales_orders")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val rawItems = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val items = rawItems.map {
                        OrderItem(
                            finishedProductId = it["finishedProductId"] as? String ?: "",
                            finishedProductName = it["finishedProductName"] as? String ?: "",
                            quantity = (it["quantity"] as? Number)?.toInt() ?: 1
                        )
                    }
                    SalesOrder(
                        id = doc.id,
                        orderNumber = doc.getString("orderNumber") ?: "",
                        customerName = doc.getString("customerName") ?: "Customer",
                        status = doc.getString("status") ?: "DISPATCHED",
                        items = items,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        createdBy = doc.getString("createdBy") ?: "",
                        userEmail = doc.getString("userEmail") ?: "",
                        note = doc.getString("note") ?: ""
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun fulfillSalesOrder(
        orderNumber: String,
        customerName: String,
        items: List<OrderItem>,
        allowNegative: Boolean = false
    ): Result<String> {
        return try {
            firestore.runTransaction { transaction ->
                // 1. Verify stock of each finished product
                for (item in items) {
                    val prodRef = firestore.collection("finished_products").document(item.finishedProductId)
                    val snap = transaction.get(prodRef)
                    val currentStock = snap.getLong("currentStock")?.toInt() ?: 0
                    val prodName = snap.getString("name") ?: item.finishedProductName

                    if (currentStock < item.quantity && !allowNegative) {
                        throw IllegalStateException("Insufficient finished stock for '$prodName'! Available: $currentStock, Required: ${item.quantity}")
                    }
                }

                // 2. Deduct from finished products & log dispatch
                for (item in items) {
                    val prodRef = firestore.collection("finished_products").document(item.finishedProductId)
                    val snap = transaction.get(prodRef)
                    val currentStock = snap.getLong("currentStock")?.toInt() ?: 0
                    val prodName = snap.getString("name") ?: item.finishedProductName
                    val newStock = currentStock - item.quantity

                    transaction.update(prodRef, "currentStock", newStock)

                    val logRef = firestore.collection("stock_logs").document()
                    transaction.set(
                        logRef, mapOf(
                            "materialId" to item.finishedProductId,
                            "materialName" to prodName,
                            "changeQty" to (-item.quantity).toDouble(),
                            "remainingStock" to newStock.toDouble(),
                            "type" to "SALES_DISPATCH",
                            "timestamp" to System.currentTimeMillis(),
                            "userId" to (currentUserId ?: ""),
                            "userEmail" to (currentUserEmail ?: ""),
                            "remarks" to "Order #$orderNumber to $customerName"
                        )
                    )
                }

                // 3. Record Sales Order Document
                val orderRef = firestore.collection("sales_orders").document()
                val orderData = mapOf(
                    "orderNumber" to orderNumber,
                    "customerName" to customerName,
                    "status" to "DISPATCHED",
                    "items" to items.map {
                        mapOf(
                            "finishedProductId" to it.finishedProductId,
                            "finishedProductName" to it.finishedProductName,
                            "quantity" to it.quantity
                        )
                    },
                    "timestamp" to System.currentTimeMillis(),
                    "createdBy" to (currentUserId ?: ""),
                    "userEmail" to (currentUserEmail ?: ""),
                    "note" to "Order fulfilled from finished inventory"
                )
                transaction.set(orderRef, orderData)
            }.await()

            Result.success("Order #$orderNumber dispatched successfully! Finished goods stock deducted.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- PRODUCTION: ASSEMBLE FINISHED PRODUCTS VIA BOM ---
    // Deducts Ready-Made Liquid + Bottles + Caps + Packaging and INCREASES Finished Goods Stock
    suspend fun assembleFinishedProduct(
        productId: String,
        quantityToProduce: Int,
        allowOverride: Boolean = false
    ): Result<String> {
        return try {
            val bom = getBomForProduct(productId)
                ?: return Result.failure(Exception("No Bill of Materials (BOM) found for this product! Please configure BOM first."))

            firestore.runTransaction { transaction ->
                val prodRef = firestore.collection("finished_products").document(productId)
                val prodSnap = transaction.get(prodRef)
                val prodName = prodSnap.getString("name") ?: bom.finishedProductName
                val currentProdStock = prodSnap.getLong("currentStock")?.toInt() ?: 0

                // 1. Verify stock of all BOM components (Ready-made liquid, bottles, caps, packaging)
                for (bomItem in bom.items) {
                    val matRef = firestore.collection("raw_materials").document(bomItem.materialId)
                    val matSnap = transaction.get(matRef)
                    val availableStock = matSnap.getDouble("currentStock") ?: 0.0
                    val totalNeeded = bomItem.quantityRequired * quantityToProduce

                    if (availableStock < totalNeeded && !allowOverride) {
                        throw IllegalStateException("Insufficient '${bomItem.materialName}'! Needed: $totalNeeded ${bomItem.unit}, Available: $availableStock ${bomItem.unit}")
                    }
                }

                // 2. Deduct BOM components
                for (bomItem in bom.items) {
                    val matRef = firestore.collection("raw_materials").document(bomItem.materialId)
                    val matSnap = transaction.get(matRef)
                    val currentStock = matSnap.getDouble("currentStock") ?: 0.0
                    val totalNeeded = bomItem.quantityRequired * quantityToProduce
                    val newStock = currentStock - totalNeeded

                    transaction.update(matRef, "currentStock", newStock)

                    val logRef = firestore.collection("stock_logs").document()
                    transaction.set(
                        logRef, mapOf(
                            "materialId" to bomItem.materialId,
                            "materialName" to bomItem.materialName,
                            "changeQty" to -totalNeeded,
                            "remainingStock" to newStock,
                            "type" to "BOM_ASSEMBLY_DEDUCT",
                            "timestamp" to System.currentTimeMillis(),
                            "userId" to (currentUserId ?: ""),
                            "userEmail" to (currentUserEmail ?: ""),
                            "remarks" to "Consumed to produce $quantityToProduce units of $prodName"
                        )
                    )
                }

                // 3. Add to Finished Products Stock
                val newProdStock = currentProdStock + quantityToProduce
                transaction.update(prodRef, "currentStock", newProdStock)

                val prodLogRef = firestore.collection("stock_logs").document()
                transaction.set(
                    prodLogRef, mapOf(
                        "materialId" to productId,
                        "materialName" to prodName,
                        "changeQty" to quantityToProduce.toDouble(),
                        "remainingStock" to newProdStock.toDouble(),
                        "type" to "FINISHED_GOODS_PRODUCED",
                        "timestamp" to System.currentTimeMillis(),
                        "userId" to (currentUserId ?: ""),
                        "userEmail" to (currentUserEmail ?: ""),
                        "remarks" to "Batch assembly: +$quantityToProduce units produced"
                    )
                )
            }.await()

            Result.success("Production complete! +$quantityToProduce units added to Finished Stock, BOM materials deducted.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- LIQUID PREPARATION: MIX BULK READY-MADE LIQUID ---
    // Formulations:
    // AF / AFR: Fragrance Oil (6%) + Sarvonol (62.67%) + Heavy Water (31.33%)
    // CF / CFR: Fragrance Oil (15%) + Sarvonol (42.5%) + Heavy Water (42.5%)
    suspend fun mixReadyMadeLiquid(
        liquidType: String, // "AF/AFR" or "CF/CFR"
        fragranceName: String,
        liquidVolumeToMixMl: Double,
        allowOverride: Boolean = false
    ): Result<String> {
        return try {
            val slug = fragranceName.lowercase().replace(" ", "_").replace("(", "").replace(")", "")
            val fragOilMatId = "mat_frag_$slug"

            val isAirFreshener = liquidType.contains("AF", ignoreCase = true)
            val readyMadeLiquidId = if (isAirFreshener) "mat_liq_af_$slug" else "mat_liq_cf_$slug"
            val liquidDisplayName = if (isAirFreshener) "Ready-Made Liquid (AF/AFR) - $fragranceName" else "Ready-Made Liquid (CF/CFR) - $fragranceName"

            val oilRatio = if (isAirFreshener) 0.06 else 0.15
            val sarvRatio = if (isAirFreshener) 0.6267 else 0.425
            val waterRatio = if (isAirFreshener) 0.3133 else 0.425

            val requiredFragOil = liquidVolumeToMixMl * oilRatio
            val requiredSarvonol = liquidVolumeToMixMl * sarvRatio
            val requiredHeavyWater = liquidVolumeToMixMl * waterRatio

            firestore.runTransaction { transaction ->
                val fragRef = firestore.collection("raw_materials").document(fragOilMatId)
                val sarvRef = firestore.collection("raw_materials").document("mat_base_sarvonol")
                val waterRef = firestore.collection("raw_materials").document("mat_base_heavy_water")
                val liqRef = firestore.collection("raw_materials").document(readyMadeLiquidId)

                val fragSnap = transaction.get(fragRef)
                val sarvSnap = transaction.get(sarvRef)
                val waterSnap = transaction.get(waterRef)
                val liqSnap = transaction.get(liqRef)

                val fragStock = fragSnap.getDouble("currentStock") ?: 0.0
                val sarvStock = sarvSnap.getDouble("currentStock") ?: 0.0
                val waterStock = waterSnap.getDouble("currentStock") ?: 0.0
                val currentLiquidStock = liqSnap.getDouble("currentStock") ?: 0.0

                if (!allowOverride) {
                    if (fragStock < requiredFragOil) throw IllegalStateException("Insufficient Fragrance Oil ($fragranceName)! Needed: ${String.format("%.1f", requiredFragOil)} ml, Available: $fragStock ml")
                    if (sarvStock < requiredSarvonol) throw IllegalStateException("Insufficient Sarvonol! Needed: ${String.format("%.1f", requiredSarvonol)} ml, Available: $sarvStock ml")
                    if (waterStock < requiredHeavyWater) throw IllegalStateException("Insufficient Heavy Water! Needed: ${String.format("%.1f", requiredHeavyWater)} ml, Available: $waterStock ml")
                }

                // Deduct ingredients
                transaction.update(fragRef, "currentStock", fragStock - requiredFragOil)
                transaction.update(sarvRef, "currentStock", sarvStock - requiredSarvonol)
                transaction.update(waterRef, "currentStock", waterStock - requiredHeavyWater)

                // Add to Ready-Made Liquid
                val newLiquidStock = currentLiquidStock + liquidVolumeToMixMl
                transaction.update(liqRef, "currentStock", newLiquidStock)

                // Log mixing
                val logRef = firestore.collection("stock_logs").document()
                transaction.set(
                    logRef, mapOf(
                        "materialId" to readyMadeLiquidId,
                        "materialName" to liquidDisplayName,
                        "changeQty" to liquidVolumeToMixMl,
                        "remainingStock" to newLiquidStock,
                        "type" to "LIQUID_MIX_PRODUCED",
                        "timestamp" to System.currentTimeMillis(),
                        "userId" to (currentUserId ?: ""),
                        "userEmail" to (currentUserEmail ?: ""),
                        "remarks" to "Mixed $liquidVolumeToMixMl ml of $liquidDisplayName (Oil: ${String.format("%.1f", requiredFragOil)}ml, Sarvonol: ${String.format("%.1f", requiredSarvonol)}ml, Water: ${String.format("%.1f", requiredHeavyWater)}ml)"
                    )
                )
            }.await()

            Result.success("Liquid mixed! +$liquidVolumeToMixMl ml added to '$liquidDisplayName'.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- BOM (BILL OF MATERIALS) ---
    suspend fun getBomForProduct(productId: String): BillOfMaterials? {
        val doc = firestore.collection("boms").document(productId).get().await()
        if (!doc.exists()) return null
        val itemsList = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
        val items = itemsList.map {
            BomItem(
                materialId = it["materialId"] as? String ?: "",
                materialName = it["materialName"] as? String ?: "",
                quantityRequired = (it["quantityRequired"] as? Number)?.toDouble() ?: 0.0,
                unit = it["unit"] as? String ?: "pcs"
            )
        }
        return BillOfMaterials(
            finishedProductId = productId,
            finishedProductName = doc.getString("finishedProductName") ?: "",
            items = items
        )
    }

    suspend fun saveBom(bom: BillOfMaterials) {
        val itemsList = bom.items.map {
            mapOf(
                "materialId" to it.materialId,
                "materialName" to it.materialName,
                "quantityRequired" to it.quantityRequired,
                "unit" to it.unit
            )
        }
        val data = mapOf(
            "finishedProductId" to bom.finishedProductId,
            "finishedProductName" to bom.finishedProductName,
            "items" to itemsList
        )
        firestore.collection("boms").document(bom.finishedProductId).set(data).await()
    }

    // --- STOCK LOGS FLOW ---
    fun getStockLogsFlow(): Flow<List<StockLog>> = callbackFlow {
        val listener = firestore.collection("stock_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    StockLog(
                        id = doc.id,
                        materialId = doc.getString("materialId") ?: "",
                        materialName = doc.getString("materialName") ?: "",
                        changeQty = doc.getDouble("changeQty") ?: 0.0,
                        remainingStock = doc.getDouble("remainingStock") ?: 0.0,
                        type = doc.getString("type") ?: "PURCHASE_IN",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        userId = doc.getString("userId") ?: "",
                        userEmail = doc.getString("userEmail") ?: "",
                        remarks = doc.getString("remarks") ?: ""
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // --- SEED COMPREHENSIVE AMI MASTER CATALOG ---
    suspend fun seedInitialAmiData() {
        val rawMaterialsCol = firestore.collection("raw_materials")
        val finishedProductsCol = firestore.collection("finished_products")
        val bomsCol = firestore.collection("boms")

        // 1. Common Materials (25 items)
        val commonMaterials = listOf(
            // Bottle
            RawMaterial(id = "mat_bot_af_200", name = "AF Empty Bottle 200ml", category = "Bottle", currentStock = 1200.0, unit = "pcs", minStockThreshold = 250.0, reorderQty = 1000.0, leadTimeDays = 7),
            RawMaterial(id = "mat_bot_af_ref", name = "AF Refill Bottle", category = "Bottle", currentStock = 1000.0, unit = "pcs", minStockThreshold = 200.0, reorderQty = 800.0, leadTimeDays = 7),
            RawMaterial(id = "mat_bot_cf_35", name = "CF Empty Bottle 35ml", category = "Bottle", currentStock = 1500.0, unit = "pcs", minStockThreshold = 300.0, reorderQty = 1200.0, leadTimeDays = 7),
            RawMaterial(id = "mat_bot_cf_ref", name = "CF Refill Bottle", category = "Bottle", currentStock = 900.0, unit = "pcs", minStockThreshold = 200.0, reorderQty = 800.0, leadTimeDays = 7),

            // Bottle Cap
            RawMaterial(id = "mat_cap_afr_out", name = "Bottle outer cap (AFR)", category = "Bottle Cap", currentStock = 1100.0, unit = "pcs", minStockThreshold = 250.0, reorderQty = 1000.0, leadTimeDays = 5),
            RawMaterial(id = "mat_cap_cfr_out", name = "Bottle outer cap (CFR)", category = "Bottle Cap", currentStock = 950.0, unit = "pcs", minStockThreshold = 200.0, reorderQty = 800.0, leadTimeDays = 5),
            RawMaterial(id = "mat_cap_cf_black_in", name = "Inner Cap (Black - CF)", category = "Bottle Cap", currentStock = 1400.0, unit = "pcs", minStockThreshold = 300.0, reorderQty = 1200.0, leadTimeDays = 5),
            RawMaterial(id = "mat_cap_af_std_in", name = "Inner Cap (Standard - AF)", category = "Bottle Cap", currentStock = 1300.0, unit = "pcs", minStockThreshold = 250.0, reorderQty = 1000.0, leadTimeDays = 5),

            // Box
            RawMaterial(id = "mat_box_afr_brown", name = "Brown Box (AFR)", category = "Box", currentStock = 850.0, unit = "pcs", minStockThreshold = 200.0, reorderQty = 800.0, leadTimeDays = 6),
            RawMaterial(id = "mat_box_cfr_brown", name = "Brown Box (CFR)", category = "Box", currentStock = 800.0, unit = "pcs", minStockThreshold = 200.0, reorderQty = 800.0, leadTimeDays = 6),
            RawMaterial(id = "mat_box_courier_1", name = "Courier Box 1", category = "Box", currentStock = 500.0, unit = "pcs", minStockThreshold = 100.0, reorderQty = 500.0, leadTimeDays = 4),
            RawMaterial(id = "mat_box_courier_2", name = "Courier Box 2", category = "Box", currentStock = 450.0, unit = "pcs", minStockThreshold = 100.0, reorderQty = 500.0, leadTimeDays = 4),
            RawMaterial(id = "mat_box_courier_3", name = "Courier Box 3", category = "Box", currentStock = 400.0, unit = "pcs", minStockThreshold = 100.0, reorderQty = 400.0, leadTimeDays = 4),
            RawMaterial(id = "mat_box_courier_4", name = "Courier Box 4", category = "Box", currentStock = 350.0, unit = "pcs", minStockThreshold = 100.0, reorderQty = 400.0, leadTimeDays = 4),

            // Packaging & Accessories
            RawMaterial(id = "mat_pkg_cf_envelope", name = "Envelope (CF)", category = "Packaging", currentStock = 1200.0, unit = "pcs", minStockThreshold = 250.0, reorderQty = 1000.0, leadTimeDays = 4),
            RawMaterial(id = "mat_pkg_hanging_tag", name = "Hanging Tag", category = "Packaging", currentStock = 1400.0, unit = "pcs", minStockThreshold = 300.0, reorderQty = 1200.0, leadTimeDays = 4),
            RawMaterial(id = "mat_pkg_cf_tray", name = "Holding Tray (CF)", category = "Packaging", currentStock = 1300.0, unit = "pcs", minStockThreshold = 250.0, reorderQty = 1000.0, leadTimeDays = 5),
            RawMaterial(id = "mat_pkg_string", name = "String", category = "Packaging", currentStock = 2000.0, unit = "pcs", minStockThreshold = 400.0, reorderQty = 1500.0, leadTimeDays = 3),

            // Raw Base Chemicals
            RawMaterial(id = "mat_base_heavy_water", name = "Heavy Water", category = "Raw Material Base", currentStock = 50000.0, unit = "ml", minStockThreshold = 10000.0, reorderQty = 40000.0, leadTimeDays = 3),
            RawMaterial(id = "mat_base_sarvonol", name = "Sarvonol", category = "Raw Material Base", currentStock = 25000.0, unit = "ml", minStockThreshold = 5000.0, reorderQty = 20000.0, leadTimeDays = 3),

            // Paper
            RawMaterial(id = "mat_paper_a4_sheets", name = "A4 Paper (Sheets)", category = "Paper", currentStock = 1500.0, unit = "sheets", minStockThreshold = 300.0, reorderQty = 1000.0, leadTimeDays = 2),

            // Roll
            RawMaterial(id = "mat_roll_shrink_af", name = "Shrinkage Roll (AF)", category = "Roll", currentStock = 800.0, unit = "pcs", minStockThreshold = 150.0, reorderQty = 600.0, leadTimeDays = 4),
            RawMaterial(id = "mat_roll_shrink_cf", name = "Shrinkage Roll (CF)", category = "Roll", currentStock = 900.0, unit = "pcs", minStockThreshold = 150.0, reorderQty = 700.0, leadTimeDays = 4),
            RawMaterial(id = "mat_roll_tape", name = "Tape Roll", category = "Roll", currentStock = 120.0, unit = "pcs", minStockThreshold = 30.0, reorderQty = 100.0, leadTimeDays = 3),
            RawMaterial(id = "mat_roll_tsc_printer", name = "TSC printer black roll", category = "Roll", currentStock = 45.0, unit = "pcs", minStockThreshold = 10.0, reorderQty = 40.0, leadTimeDays = 4)
        )

        // 2. 30 Fragrance Scents (6 Editions x 5 each)
        data class FragranceInfo(val edition: String, val name: String)
        val fragrances = listOf(
            FragranceInfo("Aquatic Edition", "Aqua Marine"),
            FragranceInfo("Aquatic Edition", "Crystal Water"),
            FragranceInfo("Aquatic Edition", "Himalayan Ice"),
            FragranceInfo("Aquatic Edition", "Indian Ocean"),
            FragranceInfo("Aquatic Edition", "Sea Salt"),

            FragranceInfo("Arabic Edition", "Amber Bakhoor"),
            FragranceInfo("Arabic Edition", "Arabian Nights"),
            FragranceInfo("Arabic Edition", "Majestic Oud"),
            FragranceInfo("Arabic Edition", "Sands of Arabia"),
            FragranceInfo("Arabic Edition", "White Desert"),

            FragranceInfo("Floral Edition", "French Rose"),
            FragranceInfo("Floral Edition", "Hibiscus Flora"),
            FragranceInfo("Floral Edition", "Lavender Bliss"),
            FragranceInfo("Floral Edition", "Lily Gardenia"),
            FragranceInfo("Floral Edition", "Orchid Flora"),

            FragranceInfo("Fruity Edition", "Blueberry Burst"),
            FragranceInfo("Fruity Edition", "Green Apple"),
            FragranceInfo("Fruity Edition", "Lemon Zest"),
            FragranceInfo("Fruity Edition", "Pineapple Crust"),
            FragranceInfo("Fruity Edition", "Strawberry Bliss"),

            FragranceInfo("Organic Edition", "Camphor (Kapur)"),
            FragranceInfo("Organic Edition", "First Rainn"),
            FragranceInfo("Organic Edition", "Lemongrass"),
            FragranceInfo("Organic Edition", "Organic"),
            FragranceInfo("Organic Edition", "Sacred Sandal"),

            FragranceInfo("Special Edition", "Cherry Blossom"),
            FragranceInfo("Special Edition", "Floral Fantasy"),
            FragranceInfo("Special Edition", "Morning Fresh"),
            FragranceInfo("Special Edition", "Vanilla Bliss"),
            FragranceInfo("Special Edition", "Velvet Musk")
        )

        var batch = firestore.batch()
        var opCount = 0

        // Upload Common Materials
        for (m in commonMaterials) {
            val docRef = rawMaterialsCol.document(m.id)
            val data = mapOf(
                "name" to m.name,
                "category" to m.category,
                "currentStock" to m.currentStock,
                "unit" to m.unit,
                "minStockThreshold" to m.minStockThreshold,
                "reorderQty" to m.reorderQty,
                "leadTimeDays" to m.leadTimeDays
            )
            batch.set(docRef, data)
            opCount++
        }

        // Upload Fragrance Raw Oils AND Ready-Made Liquids (AF/AFR and CF/CFR)
        val readyMadeLiquidAfIds = mutableMapOf<String, String>()
        val readyMadeLiquidCfIds = mutableMapOf<String, String>()

        for (f in fragrances) {
            val slug = f.name.lowercase().replace(" ", "_").replace("(", "").replace(")", "")
            val oilMatId = "mat_frag_$slug"
            val liquidAfMatId = "mat_liq_af_$slug"
            val liquidCfMatId = "mat_liq_cf_$slug"

            readyMadeLiquidAfIds[f.name] = liquidAfMatId
            readyMadeLiquidCfIds[f.name] = liquidCfMatId

            // 1. Pure Fragrance Oil
            batch.set(
                rawMaterialsCol.document(oilMatId), mapOf(
                    "name" to "Fragrance Oil - ${f.name} (${f.edition})",
                    "category" to "Fragrance Concentrate",
                    "currentStock" to 5000.0,
                    "unit" to "ml",
                    "minStockThreshold" to 1000.0,
                    "reorderQty" to 4000.0,
                    "leadTimeDays" to 5
                )
            )

            // 2. Ready-Made Liquid (AF/AFR) - Formulated: 6% Oil, 62.67% Sarvonol, 31.33% Water
            batch.set(
                rawMaterialsCol.document(liquidAfMatId), mapOf(
                    "name" to "Ready-Made Liquid (AF/AFR) - ${f.name}",
                    "category" to "Ready-Made Liquid",
                    "currentStock" to 15000.0,
                    "unit" to "ml",
                    "minStockThreshold" to 3000.0,
                    "reorderQty" to 10000.0,
                    "leadTimeDays" to 2
                )
            )

            // 3. Ready-Made Liquid (CF/CFR) - Formulated: 15% Oil, 42.5% Sarvonol, 42.5% Water
            batch.set(
                rawMaterialsCol.document(liquidCfMatId), mapOf(
                    "name" to "Ready-Made Liquid (CF/CFR) - ${f.name}",
                    "category" to "Ready-Made Liquid",
                    "currentStock" to 10000.0,
                    "unit" to "ml",
                    "minStockThreshold" to 2000.0,
                    "reorderQty" to 8000.0,
                    "leadTimeDays" to 2
                )
            )
            opCount += 3
        }

        batch.commit().await()
        batch = firestore.batch()
        opCount = 0

        // 3. 120 Finished Products & Accurate BOMs
        data class ProductLine(val categoryName: String, val skuPrefix: String)
        val productLines = listOf(
            ProductLine("Air Freshener (AF)", "AMI-Air"),
            ProductLine("Air Freshener Refill (AFR)", "AMI-Air-Ref"),
            ProductLine("Car Perfume (CF)", "AMI-Car"),
            ProductLine("Car Perfume Refill (CFR)", "AMI-Car-Ref")
        )

        for (line in productLines) {
            fragrances.forEachIndexed { index, fragrance ->
                val code = String.format("%02d", index + 1)
                val sku = "${line.skuPrefix}-$code"
                val prodId = "prod_${sku.lowercase().replace("-", "_")}"
                val prodName = "${line.categoryName} - ${fragrance.name}"

                val prodRef = finishedProductsCol.document(prodId)
                batch.set(
                    prodRef, mapOf(
                        "name" to prodName,
                        "category" to line.categoryName,
                        "sku" to sku,
                        "currentStock" to 50
                    )
                )

                // The BOM consumes AF/AFR liquid or CF/CFR liquid + Packaging!
                val isAfLine = line.categoryName.startsWith("Air")
                val liquidMatId = if (isAfLine) (readyMadeLiquidAfIds[fragrance.name] ?: "") else (readyMadeLiquidCfIds[fragrance.name] ?: "")
                val liquidMatName = if (isAfLine) "Ready-Made Liquid (AF/AFR) - ${fragrance.name}" else "Ready-Made Liquid (CF/CFR) - ${fragrance.name}"

                val bomItems: List<BomItem> = when (line.categoryName) {
                    "Air Freshener (AF)" -> listOf(
                        BomItem(liquidMatId, liquidMatName, 200.0, "ml"),
                        BomItem("mat_bot_af_200", "AF Empty Bottle 200ml", 1.0, "pcs"),
                        BomItem("mat_cap_af_std_in", "Inner Cap (Standard - AF)", 1.0, "pcs"),
                        BomItem("mat_roll_shrink_af", "Shrinkage Roll (AF)", 1.0, "pcs")
                    )
                    "Air Freshener Refill (AFR)" -> listOf(
                        BomItem(liquidMatId, liquidMatName, 200.0, "ml"),
                        BomItem("mat_bot_af_ref", "AF Refill Bottle", 1.0, "pcs"),
                        BomItem("mat_cap_afr_out", "Bottle outer cap (AFR)", 1.0, "pcs"),
                        BomItem("mat_box_afr_brown", "Brown Box (AFR)", 1.0, "pcs")
                    )
                    "Car Perfume (CF)" -> listOf(
                        BomItem(liquidMatId, liquidMatName, 35.0, "ml"),
                        BomItem("mat_bot_cf_35", "CF Empty Bottle 35ml", 1.0, "pcs"),
                        BomItem("mat_cap_cf_black_in", "Inner Cap (Black - CF)", 1.0, "pcs"),
                        BomItem("mat_pkg_cf_tray", "Holding Tray (CF)", 1.0, "pcs"),
                        BomItem("mat_pkg_hanging_tag", "Hanging Tag", 1.0, "pcs"),
                        BomItem("mat_pkg_string", "String", 1.0, "pcs"),
                        BomItem("mat_pkg_cf_envelope", "Envelope (CF)", 1.0, "pcs"),
                        BomItem("mat_roll_shrink_cf", "Shrinkage Roll (CF)", 1.0, "pcs")
                    )
                    else -> listOf( // Car Perfume Refill (CFR)
                        BomItem(liquidMatId, liquidMatName, 35.0, "ml"),
                        BomItem("mat_bot_cf_ref", "CF Refill Bottle", 1.0, "pcs"),
                        BomItem("mat_cap_cfr_out", "Bottle outer cap (CFR)", 1.0, "pcs"),
                        BomItem("mat_box_cfr_brown", "Brown Box (CFR)", 1.0, "pcs")
                    )
                }

                val bomRef = bomsCol.document(prodId)
                batch.set(
                    bomRef, mapOf(
                        "finishedProductId" to prodId,
                        "finishedProductName" to prodName,
                        "items" to bomItems.map {
                            mapOf(
                                "materialId" to it.materialId,
                                "materialName" to it.materialName,
                                "quantityRequired" to it.quantityRequired,
                                "unit" to it.unit
                            )
                        }
                    )
                )

                opCount += 2
                if (opCount >= 250) {
                    batch.commit()
                    batch = firestore.batch()
                    opCount = 0
                }
            }
        }

        if (opCount > 0) {
            batch.commit().await()
        }
    }
}
