package com.example.final_project

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// --- 資料模型 ---

@Serializable
data class Trip(val id: String = System.currentTimeMillis().toString(), var name: String, var icon: String = "✈️", var dateRange: String = "", var budget: Double = 0.0)

@Serializable
data class ItineraryItem(val id: Long = System.currentTimeMillis(), val tripId: String, var date: String, var time: String, var description: String, var category: String, var isFavorite: Boolean = false, var cost: Double = 0.0)

@Serializable
data class TripBackup(val meta: Trip, val content: List<ItineraryItem>)

@Serializable
data class FullBackup(val trips: List<Trip>, val itineraries: List<ItineraryItem>)

private val tripJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

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
        setContent { Final_projectTheme { TripApp() } }
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

    fun exportData(selectedTrips: List<Trip>) {
        try {
            val selectedTripIds = selectedTrips.map { it.id }.toSet()
            val relevantItineraries = itineraries.filter { it.tripId in selectedTripIds }
            val backup = FullBackup(selectedTrips, relevantItineraries)
            val jsonStr = tripJson.encodeToString(backup)
            val fileName = "TripFlow_Export_${System.currentTimeMillis()}.json"
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            FileOutputStream(file).use { it.write(jsonStr.toByteArray()) }
            Toast.makeText(context, "已匯出 ${selectedTrips.size} 個旅程至下載資料夾", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "匯出失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    fun importData(uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val jsonStr = inputStream?.bufferedReader().use { it?.readText() } ?: ""
            if (jsonStr.isNotEmpty()) {
                val backup = tripJson.decodeFromString<FullBackup>(jsonStr)
                val idMapping = mutableMapOf<String, String>()

                val newTrips = backup.trips.map { oldTrip ->
                    val newId = System.currentTimeMillis().toString() + Random().nextInt(1000)
                    idMapping[oldTrip.id] = newId
                    oldTrip.copy(id = newId)
                }

                val newItineraries = backup.itineraries.mapNotNull { item ->
                    val newTripId = idMapping[item.tripId]
                    if (newTripId != null) {
                        item.copy(id = System.currentTimeMillis() + Random().nextInt(10000), tripId = newTripId)
                    } else null
                }

                trips.addAll(newTrips)
                itineraries.addAll(newItineraries)
                saveData()
                Toast.makeText(context, "成功匯入 ${newTrips.size} 個旅程", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "匯入失敗: 格式錯誤", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importData(it) }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("trip_data", Context.MODE_PRIVATE)
        try {
            prefs.getString("trips_json", null)?.let { trips.addAll(tripJson.decodeFromString<List<Trip>>(it)) }
            prefs.getString("itineraries_json", null)?.let { itineraries.addAll(tripJson.decodeFromString<List<ItineraryItem>>(it)) }
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
                onAddTrip = { name, icon, range, budget -> trips.add(Trip(name = name, icon = icon, dateRange = range, budget = budget)); saveData() },
                onDeleteTrip = { trip -> trips.remove(trip); itineraries.removeAll { it.tripId == trip.id }; saveData() },
                onScanClick = { navController.navigate("scanner") },
                onExportSelected = { selectedTrips -> exportData(selectedTrips) },
                onImportClick = { importLauncher.launch("application/json") }
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

// --- Dashboard ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    trips: List<Trip>,
    onTripClick: (Trip) -> Unit,
    onUpdateTrip: (Trip) -> Unit,
    onAddTrip: (String, String, String, Double) -> Unit,
    onDeleteTrip: (Trip) -> Unit,
    onScanClick: () -> Unit,
    onExportSelected: (List<Trip>) -> Unit,
    onImportClick: () -> Unit
) {
    var showSettingsDialog by remember { mutableStateOf<Trip?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var tripToDelete by remember { mutableStateOf<Trip?>(null) }
    var currentTab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTripIds by remember { mutableStateOf(setOf<String>()) }

    fun exitSelectionMode() { isSelectionMode = false; selectedTripIds = emptySet() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { if (isSelectionMode) Text("已選擇 ${selectedTripIds.size} 個", color = Color.White) else Text("🌍 我的旅程存檔", color = Color(0xFF008080), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = if (isSelectionMode) Color(0xFF008080) else MaterialTheme.colorScheme.surface),
                navigationIcon = { if (isSelectionMode) IconButton({ exitSelectionMode() }) { Icon(Icons.Default.Close, "取消", tint = Color.White) } },
                actions = {
                    if (isSelectionMode) {
                        IconButton({
                            val selectedTrips = trips.filter { it.id in selectedTripIds }
                            if (selectedTrips.isNotEmpty()) { onExportSelected(selectedTrips); exitSelectionMode() }
                        }) { Icon(Icons.Default.Check, "確認匯出", tint = Color.White) }
                    } else {
                        IconButton(onScanClick) { Icon(Icons.Default.QrCodeScanner, null) }
                        Box {
                            IconButton({ showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(showMenu, { showMenu = false }) {
                                DropdownMenuItem({ Text("匯出 (選擇行程)") }, { isSelectionMode = true; showMenu = false }, leadingIcon = { Icon(Icons.Default.CheckBox, null) })
                                DropdownMenuItem({ Text("匯入 JSON") }, { onImportClick(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Download, null) })
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isSelectionMode) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(icon = { Icon(Icons.Default.List, null) }, label = { Text("列表") }, selected = currentTab == 0, onClick = { currentTab = 0 }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF008080), selectedTextColor = Color(0xFF008080)))
                    NavigationBarItem(icon = { Icon(Icons.Default.DateRange, null) }, label = { Text("日曆") }, selected = currentTab == 1, onClick = { currentTab = 1 }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF008080), selectedTextColor = Color(0xFF008080)))
                }
            }
        },
        floatingActionButton = {
            if (currentTab == 0 && !isSelectionMode) FloatingActionButton({ showAddDialog = true }, containerColor = Color(0xFF008080)) { Icon(Icons.Default.Add, null, tint = Color.White) }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (currentTab == 0) {
                Text(
                    if (isSelectionMode) "點擊卡片以選取，完成後按右上角打勾" else "長按行程卡片可進入存檔設定",
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center,
                    color = if(isSelectionMode) Color(0xFF008080) else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = if(isSelectionMode) FontWeight.Bold else FontWeight.Normal
                )
                LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                    items(trips) { trip ->
                        TripGridCard(
                            trip = trip,
                            isSelectionMode = isSelectionMode,
                            isSelected = trip.id in selectedTripIds,
                            onClick = { if (isSelectionMode) selectedTripIds = if (trip.id in selectedTripIds) selectedTripIds - trip.id else selectedTripIds + trip.id else onTripClick(trip) },
                            onLongClick = { if (!isSelectionMode) showSettingsDialog = trip },
                            onDelete = { if (!isSelectionMode) tripToDelete = trip }
                        )
                    }
                }
            } else {
                TripCalendar(trips)
            }
        }
        if (showAddDialog) TripSettingsDialog("建立新旅程", null, { showAddDialog = false }) { name, icon, range, budget -> onAddTrip(name, icon, range, budget); showAddDialog = false }
        showSettingsDialog?.let { trip -> TripSettingsDialog("存檔設定", trip, { showSettingsDialog = null }) { name, icon, range, budget -> onUpdateTrip(trip.copy(name = name, icon = icon, dateRange = range, budget = budget)); showSettingsDialog = null } }
        tripToDelete?.let { trip ->
            AlertDialog(onDismissRequest = { tripToDelete = null }, title = { Text("確定要刪除旅程？", fontWeight = FontWeight.Bold) }, text = { Text("您即將刪除「${trip.name}」\n此動作無法復原，是否繼續？") }, confirmButton = { Button({ onDeleteTrip(trip); tripToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("刪除") } }, dismissButton = { TextButton({ tripToDelete = null }) { Text("取消") } })
        }
    }
}

// --- Trip Calendar ---

@Composable
fun TripCalendar(trips: List<Trip>) {
    var currentYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    val calendarInfo = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply { set(Calendar.YEAR, currentYear); set(Calendar.MONTH, currentMonth); set(Calendar.DAY_OF_MONTH, 1) }
        Triple(cal, cal.getActualMaximum(Calendar.DAY_OF_MONTH), cal.get(Calendar.DAY_OF_WEEK))
    }
    val (baseCal, daysInMonth, firstDayOfWeek) = calendarInfo
    val monthFormat = remember { SimpleDateFormat("yyyy 年 MM 月", Locale.getDefault()) }
    val tripMap = remember(trips, currentYear, currentMonth) {
        val map = mutableMapOf<Int, Pair<Trip, Int>>()
        fun parseDate(dateStr: String): Calendar? {
            try { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).parse(dateStr)?.let { return Calendar.getInstance().apply { time = it } } } catch (e: Exception) {}
            try { SimpleDateFormat("MM/dd", Locale.getDefault()).parse(dateStr)?.let { val c = Calendar.getInstance(); c.time = it; c.set(Calendar.YEAR, currentYear); return c } } catch (e: Exception) {}
            return null
        }
        trips.forEach { trip ->
            if (trip.dateRange.contains("~")) {
                val parts = trip.dateRange.split("~").map { it.trim() }
                if (parts.size == 2) {
                    val start = parseDate(parts[0])
                    val end = parseDate(parts[1])
                    if (start != null && end != null) {
                        val (s, e) = if (start.timeInMillis > end.timeInMillis) end to start else start to end
                        val iter = s.clone() as Calendar
                        while (!iter.after(e)) {
                            if (iter.get(Calendar.YEAR) == currentYear && iter.get(Calendar.MONTH) == currentMonth) {
                                val day = iter.get(Calendar.DAY_OF_MONTH)
                                val pos = when {
                                    iter.get(Calendar.DAY_OF_YEAR) == s.get(Calendar.DAY_OF_YEAR) -> 1
                                    iter.get(Calendar.DAY_OF_YEAR) == e.get(Calendar.DAY_OF_YEAR) -> 3
                                    else -> 2
                                }
                                map[day] = trip to pos
                            }
                            iter.add(Calendar.DAY_OF_MONTH, 1)
                        }
                    }
                }
            } else {
                parseDate(trip.dateRange)?.let { if (it.get(Calendar.YEAR) == currentYear && it.get(Calendar.MONTH) == currentMonth) map[it.get(Calendar.DAY_OF_MONTH)] = trip to 0 }
            }
        }
        map
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconButton({ if (currentMonth == 0) { currentMonth = 11; currentYear-- } else currentMonth-- }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "上個月") }
            Text(monthFormat.format(baseCal.time), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF008080))
            IconButton({ if (currentMonth == 11) { currentMonth = 0; currentYear++ } else currentMonth++ }) { Icon(Icons.Default.ArrowForward, "下個月") }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) { listOf("日", "一", "二", "三", "四", "五", "六").forEach { Text(it, fontWeight = FontWeight.Bold, color = Color.Gray) } }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(GridCells.Fixed(7), Modifier.fillMaxSize()) {
            items(firstDayOfWeek - 1) { Box(Modifier.size(40.dp)) }
            items(daysInMonth) { index ->
                val day = index + 1
                val tripInfo = tripMap[day]
                val trip = tripInfo?.first
                val pos = tripInfo?.second ?: -1
                val bgShape = when (pos) { 0 -> CircleShape; 1 -> RoundedCornerShape(50.dp, 0.dp, 0.dp, 50.dp); 3 -> RoundedCornerShape(0.dp, 50.dp, 50.dp, 0.dp); 2 -> RectangleShape; else -> RectangleShape }
                Box(Modifier.padding(vertical = 2.dp).aspectRatio(1f).clip(bgShape).background(if (trip != null) Color(0xFF008080).copy(alpha = if (pos == 2) 0.15f else 0.3f) else Color.Transparent), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$day", fontWeight = if (trip != null) FontWeight.Bold else FontWeight.Normal, color = if (trip != null) Color(0xFF008080) else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

// --- Itinerary Detail Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(trip: Trip?, allItineraries: List<ItineraryItem>, onBack: () -> Unit, onAddItem: (ItineraryItem) -> Unit, onUpdateItem: (ItineraryItem) -> Unit, onDeleteItem: (ItineraryItem) -> Unit, onToggleFavorite: (ItineraryItem) -> Unit) {
    if (trip == null) return
    val tripItineraries = allItineraries.filter { it.tripId == trip.id }
    val uniqueDates = tripItineraries.map { it.date }.distinct().sorted()
    var selectedDate by remember(uniqueDates) { mutableStateOf(if (uniqueDates.isNotEmpty()) uniqueDates[0] else "") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ItineraryItem?>(null) }
    val totalSpent = tripItineraries.sumOf { it.cost }
    val remainingBudget = trip.budget - totalSpent
    val budgetProgress = if (trip.budget > 0) (totalSpent / trip.budget).toFloat().coerceIn(0f, 1f) else 0f

    Scaffold(
        topBar = { TopAppBar(title = { Text(trip.name, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = { IconButton({ showQrDialog = true }) { Icon(Icons.Default.Share, null) } }) },
        floatingActionButton = { FloatingActionButton({ showAddDialog = true }, containerColor = Color(0xFF008080)) { Icon(Icons.Default.Add, null, tint = Color.White) } }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("總預算: $${trip.budget.toInt()}", fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                        Text("剩餘: $${remainingBudget.toInt()}", fontWeight = FontWeight.Bold, color = if (remainingBudget >= 0) Color(0xFF00695C) else Color.Red)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator({ budgetProgress }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = if (budgetProgress > 0.9f) Color.Red else Color(0xFF008080), trackColor = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("已支出: $${totalSpent.toInt()}", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
                }
            }
            if (uniqueDates.isNotEmpty() && !showFavoritesOnly) {
                LazyRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uniqueDates) { date -> FilterChip(selectedDate == date, { selectedDate = date }, { Text(date.substring(5)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF008080), selectedLabelColor = Color.White)) }
                }
            }
            val displayItems = if (showFavoritesOnly) tripItineraries.filter { it.isFavorite }.sortedBy { it.date + it.time } else tripItineraries.filter { it.date == selectedDate }.sortedBy { it.time }
            if (displayItems.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("目前沒有行程喔！", color = Color.Gray) }
            else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { items(displayItems) { item -> TimelineItem(item, { onToggleFavorite(item) }, { onDeleteItem(item) }, { editingItem = item }) } }
        }
        if (showAddDialog) AddItineraryDialog(trip.id, null, { showAddDialog = false }) { onAddItem(it); if (selectedDate.isEmpty()) selectedDate = it.date; showAddDialog = false }
        editingItem?.let { item -> AddItineraryDialog(trip.id, item, { editingItem = null }) { onUpdateItem(it.copy(id = item.id)); editingItem = null } }
        if (showQrDialog) QrCodeDialog(Json.encodeToString(TripBackup(trip, tripItineraries)), { showQrDialog = false })
    }
}

// --- Dialogs & Items ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItineraryDialog(tripId: String, initialItem: ItineraryItem?, onDismiss: () -> Unit, onConfirm: (ItineraryItem) -> Unit) {
    val cal = Calendar.getInstance()
    var date by remember { mutableStateOf(initialItem?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)) }
    var time by remember { mutableStateOf(initialItem?.time ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)) }
    var desc by remember { mutableStateOf(initialItem?.description ?: "") }
    var costStr by remember { mutableStateOf(if (initialItem != null && initialItem.cost != 0.0) initialItem.cost.toInt().toString() else "") }
    var selectedCat by remember { mutableStateOf(if (initialItem != null) Category.entries.find { it.name == initialItem.category } ?: Category.SPOT else Category.SPOT) }
    var showDP by remember { mutableStateOf(false) }
    var showTP by remember { mutableStateOf(false) }

    if (showDP) { val state = rememberDatePickerState(); DatePickerDialog({ showDP = false }, { TextButton({ state.selectedDateMillis?.let { date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) }; showDP = false }) { Text("確定") } }) { DatePicker(state) } }
    if (showTP) { val state = rememberTimePickerState(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true); AlertDialog({ showTP = false }, { TextButton({ time = String.format("%02d:%02d", state.hour, state.minute); showTP = false }) { Text("確定") } }, text = { TimePicker(state) }) }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initialItem != null) "修改行程" else "新增行程", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(date, {}, label = { Text("日期") }, modifier = Modifier.weight(1f).clickable { showDP = true }, readOnly = true, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline))
                    OutlinedTextField(time, {}, label = { Text("時間") }, modifier = Modifier.weight(1f).clickable { showTP = true }, readOnly = true, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(Category.entries) { cat ->
                        Surface(Modifier.clickable { selectedCat = cat }, shape = RoundedCornerShape(20.dp), color = if (selectedCat == cat) cat.color else Color.Transparent, border = BorderStroke(1.dp, Color.LightGray)) { Text("${cat.icon} ${cat.label}", Modifier.padding(12.dp, 6.dp), color = if (selectedCat == cat) Color.White else Color.Gray, fontSize = 12.sp) }
                    }
                }
                OutlinedTextField(desc, { desc = it }, label = { Text("內容") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(costStr, { if (it.all { c -> c.isDigit() }) costStr = it }, label = { Text("花費 ($)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { Button({ if (desc.isNotBlank()) onConfirm(ItineraryItem(tripId = tripId, date = date, time = time, description = desc, category = selectedCat.name, cost = costStr.toDoubleOrNull() ?: 0.0)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008080))) { Text("確認") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSettingsDialog(title: String, initialTrip: Trip?, onDismiss: () -> Unit, onConfirm: (String, String, String, Double) -> Unit) {
    var name by remember { mutableStateOf(initialTrip?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(initialTrip?.icon ?: "✈️") }
    var budgetStr by remember { mutableStateOf(if (initialTrip != null && initialTrip.budget != 0.0) initialTrip.budget.toInt().toString() else "") }
    val initialDates = initialTrip?.dateRange?.split(" ~ ")
    var startDate by remember { mutableStateOf(initialDates?.getOrNull(0) ?: "") }
    var endDate by remember { mutableStateOf(initialDates?.getOrNull(1) ?: "") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val icons = listOf("✈️", "🧳", "🏝️", "🏔️", "🏕️", "🏙️", "🚂", "🚗", "🚢", "🎡", "🎢", "📷", "🛍️", "🍜", "🍻")

    if (showStartPicker) { val s = rememberDatePickerState(); DatePickerDialog({ showStartPicker = false }, { TextButton({ s.selectedDateMillis?.let { startDate = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(it)) }; showStartPicker = false }) { Text("確定") } }) { DatePicker(s) } }
    if (showEndPicker) { val s = rememberDatePickerState(); DatePickerDialog({ showEndPicker = false }, { TextButton({ s.selectedDateMillis?.let { endDate = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(it)) }; showEndPicker = false }) { Text("確定") } }) { DatePicker(s) } }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🌍 選擇圖示", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(icons) { icon -> Box(Modifier.size(45.dp).background(if (selectedIcon == icon) Color(0xFF008080).copy(0.2f) else Color.DarkGray.copy(0.3f), RoundedCornerShape(8.dp)).border(if (selectedIcon == icon) 2.dp else 0.dp, if (selectedIcon == icon) Color(0xFF008080) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { selectedIcon = icon }, Alignment.Center) { Text(icon, fontSize = 24.sp) } } }
                OutlinedTextField(name, { name = it }, placeholder = { Text("請輸入旅程...", color = Color.Gray) }, modifier = Modifier.fillMaxWidth())
                Text("💰 總預算", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(budgetStr, { if (it.all { c -> c.isDigit() }) budgetStr = it }, placeholder = { Text("例如：50000", color = Color.Gray) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("📅 旅遊日期區間 (選填)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startDate, {}, readOnly = true, enabled = false, placeholder = { Text("開始日期", color = Color.Gray) }, modifier = Modifier.weight(1f).clickable { showStartPicker = true }, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline))
                    OutlinedTextField(endDate, {}, readOnly = true, enabled = false, placeholder = { Text("結束日期", color = Color.Gray) }, modifier = Modifier.weight(1f).clickable { showEndPicker = true }, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline))
                }
            }
        },
        confirmButton = {
            Button({
                if (name.isNotBlank()) {
                    var s = startDate; var e = endDate
                    if (s.isNotBlank() && e.isNotBlank() && s > e) { val temp = s; s = e; e = temp }
                    onConfirm(name, selectedIcon, if (s.isNotBlank() && e.isNotBlank()) "$s ~ $e" else if (s.isNotBlank()) s else "", budgetStr.toDoubleOrNull() ?: 0.0)
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008080))) { Text("儲存") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripGridCard(trip: Trip, isSelectionMode: Boolean, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().height(160.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(trip.icon, fontSize = 48.sp); Spacer(Modifier.height(8.dp))
                Text(trip.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 1)
                if (trip.dateRange.isNotEmpty()) Text(trip.dateRange, fontSize = 10.sp, color = Color.Gray)
                if (trip.budget > 0) { Spacer(Modifier.height(4.dp)); Text("預算: $${trip.budget.toInt()}", fontSize = 10.sp, color = Color(0xFF00695C)) }
            }
            if (isSelectionMode) Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            else IconButton(onDelete, Modifier.align(Alignment.TopEnd).size(32.dp)) { Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
fun TimelineItem(item: ItineraryItem, onToggleFavorite: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val cat = Category.entries.find { it.name == item.category } ?: Category.OTHER
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) { Text(item.time, fontWeight = FontWeight.Bold, fontSize = 16.sp); Box(Modifier.width(2.dp).height(40.dp).background(Color.LightGray)) }
        Card(Modifier.weight(1f).padding(start = 8.dp), RoundedCornerShape(12.dp), CardDefaults.cardColors(MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Surface(color = cat.color, shape = RoundedCornerShape(12.dp)) { Text("${cat.icon} ${cat.label}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(8.dp, 2.dp)) }
                    Spacer(Modifier.height(4.dp))
                    Text(item.description, fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.clickable { ContextCompat.startActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${item.description}")), null) })
                    if (item.cost > 0) Text("💰 $${item.cost.toInt()}", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
                IconButton(onToggleFavorite) { Icon(if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (item.isFavorite) Color.Red else Color.LightGray) }
                IconButton(onEdit) { Icon(Icons.Default.Edit, null, tint = Color.Gray, modifier = Modifier.size(20.dp)) }
                IconButton(onDelete) { Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(onResult: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val isProcessing = remember { AtomicBoolean(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }
    LaunchedEffect(Unit) { if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val image = InputImage.fromBitmap(BitmapFactory.decodeStream(context.contentResolver.openInputStream(it)), 0)
            BarcodeScanning.getClient().process(image).addOnSuccessListener { barcodes -> barcodes.firstOrNull()?.rawValue?.let { onResult(it) } }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("掃描行程 QR Code") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            if (hasCameraPermission) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView({ ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also { p -> p.setSurfaceProvider(previewView.surfaceProvider) }
                            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { a ->
                                a.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                                    if (isProcessing.get()) { proxy.close(); return@setAnalyzer }
                                    proxy.image?.let { img ->
                                        BarcodeScanning.getClient().process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { codes ->
                                            codes.firstOrNull()?.rawValue?.let { if (!isProcessing.getAndSet(true)) Handler(Looper.getMainLooper()).post { cameraProvider.unbindAll(); onResult(it) } }
                                        }.addOnCompleteListener { proxy.close() }
                                    } ?: proxy.close()
                                }
                            }
                            try { cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) } catch (e: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }, Modifier.fillMaxSize())
                    Box(Modifier.size(250.dp).border(2.dp, Color.White, RoundedCornerShape(12.dp)).align(Alignment.Center))
                }
            }
            Button({ galleryLauncher.launch("image/*") }, Modifier.padding(16.dp).fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008080))) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("從相簿選擇圖片") }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("分享 QR Code", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { bm?.let { Image(it.asImageBitmap(), null, Modifier.size(200.dp)) }; Text("好友掃描即可匯入行程", fontSize = 12.sp, color = Color.Gray) } }, confirmButton = { Button(onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008080))) { Text("關閉") } })
}