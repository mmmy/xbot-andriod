package com.gouge.xbot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gouge.xbot.data.ServerConfigStore
import com.gouge.xbot.data.SessionStore
import com.gouge.xbot.data.SignalViewDto
import com.gouge.xbot.data.AlertVisibilityStore
import com.gouge.xbot.data.TvAlertConfigDto
import com.gouge.xbot.data.TvAlertDto
import com.gouge.xbot.data.XbotRepository
import com.gouge.xbot.domain.tickerLabel
import com.gouge.xbot.domain.normalizeTradingViewTicker
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class MainUiState(
    val serverUrl: String,
    val isAuthenticated: Boolean,
    val isLoading: Boolean = false,
    val signals: List<SignalViewDto> = emptyList(),
    val errorMessage: String? = null,
    val editingSignal: SignalViewDto? = null,
    val isSavingSettings: Boolean = false,
    val settingsErrorMessage: String? = null,
    val alertConfigs: List<TvAlertConfigDto> = emptyList(),
    val visibleAlertIds: Set<String> = emptySet(),
    val tvAlertsByCookieId: Map<String, List<TvAlertDto>> = emptyMap(),
    val hasLoadedAlerts: Boolean = false,
    val isLoadingAlerts: Boolean = false,
    val alertErrorMessage: String? = null,
    val quickAlertConfig: TvAlertConfigDto? = null,
    val isCreatingAlert: Boolean = false,
    val alertSetupErrorMessage: String? = null,
    val alertActionMessage: String? = null,
    val deletingTvAlert: TvAlertDeletionKey? = null,
)

data class TvAlertDeletionKey(
    val cookieId: String,
    val alertId: Long,
)

class MainViewModel(
    private val repository: XbotRepository,
    private val serverConfigStore: ServerConfigStore,
    private val sessionStore: SessionStore,
    private val alertVisibilityStore: AlertVisibilityStore,
    private val onSignalsChanged: () -> Unit,
) : ViewModel() {
    private val initialAuthenticated = !sessionStore.getAccessToken().isNullOrBlank()
    private val _uiState = MutableStateFlow(
        MainUiState(
            serverUrl = serverConfigStore.getBaseUrl(),
            isAuthenticated = initialAuthenticated,
            isLoading = initialAuthenticated,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        if (initialAuthenticated) refresh()
    }

    fun login(serverUrl: String, username: String, password: String) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(serverUrl = serverUrl.trim(), isLoading = true, errorMessage = null)
            }
            try {
                repository.login(serverUrl, username, password)
                val signals = repository.getSignalViews()
                _uiState.value = MainUiState(
                    serverUrl = serverConfigStore.getBaseUrl(),
                    isAuthenticated = true,
                    signals = signals,
                )
                onSignalsChanged()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isAuthenticated = !sessionStore.getAccessToken().isNullOrBlank(),
                        isLoading = false,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isLoading && _uiState.value.signals.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val signals = repository.getSignalViews()
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        isLoading = false,
                        signals = signals,
                    )
                }
                onSignalsChanged()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is HttpException && error.code() == 401) {
                    sessionStore.clear()
                    _uiState.value = MainUiState(
                        serverUrl = serverConfigStore.getBaseUrl(),
                        isAuthenticated = false,
                        errorMessage = "登录已失效，请重新登录",
                    )
                    onSignalsChanged()
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
                }
            }
        }
    }

    fun loadAlerts(force: Boolean = false) {
        val current = _uiState.value
        if (current.isLoadingAlerts || (!force && current.hasLoadedAlerts)) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAlerts = true, alertErrorMessage = null) }
            try {
                val configs = repository.getTvAlertConfigs()
                val visibleIds = alertVisibilityStore.resolveVisibleIds(configs)
                val alerts = repository.getTvAlerts(
                    configs
                        .asSequence()
                        .filter { it.id in visibleIds }
                        .map { it.cookieId }
                        .toSet(),
                )
                _uiState.update {
                    it.copy(
                        alertConfigs = configs,
                        visibleAlertIds = visibleIds,
                        tvAlertsByCookieId = alerts,
                        hasLoadedAlerts = true,
                        isLoadingAlerts = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                handleAlertError(error)
            }
        }
    }

    fun saveVisibleAlertIds(ids: Set<String>) {
        val configs = _uiState.value.alertConfigs
        alertVisibilityStore.saveVisibleIds(ids, configs)
        _uiState.update {
            it.copy(
                visibleAlertIds = ids.intersect(configs.mapTo(linkedSetOf()) { config -> config.id }),
                hasLoadedAlerts = false,
                tvAlertsByCookieId = emptyMap(),
            )
        }
        loadAlerts()
    }

    fun openAlertSetup(config: TvAlertConfigDto) {
        _uiState.update {
            it.copy(
                quickAlertConfig = config,
                alertSetupErrorMessage = null,
                alertActionMessage = null,
            )
        }
    }

    fun dismissAlertSetup() {
        if (_uiState.value.isCreatingAlert) return
        _uiState.update {
            it.copy(
                quickAlertConfig = null,
                alertSetupErrorMessage = null,
            )
        }
    }

    fun createTvAlert(tickerInput: String, periods: List<String>) {
        val config = _uiState.value.quickAlertConfig ?: return
        if (_uiState.value.isCreatingAlert) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isCreatingAlert = true, alertSetupErrorMessage = null)
            }
            try {
                val ticker = normalizeTradingViewTicker(tickerInput)
                val result = repository.addTvAlerts(config.id, ticker, periods)
                if (result.result) {
                    _uiState.update {
                        it.copy(
                            quickAlertConfig = null,
                            isCreatingAlert = false,
                            alertSetupErrorMessage = null,
                            alertActionMessage = result.msg.ifBlank { "$ticker 警报设置成功" },
                        )
                    }
                    loadAlerts(force = true)
                } else {
                    _uiState.update {
                        it.copy(
                            isCreatingAlert = false,
                            alertSetupErrorMessage = result.msg.ifBlank { "警报设置失败" },
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is HttpException && error.code() == 401) {
                    handleAlertError(error)
                } else {
                    _uiState.update {
                        it.copy(
                            isCreatingAlert = false,
                            alertSetupErrorMessage = error.toUserMessage(),
                        )
                    }
                }
            }
        }
    }

    fun deleteTvAlert(config: TvAlertConfigDto, alert: TvAlertDto) {
        if (_uiState.value.deletingTvAlert != null) return
        val deletionKey = TvAlertDeletionKey(config.cookieId, alert.alertId)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    deletingTvAlert = deletionKey,
                    alertErrorMessage = null,
                    alertActionMessage = null,
                )
            }
            try {
                val result = repository.deleteTvAlert(config.cookieId, alert.alertId)
                if (result.result) {
                    _uiState.update { state ->
                        state.copy(
                            tvAlertsByCookieId = state.tvAlertsByCookieId + (
                                config.cookieId to state.tvAlertsByCookieId[config.cookieId]
                                    .orEmpty()
                                    .filterNot { it.alertId == alert.alertId }
                                ),
                            deletingTvAlert = null,
                            alertActionMessage = result.msg.ifBlank {
                                "已删除 ${alert.tickerLabel()} · ${alert.resolution}"
                            },
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            deletingTvAlert = null,
                            alertErrorMessage = result.msg.ifBlank { "删除警报失败" },
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is HttpException && error.code() == 401) {
                    handleAlertError(error)
                } else {
                    _uiState.update {
                        it.copy(
                            deletingTvAlert = null,
                            alertErrorMessage = error.toUserMessage(),
                        )
                    }
                }
            }
        }
    }

    fun openSignalSettings(signal: SignalViewDto) {
        _uiState.update {
            it.copy(
                editingSignal = signal,
                settingsErrorMessage = null,
            )
        }
    }

    fun dismissSignalSettings() {
        if (_uiState.value.isSavingSettings) return
        _uiState.update {
            it.copy(
                editingSignal = null,
                settingsErrorMessage = null,
            )
        }
    }

    fun saveSignalSettings(periods: List<String>, expireAt: String?) {
        val signal = _uiState.value.editingSignal ?: return
        if (_uiState.value.isSavingSettings) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingSettings = true,
                    settingsErrorMessage = null,
                )
            }
            try {
                val updated = repository.updateSignalSettings(signal.id, periods, expireAt)
                _uiState.update { state ->
                    state.copy(
                        signals = state.signals.map { if (it.id == updated.id) updated else it },
                        editingSignal = null,
                        isSavingSettings = false,
                        settingsErrorMessage = null,
                    )
                }
                onSignalsChanged()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is HttpException && error.code() == 401) {
                    sessionStore.clear()
                    _uiState.value = MainUiState(
                        serverUrl = serverConfigStore.getBaseUrl(),
                        isAuthenticated = false,
                        errorMessage = "登录已失效，请重新登录",
                    )
                    onSignalsChanged()
                } else {
                    _uiState.update {
                        it.copy(
                            isSavingSettings = false,
                            settingsErrorMessage = error.toUserMessage(),
                        )
                    }
                }
            }
        }
    }

    fun logout() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.logout()
            _uiState.value = MainUiState(
                serverUrl = serverConfigStore.getBaseUrl(),
                isAuthenticated = false,
            )
            onSignalsChanged()
        }
    }

    private fun handleAlertError(error: Exception) {
        if (error is HttpException && error.code() == 401) {
            sessionStore.clear()
            _uiState.value = MainUiState(
                serverUrl = serverConfigStore.getBaseUrl(),
                isAuthenticated = false,
                errorMessage = "登录已失效，请重新登录",
            )
            onSignalsChanged()
        } else {
            _uiState.update {
                it.copy(
                    isLoadingAlerts = false,
                    hasLoadedAlerts = true,
                    deletingTvAlert = null,
                    alertErrorMessage = error.toUserMessage(),
                )
            }
        }
    }
}

private fun Throwable.toUserMessage(): String = when (this) {
    is IllegalArgumentException -> message ?: "输入有误"
    is IllegalStateException -> message ?: "当前状态无效"
    is IOException -> "无法连接服务器，请检查地址和网络"
    is HttpException -> when (code()) {
        400 -> "用户名、密码或请求内容有误"
        401 -> "用户名或密码错误，或登录已失效"
        403 -> "用户名或密码错误，或当前账户没有访问权限"
        404 -> "服务器接口不存在，请检查服务器地址"
        else -> "服务器请求失败（HTTP ${code()}）"
    }
    else -> message ?: "请求失败"
}
