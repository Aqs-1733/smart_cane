package com.nankai.smartcane.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankai.smartcane.data.local.DemoData
import com.nankai.smartcane.data.model.CareRelation
import com.nankai.smartcane.data.model.RelationStatus
import com.nankai.smartcane.data.network.EmergencyAlertDto
import com.nankai.smartcane.ui.components.BigPrimaryButton
import com.nankai.smartcane.ui.components.BigSecondaryButton
import com.nankai.smartcane.ui.components.LabelValueRow
import com.nankai.smartcane.ui.components.ScreenTitle
import com.nankai.smartcane.ui.components.SmartBg
import com.nankai.smartcane.ui.components.SmartDark
import com.nankai.smartcane.ui.components.SmartMuted
import com.nankai.smartcane.ui.components.StatusPill

@Composable
fun CompanionHomeScreen(
    relation: CareRelation?,
    relationUpdateText: String,
    urgentAlert: EmergencyAlertDto?,
    onAddCareTarget: () -> Unit,
    onViewLocation: () -> Unit,
    onViewRiskRecords: () -> Unit,
    onViewDevice: () -> Unit,
    onSwitchMode: () -> Unit,
    onLogout: () -> Unit,
    onClearDemoData: () -> Unit,
    onDismissAlert: () -> Unit,
    onUnlink: () -> Unit
) {
    var showUnlink by rememberSaveable { mutableStateOf(false) }
    val scenario = DemoData.navigationScenario
    val cane = relation?.caneDevice ?: DemoData.defaultCane
    val active = relation?.status == RelationStatus.Active
    val blindName = if (active) relation?.blindUser?.displayName ?: DemoData.blindUser.displayName else "未关联"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SmartBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenTitle("陪护", "状态、位置与风险总览")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "被陪护人$blindName，${if (active) "正在导航" else "未关联"}" },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF2563EB))))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(blindName, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text(if (active) "正在导航 · ${scenario.destination}" else "请先添加陪护对象", color = Color(0xFFE0F2FE), fontSize = 16.sp, lineHeight = 23.sp)
                if (!active) BigPrimaryButton("添加陪护对象", onClick = onAddCareTarget, containerColor = Color.White, contentColor = Color(0xFF0F766E))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactMetric("导航", if (active) "直行" else "暂无", Modifier.weight(1f))
            CompactMetric("盲杖", if (cane.online) "在线" else "离线", Modifier.weight(1f))
            CompactMetric("SOS", "未触发", Modifier.weight(1f))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("当前重点", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SmartDark)
                if (urgentAlert != null) {
                    LabelValueRow(urgentAlert.title, urgentAlert.message)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatusPill(urgentAlert.priority.uppercase())
                        TextButton(onClick = onDismissAlert) { Text("已知晓") }
                    }
                }
                LabelValueRow("下一条建议", if (active) scenario.nextInstruction else "暂无")
                LabelValueRow("最近风险", urgentAlert?.message ?: if (active) DemoData.latestRiskAlert.message else "暂无")
                LabelValueRow("盲杖", if (cane.online) "已连接" else "未连接")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatusPill(if (cane.online) "在线" else "离线")
                    Text("最后更新 ${cane.lastSeenText}", color = SmartDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                LabelValueRow("更新时间", if (active) relationUpdateText else "暂无")
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigPrimaryButton("位置", onClick = onViewLocation, modifier = Modifier.weight(1f))
            BigSecondaryButton("风险", onClick = onViewRiskRecords, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigSecondaryButton("设备", onClick = onViewDevice, modifier = Modifier.weight(1f))
            BigSecondaryButton("管理", onClick = onAddCareTarget, modifier = Modifier.weight(1f))
        }
        if (active) BigSecondaryButton("解除关联", onClick = { showUnlink = true })
        BigSecondaryButton("切换到盲人模式", onClick = onSwitchMode)
        BigSecondaryButton("清除演示数据", onClick = onClearDemoData)
        BigSecondaryButton("退出登录", onClick = onLogout)
    }

    if (showUnlink) {
        AlertDialog(
            onDismissRequest = { showUnlink = false },
            title = { Text("解除关联？") },
            text = { Text("解除后需要重新配对。") },
            confirmButton = { TextButton(onClick = { showUnlink = false; onUnlink() }) { Text("解除") } },
            dismissButton = { TextButton(onClick = { showUnlink = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun CompactMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = SmartMuted, fontSize = 13.sp, maxLines = 1)
            Text(value, color = SmartDark, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}


