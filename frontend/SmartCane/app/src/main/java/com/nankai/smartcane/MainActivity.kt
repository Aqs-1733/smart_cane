package com.nankai.smartcane

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.nankai.smartcane.data.network.ApiResult
import com.nankai.smartcane.data.network.DeviceDto
import com.nankai.smartcane.data.network.LatestRiskEventDto
import com.nankai.smartcane.data.network.SmartCaneApiClient
import com.nankai.smartcane.data.network.ServerStatusDto
import com.nankai.smartcane.data.network.SosRequestDto
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

data class RiskEvent(
    val id: Int,
    val deviceId: String,
    val riskType: String,
    val level: String,
    val direction: String,
    val distanceMm: Int?,
    val aiMessage: String,
    val time: String
)

private val demoEvents = listOf(
    RiskEvent(
        id = 38,
        deviceId = "cane_003",
        riskType = "front_obstacle",
        level = "low",
        direction = "front",
        distanceMm = 1285,
        aiMessage = "前方约 128 厘米有障碍物，请减速并准备绕行。",
        time = "14:30:35"
    ),
    RiskEvent(
        id = 37,
        deviceId = "cane_002",
        riskType = "left_obstacle",
        level = "high",
        direction = "left",
        distanceMm = 422,
        aiMessage = "左侧约 42 厘米有障碍物，请向右侧保持距离。",
        time = "14:30:33"
    ),
    RiskEvent(
        id = 36,
        deviceId = "cane_003",
        riskType = "right_obstacle",
        level = "medium",
        direction = "right",
        distanceMm = 1196,
        aiMessage = "右侧约 119 厘米有障碍物，请向左侧保持距离。",
        time = "14:30:31"
    ),
    RiskEvent(
        id = 35,
        deviceId = "cane_001",
        riskType = "ground_drop",
        level = "high",
        direction = "down",
        distanceMm = 1200,
        aiMessage = "前方地面高度变化明显，可能有台阶或坑洼，请停止前进并用盲杖确认地面。",
        time = "14:30:29"
    )
)

private sealed interface LatestEventsUiState {
    data object Loading : LatestEventsUiState
    data class Success(val events: List<RiskEvent>) : LatestEventsUiState
    data class Error(val message: String) : LatestEventsUiState
}

private sealed interface DeviceStatusUiState {
    data object Loading : DeviceStatusUiState
    data class Success(
        val status: ServerStatusDto,
        val devices: List<DeviceDto>
    ) : DeviceStatusUiState
    data class Error(val message: String) : DeviceStatusUiState
}

private const val DEVICE_STATUS_REFRESH_INTERVAL_MS = 10_000L
private const val LATEST_EVENTS_REFRESH_INTERVAL_MS = 10_000L
private const val SOS_DEVICE_ID = "cane_001"
private const val SOS_MESSAGE = "用户通过 Android App 发起紧急求助"
private const val SOS_LOCATION_TIMEOUT_MS = 8_000L

private sealed interface LocationFetchResult {
    data class Success(val location: Location) : LocationFetchResult
    data class Failure(val message: String) : LocationFetchResult
}

private data class SosSuccessInfo(
    val deviceId: String,
    val latitude: Double?,
    val longitude: Double?,
    val sentAt: String,
    val backendMessage: String
)

private sealed interface SosUiState {
    data object Idle : SosUiState
    data object Sending : SosUiState
    data class LocationUnavailable(val message: String) : SosUiState
    data class Success(val info: SosSuccessInfo) : SosUiState
    data class Error(val message: String, val retryRequest: SosRequestDto?) : SosUiState
}

private suspend fun loadDeviceStatusState(): DeviceStatusUiState {
    val status = when (val result = SmartCaneApiClient.getStatus()) {
        is ApiResult.Success -> result.data
        is ApiResult.Failure -> return DeviceStatusUiState.Error(result.message)
    }

    val devices = when (val result = SmartCaneApiClient.getDevices()) {
        is ApiResult.Success -> result.data
        is ApiResult.Failure -> return DeviceStatusUiState.Error(result.message)
    }

    return DeviceStatusUiState.Success(status = status, devices = devices)
}

private fun LatestRiskEventDto.toRiskEvent(): RiskEvent {
    return RiskEvent(
        id = id,
        deviceId = deviceId,
        riskType = riskType,
        level = riskLevel.ifBlank { "low" },
        direction = directionFromRiskType(riskType),
        distanceMm = distance,
        aiMessage = message,
        time = timeFromTimestamp(timestamp)
    )
}

private fun directionFromRiskType(riskType: String): String {
    return when {
        riskType.contains("left", ignoreCase = true) -> "left"
        riskType.contains("right", ignoreCase = true) -> "right"
        riskType.contains("front", ignoreCase = true) -> "front"
        riskType.contains("ground", ignoreCase = true) || riskType.contains("drop", ignoreCase = true) -> "down"
        else -> "unknown"
    }
}

private fun timeFromTimestamp(timestamp: String): String {
    if (timestamp.length >= 19 && timestamp[10] == 'T') {
        return timestamp.substring(11, 19)
    }
    return timestamp.takeLast(8).ifBlank { "--:--:--" }
}

private fun displayTimestamp(timestamp: String): String {
    return timestamp
        .ifBlank { "-" }
        .replace("T", " ")
        .substringBefore("+")
        .substringBefore("Z")
}

private fun currentTimeText(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
}

private fun hasLocationPermission(context: Context): Boolean {
    val fineLocationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseLocationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fineLocationGranted || coarseLocationGranted
}

private suspend fun sendSosFromApp(
    context: Context,
    allowUnknownLocation: Boolean
): SosUiState {
    val location = when (val locationResult = getBestLocationOnce(context)) {
        is LocationFetchResult.Success -> locationResult.location
        is LocationFetchResult.Failure -> {
            if (!allowUnknownLocation) {
                return SosUiState.LocationUnavailable(locationResult.message)
            }
            null
        }
    }

    val request = SosRequestDto(
        deviceId = SOS_DEVICE_ID,
        latitude = location?.latitude,
        longitude = location?.longitude,
        message = if (location == null) "$SOS_MESSAGE（位置不可用）" else SOS_MESSAGE
    )
    return sendSosRequest(request)
}

private suspend fun sendSosRequest(request: SosRequestDto): SosUiState {
    return when (val result = SmartCaneApiClient.postSos(request)) {
        is ApiResult.Success -> {
            val response = result.data
            if (response.success) {
                SosUiState.Success(
                    SosSuccessInfo(
                        deviceId = response.sos?.deviceId?.ifBlank { request.deviceId } ?: request.deviceId,
                        latitude = response.sos?.latitude ?: request.latitude,
                        longitude = response.sos?.longitude ?: request.longitude,
                        sentAt = response.sos?.receivedAt?.takeIf { it.isNotBlank() }?.let(::displayTimestamp)
                            ?: currentTimeText(),
                        backendMessage = response.message.ifBlank { "SOS 已发送" }
                    )
                )
            } else {
                SosUiState.Error(
                    message = response.message.ifBlank { "后端返回 SOS 发送失败" },
                    retryRequest = request
                )
            }
        }
        is ApiResult.Failure -> SosUiState.Error(
            message = result.message,
            retryRequest = request
        )
    }
}

private suspend fun getBestLocationOnce(context: Context): LocationFetchResult {
    if (!hasLocationPermission(context)) {
        return LocationFetchResult.Failure("未获得定位权限，请允许定位后重试。")
    }

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return LocationFetchResult.Failure("无法访问手机定位服务。")

    val enabledProviders = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    ).filter { provider ->
        try {
            locationManager.isProviderEnabled(provider)
        } catch (_: Exception) {
            false
        }
    }

    if (enabledProviders.isEmpty()) {
        return LocationFetchResult.Failure("手机定位服务未开启，请开启 GPS 或网络定位后重试。")
    }

    val lastKnownLocation = try {
        enabledProviders
            .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
            .minByOrNull { it.accuracy }
    } catch (_: SecurityException) {
        null
    }
    if (lastKnownLocation != null) {
        return LocationFetchResult.Success(lastKnownLocation)
    }

    val provider = if (LocationManager.GPS_PROVIDER in enabledProviders) {
        LocationManager.GPS_PROVIDER
    } else {
        enabledProviders.first()
    }

    return requestSingleLocation(locationManager, provider)
        ?: LocationFetchResult.Failure("获取当前位置超时，请移动到开阔区域或检查定位开关。")
}

private suspend fun requestSingleLocation(
    locationManager: LocationManager,
    provider: String
): LocationFetchResult? {
    return withTimeoutOrNull(SOS_LOCATION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(LocationFetchResult.Success(location))
                    }
                }

                override fun onProviderDisabled(provider: String) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(LocationFetchResult.Failure("定位提供方已关闭，请开启定位后重试。"))
                    }
                }

                override fun onProviderEnabled(provider: String) = Unit

                @Deprecated("Deprecated in Android API")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }

            try {
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: SecurityException) {
                if (continuation.isActive) {
                    continuation.resume(LocationFetchResult.Failure("未获得定位权限，请允许定位后重试。"))
                }
            } catch (_: IllegalArgumentException) {
                if (continuation.isActive) {
                    continuation.resume(LocationFetchResult.Failure("当前定位提供方不可用。"))
                }
            }

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
        }
    }
}

private fun formatCoordinate(latitude: Double?, longitude: Double?): String {
    return if (latitude == null || longitude == null) {
        "位置不可用"
    } else {
        String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartCaneApp()
        }
    }
}

@Composable
fun SmartCaneApp() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0F766E),
            secondary = Color(0xFF14B8A6),
            background = Color(0xFFEEF6F7),
            surface = Color.White
        )
    ) {
        var selectedTab by remember { mutableIntStateOf(0) }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                BottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEEF6F7))
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> GuidePage()
                    1 -> MapPage()
                    2 -> CollaborationPage()
                    3 -> SosPage()
                    4 -> MinePage()
                }
            }
        }
    }
}

@Composable
fun AppHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
        }

        Surface(
            color = Color(0xFFDCFCE7),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = "在线",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color(0xFF166534),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GuidePage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
    ) {
        item {
            AppHeader(
                title = "智能导盲助手",
                subtitle = "ESP32-C5 多设备协同感知"
            )
        }

        item {
            CurrentRiskCard()
        }

        item {
            DistanceGrid()
        }

        item {
            DeviceStatusFromServer()
        }

        item {
            LatestRiskFromServer()
        }
    }
}

@Composable
fun LatestRiskFromServer() {
    var uiState by remember { mutableStateOf<LatestEventsUiState>(LatestEventsUiState.Loading) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        while (true) {
            if (uiState is LatestEventsUiState.Loading || uiState is LatestEventsUiState.Error) {
                uiState = LatestEventsUiState.Loading
            }
            uiState = when (val result = SmartCaneApiClient.getLatestEvents()) {
                is ApiResult.Success -> LatestEventsUiState.Success(
                    events = result.data.map { it.toRiskEvent() }
                )
                is ApiResult.Failure -> LatestEventsUiState.Error(result.message)
            }
            delay(LATEST_EVENTS_REFRESH_INTERVAL_MS)
        }
    }

    when (val state = uiState) {
        LatestEventsUiState.Loading -> LatestRiskStateCard(
            title = "最新风险提醒",
            message = "正在从后端加载最新风险数据...",
            showRetry = false
        )
        is LatestEventsUiState.Success -> {
            if (state.events.isEmpty()) {
                LatestRiskStateCard(
                    title = "最新风险提醒",
                    message = "后端暂时没有风险事件。",
                    showRetry = false
                )
            } else {
                LatestRiskList(title = "最新风险提醒", events = state.events)
            }
        }
        is LatestEventsUiState.Error -> LatestRiskStateCard(
            title = "最新风险提醒",
            message = state.message,
            showRetry = true,
            onRetry = { retryKey++ }
        )
    }
}

@Composable
fun LatestRiskStateCard(
    title: String,
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = Color(0xFF64748B),
                fontSize = 15.sp,
                lineHeight = 23.sp
            )
            if (showRetry) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRetry) {
                    Text(text = "重试")
                }
            }
        }
    }
}

@Composable
fun CurrentRiskCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFEFFDF5),
                            Color(0xFFDFF7FF)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column {
                Text(
                    text = "当前风险",
                    fontSize = 15.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "高风险",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFDC2626)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "左侧约 42 厘米有障碍物，请向右侧保持距离。",
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusTag("左侧震动")
                    StatusTag("语音提醒")
                    StatusTag("已上传")
                }
            }
        }
    }
}

@Composable
fun StatusTag(text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.9f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 13.sp,
            color = Color(0xFF0F766E),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DistanceGrid() {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "实时测距",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DistanceCard("前方", "128 cm", Color(0xFF2563EB), Modifier.weight(1f))
            DistanceCard("左侧", "42 cm", Color(0xFFDC2626), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DistanceCard("右侧", "119 cm", Color(0xFFF59E0B), Modifier.weight(1f))
            DistanceCard("地面", "120 cm", Color(0xFFDC2626), Modifier.weight(1f))
        }
    }
}

@Composable
fun DistanceCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

@Composable
fun DeviceStatusFromServer() {
    var uiState by remember { mutableStateOf<DeviceStatusUiState>(DeviceStatusUiState.Loading) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        while (true) {
            if (uiState is DeviceStatusUiState.Loading || uiState is DeviceStatusUiState.Error) {
                uiState = DeviceStatusUiState.Loading
            }
            uiState = loadDeviceStatusState()
            delay(DEVICE_STATUS_REFRESH_INTERVAL_MS)
        }
    }

    when (val state = uiState) {
        DeviceStatusUiState.Loading -> LatestRiskStateCard(
            title = "设备状态",
            message = "正在从后端加载设备状态...",
            showRetry = false
        )
        is DeviceStatusUiState.Success -> DeviceStatusDataCard(
            status = state.status,
            devices = state.devices
        )
        is DeviceStatusUiState.Error -> LatestRiskStateCard(
            title = "设备状态",
            message = state.message,
            showRetry = true,
            onRetry = { retryKey++ }
        )
    }
}

@Composable
fun DeviceStatusDataCard(status: ServerStatusDto, devices: List<DeviceDto>) {
    val primaryDevice = devices.firstOrNull()
    val totalDeviceCount = maxOf(status.deviceCount, devices.size)
    val onlineDeviceCount = devices.count { it.online }
    val serverStatusText = if (status.online) "在线" else "离线"
    val primaryDeviceStatusText = when {
        primaryDevice == null -> "暂无设备"
        primaryDevice.online -> "在线"
        else -> "离线"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "设备状态",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(14.dp))

            StatusRow(
                "后端状态",
                serverStatusText,
                valueColor = if (status.online) Color(0xFF16A34A) else Color(0xFFDC2626)
            )
            StatusRow("服务提示", status.message.ifBlank { "-" })
            StatusRow("已连接设备", "$onlineDeviceCount / $totalDeviceCount 台在线")
            StatusRow("主要设备", primaryDevice?.name?.ifBlank { primaryDevice.deviceId } ?: "暂无设备")
            StatusRow("设备 ID", primaryDevice?.deviceId ?: "-")
            StatusRow("电量", primaryDevice?.battery?.let { "$it%" } ?: "-")
            StatusRow(
                "设备状态",
                primaryDeviceStatusText,
                valueColor = if (primaryDevice?.online == true) Color(0xFF16A34A) else Color(0xFFDC2626)
            )
            StatusRow("最后在线", primaryDevice?.lastSeen?.let(::displayTimestamp) ?: "-")

            if (!status.online) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "后端报告当前离线，请检查 server.py 或局域网连接。",
                    color = Color(0xFFDC2626),
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            } else if (primaryDevice != null && !primaryDevice.online) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "主要设备已离线，请检查盲杖电源或网络连接。",
                    color = Color(0xFFDC2626),
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            } else if (primaryDevice == null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "后端暂时没有返回设备数据。",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
fun DeviceStatusCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "设备状态",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(14.dp))

            StatusRow("设备 ID", "cane_001")
            StatusRow("电量", "88%")
            StatusRow("连接状态", "Wi-Fi 已连接")
            StatusRow("协同设备", "3 台在线")
        }
    }
}

@Composable
fun StatusRow(label: String, value: String, valueColor: Color = Color(0xFF0F172A)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF64748B), fontSize = 15.sp)
        Text(text = value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MapPage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
    ) {
        item {
            AppHeader(
                title = "风险地图",
                subtitle = "多设备共享盲人友好风险点"
            )
        }

        item {
            MapPlaceholder()
        }

        item {
            LatestRiskList(title = "地图点位记录", events = demoEvents)
        }
    }
}

@Composable
fun MapPlaceholder() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE2EEF1))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "百度地图风险点展示区\n下一步接入真实地图 SDK",
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center,
                color = Color(0xFF334155),
                fontSize = 18.sp,
                lineHeight = 28.sp
            )

            MapDot(
                level = "high",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 70.dp, top = 70.dp)
            )

            MapDot(
                level = "medium",
                modifier = Modifier
                    .align(Alignment.Center)
            )

            MapDot(
                level = "low",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 70.dp, bottom = 70.dp)
            )
        }
    }
}

@Composable
fun MapDot(level: String, modifier: Modifier = Modifier) {
    val color = when (level) {
        "high" -> Color(0xFFDC2626)
        "medium" -> Color(0xFFF59E0B)
        else -> Color(0xFF2563EB)
    }

    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun CollaborationPage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
    ) {
        item {
            AppHeader(
                title = "多设备协同",
                subtitle = "走过即建图，多人共享风险"
            )
        }

        item {
            StatisticsGrid()
        }

        item {
            DeviceListCard()
        }
    }
}

@Composable
fun StatisticsGrid() {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("今日事件", "38", "条", Modifier.weight(1f))
            StatCard("高风险", "12", "处", Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("在线设备", "3", "台", Modifier.weight(1f))
            StatCard("安全路线", "5", "条", Modifier.weight(1f))
        }
    }
}

@Composable
fun StatCard(title: String, number: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = title, color = Color(0xFF64748B), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = number,
                    color = Color(0xFF0F766E),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = unit,
                    color = Color(0xFF64748B),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DeviceListCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "协同设备",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(14.dp))

            DeviceRow("cane_001", "在线", "已上传 15 条风险")
            DeviceRow("cane_002", "在线", "已上传 12 条风险")
            DeviceRow("cane_003", "在线", "已上传 11 条风险")
        }
    }
}

@Composable
fun DeviceRow(id: String, status: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0F2FE)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "杖", color = Color(0xFF0369A1), fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(text = id, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(text = desc, color = Color(0xFF64748B), fontSize = 13.sp)
        }

        Text(
            text = status,
            color = Color(0xFF16A34A),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun SosPage() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<SosUiState>(SosUiState.Idle) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    fun startSend(allowUnknownLocation: Boolean) {
        if (uiState is SosUiState.Sending) return
        coroutineScope.launch {
            uiState = SosUiState.Sending
            uiState = sendSosFromApp(context, allowUnknownLocation)
        }
    }

    fun retryRequest(request: SosRequestDto) {
        if (uiState is SosUiState.Sending) return
        coroutineScope.launch {
            uiState = SosUiState.Sending
            uiState = sendSosRequest(request)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            startSend(allowUnknownLocation = false)
        } else {
            uiState = SosUiState.LocationUnavailable(
                "未获得定位权限，请授权后重试，或明确选择使用未知位置发送。"
            )
        }
    }

    fun requestPermissionOrSend() {
        if (hasLocationPermission(context)) {
            startSend(allowUnknownLocation = false)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认发送 SOS？") },
            text = {
                Text(
                    text = "确认后将尝试获取当前手机位置，并向后端发送紧急求助信息。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        requestPermissionOrSend()
                    }
                ) {
                    Text("确认发送")
                }
            },
            dismissButton = {
                Button(onClick = { showConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "一键求助",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(80.dp))

        val sending = uiState is SosUiState.Sending
        Button(
            onClick = { showConfirmDialog = true },
            enabled = !sending,
            modifier = Modifier.size(190.dp),
            shape = CircleShape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color(0xFFDC2626)
            )
        ) {
            Text(
                text = if (sending) "发送中" else "SOS",
                fontSize = if (sending) 28.sp else 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        SosStatusContent(
            state = uiState,
            onRetryLocation = { requestPermissionOrSend() },
            onSendUnknownLocation = { startSend(allowUnknownLocation = true) },
            onRetryRequest = { retryRequest(it) }
        )
    }
}

@Composable
private fun SosStatusContent(
    state: SosUiState,
    onRetryLocation: () -> Unit,
    onSendUnknownLocation: () -> Unit,
    onRetryRequest: (SosRequestDto) -> Unit
) {
    when (state) {
        SosUiState.Idle -> Text(
            text = "点击 SOS 后需要二次确认。确认后将获取手机当前位置，并向后端发送设备编号、位置和求助信息。",
            textAlign = TextAlign.Center,
            color = Color(0xFF475569),
            fontSize = 17.sp,
            lineHeight = 28.sp
        )
        SosUiState.Sending -> SosInfoCard(
            title = "正在发送求助",
            message = "正在获取当前位置并向后端发送 SOS，请稍候...",
            titleColor = Color(0xFFDC2626)
        )
        is SosUiState.LocationUnavailable -> SosInfoCard(
            title = "定位不可用",
            message = state.message,
            titleColor = Color(0xFFDC2626)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetryLocation, modifier = Modifier.fillMaxWidth()) {
                Text("重试定位")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onSendUnknownLocation, modifier = Modifier.fillMaxWidth()) {
                Text("使用未知位置发送")
            }
        }
        is SosUiState.Success -> SosSuccessCard(state.info)
        is SosUiState.Error -> SosInfoCard(
            title = "SOS 发送失败",
            message = state.message,
            titleColor = Color(0xFFDC2626)
        ) {
            state.retryRequest?.let { request ->
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { onRetryRequest(request) }, modifier = Modifier.fillMaxWidth()) {
                    Text("重新发送")
                }
            }
        }
    }
}

@Composable
private fun SosInfoCard(
    title: String,
    message: String,
    titleColor: Color,
    extraContent: @Composable () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                textAlign = TextAlign.Start,
                color = Color(0xFF475569),
                fontSize = 15.sp,
                lineHeight = 24.sp
            )
            extraContent()
        }
    }
}

@Composable
private fun SosSuccessCard(info: SosSuccessInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "SOS 已发送",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF16A34A)
            )
            Spacer(modifier = Modifier.height(12.dp))
            StatusRow("发送时间", info.sentAt)
            StatusRow("设备 ID", info.deviceId)
            StatusRow("定位坐标", formatCoordinate(info.latitude, info.longitude))
            StatusRow("后端返回", info.backendMessage)
        }
    }
}

@Composable
fun MinePage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
    ) {
        item {
            AppHeader(
                title = "我的设备",
                subtitle = "设备管理与系统设置"
            )
        }

        item {
            DeviceStatusCard()
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "服务器",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "http://电脑IPv4地址:8000",
                        color = Color(0xFF0F766E),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "下一步会把这里改成可配置的后端地址，并从 server.py 获取真实数据。",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LatestRiskList(title: String, events: List<RiskEvent>) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(12.dp))

        events.forEach { event ->
            RiskEventItem(event)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun RiskEventItem(event: RiskEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "#${event.id} ${event.level.uppercase()}",
                    fontWeight = FontWeight.Bold,
                    color = when (event.level) {
                        "high" -> Color(0xFFDC2626)
                        "medium" -> Color(0xFFF59E0B)
                        else -> Color(0xFF2563EB)
                    }
                )

                Text(
                    text = event.time,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${event.deviceId} / ${event.direction} / ${event.distanceMm ?: "-"} mm",
                color = Color(0xFF64748B),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = event.aiMessage,
                color = Color(0xFF1E293B),
                fontSize = 15.sp,
                lineHeight = 23.sp
            )
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = Color.White
    ) {
        val items = listOf(
            "导盲" to "🦯",
            "地图" to "🗺️",
            "协同" to "🔗",
            "求助" to "🆘",
            "我的" to "👤"
        )

        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = {
                    Text(
                        text = item.second,
                        fontSize = 20.sp
                    )
                },
                label = {
                    Text(
                        text = item.first,
                        fontSize = 12.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF0F766E),
                    selectedTextColor = Color(0xFF0F766E),
                    indicatorColor = Color(0xFFE0F2F1),
                    unselectedIconColor = Color(0xFF94A3B8),
                    unselectedTextColor = Color(0xFF94A3B8)
                )
            )
        }
    }
}