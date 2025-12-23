# TripFlow--Smart_Itinerar_ Manager_Share

# ✈️ TripFlow - Android 旅遊行程管理助手

**TripFlow** 是一款基於 **Jetpack Compose** 開發的現代化 Android 旅遊規劃應用程式。它提供直觀的介面來管理旅程、規劃詳細時間軸，並透過 QR Code 技術實現行程的快速分享與匯入。

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)
![Material3](https://img.shields.io/badge/Material3-757575?style=for-the-badge&logo=materialdesign&logoColor=white)

## ✨ 主要功能 (Features)

### 1. 🌍 旅程儀表板
* **視覺化管理**：使用卡片式設計展示所有旅程，支援自定義圖示 (Icon) 與日期區間。
* **CRUD 操作**：輕鬆新增、修改（長按卡片）、刪除旅程。
* **本地儲存**：自動保存資料至 SharedPreferences，確保數據持久化。

### 2. 📅 行程細節規劃
* **時間軸檢視**：依據日期與時間自動排序行程，清晰呈現每日活動。
* **分類標籤**：內建 5 種分類（📸 景點、🍜 美食、🚌 交通、🏨 住宿、📝 其他），並以不同顏色區分。
* **地圖連動**：點擊行程項目即可直接喚起 **Google Maps** 進行導航。
* **預算控制**：可以設定旅行預算，並告知剩餘預算量。
* **我的最愛**：可標記重點行程，並支援「僅顯示最愛」篩選模式。

### 3. 🤝 QR Code 分享與匯入
* **行程打包**：將整趟旅程及其細項序列化為 JSON，生成 QR Code 供朋友掃描。
* **雙重匯入模式**：
    * **相機掃描**：整合 CameraX 與 ML Kit，實時掃描 QR Code。
    * **相簿讀取**：支援從圖庫選擇 QR Code 圖片進行匯入。

## 🛠️ 技術棧 (Tech Stack)

本專案完全採用 **Modern Android Development (MAD)** 標準構建：

* **UI Toolkit**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material3)
* **Language**: Kotlin
* **Navigation**: AndroidX Navigation Compose
* **Data Serialization**: Kotlinx Serialization (JSON)
* **Camera & Scanning**:
    * AndroidX CameraX (Preview, ImageAnalysis)
    * Google ML Kit (Barcode Scanning)
    * ZXing (QR Code Generation)
* **Async**: Coroutines, Executors
* **Permissions**: Activity Result API

## 📦 安裝依賴 (Dependencies)

若要執行此專案，請確保你的 `build.gradle.kts` (Module) 包含以下依賴：

```kotlin
dependencies {
    // Jetpack Compose & Material3
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")
    
    // CameraX (相機功能)
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    
    // ML Kit & ZXing (掃描與生成)
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.google.zxing:core:3.5.3")
    
    // Serialization (資料處理)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

```

## 🚀 如何使用 (User Guide)

1. **建立旅程**：點擊首頁右下角 `+`，輸入名稱（如：大阪五日遊）、選擇圖示並設定日期。
2. **編輯設定**：在首頁 **長按** 旅程卡片，可修改名稱或圖示。
3. **新增行程**：點擊進入旅程，使用 `+` 新增具體時間點的活動（支援日期/時間選擇器）。
4. **導航**：在行程列表中，直接點擊行程文字描述，App 會自動跳轉 Google Maps 搜尋該地點。
5. **分享行程**：
* 點擊詳情頁右上角的「分享圖示」生成 QR Code。
* 朋友在首頁點擊右上角的「掃描圖示」，掃描後即可將完整行程匯入他們的手機。



## ⚠️ 權限說明

* `android.permission.CAMERA`: 必須權限，用於掃描 QR Code。
* `Read External Storage` (Gallery): 選用權限，用於從相簿選取 QR Code 圖片。

## 🤝 貢獻 (Contributing)

歡迎提交 Issue 或 Pull Request！
如果你有更好的資料庫實作方案（如遷移至 Room Database），歡迎貢獻程式碼。

## 📄 授權 (License)

[MIT License](https://www.google.com/search?q=LICENSE)

```

```
