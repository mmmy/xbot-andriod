package com.gouge.xbot.data

import com.gouge.xbot.domain.sortSignalPeriods
import com.gouge.xbot.domain.normalizeTradingViewTicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class XbotRepository(
    private val serverConfigStore: ServerConfigStore,
    private val sessionStore: SessionStore,
) {
    suspend fun login(baseUrl: String, username: String, password: String) {
        require(username.isNotBlank()) { "请输入用户名" }
        require(password.isNotBlank()) { "请输入密码" }

        val normalizedUrl = normalizeBaseUrl(baseUrl)
        sessionStore.clear()
        val api = ApiClientFactory.create(normalizedUrl) { null }
        val result = api.login(LoginRequest(username.trim(), password))
        require(result.accessToken.isNotBlank()) { "后端未返回 access_token" }
        serverConfigStore.saveBaseUrl(normalizedUrl)
        sessionStore.saveAccessToken(result.accessToken)
    }

    suspend fun getSignalViews(): List<SignalViewDto> {
        check(!sessionStore.getAccessToken().isNullOrBlank()) { "请先登录" }
        return authenticatedApi().getSignalViews().sortedBy { it.sort }
    }

    suspend fun getSignalView(signalId: String): SignalViewDto {
        check(!sessionStore.getAccessToken().isNullOrBlank()) { "请先登录" }
        return authenticatedApi().getSignalView(signalId)
    }

    suspend fun getTvAlertConfigs(): List<TvAlertConfigDto> {
        check(!sessionStore.getAccessToken().isNullOrBlank()) { "请先登录" }
        return authenticatedApi().getTvAlertConfigs().sortedWith(
            compareBy<TvAlertConfigDto> { it.sort ?: Double.MAX_VALUE }
                .thenBy { it.title },
        )
    }

    suspend fun getTvAlerts(cookieIds: Set<String>): Map<String, List<TvAlertDto>> = coroutineScope {
        check(!sessionStore.getAccessToken().isNullOrBlank()) { "请先登录" }
        val api = authenticatedApi()
        cookieIds
            .filter { it.isNotBlank() }
            .map { cookieId ->
                async {
                    cookieId to api.getTvAlerts(
                        TvAlertListRequest(cookieId = cookieId, namePre = "_"),
                    )
                }
            }
            .awaitAll()
            .toMap()
    }

    suspend fun addTvAlerts(
        config: TvAlertConfigDto,
        ticker: String,
        periods: List<String>,
        onSubmitted: () -> Unit = {},
    ): OperationResultDto {
        check(!sessionStore.getAccessToken().isNullOrBlank()) { "请先登录" }
        require(periods.isNotEmpty()) { "请至少选择一个级别" }
        val api = authenticatedApi()
        val previousIds = api.getTvAlerts(TvAlertListRequest(config.cookieId, "_")).mapTo(hashSetOf()) { it.alertId }
        val request = AddTvAlertRequest(
                alertId = config.id,
                symbols = normalizeTradingViewTicker(ticker),
                periods = sortSignalPeriods(periods).joinToString(" "),
            )
        return api.addTvAlertsAndWait(
            request,
            onSubmitted = onSubmitted,
            verifyCreated = { count -> api.awaitCreatedAlerts(config, request, previousIds, count) },
        )
    }

    suspend fun refreshTradingViewCache(cookieIds: Set<String>) {
        val api = authenticatedApi()
        cookieIds.filter(String::isNotBlank).forEach { cookieId ->
            val result = api.refreshTradingViewCache(RefreshAlertCacheRequest(cookieId))
            check(result.result) { result.msg.ifBlank { "刷新 TradingView 缓存失败" } }
        }
    }

    suspend fun deleteTvAlert(cookieId: String, alertId: Long): OperationResultDto {
        check(!sessionStore.getAccessToken().isNullOrBlank()) { "请先登录" }
        require(cookieId.isNotBlank()) { "警报账户无效" }
        return authenticatedApi().deleteTvAlertById(
            DeleteTvAlertByIdRequest(cookieId = cookieId, alertId = alertId),
        )
    }

    suspend fun resetTvAlert(
        config: TvAlertConfigDto,
        alert: TvAlertDto,
        knownAlertIds: Set<Long>,
        onSubmitted: () -> Unit,
    ): TvAlertResetResult {
        check(!sessionStore.getAccessToken().isNullOrBlank()) { "请先登录" }
        return TvAlertResetter(authenticatedApi()).reset(config, alert, knownAlertIds, onSubmitted)
    }

    suspend fun updateSignalSettings(
        signalId: String,
        periods: List<String>,
        expireAt: String?,
    ): SignalViewDto {
        check(!sessionStore.getAccessToken().isNullOrBlank()) { "请先登录" }
        return authenticatedApi().updateSignalSettings(
            id = signalId,
            request = UpdateSignalSettingsRequest(
                periods = sortSignalPeriods(periods),
                expireAt = expireAt,
            ),
        )
    }

    suspend fun logout() {
        try {
            if (!sessionStore.getAccessToken().isNullOrBlank()) {
                authenticatedApi().logout()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Local logout must still succeed when the server is unreachable.
        } finally {
            sessionStore.clear()
        }
    }

    private fun authenticatedApi(): XbotApiService = ApiClientFactory.create(
        baseUrl = serverConfigStore.getBaseUrl(),
        tokenProvider = sessionStore::getAccessToken,
    )

}
