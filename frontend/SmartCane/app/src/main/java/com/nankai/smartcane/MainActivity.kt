package com.nankai.smartcane

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankai.smartcane.data.network.ApiResult
import com.nankai.smartcane.data.network.DeviceDto
import com.nankai.smartcane.data.network.LatestRiskEventDto
import com.nankai.smartcane.data.network.ServerStatusDto
import com.nankai.smartcane.data.network.SmartCaneApiClient
import com.nankai.smartcane.data.network.SosRequestDto
import com.nankai.smartcane.navigation.SmartCaneRootApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SmartCaneRootApp() }
    }
}

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
    RiskEvent(38, "cane_003", "front_obstacle", "low", "front", 1285, "前方约 128 厘米有障碍物，请减速并准备绕行。", "14:30:35"),
    RiskEvent(37, "cane_002", "left_obstacle", "high", "left", 422, "左侧约 42 厘米有障碍物，请向右侧保持距离。", "14:30:33"),
    RiskEvent(36, "cane_003", "right_obstacle", "medium", "right", 1196, "右侧约 119 厘米有障碍物，请向左侧保持距离。", "14:30:31"),
    RiskEvent(35, "cane_001", "ground_drop", "high", "down", 1200, "前方地面高度变化明显，可能有台阶或坑洼，请停止前进并用盲杖确认。", "14:30:29")
)

private sealed interface LatestEventsUiState {
    data object Loading : LatestEventsUiState
    data class Success(val events: List<RiskEvent>) : LatestEventsUiState
    data class Error(val message: String) : LatestEventsUiState
}

private sealed interface DeviceStatusUiState {
    data object Loading : DeviceStatusUiState
    data class Success(val status: ServerStatusDto, val devices: List<DeviceDto>) : DeviceStatusUiState
    data class Error(val message: String) : DeviceStatusUiState
}

private sealed interface SosUiState {
    data object Idle : SosUiState
    data object Sending : SosUiState
    data class Success(val message: String, val sentAt: String) : SosUiState
    data class Error(val message: String) : SosUiState
}

private fun LatestRiskEventDto.toRiskEvent(): RiskEvent = RiskEvent(
    id = id,
    deviceId = deviceId,
    riskType = riskType,
    level = riskLevel,
    direction = directionFromRiskType(riskType),
    distanceMm = distance,
    aiMessage = message,
    time = displayTimestamp(timestamp)
)

private fun directionFromRiskType(riskType: String): String = when {
    riskType.contains("left", ignoreCase = true) -> "left"
    riskType.contains("right", ignoreCase = true) -> "right"
    riskType.contains("drop", ignoreCase = true) || riskType.contains("down", ignoreCase = true) -> "down"
    else -> "front"
}

private fun displayTimestamp(timestamp: String): String = runCatching {
    timestamp.takeIf { it.length >= 16 }?.substring(11, 16) ?: timestamp
}.getOrDefault(timestamp.ifBlank { currentTimeText() })

private fun currentTimeText(): String = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())

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
            bottomBar = { BottomNavigationBar(selectedTab = selectedTab, onTabSelected = { selectedTab = it }) }
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title。$subtitle" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), lineHeight = 32.sp)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, fontSize = 14.sp, color = Color(0xFF64748B), lineHeight = 20.sp)
        }
        Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(999.dp)) {
            Text("在线", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color(0xFF166534), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GuidePage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item { AppHeader("智能导盲助手", "ESP32-C5 多设备协同感知") }
        item { CurrentRiskCard() }
        item { DistanceGrid() }
        item { LatestRiskFromServer() }
    }
}

@Composable
fun LatestRiskFromServer() {
    var retryKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LatestEventsUiState>(LatestEventsUiState.Loading) }

    LaunchedEffect(retryKey) {
        state = LatestEventsUiState.Loading
        state = when (val result = SmartCaneApiClient.getLatestEvents()) {
            is ApiResult.Success -> LatestEventsUiState.Success(result.data.map { it.toRiskEvent() }.ifEmpty { demoEvents })
            is ApiResult.Failure -> LatestEventsUiState.Error(result.message)
        }
    }

    when (val current = state) {
        LatestEventsUiState.Loading -> StateCard("最新风险提醒", "正在从后端加载最新风险数据…")
        is LatestEventsUiState.Success -> LatestRiskList("最新风险提醒", current.events)
        is LatestEventsUiState.Error -> StateCard("最新风险提醒", current.message, showRetry = true, onRetry = { retryKey++ })
    }
}

@Composable
fun StateCard(title: String, message: String, showRetry: Boolean = false, onRetry: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(message, fontSize = 15.sp, color = Color(0xFF64748B), lineHeight = 22.sp)
            if (showRetry) OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重试") }
        }
    }
}

@Composable
fun CurrentRiskCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "当前风险，高风险，左侧约四十二厘米有障碍物，请向右侧保持距离。" },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("当前风险", color = Color(0xFF64748B), fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text("高风险", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFDC2626))
            Spacer(Modifier.height(10.dp))
            Text("左侧约 42 厘米有障碍物，请向右侧保持距离。", fontSize = 17.sp, lineHeight = 26.sp, color = Color(0xFF1E293B))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusTag("左侧障碍")
                StatusTag("近距离")
            }
        }
    }
}

@Composable
fun StatusTag(text: String) {
    Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(999.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color(0xFF991B1B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DistanceGrid() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DistanceCard("前方", "128 cm", Color(0xFF0F766E), Modifier.weight(1f))
        DistanceCard("左侧", "42 cm", Color(0xFFDC2626), Modifier.weight(1f))
        DistanceCard("右侧", "119 cm", Color(0xFFF59E0B), Modifier.weight(1f))
    }
}

@Composable
fun DistanceCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$title 距离 $value" }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color(0xFF64748B), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DeviceStatusFromServer() {
    var retryKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<DeviceStatusUiState>(DeviceStatusUiState.Loading) }

    LaunchedEffect(retryKey) {
        state = DeviceStatusUiState.Loading
        val statusResult = SmartCaneApiClient.getStatus()
        val devicesResult = SmartCaneApiClient.getDevices()
        state = if (statusResult is ApiResult.Success && devicesResult is ApiResult.Success) {
            DeviceStatusUiState.Success(statusResult.data, devicesResult.data)
        } else {
            DeviceStatusUiState.Error(
                (statusResult as? ApiResult.Failure)?.message
                    ?: (devicesResult as? ApiResult.Failure)?.message
                    ?: "设备状态加载失败。"
            )
        }
    }

    when (val current = state) {
        DeviceStatusUiState.Loading -> StateCard("设备状态", "正在从后端加载设备状态…")
        is DeviceStatusUiState.Success -> DeviceStatusDataCard(current.status, current.devices)
        is DeviceStatusUiState.Error -> StateCard("设备状态", current.message, showRetry = true, onRetry = { retryKey++ })
    }
}

@Composable
fun DeviceStatusDataCard(status: ServerStatusDto, devices: List<DeviceDto>) {
    val total = devices.size.coerceAtLeast(status.deviceCount)
    val online = devices.count { it.online }
    val primary = devices.firstOrNull { it.deviceId == "cane_001" } ?: devices.firstOrNull()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "设备状态，后端${if (status.online) "在线" else "离线"}，在线设备 $online 台，共 $total 台。" },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("设备状态", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            StatusRow("后端状态", if (status.online) "在线" else "离线", if (status.online) Color(0xFF16A34A) else Color(0xFFDC2626))
            StatusRow("服务提示", status.message.ifBlank { "-" })
            StatusRow("已连接设备", "$online / $total 台在线")
            StatusRow("主要设备", primary?.name?.ifBlank { primary.deviceId } ?: "暂无设备")
            StatusRow("最后在线", primary?.lastSeen?.let(::displayTimestamp) ?: "-")
        }
    }
}

@Composable
fun DeviceStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("我的设备", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            StatusRow("设备名称", "SmartCane 001")
            StatusRow("设备 ID", "cane_001")
            StatusRow("连接状态", "在线", Color(0xFF16A34A))
            StatusRow("最后更新时间", "刚刚")
        }
    }
}

@Composable
fun StatusRow(label: String, value: String, valueColor: Color = Color(0xFF0F172A)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = Color(0xFF64748B), fontSize = 15.sp, modifier = Modifier.weight(1f), lineHeight = 21.sp)
        Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f), lineHeight = 21.sp)
    }
}

@Composable
fun MapPage() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { AppHeader("????", "???????????") }
        item { DemoRouteMap() }
        item { LatestRiskList("????", demoEvents.take(3)) }
    }
}

@Composable
fun DemoRouteMap() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("?????????", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Box(Modifier.fillMaxWidth().height(280.dp).background(Color(0xFFEFF6FF), RoundedCornerShape(22.dp))) {
                Canvas(Modifier.fillMaxSize().padding(20.dp)) {
                    val w = size.width
                    val h = size.height
                    val roadColor = Color(0xFFE2E8F0)
                    val routeColor = Color(0xFF2563EB)
                    for (i in 1..3) {
                        val y = h * i / 4f
                        drawLine(roadColor, Offset(0f, y), Offset(w, y), strokeWidth = 5f, cap = StrokeCap.Round)
                    }
                    for (i in 1..2) {
                        val x = w * i / 3f
                        drawLine(roadColor, Offset(x, 0f), Offset(x, h), strokeWidth = 5f, cap = StrokeCap.Round)
                    }
                    val route = Path().apply {
                        moveTo(w * 0.16f, h * 0.80f)
                        cubicTo(w * 0.30f, h * 0.68f, w * 0.36f, h * 0.54f, w * 0.48f, h * 0.50f)
                        cubicTo(w * 0.62f, h * 0.45f, w * 0.70f, h * 0.30f, w * 0.84f, h * 0.22f)
                    }
                    drawPath(route, routeColor, style = Stroke(width = 12f, cap = StrokeCap.Round))
                    drawCircle(Color(0xFF16A34A), radius = 18f, center = Offset(w * 0.16f, h * 0.80f))
                    drawCircle(Color(0xFFDC2626), radius = 18f, center = Offset(w * 0.84f, h * 0.22f))
                    drawCircle(Color(0xFFF59E0B), radius = 12f, center = Offset(w * 0.55f, h * 0.47f))
                    drawCircle(Color(0xFFF59E0B).copy(alpha = 0.18f), radius = 34f, center = Offset(w * 0.55f, h * 0.47f))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("???? ? ???", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("????????????", color = Color(0xFF64748B), fontSize = 13.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RouteLegend("??", Color(0xFF16A34A), Modifier.weight(1f))
                RouteLegend("??", Color(0xFFF59E0B), Modifier.weight(1f))
                RouteLegend("???", Color(0xFFDC2626), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun RouteLegend(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Surface(modifier = Modifier.size(10.dp), color = color, shape = RoundedCornerShape(999.dp)) {}
        Text(text, modifier = Modifier.padding(start = 6.dp), color = Color(0xFF64748B), fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun MapPlaceholder() {
    DemoRouteMap()
}

@Composable
fun MapDot(level: String, modifier: Modifier = Modifier) {
    val color = when (level) {
        "high" -> Color(0xFFDC2626)
        "medium" -> Color(0xFFF59E0B)
        else -> Color(0xFF16A34A)
    }
    Surface(modifier = modifier.size(18.dp), color = color, shape = RoundedCornerShape(999.dp)) {}
}

@Composable
fun CollaborationPage() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { AppHeader("多设备协同", "三台盲杖风险事件聚合") }
        item { StatisticsGrid() }
        item { DeviceListCard() }
        item { LatestRiskList("协同风险提醒", demoEvents) }
    }
}

@Composable
fun StatisticsGrid() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("累计事件", "38", "条", Modifier.weight(1f))
        StatCard("在线设备", "3", "台", Modifier.weight(1f))
        StatCard("高风险", "5", "条", Modifier.weight(1f))
    }
}

@Composable
fun StatCard(title: String, number: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color(0xFF64748B), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(number, color = Color(0xFF0F766E), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = Color(0xFF64748B), fontSize = 12.sp)
        }
    }
}

@Composable
fun DeviceListCard() {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
            Text("设备列表", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Spacer(Modifier.height(10.dp))
            DeviceRow("盲杖一号", "在线", "已上传风险提醒")
            DeviceRow("盲杖二号", "在线", "已上传风险提醒")
            DeviceRow("盲杖三号", "在线", "已上传风险提醒")
        }
    }
}

@Composable
fun DeviceRow(id: String, status: String, desc: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) { contentDescription = "$id，$status，$desc" }, verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Color(0xFFE0F2FE), shape = RoundedCornerShape(12.dp)) {
            Text("杖", modifier = Modifier.padding(10.dp), color = Color(0xFF0369A1), fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(id, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(desc, color = Color(0xFF64748B), fontSize = 13.sp)
        }
        Text(status, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun SosPage() {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<SosUiState>(SosUiState.Idle) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    fun sendSos() {
        if (state == SosUiState.Sending) return
        scope.launch {
            state = SosUiState.Sending
            state = when (val result = SmartCaneApiClient.postSos(SosRequestDto("cane_001", null, null, "用户通过 Android App 发起紧急求助（位置不可用）"))) {
                is ApiResult.Success -> if (result.data.success) {
                    SosUiState.Success(result.data.message.ifBlank { "SOS 已发送" }, currentTimeText())
                } else {
                    SosUiState.Error(result.data.message.ifBlank { "后端返回 SOS 发送失败" })
                }
                is ApiResult.Failure -> SosUiState.Error(result.message)
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { AppHeader("SOS 紧急求助", "二次确认，避免误触") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("紧急求助", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    Text("点击 SOS 后需要二次确认。确认后会尝试向现有后端发送演示求助信息。", textAlign = TextAlign.Center, color = Color(0xFF475569), fontSize = 16.sp, lineHeight = 24.sp)
                    Button(onClick = { showConfirmDialog = true }, enabled = state != SosUiState.Sending, modifier = Modifier.fillMaxWidth().height(68.dp)) {
                        Text(if (state == SosUiState.Sending) "发送中…" else "发送 SOS", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    SosStatusContent(state)
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认发送 SOS？") },
            text = { Text("确认后将向后端发送紧急求助演示信息。") },
            confirmButton = { TextButton(onClick = { showConfirmDialog = false; sendSos() }) { Text("确认发送") } },
            dismissButton = { TextButton(onClick = { showConfirmDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SosStatusContent(state: SosUiState) {
    when (state) {
        SosUiState.Idle -> Text("尚未发送 SOS。", color = Color(0xFF64748B))
        SosUiState.Sending -> Text("正在发送 SOS，请稍候…", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
        is SosUiState.Success -> Text("${state.message}\n发送时间：${state.sentAt}", color = Color(0xFF166534), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        is SosUiState.Error -> Text("发送失败：${state.message}", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun MinePage() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { AppHeader("我的设备", "设备管理与系统设置") }
        item { DeviceStatusCard() }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("系统说明", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text("当前 Android 前端保留原有网络能力，并新增本地演示登录、模式选择和陪护关联闭环。真实账号、跨手机同步仍需后端接口支持。", color = Color(0xFF64748B), fontSize = 15.sp, lineHeight = 23.sp)
                }
            }
        }
    }
}

@Composable
fun LatestRiskList(title: String, events: List<RiskEvent>) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Spacer(Modifier.height(10.dp))
            events.forEach { RiskEventItem(it) }
        }
    }
}

@Composable
fun RiskEventItem(event: RiskEvent) {
    val levelText = when (event.level.lowercase()) {
        "high" -> "高风险"
        "medium" -> "中风险"
        else -> "低风险"
    }
    val levelColor = when (event.level.lowercase()) {
        "high" -> Color(0xFFDC2626)
        "medium" -> Color(0xFFF59E0B)
        else -> Color(0xFF16A34A)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) { contentDescription = "$levelText，${event.aiMessage}，时间${event.time}" }, verticalAlignment = Alignment.Top) {
        Surface(color = levelColor.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
            Text(levelText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = levelColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(event.aiMessage, color = Color(0xFF1E293B), fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(event.time, color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
    }
}

@Composable
fun BottomNavigationBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(modifier = Modifier.navigationBarsPadding(), containerColor = Color.White) {
        val items = listOf("导盲" to "盲", "地图" to "图", "协同" to "协", "求助" to "救", "我的" to "我")
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Text(item.second, fontSize = 20.sp) },
                label = { Text(item.first, fontSize = 12.sp) },
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
