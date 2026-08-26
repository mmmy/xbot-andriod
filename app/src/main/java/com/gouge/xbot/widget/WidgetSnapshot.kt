package com.gouge.xbot.widget

import com.gouge.xbot.data.SignalViewDto
import kotlinx.serialization.Serializable

@Serializable
data class WidgetSnapshot(
    val signalId: String,
    val symbol: String,
    val name: String,
    val comment: String? = null,
    val periods: List<String>,
    val longOn: Boolean,
    val shortOn: Boolean,
    val expireAt: String? = null,
    val levelMin: String,
    val levelMax: String,
    val updatedAtMillis: Long,
    val showSymbol: Boolean = true,
) {
    fun toSignalViewDto(): SignalViewDto = SignalViewDto(
        id = signalId,
        name = name,
        comment = comment,
        symbol = symbol,
        periods = periods,
        longOn = longOn,
        shortOn = shortOn,
        expireAt = expireAt,
        levelMin = levelMin,
        levelMax = levelMax,
    )

    companion object {
        fun from(
            signal: SignalViewDto,
            updatedAtMillis: Long = System.currentTimeMillis(),
            showSymbol: Boolean = true,
        ) =
            WidgetSnapshot(
                signalId = signal.id,
                symbol = signal.symbol,
                name = signal.name,
                comment = signal.comment,
                periods = signal.periods,
                longOn = signal.longOn,
                shortOn = signal.shortOn,
                expireAt = signal.expireAt,
                levelMin = signal.levelMin,
                levelMax = signal.levelMax,
                updatedAtMillis = updatedAtMillis,
                showSymbol = showSymbol,
            )
    }
}
