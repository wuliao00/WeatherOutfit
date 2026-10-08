package com.jianyi.outfit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jianyi.outfit.data.model.HistoricalEvent
import com.jianyi.outfit.ui.components.SourceBadge

/**
 * 「历史上的今天」卡。
 *
 * 列表为空时整卡不渲染：调用方在开关关闭或数据集缺当日时传空列表，
 * 那种情况下页面应该什么都没有，而不是一张写着「暂无数据」的空壳。
 */
@Composable
fun HistoryTodayCard(events: List<HistoricalEvent>, modifier: Modifier = Modifier) {
    if (events.isEmpty()) return   // 缺数据时整卡不出现，不留空壳（spec 第 7 节）
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "历史上的今天",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            SourceBadge(text = HistoricalEvent.SOURCE_LABEL)
        }
        events.forEach { e ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 公元前的年份在数据集里以负数存（约定见 HistoricalEvent.year 的 KDoc）：
                // 负号直接打印会被读成负数而非纪年，一律转「公元前 N」
                Text(
                    text = if (e.year > 0) "${e.year}" else "公元前${-e.year}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = e.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
