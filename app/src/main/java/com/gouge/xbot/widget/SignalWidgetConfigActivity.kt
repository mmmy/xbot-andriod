package com.gouge.xbot.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gouge.xbot.MainActivity
import com.gouge.xbot.data.ServerConfigStore
import com.gouge.xbot.data.SessionStore
import com.gouge.xbot.data.SignalViewDto
import com.gouge.xbot.data.XbotRepository
import com.gouge.xbot.domain.directionState
import com.gouge.xbot.domain.formatExpiry
import com.gouge.xbot.domain.levelText
import com.gouge.xbot.domain.parseSignalComment
import com.gouge.xbot.ui.SignalCommentRow
import com.gouge.xbot.ui.theme.XbotTheme
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class SignalWidgetConfigActivity : ComponentActivity() {
    private val resumeGeneration = mutableIntStateOf(0)
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val sessionStore = SessionStore(applicationContext)
        val repository = XbotRepository(ServerConfigStore(applicationContext), sessionStore)
        val widgetPreferences = WidgetPreferences(applicationContext)
        val configuredSnapshot = widgetPreferences.get(appWidgetId)

        setContent {
            XbotTheme {
                SignalWidgetConfigScreen(
                    resumeGeneration = resumeGeneration.intValue,
                    repository = repository,
                    sessionStore = sessionStore,
                    initiallySelectedId = configuredSnapshot?.signalId,
                    initiallyShowSymbol = configuredSnapshot?.showSymbol ?: true,
                    isEditing = configuredSnapshot != null,
                    onOpenLogin = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                    onConfirm = { signal, showSymbol ->
                        val snapshot = WidgetSnapshot.from(signal, showSymbol = showSymbol)
                        widgetPreferences.save(appWidgetId, snapshot)
                        SignalWidgetRenderer.render(this, appWidgetId, snapshot)
                        SignalWidgetScheduler.schedulePeriodic(applicationContext)
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeGeneration.intValue += 1
    }
}

@Composable
private fun SignalWidgetConfigScreen(
    resumeGeneration: Int,
    repository: XbotRepository,
    sessionStore: SessionStore,
    initiallySelectedId: String?,
    initiallyShowSymbol: Boolean,
    isEditing: Boolean,
    onOpenLogin: () -> Unit,
    onConfirm: (SignalViewDto, Boolean) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var needsLogin by remember { mutableStateOf(false) }
    var signals by remember { mutableStateOf(emptyList<SignalViewDto>()) }
    var selectedId by remember { mutableStateOf(initiallySelectedId) }
    var showSymbol by remember { mutableStateOf(initiallyShowSymbol) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(resumeGeneration, reloadKey) {
        if (resumeGeneration == 0) return@LaunchedEffect
        if (sessionStore.getAccessToken().isNullOrBlank()) {
            needsLogin = true
            isLoading = false
            signals = emptyList()
            return@LaunchedEffect
        }
        needsLogin = false
        isLoading = true
        errorMessage = null
        try {
            val loadedSignals = repository.getSignalViews()
            signals = loadedSignals
            if (loadedSignals.none { it.id == selectedId }) {
                selectedId = loadedSignals.firstOrNull()?.id
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is HttpException && error.code() == 401) {
                sessionStore.clear()
                needsLogin = true
                errorMessage = "登录已失效"
            } else {
                errorMessage = "加载信号失败，请检查网络和服务器地址"
            }
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = if (isEditing) "编辑小组件" else "选择小组件信号",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSymbol = !showSymbol }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("显示品种名称", style = MaterialTheme.typography.titleMedium)
                Text(
                    "例如 BTCUSDT；关闭后小组件仅显示信号名称",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = showSymbol,
                onCheckedChange = { showSymbol = it },
            )
        }
        when {
            needsLogin -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(errorMessage ?: "请先登录 XBot Signal")
                Button(
                    onClick = onOpenLogin,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("打开应用登录")
                }
            }
            isLoading -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            errorMessage != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { reloadKey += 1 },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("重试")
                }
            }
            signals.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("暂无信号设置")
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(signals, key = { it.id }) { signal ->
                        ConfigSignalCard(
                            signal = signal,
                            selected = selectedId == signal.id,
                            onSelect = { selectedId = signal.id },
                        )
                    }
                }
                Button(
                    onClick = {
                        signals.firstOrNull { it.id == selectedId }?.let { signal ->
                            onConfirm(signal, showSymbol)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = selectedId != null,
                ) {
                    Text(if (isEditing) "保存设置" else "添加小组件")
                }
            }
        }
    }
}

@Composable
private fun ConfigSignalCard(
    signal: SignalViewDto,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val comments = parseSignalComment(signal.comment)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${signal.symbol}  ${signal.name}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (comments.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    comments.forEach { item ->
                        SignalCommentRow(item)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text("级别  ${signal.levelText()}")
                Text(
                    "${directionState(signal.longOn, signal.shortOn).label} · ${formatExpiry(signal.expireAt).text}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
