package com.example.final_project

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.final_project.ui.theme.Final_projectTheme
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

// --- 資料模型 ---
@Serializable
data class Trip(
    val id: String = System.currentTimeMillis().toString(),
    var name: String,
    var icon: String = "✈️",
    var dateRange: String = ""
)

@Serializable
data class ItineraryItem(
    val id: Long = System.currentTimeMillis(),
    val tripId: String,
    var date: String,
    var time: String,
    var description: String,
    var category: String,
    var isFavorite: Boolean = false
)

@Serializable
data class TripBackup(
    val meta: Trip,
    val content: List<ItineraryItem>
)

private val tripJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

// --- 分類設定 ---
enum class Category(val label: String, val color: Color, val icon: String) {
    SPOT("景點", Color(0xFF28A745), "📸"),
    FOOD("美食", Color(0xFFFF8C00), "🍜"),
    TRAFFIC("交通", Color(0xFF007BFF), "🚌"),
    STAY("住宿", Color(0xFF6F42C1), "🏨"),
    OTHER("其他", Color(0xFF6C757D), "📝")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Final_projectTheme {
                TripApp()
            }
        }
    }
}

@Composable
fun TripApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val trips = remember { mutableStateListOf<Trip>() }
    val itineraries = remember { mutableStateListOf<ItineraryItem>() }

    fun saveData() {
        val prefs = context.getSharedPreferences("trip_data", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("trips_json", tripJson.encodeToString(trips.toList()))
            putString("itineraries_json", tripJson.encodeToString(itineraries.toList()))
            apply()
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("trip_data", Context.MODE_PRIVATE)
        val tripsStr = prefs.getString("trips_json", null)
        val itinerariesStr = prefs.getString("itineraries_json", null)
        try {
            if (!tripsStr.isNullOrEmpty()) {
                trips.clear()
                trips.addAll(tripJson.decodeFromString<List<Trip>>(tripsStr))
            }
            if (!itinerariesStr.isNullOrEmpty()) {
                itineraries.clear()
                itineraries.addAll(tripJson.decodeFromString<List<ItineraryItem>>(itinerariesStr))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(
                trips = trips,
                onTripClick = { navController.navigate("details/${it.id}") },
                onUpdateTrip = { trip ->
                    val index = trips.indexOfFirst { it.id == trip.id }
                    if (index != -1) { trips[index] = trip; saveData() }
                },
                onAddTrip = { name, icon, range ->
                    trips.add(Trip(name = name, icon = icon, dateRange = range))
                    saveData()
                },
                onDeleteTrip = { trip ->
                    trips.remove(trip)
                    itineraries.removeAll { it.tripId == trip.id }
                    saveData()
                },
                onScanClick = { navController.navigate("scanner") }
            )
        }
        composable("details/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            val trip = trips.find { it.id == tripId }
            ItineraryScreen(
                trip = trip,
                allItineraries = itineraries,
                onBack = { navController.popBackStack() },
                onAddItem = { item -> itineraries.add(item); saveData() },
                onUpdateItem = { updatedItem ->
                    val index = itineraries.indexOfFirst { it.id == updatedItem.id }
                    if (index != -1) { itineraries[index] = updatedItem; saveData() }
                },
                onDeleteItem = { item -> itineraries.remove(item); saveData() },
                onToggleFavorite = { item ->
                    val index = itineraries.indexOfFirst { it.id == item.id }
                    if (index != -1) { itineraries[index] = itineraries[index].copy(isFavorite = !itineraries[index].isFavorite); saveData() }
                }
            )
        }
        composable("scanner") {
            ScannerScreen(
                onResult = { jsonStr ->
                    try {
                        val backup = tripJson.decodeFromString<TripBackup>(jsonStr)
                        val newTripId = System.currentTimeMillis().toString()
                        val newTrip = backup.meta.copy(id = newTripId, name = backup.meta.name + " (匯入)")
                        trips.add(newTrip)
                        backup.content.forEach { item -> itineraries.add(item.copy(id = System.currentTimeMillis() + item.hashCode(), tripId = newTripId)) }
                        saveData()
                        Toast.makeText(context, "匯入成功", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    } catch (e: Exception) { Toast.makeText(context, "解析失敗", Toast.LENGTH_SHORT).show() }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    trips: List<Trip>,
    onTripClick: (Trip) -> Unit,
    onUpdateTrip: (Trip) -> Unit,
    onAddTrip: (String, String, String) -> Unit,
    onDeleteTrip: (Trip) -> Unit,
    onScanClick: () -> Unit
) {
    var showSettingsDialog by remember { mutableStateOf<Trip?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("🌍 我的旅程存檔", color = Color(0xFF008080), fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onScanClick) { Icon(Icons.Default.QrCodeScanner, null) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Color(0xFF008080)) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Text("長按行程卡片可進入存檔設定", modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 12.sp)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(trips) { trip ->
                    TripGridCard(
                        trip = trip,
                        onClick = { onTripClick(trip) },
                        onLongClick = { showSettingsDialog = trip },
                        onDelete = { onDeleteTrip(trip) }
                    )
                }
            }
        }

        if (showAddDialog) {
            TripSettingsDialog(
                title = "建立新旅程",
                onDismiss = { showAddDialog = false },
                onConfirm = { name, icon, range ->
                    onAddTrip(name, icon, range)
                    showAddDialog = false
                }
            )
        }

        showSettingsDialog?.let { trip ->
            TripSettingsDialog(
                title = "存檔設定",
                initialTrip = trip,
                onDismiss = { showSettingsDialog = null },
                onConfirm = { name, icon, range ->
                    onUpdateTrip(trip.copy(name = name, icon = icon, dateRange = range))
                    showSettingsDialog = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSettingsDialog(
    title: String,
    initialTrip: Trip? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialTrip?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(initialTrip?.icon ?: "✈️") }
    var dateRange by remember { mutableStateOf(initialTrip?.dateRange ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    val icons = listOf("✈️", "🧳", "🏝️", "🏔️", "🏕️", "🏙️", "🚂", "🚗", "🚢", "🎡", "🎢", "📷", "🛍️", "🍜", "🍻")

    if (showDatePicker) {
        val datePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = datePickerState.selectedStartDateMillis?.let { SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(it)) } ?: ""
                    val end = datePickerState.selectedEndDateMillis?.let { SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(it)) } ?: ""
                    if (start.isNotEmpty() && end.isNotEmpty()) dateRange = "$start ~ $end"
                    showDatePicker = false
                }) { Text("確定") }
            }
        ) { DateRangePicker(state = datePickerState, modifier = Modifier.height(400.dp)) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🌍 選擇圖示", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(icons) { icon ->
                        Box(
                            modifier = Modifier
                                .size(45.dp)
                                .background(if (selectedIcon == icon) Color(0xFF008080).copy(0.2f) else Color.DarkGray.copy(0.3f), RoundedCornerShape(8.dp))
                                .border(if (selectedIcon == icon) 2.dp else 0.dp, if (selectedIcon == icon) Color(0xFF008080) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) { Text(icon, fontSize = 24.sp) }
                    }
                }
                Text("📝 旅程名稱", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("日本東京之旅") }, modifier = Modifier.fillMaxWidth())

                Text("📅 旅遊日期區間 (選填)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = dateRange, onValueChange = {}, readOnly = true, enabled = false,
                    placeholder = { Text("點擊設定日期範圍...") },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline)
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, selectedIcon, dateRange) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008080))) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripGridCard(trip: Trip, onClick: () -> Unit, onLongClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(160.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(trip.icon, fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(trip.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 1)
                if (trip.dateRange.isNotEmpty()) Text(trip.dateRange, fontSize = 10.sp, color = Color.Gray)
            }
            IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(onResult: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }

    // 防止掃描重複觸發
    var isScanning by remember { mutableStateOf(true) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }
    LaunchedEffect(Unit) { if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val image = InputImage.fromBitmap(bitmap, 0)
            BarcodeScanning.getClient().process(image).addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { result -> onResult(result) } ?: Toast.makeText(context, "未發現 QR Code", Toast.LENGTH_SHORT).show()
            }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("掃描行程 QR Code") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            if (hasCameraPermission) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        ProcessCameraProvider.getInstance(ctx).addListener({
                            val cameraProvider = cameraProviderFutureGet(ProcessCameraProvider.getInstance(ctx))
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                                it.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                                    if (!isScanning) {
                                        proxy.close()
                                        return@setAnalyzer
                                    }
                                    proxy.image?.let { img ->
                                        BarcodeScanning.getClient().process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
                                            .addOnSuccessListener { codes ->
                                                if (isScanning) {
                                                    codes.firstOrNull()?.rawValue?.let { res ->
                                                        isScanning = false
                                                        onResult(res)
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener { proxy.close() }
                                    } ?: proxy.close()
                                }
                            }
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }, modifier = Modifier.fillMaxSize())
                    Box(Modifier.size(250.dp).border(2.dp, Color.White, RoundedCornerShape(12.dp)).align(Alignment.Center))
                }
            }
            Button(onClick = { galleryLauncher.launch("image/*") }, Modifier.padding(16.dp).fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008080))) {
                Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("從相簿選擇圖片")
            }
        }
    }
}

private fun cameraProviderFutureGet(future: com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider>): ProcessCameraProvider = future.get()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    trip: Trip?,
    allItineraries: List<ItineraryItem>,
    onBack: () -> Unit,
    onAddItem: (ItineraryItem) -> Unit,
    onUpdateItem: (ItineraryItem) -> Unit,
    onDeleteItem: (ItineraryItem) -> Unit,
    onToggleFavorite: (ItineraryItem) -> Unit
) {
    if (trip == null) return
    val tripItineraries = allItineraries.filter { it.tripId == trip.id }
    val uniqueDates = tripItineraries.map { it.date }.distinct().sorted()
    var selectedDate by remember(uniqueDates) { mutableStateOf(if (uniqueDates.isNotEmpty()) uniqueDates[0] else "") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ItineraryItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { showQrDialog = true }) { Icon(Icons.Default.Share, null) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Color(0xFF008080)) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (showFavoritesOnly) "❤️ 我的最愛" else "📅 行程列表", fontWeight = FontWeight.Bold, color = if (showFavoritesOnly) Color.Red else Color.DarkGray)
                Switch(checked = showFavoritesOnly, onCheckedChange = { showFavoritesOnly = it }, modifier = Modifier.scale(0.7f), colors = SwitchDefaults.colors(checkedThumbColor = Color.Red))
            }
            if (uniqueDates.isNotEmpty() && !showFavoritesOnly) {
                LazyRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uniqueDates) { date ->
                        FilterChip(selected = selectedDate == date, onClick = { selectedDate = date }, label = { Text(date.substring(5)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF008080), selectedLabelColor = Color.White))
                    }
                }
            }
            val displayItems = if (showFavoritesOnly) tripItineraries.filter { it.isFavorite }.sortedBy { it.date + it.time } else tripItineraries.filter { it.date == selectedDate }.sortedBy { it.time }
            if (displayItems.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("目前沒有行程喔！", color = Color.Gray) } }
            else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(displayItems) { item ->
                        TimelineItem(
                            item = item,
                            onToggleFavorite = { onToggleFavorite(item) },
                            onDelete = { onDeleteItem(item) },
                            onEdit = { editingItem = item }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddItineraryDialog(
                tripId = trip.id,
                onDismiss = { showAddDialog = false },
                onConfirm = {
                    onAddItem(it)
                    if (selectedDate.isEmpty()) selectedDate = it.date
                    showAddDialog = false
                }
            )
        }

        editingItem?.let { item ->
            AddItineraryDialog(
                tripId = trip.id,
                initialItem = item,
                onDismiss = { editingItem = null },
                onConfirm = { newItem ->
                    onUpdateItem(newItem.copy(id = item.id))
                    editingItem = null
                }
            )
        }

        if (showQrDialog) QrCodeDialog(Json.encodeToString(TripBackup(trip, tripItineraries)), { showQrDialog = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItineraryDialog(
    tripId: String,
    initialItem: ItineraryItem? = null,
    onDismiss: () -> Unit,
    onConfirm: (ItineraryItem) -> Unit
) {
    val cal = Calendar.getInstance()
    var date by remember { mutableStateOf(initialItem?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)) }
    var time by remember { mutableStateOf(initialItem?.time ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)) }
    var desc by remember { mutableStateOf(initialItem?.description ?: "") }
    var selectedCat by remember {
        mutableStateOf(
            if (initialItem != null) Category.entries.find { it.name == initialItem.category } ?: Category.SPOT
            else Category.SPOT
        )
    }

    var showDP by remember { mutableStateOf(false) }
    var showTP by remember { mutableStateOf(false) }

    if (showDP) {
        val state = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { showDP = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) }; showDP = false }) { Text("確定") } }) { DatePicker(state) }
    }
    if (showTP) {
        val parts = time.split(":")
        val initHour = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.HOUR_OF_DAY)
        val initMinute = parts.getOrNull(1)?.toIntOrNull() ?: cal.get(Calendar.MINUTE)

        var tempHour by remember { mutableIntStateOf(initHour) }
        var tempMinute by remember { mutableIntStateOf(initMinute) }

        AlertDialog(
            onDismissRequest = { showTP = false },
            confirmButton = {
                TextButton(onClick = {
                    time = String.format("%02d:%02d", tempHour, tempMinute)
                    showTP = false
                }) { Text("確定") }
            },
            text = {
                AndroidView(
                    factory = { ctx ->
                        // 修正：從 Theme_Holo_Light 改為 Theme_Holo (深色主題)
                        // 這會讓文字變成白色，適應深色的 Dialog 背景
                        val wrapper = android.view.ContextThemeWrapper(ctx, android.R.style.Theme_Holo_Dialog_NoActionBar)
                        android.widget.TimePicker(wrapper).apply {
                            setIs24HourView(true)
                            hour = initHour
                            minute = initMinute
                            setOnTimeChangedListener { _, h, m ->
                                tempHour = h
                                tempMinute = m
                            }
                            background = null
                        }
                    },
                    modifier = Modifier.wrapContentSize()
                )
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if(initialItem != null) "修改行程" else "新增行程", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(date, {}, label = { Text("日期") }, modifier = Modifier.weight(1f).clickable { showDP = true }, readOnly = true, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline))
                    OutlinedTextField(time, {}, label = { Text("時間") }, modifier = Modifier.weight(1f).clickable { showTP = true }, readOnly = true, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(Category.entries) { cat ->
                        Surface(modifier = Modifier.clickable { selectedCat = cat }, shape = RoundedCornerShape(20.dp), color = if (selectedCat == cat) cat.color else Color.Transparent, border = BorderStroke(1.dp, Color.LightGray)) {
                            Text("${cat.icon} ${cat.label}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = if (selectedCat == cat) Color.White else Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
                OutlinedTextField(desc, { desc = it }, label = { Text("內容") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { if (desc.isNotBlank()) onConfirm(ItineraryItem(tripId = tripId, date = date, time = time, description = desc, category = selectedCat.name)) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008080))
            ) { Text("確認") }
        }
    )
}

@Composable
fun TimelineItem(
    item: ItineraryItem,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val cat = Category.entries.find { it.name == item.category } ?: Category.OTHER

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
            Text(item.time, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Box(Modifier.width(2.dp).height(40.dp).background(Color.LightGray))
        }
        Card(
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Surface(color = cat.color, shape = RoundedCornerShape(12.dp)) { Text("${cat.icon} ${cat.label}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.description,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        modifier = Modifier.clickable {
                            val uri = Uri.parse("geo:0,0?q=${item.description}")
                            ContextCompat.startActivity(context, Intent(Intent.ACTION_VIEW, uri), null)
                        }
                    )
                }
                IconButton(onToggleFavorite) { Icon(if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (item.isFavorite) Color.Red else Color.LightGray) }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = Color.Gray, modifier = Modifier.size(20.dp)) }
                IconButton(onDelete) { Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
fun QrCodeDialog(data: String, onDismiss: () -> Unit) {
    val bm = remember(data) {
        try {
            val res = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 512, 512, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
            val bitmap = Bitmap.createBitmap(res.width, res.height, Bitmap.Config.RGB_565)
            for (x in 0 until res.width) for (y in 0 until res.height) bitmap.setPixel(x, y, if (res.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            bitmap
        } catch (e: Exception) { null }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("分享 QR Code", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { bm?.let { Image(it.asImageBitmap(), null, Modifier.size(200.dp)) }; Text("好友掃描即可匯入行程", fontSize = 12.sp, color = Color.Gray) } }, confirmButton = { Button(onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008080))) { Text("關閉") } })
}

fun Modifier.scale(scale: Float): Modifier = this.then(Modifier.layout { measurable, constraints -> val placeable = measurable.measure(constraints); layout(placeable.width, placeable.height) { placeable.placeRelativeWithLayer(0, 0) { scaleX = scale; scaleY = scale } } })