package com.javis.tokenmonitor.remote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCardTest {
    @Test
    fun emptyUsageProducesNoCards() {
        assertTrue(shareCards(StatsSnapshot(), PeriodId.Today).isEmpty())
    }

    @Test
    fun zeroTokenRowsAreDroppedAndMissingKindsStayHidden() {
        val snapshot = StatsSnapshot(
            today = PeriodSummary(
                totalTokens = 100,
                costUsd = 1.5,
                clients = listOf(ClientUsage("cursor", 0, 0.0), ClientUsage("codex", 80, 1.0)),
                models = emptyList()
            )
        )
        val cards = shareCards(snapshot, PeriodId.Today)
        assertEquals(listOf(ShareCard.Tokens::class, ShareCard.Cost::class, ShareCard.Tools::class), cards.map { it::class })
        val tools = cards.filterIsInstance<ShareCard.Tools>().single()
        assertEquals(listOf("codex"), tools.rows.map { it.id })
        assertEquals(0.8f, tools.rows.single().fraction, 0.001f)
    }

    @Test
    fun ranksKeepTheThreeLargest() {
        val models = (1..5).map { ClientUsage("m$it", it * 10L, 0.0) }
        val snapshot = StatsSnapshot(today = PeriodSummary(totalTokens = 150, models = models))
        val rows = shareCards(snapshot, PeriodId.Today).filterIsInstance<ShareCard.Models>().single().rows
        assertEquals(listOf("m5", "m4", "m3"), rows.map { it.id })
    }

    @Test
    fun dailyTrendKeepsTheLatestFourteenDays() {
        val daily = (1..20).map { DayUsage("2026-10-${it.toString().padStart(2, '0')}", it * 1_000L, 0.0) }
        val snapshot = StatsSnapshot(
            today = PeriodSummary(totalTokens = 1),
            daily = daily
        )
        val trend = shareCards(snapshot, PeriodId.Today).filterIsInstance<ShareCard.Trend>().single()
        assertEquals(14, trend.bars.size)
        assertEquals("10/7", trend.bars.first().label)
        assertEquals("10/20", trend.bars.last().label)
        assertEquals("10/7 – 10/20 · 189K", trend.caption)
    }

    @Test
    fun allTimeTrendUsesTheLatestEightMonths() {
        val months = (1..10).map { MonthUsage("2026-${it.toString().padStart(2, '0')}", it * 1_000_000L, 0.0) }
        val snapshot = StatsSnapshot(
            allTime = PeriodSummary(totalTokens = 1),
            daily = listOf(DayUsage("2026-10-01", 9, 0.0)),
            monthly = months
        )
        val trend = shareCards(snapshot, PeriodId.AllTime).filterIsInstance<ShareCard.Trend>().single()
        assertEquals(8, trend.bars.size)
        assertEquals("2026/3", trend.bars.first().label)
        assertEquals("2026/10", trend.bars.last().label)
    }

    @Test
    fun todayComparesWithYesterdayAndOtherPeriodsDoNot() {
        val snapshot = StatsSnapshot(
            today = PeriodSummary(totalTokens = 150, costUsd = 3.0),
            month = PeriodSummary(totalTokens = 150, costUsd = 3.0),
            daily = listOf(DayUsage("2026-10-30", 100, 2.0), DayUsage("2026-10-31", 150, 3.0))
        )
        val today = shareCards(snapshot, PeriodId.Today)
        assertEquals(0.5f, today.filterIsInstance<ShareCard.Tokens>().single().change!!, 0.001f)
        assertEquals(0.5f, today.filterIsInstance<ShareCard.Cost>().single().change!!, 0.001f)
        val month = shareCards(snapshot, PeriodId.Month)
        assertNull(month.filterIsInstance<ShareCard.Tokens>().single().change)
        assertNull(month.filterIsInstance<ShareCard.Cost>().single().change)
    }

    @Test
    fun aFlatZeroSeriesIsOmitted() {
        val snapshot = StatsSnapshot(
            month = PeriodSummary(totalTokens = 10, costUsd = 1.0),
            daily = listOf(DayUsage("2026-10-01", 0, 0.0), DayUsage("2026-10-02", null, null))
        )
        assertTrue(shareCards(snapshot, PeriodId.Month).none { it is ShareCard.Trend })
    }
}
