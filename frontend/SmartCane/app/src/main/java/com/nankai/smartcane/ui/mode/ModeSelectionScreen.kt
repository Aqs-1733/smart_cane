package com.nankai.smartcane.ui.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankai.smartcane.data.model.AppMode
import com.nankai.smartcane.ui.components.BigSecondaryButton
import com.nankai.smartcane.ui.components.DemoBanner
import com.nankai.smartcane.ui.components.ScreenTitle
import com.nankai.smartcane.ui.components.SmartBg
import com.nankai.smartcane.ui.components.SmartDark
import com.nankai.smartcane.ui.components.SmartMuted
import com.nankai.smartcane.ui.components.SmartTeal

@Composable
fun ModeSelectionScreen(
    userName: String,
    onModeSelected: (AppMode) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SmartBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ScreenTitle(
            title = "请选择使用模式",
            subtitle = "$userName，欢迎使用 SmartCane 本地演示。"
        )
        DemoBanner("选择会保存为上次模式，下次启动可直接进入；个人中心可切换模式。")

        ModeCard(
            title = "盲人导航模式",
            icon = "盲",
            bullets = listOf("路口风险建议", "语音导航", "重复播报", "设备状态", "紧急求助"),
            onClick = { onModeSelected(AppMode.Blind) }
        )
        ModeCard(
            title = "陪护模式",
            icon = "护",
            bullets = listOf("查看被陪护人状态", "查看盲杖在线状态", "风险提醒", "SOS 信息", "位置与路线展示"),
            onClick = { onModeSelected(AppMode.Companion) }
        )
        BigSecondaryButton(text = "退出登录", onClick = onLogout)
    }
}

@Composable
private fun ModeCard(
    title: String,
    icon: String,
    bullets: List<String>,
    onClick: () -> Unit
) {
    val desc = "$title。" + bullets.joinToString("，")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = desc
            },
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(icon, fontSize = 30.sp, color = SmartTeal, fontWeight = FontWeight.Bold)
                Text(title, fontSize = 22.sp, lineHeight = 28.sp, color = SmartDark, fontWeight = FontWeight.Bold)
            }
            bullets.forEach { item ->
                Text("• $item", fontSize = 16.sp, lineHeight = 23.sp, color = SmartMuted)
            }
        }
    }
}


