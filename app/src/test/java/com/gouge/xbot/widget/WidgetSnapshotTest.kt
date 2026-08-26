package com.gouge.xbot.widget

import com.gouge.xbot.data.SignalViewDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotTest {
    @Test
    fun `existing snapshot defaults to showing symbol`() {
        val snapshot = Json.decodeFromString<WidgetSnapshot>(
            """{
                "signalId":"1",
                "symbol":"BTCUSDT",
                "name":"Bitcoin",
                "periods":[],
                "longOn":true,
                "shortOn":false,
                "levelMin":"1",
                "levelMax":"5",
                "updatedAtMillis":1
            }""".trimIndent(),
        )

        assertTrue(snapshot.showSymbol)
        assertNull(snapshot.comment)
    }

    @Test
    fun `new snapshot stores hidden symbol preference`() {
        val signal = SignalViewDto(
            id = "1",
            symbol = "BTCUSDT",
            name = "Bitcoin",
            comment = "做多：突破均线 平多：反向信号",
        )

        val snapshot = WidgetSnapshot.from(signal, showSymbol = false)

        assertFalse(snapshot.showSymbol)
        assertEquals("做多：突破均线 平多：反向信号", snapshot.comment)
        assertEquals(signal.comment, snapshot.toSignalViewDto().comment)
    }
}
