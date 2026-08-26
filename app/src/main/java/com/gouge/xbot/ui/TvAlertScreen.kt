package com.gouge.xbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gouge.xbot.data.TvAlertConfigDto
import com.gouge.xbot.data.TvAlertDto
import com.gouge.xbot.domain.matches
import com.gouge.xbot.domain.tickerLabel

@Composable
fun TvAlertScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onChooseVisible: () -> Unit,
    onAddAlert: (TvAlertConfigDto) -> Unit,
    onDeleteAlert: (TvAlertConfigDto, TvAlertDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleConfigs = state.alertConfigs.filter { it.id in state.visibleAlertIds }
    var pendingDeletion by remember { mutableStateOf<AlertDeletionTarget?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("警报管理", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${state.visibleAlertIds.size}/${state.alertConfigs.size} · ${state.serverUrl}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onChooseVisible,
                enabled = state.hasLoadedAlerts &&
                    !state.isLoadingAlerts &&
                    state.deletingTvAlert == null,
            ) {
                Text("显示")
            }
            TextButton(
                onClick = onRefresh,
                enabled = !state.isLoadingAlerts && state.deletingTvAlert == null,
            ) {
                Text("刷新")
            }
            TextButton(
                onClick = onLogout,
                enabled = !state.isLoadingAlerts && state.deletingTvAlert == null,
            ) {
                Text("退出")
            }
        }
        HorizontalDivider()
        state.alertErrorMessage?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.alertActionMessage?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        when {
            state.isLoadingAlerts && !state.hasLoadedAlerts -> LoadingAlerts()
            state.alertConfigs.isEmpty() -> EmptyAlerts("暂无警报配置")
            visibleConfigs.isEmpty() -> EmptyAlerts("未选择要显示的警报，请点击右上角“显示”进行选择")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(visibleConfigs, key = { it.id }) { config ->
                    val alerts = state.tvAlertsByCookieId[config.cookieId]
                        .orEmpty()
                        .filter(config::matches)
                    TvAlertConfigCard(
                        config = config,
                        alerts = alerts,
                        onAddAlert = { onAddAlert(config) },
                        deletingAlert = state.deletingTvAlert,
                        onDeleteAlert = { alert ->
                            pendingDeletion = AlertDeletionTarget(config, alert)
                        },
                    )
                }
                if (state.isLoadingAlerts) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    pendingDeletion?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("删除警报？") },
            text = {
                Text(
                    "将删除 ${target.alert.tickerLabel()} · " +
                        "${target.alert.resolution.ifBlank { "-" }}。此操作无法撤销。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAlert(target.config, target.alert)
                        pendingDeletion = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text("取消")
                }
            },
        )
    }
}

private data class AlertDeletionTarget(
    val config: TvAlertConfigDto,
    val alert: TvAlertDto,
)

@Composable
private fun LoadingAlerts() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(8.dp))
        Text("正在加载警报")
    }
}

@Composable
private fun EmptyAlerts(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TvAlertConfigCard(
    config: TvAlertConfigDto,
    alerts: List<TvAlertDto>,
    onAddAlert: () -> Unit,
    deletingAlert: TvAlertDeletionKey?,
    onDeleteAlert: (TvAlertDto) -> Unit,
) {
    val activeCount = alerts.count { it.active }
    val inactiveCount = alerts.size - activeCount
    var expanded by rememberSaveable(config.id) { mutableStateOf(false) }
    val visibleAlerts = if (expanded) alerts else alerts.take(CollapsedAlertCount)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = config.title.ifBlank { "未命名警报" },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                alerts.isEmpty() -> "暂无警报"
                                inactiveCount > 0 -> "正常 $activeCount · 停用 $inactiveCount"
                                else -> "正常 $activeCount"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (inactiveCount > 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            maxLines = 1,
                        )
                    }
                    val detail = listOf(config.periods, config.tickerIds)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(
                    onClick = onAddAlert,
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text("添加")
                }
            }
            if (alerts.isNotEmpty()) {
                HorizontalDivider()
                visibleAlerts.forEachIndexed { index, alert ->
                    TvAlertRow(
                        alert = alert,
                        isDeleting = deletingAlert?.let {
                            it.cookieId == config.cookieId && it.alertId == alert.alertId
                        } == true,
                        deleteEnabled = deletingAlert == null,
                        onDelete = { onDeleteAlert(alert) },
                    )
                    if (index < visibleAlerts.lastIndex || alerts.size > CollapsedAlertCount) {
                        HorizontalDivider()
                    }
                }
                if (alerts.size > CollapsedAlertCount) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (expanded) {
                                "收起"
                            } else {
                                "展开其余 ${alerts.size - CollapsedAlertCount} 条"
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val CollapsedAlertCount = 3

@Composable
private fun TvAlertRow(
    alert: TvAlertDto,
    isDeleting: Boolean,
    deleteEnabled: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = alert.tickerLabel(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = alert.resolution.ifBlank { "-" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = if (alert.active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            contentColor = if (alert.active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                text = if (alert.active) "正常" else "停用",
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TextButton(
            onClick = onDelete,
            enabled = deleteEnabled,
        ) {
            Text(
                text = if (isDeleting) "删除中" else "删除",
                color = if (deleteEnabled || isDeleting) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}
