package com.nankai.smartcane.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.nankai.smartcane.data.local.DemoData
import com.nankai.smartcane.data.local.LocalAppPreferences
import com.nankai.smartcane.data.local.StoredAppState
import com.nankai.smartcane.data.model.AppMode
import com.nankai.smartcane.data.model.CareRelation
import com.nankai.smartcane.data.model.CareRequest
import com.nankai.smartcane.data.model.PairingCode
import com.nankai.smartcane.data.model.PairingFlowStatus
import com.nankai.smartcane.data.model.RelationStatus
import com.nankai.smartcane.data.model.UserProfile
import com.nankai.smartcane.data.model.UserRole
import com.nankai.smartcane.data.network.ApiResult
import com.nankai.smartcane.data.network.EmergencyAlertDto
import com.nankai.smartcane.data.network.SmartCaneApiClient
import com.nankai.smartcane.data.network.SosRequestDto
import com.nankai.smartcane.data.repository.AuthRepository
import com.nankai.smartcane.data.repository.DemoAuthRepository
import com.nankai.smartcane.data.repository.PairingRepository
import com.nankai.smartcane.data.repository.RemotePairingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmartCaneAppController private constructor(
    private val authRepository: AuthRepository,
    private val pairingRepository: PairingRepository,
    private val preferences: LocalAppPreferences,
    private val appContext: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var voiceRecognitionActive = false
    private var blindPollingJob: Job? = null
    private var companionPollingJob: Job? = null
    private var alertPollingJob: Job? = null
    private var lastAlertId: Int = 0

    private val _uiState = MutableStateFlow(AppUiState(storedState = preferences.state.value))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            preferences.state.collectLatest { stored ->
                _uiState.update { current -> current.copy(storedState = stored) }
            }
        }
    }

    fun login(account: String, password: String, rememberLogin: Boolean) {
        if (_uiState.value.isBusy) return
        scope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            val result = authRepository.login(account, password, rememberLogin)
            if (result.success && result.user != null) {
                _uiState.update { it.copy(isBusy = false, message = "登录成功") }
            } else {
                _uiState.update { it.copy(isBusy = false, message = result.message) }
            }
        }
    }

    fun loginDemoBlind() = login(DemoData.BLIND_ACCOUNT, DemoData.DEMO_PASSWORD, true)
    fun loginDemoCompanion() = login(DemoData.COMPANION_ACCOUNT, DemoData.DEMO_PASSWORD, true)

    fun selectMode(mode: AppMode) {
        preferences.saveMode(mode)
        preferences.saveFirstGuideCompleted(true)
        _uiState.update { it.copy(message = "已切换") }
    }

    fun switchMode() {
        val next = if (_uiState.value.currentMode == AppMode.Companion) AppMode.Blind else AppMode.Companion
        selectMode(next)
    }

    fun logout() {
        stopPairingPolling()
        stopAlertPolling()
        scope.launch {
            authRepository.logout()
            _uiState.update { it.copy(message = null, currentRelation = null, pendingRequest = null, urgentAlert = null, pairingStatus = PairingFlowStatus.Idle) }
        }
    }

    fun clearDemoData() {
        scope.launch {
            authRepository.clearDemoData()
            _uiState.update { it.copy(message = "已清除", currentRelation = null, pendingRequest = null, pairingStatus = PairingFlowStatus.Idle) }
        }
    }

    fun dismissMessage() { _uiState.update { it.copy(message = null) } }

    fun generatePairingCode() {
        val user = _uiState.value.currentUser ?: DemoData.blindUser
        if (_uiState.value.isBusy) return
        scope.launch {
            _uiState.update { it.copy(isBusy = true, pairingStatus = PairingFlowStatus.Loading, message = null) }
            val result = pairingRepository.generatePairingCode(user)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    lastPairingPreview = result.getOrNull(),
                    pairingStatus = if (result.isSuccess) PairingFlowStatus.Waiting else PairingFlowStatus.Error,
                    message = result.exceptionOrNull()?.message ?: "配对码已生成"
                )
            }
        }
    }

    fun findPairingCode(code: String) {
        if (_uiState.value.isBusy) return
        scope.launch {
            _uiState.update { it.copy(isBusy = true, pairingStatus = PairingFlowStatus.Loading, message = null) }
            val result = pairingRepository.findPairingCode(code)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    lastPairingPreview = result.getOrNull(),
                    pairingStatus = if (result.isSuccess) PairingFlowStatus.Idle else PairingFlowStatus.Error,
                    message = result.exceptionOrNull()?.message ?: "已找到"
                )
            }
        }
    }

    fun sendRelationRequest(code: String) {
        val companion = _uiState.value.currentUser ?: DemoData.companionUser
        if (_uiState.value.isBusy) return
        scope.launch {
            _uiState.update { it.copy(isBusy = true, pairingStatus = PairingFlowStatus.Loading, message = null) }
            val result = pairingRepository.sendRelationRequest(code, companion)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    pairingStatus = if (result.isSuccess) PairingFlowStatus.Waiting else PairingFlowStatus.Error,
                    message = result.exceptionOrNull()?.message ?: "申请已发送"
                )
            }
            if (result.isSuccess) startCompanionRelationPolling()
        }
    }

    fun approveRelation() {
        val requestId = _uiState.value.pendingRequest?.requestId ?: _uiState.value.storedState.pendingRequestId ?: return
        if (_uiState.value.isBusy) return
        scope.launch {
            _uiState.update { it.copy(isBusy = true, pairingStatus = PairingFlowStatus.Loading) }
            val result = pairingRepository.approveRequest(requestId)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    currentRelation = result.getOrNull(),
                    pendingRequest = null,
                    pairingStatus = if (result.isSuccess) PairingFlowStatus.Connected else PairingFlowStatus.Error,
                    message = result.exceptionOrNull()?.message ?: "已同意"
                )
            }
        }
    }

    fun rejectRelation() {
        val requestId = _uiState.value.pendingRequest?.requestId ?: _uiState.value.storedState.pendingRequestId ?: return
        if (_uiState.value.isBusy) return
        scope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val result = pairingRepository.rejectRequest(requestId)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    pendingRequest = null,
                    pairingStatus = if (result.isSuccess) PairingFlowStatus.Rejected else PairingFlowStatus.Error,
                    message = result.exceptionOrNull()?.message ?: "已拒绝"
                )
            }
        }
    }

    fun unlinkRelation() {
        val relationId = _uiState.value.currentRelation?.relationId ?: _uiState.value.storedState.relationId
        if (_uiState.value.isBusy) return
        scope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val result = pairingRepository.unlinkRelation(relationId)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    currentRelation = null,
                    pendingRequest = null,
                    pairingStatus = if (result.isSuccess) PairingFlowStatus.Idle else PairingFlowStatus.Error,
                    message = result.exceptionOrNull()?.message ?: "已解除"
                )
            }
        }
    }

    fun refreshCurrentRelation() {
        val user = _uiState.value.currentUser ?: return
        scope.launch {
            val result = pairingRepository.getCurrentRelation(user)
            result.getOrNull()?.let { relation ->
                _uiState.update { it.copy(currentRelation = relation, pairingStatus = PairingFlowStatus.Connected) }
            }
        }
    }

    fun startBlindRequestPolling() {
        if (blindPollingJob?.isActive == true) return
        val user = _uiState.value.currentUser ?: return
        blindPollingJob = scope.launch {
            while (true) {
                val relation = pairingRepository.getCurrentRelation(user).getOrNull()
                if (relation != null) {
                    _uiState.update { it.copy(currentRelation = relation, pendingRequest = null, pairingStatus = PairingFlowStatus.Connected) }
                } else {
                    val requests = pairingRepository.getPendingRequests(user).getOrNull().orEmpty()
                    val pending = requests.firstOrNull()
                    _uiState.update {
                        it.copy(
                            pendingRequest = pending,
                            pairingStatus = when {
                                pending != null -> PairingFlowStatus.PendingApproval
                                it.storedState.pairingCode != null -> PairingFlowStatus.Waiting
                                else -> it.pairingStatus
                            }
                        )
                    }
                }
                delay(4_000L)
            }
        }
    }

    fun startCompanionRelationPolling() {
        if (companionPollingJob?.isActive == true) return
        val user = _uiState.value.currentUser ?: return
        companionPollingJob = scope.launch {
            while (true) {
                val relationResult = pairingRepository.getCurrentRelation(user)
                val relation = relationResult.getOrNull()
                if (relation != null) {
                    _uiState.update { it.copy(currentRelation = relation, pairingStatus = PairingFlowStatus.Connected, pendingRequest = null, message = "关联成功") }
                } else {
                    val requests = pairingRepository.getCompanionRequests(user).getOrNull().orEmpty()
                    val latest = requests.lastOrNull()
                    if (latest != null) {
                        _uiState.update {
                            it.copy(
                                pendingRequest = latest,
                                pairingStatus = when (latest.status) {
                                    RelationStatus.Rejected -> PairingFlowStatus.Rejected
                                    RelationStatus.Active -> PairingFlowStatus.Connected
                                    else -> PairingFlowStatus.Waiting
                                }
                            )
                        }
                    }
                }
                delay(4_000L)
            }
        }
    }

    fun stopPairingPolling() {
        blindPollingJob?.cancel()
        companionPollingJob?.cancel()
        blindPollingJob = null
        companionPollingJob = null
    }

    fun startAlertPolling() {
        if (alertPollingJob?.isActive == true) return
        alertPollingJob = scope.launch {
            while (true) {
                val state = _uiState.value
                val mode = state.currentMode
                val role = when (mode) {
                    AppMode.Companion -> "companion"
                    AppMode.Blind -> "blind"
                    null -> state.currentUser?.role?.apiValue ?: "blind"
                }
                val userId = state.currentUser?.account ?: state.currentUser?.userId
                val deviceId = state.currentRelation?.caneDevice?.deviceId
                    ?: state.storedState.relationId?.let { DemoData.defaultCane.deviceId }
                    ?: DemoData.defaultCane.deviceId
                when (val result = SmartCaneApiClient.getLatestAlerts(role, userId, deviceId, lastAlertId)) {
                    is ApiResult.Success -> {
                        val newest = result.data.maxByOrNull { it.id }
                        if (newest != null && newest.id > lastAlertId) {
                            lastAlertId = newest.id
                            if (newest.riskType == "voice_request" && role == "blind") {
                                _uiState.update {
                                    it.copy(
                                        urgentAlert = null,
                                        voiceState = VoiceState.Speaking,
                                        message = "盲杖按钮已触发"
                                    )
                                }
                                speakText(newest.voicePrompt.ifBlank { newest.message }, listenAfter = true)
                            } else {
                                _uiState.update { it.copy(urgentAlert = newest, message = newest.title) }
                                if (role == "blind") {
                                    speakText(newest.voicePrompt.ifBlank { newest.message })
                                }
                            }
                        }
                    }
                    is ApiResult.Failure -> Unit
                }
                delay(5_000L)
            }
        }
    }

    fun stopAlertPolling() {
        alertPollingJob?.cancel()
        alertPollingJob = null
    }

    fun dismissUrgentAlert() {
        _uiState.update { it.copy(urgentAlert = null) }
    }

    fun toggleVoiceListening() {
        when (_uiState.value.voiceState) {
            VoiceState.Listening -> stopVoiceListening("已收到")
            else -> startVoiceListening()
        }
    }

    fun repeatNavigationPrompt() {
        val text = "前方路口建议直行"
        speakText(text)
    }

    fun speakText(text: String) {
        speakText(text, listenAfter = false)
    }

    private fun speakText(text: String, listenAfter: Boolean) {
        _uiState.update { it.copy(lastSpokenText = text, voiceState = VoiceState.Speaking, message = "正在播报") }
        val engine = tts
        if (engine == null) {
            tts = TextToSpeech(appContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.CHINESE
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smartcane_${System.currentTimeMillis()}")
                }
            }
        } else if (ttsReady) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smartcane_${System.currentTimeMillis()}")
        }
        scope.launch {
            delay(1600L)
            if (listenAfter) {
                startVoiceListening()
            } else {
                _uiState.update {
                    if (it.voiceState == VoiceState.Speaking) {
                        it.copy(voiceState = VoiceState.Idle)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun startVoiceListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _uiState.update { it.copy(voiceState = VoiceState.Idle, message = "手机不支持系统语音识别") }
            return
        }

        val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            speechRecognizer = it
            it.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _uiState.update { state -> state.copy(voiceState = VoiceState.Listening, message = "正在听你说") }
                }

                override fun onBeginningOfSpeech() {
                    _uiState.update { state -> state.copy(message = "正在识别") }
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    voiceRecognitionActive = false
                    _uiState.update { state -> state.copy(message = "正在理解") }
                }

                override fun onError(error: Int) {
                    voiceRecognitionActive = false
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "没听清，请再按一次按钮"
                        SpeechRecognizer.ERROR_AUDIO -> "麦克风异常"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音网络不可用"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "请给 App 开启麦克风权限"
                        else -> "语音识别失败，请再试一次"
                    }
                    _uiState.update { state -> state.copy(voiceState = VoiceState.Idle, message = message) }
                }

                override fun onResults(results: Bundle?) {
                    voiceRecognitionActive = false
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (text.isNullOrBlank()) {
                        _uiState.update { state -> state.copy(voiceState = VoiceState.Idle, message = "没听清，请再试一次") }
                        return
                    }

                    scope.launch {
                        _uiState.update { state -> state.copy(voiceState = VoiceState.Idle, message = "你说：$text") }
                        when (val result = SmartCaneApiClient.postVoiceRoute(text, null, null)) {
                            is ApiResult.Success -> speakText(result.data.voicePrompt.ifBlank { "已收到路线请求" })
                            is ApiResult.Failure -> speakText("语音指令已收到，但后端暂时不可用")
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出目的地或操作指令")
        }

        try {
            voiceRecognitionActive = true
            _uiState.update { it.copy(voiceState = VoiceState.Listening, message = "正在听你说") }
            recognizer.startListening(intent)
        } catch (_: SecurityException) {
            voiceRecognitionActive = false
            _uiState.update { it.copy(voiceState = VoiceState.Idle, message = "请在系统设置中开启麦克风权限") }
        }
    }

    private fun stopVoiceListening(message: String) {
        if (voiceRecognitionActive) {
            speechRecognizer?.stopListening()
        }
        voiceRecognitionActive = false
        _uiState.update { it.copy(voiceState = VoiceState.Idle, message = message) }
    }

    fun sendBlindSos() {
        if (_uiState.value.sosState == SosActionState.Sending) return
        scope.launch {
            _uiState.update { it.copy(sosState = SosActionState.Sending, message = null) }
            val result = SmartCaneApiClient.postSos(SosRequestDto(DemoData.defaultCane.deviceId, null, null, "用户端发起紧急求助"))
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(sosState = SosActionState.Success, message = "SOS 已发送") }
                    speakText("紧急求助已发送")
                }
                is ApiResult.Failure -> _uiState.update { it.copy(sosState = SosActionState.Error, message = "发送失败") }
            }
        }
    }

    fun release() {
        stopPairingPolling()
        stopAlertPolling()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        voiceRecognitionActive = false
    }

    fun relation(): CareRelation? {
        _uiState.value.currentRelation?.let { return it }
        val state = _uiState.value.storedState
        if (state.relationStatus != RelationStatus.Active || state.relationId == null) return null
        val now = state.relationUpdatedAtMillis ?: System.currentTimeMillis()
        return CareRelation(state.relationId, DemoData.blindUser, state.companionUser ?: DemoData.companionUser, DemoData.defaultCane, RelationStatus.Active, now, now)
    }

    fun relationUpdateText(): String {
        val millis = _uiState.value.currentRelation?.updatedAtMillis ?: _uiState.value.storedState.relationUpdatedAtMillis ?: System.currentTimeMillis()
        return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))
    }

    companion object {
        @Volatile private var INSTANCE: SmartCaneAppController? = null
        fun get(context: Context): SmartCaneAppController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val prefs = LocalAppPreferences(context.applicationContext)
                    val controller = SmartCaneAppController(
                        authRepository = DemoAuthRepository(prefs),
                        pairingRepository = RemotePairingRepository(prefs),
                        preferences = prefs,
                        appContext = context.applicationContext
                    )
                    INSTANCE = controller
                    controller
                }
            }
        }
    }
}

enum class VoiceState(val label: String) { Idle("点击说话"), Listening("正在聆听"), Speaking("正在播报") }
enum class SosActionState { Idle, Sending, Success, Error }

data class AppUiState(
    val storedState: StoredAppState,
    val isBusy: Boolean = false,
    val message: String? = null,
    val lastPairingPreview: PairingCode? = null,
    val pairingStatus: PairingFlowStatus = PairingFlowStatus.Idle,
    val pendingRequest: CareRequest? = null,
    val currentRelation: CareRelation? = null,
    val voiceState: VoiceState = VoiceState.Idle,
    val sosState: SosActionState = SosActionState.Idle,
    val isNavigationPaused: Boolean = false,
    val lastSpokenText: String? = null,
    val urgentAlert: EmergencyAlertDto? = null
) {
    val isLoggedIn: Boolean get() = storedState.isLoggedIn
    val currentUser: UserProfile? get() = storedState.currentUser
    val currentMode: AppMode? get() = storedState.lastMode
    val shouldShowModeSelection: Boolean get() = isLoggedIn && currentMode == null
}

private fun UserRole.defaultMode(): AppMode = when (this) {
    UserRole.Blind -> AppMode.Blind
    UserRole.Companion -> AppMode.Companion
}
