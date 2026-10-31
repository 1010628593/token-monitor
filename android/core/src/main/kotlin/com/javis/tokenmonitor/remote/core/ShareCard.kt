package com.javis.tokenmonitor.remote.core

sealed interface ShareCard {
    data class Tokens(
        val tokens: Long,
        val costUsd: Double,
        val change: Float? = null,
        val bars: List<ShareBar> = emptyList()
    ) : ShareCard
    data class Cost(
        val costUsd: Double,
        val tokens: Long,
        val change: Float? = null,
        val series: List<Double> = emptyList(),
        val start: String = "",
        val end: String = ""
    ) : ShareCard
    data class Models(val rows: List<ShareRank>) : ShareCard
    data class Tools(val rows: List<ShareRank>) : ShareCard
    data class Trend(val bars: List<ShareBar>, val caption: String) : ShareCard
}

data class ShareRank(val id: String, val tokens: Long, val fraction: Float)

data class ShareBar(val tokens: Long, val label: String)

fun shareCards(snapshot: StatsSnapshot, period: PeriodId): List<ShareCard> {
    val summary = snapshot.period(period)
    val cards = mutableListOf<ShareCard>()
    if (summary.totalTokens > 0 || (summary.costUsd.isFinite() && summary.costUsd != 0.0)) {
        val yesterday = if (period == PeriodId.Today) snapshot.daily.dropLast(1).lastOrNull() else null
        val history = historyBars(snapshot, period)
        cards += ShareCard.Tokens(
            summary.totalTokens,
            summary.costUsd,
            changeRatio(summary.totalTokens.toDouble(), yesterday?.tokens?.toDouble()),
            history
        )
        cards += ShareCard.Cost(
            summary.costUsd,
            summary.totalTokens,
            changeRatio(summary.costUsd, yesterday?.costUsd),
            costSeries(snapshot, period),
            history.firstOrNull()?.label.orEmpty(),
            history.lastOrNull()?.label.orEmpty()
        )
    }
    ranks(summary.models, summary.totalTokens)?.let { cards += ShareCard.Models(it) }
    ranks(summary.clients, summary.totalTokens)?.let { cards += ShareCard.Tools(it) }
    trend(snapshot, period)?.let { cards += it }
    return cards
}

private fun ranks(rows: List<ClientUsage>, total: Long): List<ShareRank>? {
    if (total <= 0L) return null
    val visible = rows.filter { it.tokens > 0 }.sortedByDescending { it.tokens }.take(3)
    if (visible.isEmpty()) return null
    return visible.map { row ->
        ShareRank(row.id, row.tokens, (row.tokens.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f))
    }
}

private fun historyBars(snapshot: StatsSnapshot, period: PeriodId): List<ShareBar> = when (period) {
    PeriodId.AllTime -> snapshot.monthly.takeLast(8).map { ShareBar(it.tokens, shareDateLabel(it.month)) }
    PeriodId.Today, PeriodId.Month -> snapshot.daily.takeLast(14).map {
        ShareBar(it.tokens ?: 0L, shareDateLabel(it.date))
    }
}

private fun costSeries(snapshot: StatsSnapshot, period: PeriodId): List<Double> = when (period) {
    PeriodId.AllTime -> snapshot.monthly.takeLast(8).map { it.costUsd }
    PeriodId.Today, PeriodId.Month -> snapshot.daily.takeLast(14).map { it.costUsd ?: 0.0 }
}

private fun changeRatio(current: Double, previous: Double?): Float? {
    if (previous == null || !previous.isFinite() || previous <= 0.0 || !current.isFinite()) return null
    return ((current - previous) / previous).toFloat()
}

private fun trend(snapshot: StatsSnapshot, period: PeriodId): ShareCard.Trend? {
    val bars = historyBars(snapshot, period)
    if (bars.isEmpty() || bars.all { it.tokens <= 0L }) return null
    val caption = shareTrendCaption(bars.first().label, bars.last().label, bars.sumOf { it.tokens })
    return ShareCard.Trend(bars, caption)
}

internal fun shareDateLabel(date: String): String {
    val parts = date.split("-")
    val month = parts.getOrNull(1)?.toIntOrNull()
    return when (parts.size) {
        3 -> {
            val day = parts.getOrNull(2)?.toIntOrNull() ?: return date
            if (month == null) date else "$month/$day"
        }
        2 -> if (month == null) date else "${parts[0]}/$month"
        else -> date
    }
}

internal fun shareTrendCaption(start: String, end: String, tokens: Long): String {
    val range = if (start == end) start else "$start – $end"
    return "$range · ${Format.compactTokens(tokens)}"
}
