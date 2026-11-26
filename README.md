# StockSense – AMI Inventory Management System 

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Backend-Firebase%20Firestore-orange.svg)](https://firebase.google.com)

**StockSense** is an Android-based manufacturing resource planning (MRP) and real-time inventory tracking system engineered specifically for **AMI**. It manages raw materials, chemical bulk liquids, packaging stock, and ready-to-ship finished goods through automated **Bill of Materials (BOM)** auto-deduction.

> **Status:** Interim Progress Milestone (Submission: 26 November 2026)

---

## Development Team & Roles

| Contributor | GitHub Profile | Responsibilities |
| :--- | :--- | :--- |
| **Priyanshi** *(Project Lead)* | [`@Priyanshi810`](https://github.com/Priyanshi810) | Architecture, Firebase Firestore/Auth, BOM Engine, Models & Scaffold |
| **Divya** | [`@Divya4f`](https://github.com/Divya4f) | Finished Goods catalog UI, Customer Order Dispatch flow |
| **Priyanshi Patel** | [`@PriyanshiPatel3015`](https://github.com/PriyanshiPatel3015) | Production assembly, Dual-tier liquid mixing formulas, Raw Materials UI |

---

##  Core Modules Completed (Phase 1)

### 1.  Dedicated Finished Goods Section
* Complete inventory tracking for 120 ready-to-ship products:
  * **Air Freshener (AF - 200ml)** (30 Scents: `AMI-Air-01` to `AMI-Air-30`)
  * **Air Freshener Refill (AFR - 200ml)** (30 Scents: `AMI-Air-Ref-01` to `AMI-Air-Ref-30`)
  * **Car Perfume (CF - 35ml)** (30 Scents: `AMI-Car-01` to `AMI-Car-30`)
  * **Car Perfume Refill (CFR - 35ml)** (30 Scents: `AMI-Car-Ref-01` to `AMI-Car-Ref-30`)
* Real-time ready stock indicators with quick manual `+ / - Stock` adjustments.

### 2.  Two-Tier Manufacturing & Liquid Formulations
* **Intermediate Ready-Made Liquids**: Tracks bulk liquid stock for all 30 scents.
* **Exact Liquid Formulations**:
  * **Air Freshener (AF / AFR)**: Fragrance Oil (6.00%) + Sarvonol (62.67%) + Heavy Water (31.33%)
  * **Car Perfume (CF / CFR)**: Fragrance Oil (15.00%) + Sarvonol (42.50%) + Heavy Water (42.50%)
* **Assembly Engine**: Assembling finished goods automatically deducts ready-made liquid and packaging (bottles, caps, boxes, shrinkage rolls) via BOM and credits finished goods inventory.

### 3.  Customer Orders & Dispatch
* Fulfill and dispatch customer orders by invoice/order number.
* Live stock availability check before dispatch.
* **Directly subtracts Finished Product inventory** upon dispatch.

### 4.  Real-Time Stock Alerts & Audit Trail
* Automated low-stock warning badges based on material lead times and reorder thresholds.
* Comprehensive chronological audit log for all material receipts, order dispatches, batch assemblies, and liquid mixes.
* Role-based access control (`ADMIN`, `PRODUCTION`, `INVENTORY`).

---

##  Next Steps — Features to be Added (Phase 2)

As this is an ongoing project, the following features are scheduled for Phase 2:
1. **Barcode / QR Code Scanning**: CameraX + Google ML Kit integration to scan incoming supplier materials and outgoing dispatch boxes.
2. **Push Notifications**: Firebase Cloud Messaging (FCM) alerts sent directly to procurement managers when materials reach reorder thresholds.
3. **Data Export**: Export stock balances, audit logs, and dispatch reports into Excel / CSV spreadsheets.
4. **Supplier Management**: Dedicated purchase order (PO) generation and supplier lead time tracking.
5. **Consumption Charts**: Graphical reporting of material usage trends using MPAndroidChart / Compose Canvas.

---

##  Tech Stack

* **Platform:** Android (API 26+)
* **Language:** Kotlin 2.2.10
* **UI Framework:** Jetpack Compose & Material 3
* **Concurrency:** Kotlin Coroutines & Reactive StateFlow
* **Backend:** Google Firebase (Cloud Firestore + Firebase Authentication)
* **Version Control:** Git & GitHub

---

##  Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/Priyanshi810/ami-inventory-manager.git
   ```
2. Open the project in **Android Studio** (Rabbit 2 or newer).
3. Place your `google-services.json` inside the `app/` folder.
4. Let Gradle sync and click the green **Run (▶)** button!
