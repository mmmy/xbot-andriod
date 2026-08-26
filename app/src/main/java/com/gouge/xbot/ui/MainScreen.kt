package com.gouge.xbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gouge.xbot.data.SignalViewDto
import com.gouge.xbot.data.TvAlertSymbolStore
import com.gouge.xbot.domain.DirectionState
import com.gouge.xbot.domain.SignalCommentItem
import com.gouge.xbot.domain.SignalCommentType
import com.gouge.xbot.domain.directionState
import com.gouge.xbot.domain.formatExpiry
import com.gouge.xbot.domain.levelText
import com.gouge.xbot.domain.parseSignalComment
import com.gouge.xbot.widget.SignalIconMapping
import com.gouge.xbot.widget.SignalIconMappingStore
import com.gouge.xbot.widget.SignalWidgetRenderer

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val mappingStore = remember { SignalIconMappingStore(context) }
    val alertSymbolStore = remember { TvAlertSymbolStore(context) }
    var iconMappings by remember { mutableStateOf(mappingStore.getAll()) }
    var showIconMappings by remember { mutableStateOf(false) }
    var showAlertVisibility by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(MainPage.Alerts) }
    val updateIconMappings: (List<SignalIconMapping>) -> Unit = { mappings ->
        mappingStore.save(mappings)
        iconMappings = mappings
        SignalWidgetRenderer.renderAll(context)
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        if (state.isAuthenticated) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    MainPage.Signals -> SignalListScreen(
                        state = state,
                        onRefresh = viewModel::refresh,
                        onLogout = viewModel::logout,
                        onEditSignal = viewModel::openSignalSettings,
                        onManageIcons = { showIconMappings = true },
                        modifier = Modifier.weight(1f),
                    )
                    MainPage.Alerts -> TvAlertScreen(
                        state = state,
                        onRefresh = { viewModel.loadAlerts(force = true) },
                        onLogout = viewModel::logout,
                        onChooseVisible = { showAlertVisibility = true },
                        onAddAlert = viewModel::openAlertSetup,
                        onDeleteAlert = viewModel::deleteTvAlert,
                        modifier = Modifier.weight(1f),
                    )
                }
                MainPageNavigation(selected = page, onSelected = { page = it })
            }
        } else {
            LoginScreen(
                state = state,
                onLogin = viewModel::login,
            )
        }
    }
    LaunchedEffect(state.isAuthenticated, page) {
        if (!state.isAuthenticated) {
            page = MainPage.Alerts
        } else if (page == MainPage.Alerts) {
            viewModel.loadAlerts()
        }
    }
    state.editingSignal?.let { signal ->
        SignalSettingsSheet(
            signal = signal,
            isSaving = state.isSavingSettings,
            errorMessage = state.settingsErrorMessage,
            onDismiss = viewModel::dismissSignalSettings,
            onSave = viewModel::saveSignalSettings,
        )
    }
    if (showIconMappings) {
        SignalIconMappingSheet(
            mappings = iconMappings,
            onMappingsChange = updateIconMappings,
            onDismiss = { showIconMappings = false },
        )
    }
    if (showAlertVisibility) {
        AlertVisibilitySheet(
            configs = state.alertConfigs,
            selectedIds = state.visibleAlertIds,
            onSave = viewModel::saveVisibleAlertIds,
            onDismiss = { showAlertVisibility = false },
        )
    }
    state.quickAlertConfig?.let { config ->
        TvAlertSetupSheet(
            config = config,
            initialTicker = alertSymbolStore.getLastTicker(),
            isSaving = state.isCreatingAlert,
            errorMessage = state.alertSetupErrorMessage,
            onSave = { ticker, periods ->
                alertSymbolStore.saveLastTicker(ticker)
                viewModel.createTvAlert(ticker, periods)
            },
            onDismiss = viewModel::dismissAlertSetup,
        )
    }
}

private enum class MainPage(val label: String) {
    Alerts("警报"),
    Signals("信号"),
}

@Composable
private fun MainPageNavigation(
    selected: MainPage,
    onSelected: (MainPage) -> Unit,
) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MainPage.entries.forEach { page ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelected(page) },
                color = if (page == selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (page == selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = page.label,
                    modifier = Modifier.padding(vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    state: MainUiState,
    onLogin: (String, String, String) -> Unit,
) {
    var serverUrl by remember { mutableStateOf(state.serverUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.serverUrl) {
        serverUrl = state.serverUrl
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("XBot Signal", style = MaterialTheme.typography.headlineMedium)
        Text(
            "登录后可查看信号设置并配置桌面小组件",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("账户/信号服务地址") },
            placeholder = { Text("http://192.168.1.100:3002/") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("用户名") },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onLogin(serverUrl, username, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("登录")
            }
        }
    }
}

@Composable
private fun SignalListScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onEditSignal: (SignalViewDto) -> Unit,
    onManageIcons: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("信号设置", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.serverUrl,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onManageIcons) {
                Text("图标")
            }
            TextButton(onClick = onRefresh, enabled = !state.isLoading) {
                Text("刷新")
            }
            TextButton(onClick = onLogout, enabled = !state.isLoading) {
                Text("退出")
            }
        }
        HorizontalDivider()
        state.errorMessage?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.isLoading && state.signals.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.signals.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("暂无信号设置")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.signals, key = { it.id }) { signal ->
                    SignalCard(signal, onEdit = { onEditSignal(signal) })
                }
            }
        }
    }
}

@Composable
private fun SignalCard(
    signal: SignalViewDto,
    onEdit: () -> Unit,
) {
    val direction = directionState(signal.longOn, signal.shortOn)
    val expiry = formatExpiry(signal.expireAt)
    val comments = parseSignalComment(signal.comment)
    val directionColor = when (direction) {
        DirectionState.LongOnly -> MaterialTheme.colorScheme.primary
        DirectionState.ShortOnly -> MaterialTheme.colorScheme.error
        DirectionState.Both -> MaterialTheme.colorScheme.tertiary
        DirectionState.Disabled -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = signal.symbol.ifBlank { "-" },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (signal.name.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = signal.name,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = direction.label,
                    color = directionColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onEdit) {
                    Text("设置")
                }
            }
            if (comments.isNotEmpty()) {
                CompactSignalComments(comments)
                Spacer(Modifier.height(2.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "级别  ${signal.levelText()}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = expiry.text,
                    color = when {
                        expiry.isExpired -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        expiry.isExpiringSoon -> colorResource(com.gouge.xbot.R.color.signal_orange)
                        else -> colorResource(com.gouge.xbot.R.color.signal_cyan)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompactSignalComments(comments: List<SignalCommentItem>) {
    val plainComment = comments.singleOrNull { it.type == null }
    if (plainComment != null) {
        SignalCommentRow(plainComment, maxLines = 2)
        return
    }

    comments.chunked(2).take(2).forEachIndexed { rowIndex, rowComments ->
        if (rowIndex > 0) Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowComments.forEach { item ->
                SignalCommentRow(
                    item = item,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
            if (rowComments.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun SignalCommentRow(
    item: SignalCommentItem,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    val type = item.type
    if (type == null) {
        Text(
            text = item.text,
            modifier = modifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val (containerColor, contentColor) = when (type) {
        SignalCommentType.OpenLong -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        SignalCommentType.OpenShort -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
        SignalCommentType.CloseLong -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
        SignalCommentType.CloseShort -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                text = type.label,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (item.text.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = item.text,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
