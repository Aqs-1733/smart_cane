package com.nankai.smartcane.ui.blind

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankai.smartcane.data.network.EmergencyAlertDto
import com.nankai.smartcane.ui.design.SmartCaneColors
import com.nankai.smartcane.viewmodel.SosActionState
import com.nankai.smartcane.viewmodel.VoiceState

@Composable
fun BlindHomeScreen(
    voiceState: VoiceState,
    sosState: SosActionState,
    message: String?,
    urgentAlert: EmergencyAlertDto?,
    onVoiceToggle: () -> Unit,
    onRepeat: () -> Unit,
    onSos: () -> Unit,
    onDismissAlert: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showSosConfirm by rememberSaveable { mutableStateOf(false) }
    val prompt = "前方路口建议直行"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SmartCaneColors.BlindTop, Color(0xFF172554), SmartCaneColors.BlindBottom)))
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(0.20f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(urgentAlert?.title ?: "你好，我已准备好。", color = Color.White, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        urgentAlert?.message ?: prompt,
                        color = if (urgentAlert != null) Color(0xFFFECACA) else Color(0xFFC7D2FE),
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                }
                TextButton(onClick = if (urgentAlert != null) onDismissAlert else onOpenSettings, modifier = Modifier.semantics { contentDescription = "我的和设置" }) {
                    Text(if (urgentAlert != null) "知道了" else "设置", color = Color(0xFFE0E7FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.52f)
                    .clip(RoundedCornerShape(36.dp))
                    .background(SmartCaneColors.BlindPanel)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                VoiceOrb(state = voiceState, onPressToggle = onVoiceToggle)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onRepeat,
                        modifier = Modifier.weight(1f).height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) { Text("重复播报", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = onVoiceToggle,
                        modifier = Modifier.weight(1f).height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) { Text(if (voiceState == VoiceState.Listening) "结束" else "说话", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                }
                Text(
                    text = when (voiceState) {
                        VoiceState.Idle -> message?.takeIf { it.length <= 12 } ?: "按住说话，松开结束"
                        VoiceState.Listening -> "正在听你说"
                        VoiceState.Speaking -> "正在播报"
                    },
                    color = Color(0xFFE0E7FF),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 12.dp),
                    maxLines = 1
                )
            }

            Spacer(Modifier.height(18.dp))
            Box(modifier = Modifier.fillMaxWidth().weight(0.28f), contentAlignment = Alignment.Center) {
                SosButton(sosState = sosState, onClick = { if (sosState != SosActionState.Sending) showSosConfirm = true })
            }
        }
    }

    if (showSosConfirm) {
        AlertDialog(
            onDismissRequest = { showSosConfirm = false },
            title = { Text("确认紧急求助？") },
            text = { Text("确认后提交 SOS 求助。") },
            confirmButton = { TextButton(onClick = { showSosConfirm = false; onSos() }) { Text("确认发送") } },
            dismissButton = { TextButton(onClick = { showSosConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun VoiceOrb(state: VoiceState, onPressToggle: () -> Unit) {
    val label = when (state) {
        VoiceState.Idle -> "按住说话"
        VoiceState.Listening -> "正在聆听"
        VoiceState.Speaking -> "正在播报"
    }
    Box(
        modifier = Modifier
            .size(218.dp)
            .shadow(28.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFA5B4FC), Color(0xFF6366F1), Color(0xFF312E81))))
            .pointerInput(state) {
                detectTapGestures(
                    onPress = {
                        if (state != VoiceState.Speaking) {
                            onPressToggle()
                            tryAwaitRelease()
                            if (state == VoiceState.Idle) onPressToggle()
                        }
                    }
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = "语音按钮，双击或按住开始说话"
                stateDescription = label
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            SoundWave(active = state == VoiceState.Listening || state == VoiceState.Speaking)
            Spacer(Modifier.height(14.dp))
            Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun SoundWave(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "voice-wave")
    val a by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "a")
    val b by transition.animateFloat(1f, 0.45f, infiniteRepeatable(tween(680), RepeatMode.Reverse), label = "b")
    val c by transition.animateFloat(0.5f, 1f, infiniteRepeatable(tween(440), RepeatMode.Reverse), label = "c")
    val scales = if (active) listOf(a, b, c, b, a) else listOf(0.45f, 0.65f, 0.85f, 0.65f, 0.45f)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        scales.forEach { scale ->
            Box(Modifier.size(width = 10.dp, height = (58 * scale).dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.92f)))
        }
    }
}

@Composable
private fun SosButton(sosState: SosActionState, onClick: () -> Unit) {
    val statusText = when (sosState) {
        SosActionState.Idle -> "紧急求助"
        SosActionState.Sending -> "发送中"
        SosActionState.Success -> "已发送"
        SosActionState.Error -> "重试求助"
    }
    Box(
        modifier = Modifier
            .size(168.dp)
            .shadow(20.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFF6B6B), Color(0xFFDC2626), Color(0xFF7F1D1D))))
            .clickable(enabled = sosState != SosActionState.Sending, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "紧急求助按钮，双击后需要再次确认"
                stateDescription = statusText
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SOS", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(statusText, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
