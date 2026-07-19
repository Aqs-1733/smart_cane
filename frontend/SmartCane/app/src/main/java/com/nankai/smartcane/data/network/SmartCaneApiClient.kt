package com.nankai.smartcane.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiConfig {
    /**
     * Android 模拟器访问宿主机的固定地址是 10.0.2.2。
     * 如果在真机运行，请把这里集中改成电脑局域网 IPv4，例如：
     * const val BASE_URL = "http://192.168.1.23:8000"
     */
    const val BASE_URL = "http://10.136.53.207:8000"
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val message: String) : ApiResult<Nothing>
}

data class LatestRiskEventDto(
    val id: Int,
    val deviceId: String,
    val riskType: String,
    val riskLevel: String,
    val distance: Int?,
    val message: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: String
)

data class ServerStatusDto(
    val online: Boolean,
    val message: String,
    val deviceCount: Int
)

data class DeviceDto(
    val deviceId: String,
    val name: String,
    val online: Boolean,
    val battery: Int?,
    val lastSeen: String
)

data class SosRequestDto(
    val deviceId: String,
    val latitude: Double?,
    val longitude: Double?,
    val message: String
)

data class SosRecordDto(
    val id: Int,
    val deviceId: String,
    val latitude: Double?,
    val longitude: Double?,
    val message: String,
    val receivedAt: String
)

data class SosResponseDto(
    val success: Boolean,
    val message: String,
    val sos: SosRecordDto?
)

object SmartCaneApiClient {
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    suspend fun getLatestEvents(): ApiResult<List<LatestRiskEventDto>> = withContext(Dispatchers.IO) {
        try {
            val response = getJson("/events/latest")
            val events = response.optJSONArray("events") ?: JSONArray()
            ApiResult.Success(
                List(events.length()) { index ->
                    events.getJSONObject(index).toLatestRiskEventDto()
                }
            )
        } catch (exception: Exception) {
            ApiResult.Failure(exception.toUserMessage())
        }
    }

    suspend fun getStatus(): ApiResult<ServerStatusDto> = withContext(Dispatchers.IO) {
        try {
            ApiResult.Success(getJson("/status").toServerStatusDto())
        } catch (exception: Exception) {
            ApiResult.Failure(exception.toUserMessage())
        }
    }

    suspend fun getDevices(): ApiResult<List<DeviceDto>> = withContext(Dispatchers.IO) {
        try {
            val response = getJson("/devices")
            val devices = response.optJSONArray("devices") ?: JSONArray()
            ApiResult.Success(
                List(devices.length()) { index ->
                    devices.getJSONObject(index).toDeviceDto()
                }
            )
        } catch (exception: Exception) {
            ApiResult.Failure(exception.toUserMessage())
        }
    }

    suspend fun postSos(request: SosRequestDto): ApiResult<SosResponseDto> = withContext(Dispatchers.IO) {
        try {
            ApiResult.Success(postJson("/sos", request.toJson()).toSosResponseDto())
        } catch (exception: Exception) {
            ApiResult.Failure(exception.toUserMessage())
        }
    }

    private fun getJson(path: String): JSONObject {
        val connection = (URL(ApiConfig.BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }

        return connection.useJsonConnection { code, body ->
            if (code in 200..299) {
                JSONObject(body)
            } else {
                val errorMessage = body.takeIf { it.isNotBlank() } ?: "HTTP $code"
                throw IllegalStateException("服务器返回错误：$errorMessage")
            }
        }
    }

    private fun postJson(path: String, payload: JSONObject): JSONObject {
        val body = payload.toString().toByteArray(Charsets.UTF_8)
        val connection = (URL(ApiConfig.BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setFixedLengthStreamingMode(body.size)
        }

        connection.outputStream.use { it.write(body) }

        return connection.useJsonConnection { code, responseBody ->
            if (code in 200..299) {
                JSONObject(responseBody)
            } else {
                val errorMessage = responseBody.takeIf { it.isNotBlank() } ?: "HTTP $code"
                throw IllegalStateException("????????$errorMessage")
            }
        }
    }

    private fun HttpURLConnection.useJsonConnection(block: (Int, String) -> JSONObject): JSONObject {
        return try {
            val code = responseCode
            val stream = if (code in 200..299) inputStream else errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            block(code, body)
        } finally {
            disconnect()
        }
    }

    private fun SosRequestDto.toJson(): JSONObject {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("latitude", latitude ?: JSONObject.NULL)
            put("longitude", longitude ?: JSONObject.NULL)
            put("message", message)
        }
    }

    private fun JSONObject.toLatestRiskEventDto(): LatestRiskEventDto {
        return LatestRiskEventDto(
            id = optInt("id"),
            deviceId = optString("deviceId"),
            riskType = optString("riskType"),
            riskLevel = optString("riskLevel"),
            distance = if (isNull("distance")) null else optInt("distance"),
            message = optString("message"),
            latitude = if (isNull("latitude")) null else optDouble("latitude"),
            longitude = if (isNull("longitude")) null else optDouble("longitude"),
            timestamp = optString("timestamp")
        )
    }

    private fun JSONObject.toServerStatusDto(): ServerStatusDto {
        return ServerStatusDto(
            online = optBoolean("online"),
            message = optString("message"),
            deviceCount = optInt("deviceCount")
        )
    }

    private fun JSONObject.toDeviceDto(): DeviceDto {
        return DeviceDto(
            deviceId = optString("deviceId"),
            name = optString("name"),
            online = optBoolean("online"),
            battery = if (isNull("battery")) null else optInt("battery"),
            lastSeen = optString("lastSeen")
        )
    }

    private fun JSONObject.toSosResponseDto(): SosResponseDto {
        return SosResponseDto(
            success = optBoolean("success"),
            message = optString("message"),
            sos = optJSONObject("sos")?.toSosRecordDto()
        )
    }

    private fun JSONObject.toSosRecordDto(): SosRecordDto {
        return SosRecordDto(
            id = optInt("id"),
            deviceId = optString("deviceId"),
            latitude = if (isNull("latitude")) null else optDouble("latitude"),
            longitude = if (isNull("longitude")) null else optDouble("longitude"),
            message = optString("message"),
            receivedAt = optString("receivedAt")
        )
    }

    private fun Exception.toUserMessage(): String {
        return when (this) {
            is java.net.ConnectException -> "无法连接后端服务，请确认 FastAPI 后端已启动，并检查后端 IP 配置。"
            is java.net.SocketTimeoutException -> "连接后端超时，请检查电脑和手机是否在同一网络。"
            is java.net.UnknownHostException -> "找不到后端服务器地址，请检查 API Base URL。"
            else -> message?.takeIf { it.isNotBlank() } ?: "网络请求失败，请稍后重试。"
        }
    }
}
