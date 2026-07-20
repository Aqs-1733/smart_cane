package com.nankai.smartcane.data.repository

import com.nankai.smartcane.data.local.DemoData
import com.nankai.smartcane.data.local.LocalAppPreferences
import com.nankai.smartcane.data.model.AuthResult
import com.nankai.smartcane.data.model.UserProfile

interface AuthRepository {
    suspend fun login(account: String, password: String, rememberLogin: Boolean): AuthResult
    suspend fun logout()
    suspend fun clearDemoData()
}

class DemoAuthRepository(
    private val preferences: LocalAppPreferences
) : AuthRepository {
    override suspend fun login(account: String, password: String, rememberLogin: Boolean): AuthResult {
        val normalized = account.trim()
        val user: UserProfile? = when (normalized) {
            DemoData.BLIND_ACCOUNT -> DemoData.blindUser
            DemoData.COMPANION_ACCOUNT -> DemoData.companionUser
            else -> null
        }

        if (user == null || password != DemoData.DEMO_PASSWORD) {
            return AuthResult(
                success = false,
                message = "账号或密码错误。演示账号为 blind_demo / companion_demo，密码 123456。"
            )
        }

        preferences.saveLogin(user, rememberLogin = rememberLogin)
        return AuthResult(success = true, user = user, message = "登录成功，当前为本地演示模式。")
    }

    override suspend fun logout() {
        preferences.logout()
    }

    override suspend fun clearDemoData() {
        preferences.clearDemoData()
    }
}

