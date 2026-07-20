package com.nankai.smartcane.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankai.smartcane.ui.components.SmartTeal

@Composable
fun LoginScreen(
    isBusy: Boolean,
    message: String?,
    onLogin: (String, String, Boolean) -> Unit,
    onBlindDemo: () -> Unit,
    onCompanionDemo: () -> Unit,
    onMessageShown: () -> Unit
) {
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank() && !message.contains("成功")) localError = message
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEAF7F6), Color(0xFFF7FAFC), Color(0xFFEFF6FF))
                )
            )
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.size(72.dp).shadow(10.dp, CircleShape),
                shape = CircleShape,
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("S", color = SmartTeal, fontSize = 36.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("SmartCane", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
            Text("智能导盲与远程陪护", fontSize = 16.sp, color = Color(0xFF64748B), lineHeight = 22.sp)

            Spacer(Modifier.height(26.dp))
            OutlinedTextField(
                value = account,
                onValueChange = { account = it; localError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("账号") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; localError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.semantics { contentDescription = if (passwordVisible) "隐藏密码" else "显示密码" }
                    ) { Text(if (passwordVisible) "隐藏" else "显示", fontSize = 13.sp, color = SmartTeal) }
                }
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.heightIn(min = 22.dp), contentAlignment = Alignment.CenterStart) {
                localError?.let { Text(it, color = Color(0xFFDC2626), fontSize = 14.sp, maxLines = 1) }
            }
            Button(
                onClick = {
                    localError = when {
                        account.isBlank() -> "请输入账号"
                        password.isBlank() -> "请输入密码"
                        else -> null
                    }
                    if (localError == null) onLogin(account, password, true)
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SmartTeal)
            ) { Text(if (isBusy) "登录中…" else "登录", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { localError = "暂未开放注册" }) { Text("注册", color = Color(0xFF64748B)) }
                TextButton(onClick = { localError = "暂不支持找回密码" }) { Text("忘记密码", color = Color(0xFF64748B)) }
            }

            Spacer(Modifier.weight(1f))
            Text("快速体验", color = Color(0xFF334155), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExperienceButton("体验盲人端", "直达首页", Modifier.weight(1f), !isBusy, onBlindDemo)
                ExperienceButton("体验陪护端", "查看状态", Modifier.weight(1f), !isBusy, onCompanionDemo)
            }
            Spacer(Modifier.height(8.dp))
            Text("演示账号不保存真实密码", color = Color(0xFF94A3B8), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ExperienceButton(title: String, subtitle: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF64748B), maxLines = 1)
        }
    }
}
