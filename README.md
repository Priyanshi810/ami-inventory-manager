# AMI Inventory Manager 📦

An Android inventory management and MRP (Manufacturing Resource Planning) system built for **AMI** to track raw materials, ready-made liquids, packaging, and finished goods via automated **Bill of Materials (BOM)** auto-deduction.

---

## 🌟 Key Features

### 1. 📦 Dedicated Finished Goods Section
* Complete inventory tracking for 120 ready-to-ship products:
  * **Air Freshener (AF - 200ml)** (30 Scents)
  * **Air Freshener Refill (AFR - 200ml)** (30 Scents)
  * **Car Perfume (CF - 35ml)** (30 Scents)
  * **Car Perfume Refill (CFR - 35ml)** (30 Scents)
* Real-time ready stock indicators with quick manual `+ / - Stock` adjustments.

### 2. 🧪 Two-Tier Manufacturing & Liquid Formulations
* **Intermediate Ready-Made Liquids**: Tracks bulk liquid stock for all 30 scents.
* **Exact Liquid Formulations**:
  * **Air Freshener (AF / AFR)**: Fragrance Oil (6.00%) + Sarvonol (62.67%) + Heavy Water (31.33%)
  * **Car Perfume (CF / CFR)**: Fragrance Oil (15.00%) + Sarvonol (42.50%) + Heavy Water (42.50%)
* **Assembly Engine**: Assembling finished goods automatically deducts the ready-made liquid and packaging (bottles, caps, boxes, shrinkage rolls) via BOM and credits finished goods inventory.

### 3. 🚚 Customer Orders & Dispatch
* Fulfill and dispatch customer orders by invoice/order number.
* Live stock availability check before dispatch.
* **Directly subtracts Finished Product inventory** upon dispatch.

### 4. 🔔 Real-Time Stock Alerts & Audit Trail
* Automated low-stock warning badges based on material lead times and reorder thresholds.
* Comprehensive chronological audit log for all material receipts, order dispatches, batch assemblies, and liquid mixes.
* Role-based access control (`ADMIN`, `PRODUCTION`, `INVENTORY`).

---

## 🛠️ Tech Stack

* **Platform:** Android (API 26+)
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose & Material 3
* **Architecture:** MVVM + Clean Architecture with Kotlin StateFlow & Coroutines
* **Backend & Database:** Google Firebase (Cloud Firestore + Firebase Authentication)

---

## 🚀 Getting Started

### Prerequisites
* [Android Studio](https://developer.android.com/studio) (Rabbit 2 or newer recommended)
* Android SDK (API 26+)
* Physical Android phone with USB Debugging enabled OR an Android Virtual Device (Emulator)

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/<YOUR_USERNAME>/ami-inventory-manager.git
   ```
2. Open the project folder in **Android Studio**.
3. Place your `google-services.json` inside the `app/` folder (`app/google-services.json`).
4. Let Gradle sync and download dependencies.
5. Click the green **Run (▶)** button to launch on your device!
