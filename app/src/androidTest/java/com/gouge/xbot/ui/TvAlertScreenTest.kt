package com.gouge.xbot.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.gouge.xbot.data.TvAlertConfigDto
import com.gouge.xbot.data.TvAlertDto
import com.gouge.xbot.data.TvAlertParamDto
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvAlertScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val config = TvAlertConfigDto(
        id = "config-1",
        title = "业务过期测试",
        cookieId = "cookie-1",
        namePre = "_TEST_",
        startTimeParamIndex = 0,
        validBarsParamIndex = 7,
        params = listOf(
            TvAlertParamDto(index = "7", value = "1"),
            TvAlertParamDto(index = "0", value = "0"),
        ),
    )

    @Test
    fun expiryUpdatesAutomaticallyAndDoesNotChangeEnabledState() {
        var refreshCount = 0
        showScreen(
            alerts = listOf(alert(1, Instant.now().plusSeconds(8))),
            onRefresh = { refreshCount++ },
        )
        compose.onNodeWithText("业务有效 ·", substring = true).assertIsDisplayed()

        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("业务已过期 ·", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("业务已过期 1 条").assertIsDisplayed()
        compose.onNodeWithText("启用").assertIsDisplayed()
        compose.onNodeWithContentDescription("业务过期：", substring = true).assertIsDisplayed()
        assertEquals(0, refreshCount)
    }

    @Test
    fun summaryIncludesExpiredAlertHiddenByCollapse() {
        val now = Instant.now()
        showScreen(alerts = listOf(
            alert(1, now.plusSeconds(3_600)),
            alert(2, now.minusSeconds(900)).copy(active = false),
            alert(3, now.plusSeconds(3_600)).copy(createTime = ""),
            alert(4, now.minusSeconds(900)),
        ))

        compose.onNodeWithText("业务已过期 2 条").assertIsDisplayed()
        compose.onAllNodesWithText("业务已过期 ·", substring = true).assertCountEquals(1)
        compose.onNodeWithText("展开其余 1 条").performClick()
        compose.onAllNodesWithText("业务已过期 ·", substring = true).assertCountEquals(2)
        saveScreenshot("business-expiry-light")
    }

    @Test
    fun stoppedUnconfiguredAlertHasNoBusinessCountdownInDarkTheme() {
        showScreen(
            alerts = listOf(alert(1, Instant.now().minusSeconds(900)).copy(active = false)),
            alertConfig = config.copy(startTimeParamIndex = null),
            darkTheme = true,
        )

        compose.onNodeWithText("停用").assertIsDisplayed()
        compose.onNodeWithText("业务过期：未配置").assertIsDisplayed()
        compose.onAllNodesWithText("业务已过期", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("剩余", substring = true).assertCountEquals(0)
        saveScreenshot("business-expiry-unconfigured-dark")
    }

    @Test
    fun expiredAlertIsReadableInDarkTheme() {
        showScreen(
            alerts = listOf(alert(1, Instant.now().minusSeconds(900))),
            darkTheme = true,
        )
        compose.onNodeWithText("业务已过期 ·", substring = true).assertIsDisplayed()
        compose.onNodeWithText("业务已过期 1 条").assertIsDisplayed()
        saveScreenshot("business-expiry-expired-dark")
    }

    private fun saveScreenshot(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun showScreen(
        alerts: List<TvAlertDto>,
        alertConfig: TvAlertConfigDto = config,
        darkTheme: Boolean = false,
        onRefresh: () -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else MaterialTheme.colorScheme) {
                Surface {
                    TvAlertScreen(
                        state = MainUiState(
                            serverUrl = "test.local",
                            isAuthenticated = true,
                            hasLoadedAlerts = true,
                            alertConfigs = listOf(alertConfig),
                            visibleAlertIds = setOf(alertConfig.id),
                            tvAlertsByCookieId = mapOf(alertConfig.cookieId to alerts),
                        ),
                        onRefresh = onRefresh,
                        onLogout = {},
                        onChooseVisible = {},
                        onAddAlert = {},
                        onDeleteAlert = { _, _ -> },
                    )
                }
            }
        }
    }

    private fun alert(id: Long, expiresAt: Instant) = TvAlertDto(
        alertId = id,
        active = true,
        name = "_TEST_$id",
        symbol = "={\"symbol\":\"BINANCE:BTCUSDT.P\"}",
        resolution = "1",
        createTime = expiresAt.minusSeconds(60).toString(),
    )
}
