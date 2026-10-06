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
import com.jianyi.outfit.data.model.AlmanacDay
import com.jianyi.outfit.ui.components.SourceBadge

/**
 * 黄历卡。
 *
 * 宜/忌用「宜」「忌」两个单字起头而不是画两个色块：色块会把它打扮成状态标签，
 * 但这两行只是传统历法的推算结论，视觉上不该显得比天气数据更权威。
 */
@Composable
fun AlmanacCard(day: AlmanacDay, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = day.lunarDateText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = day.ganzhiDay,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            SourceBadge(text = AlmanacDay.SOURCE_LABEL)
        }

        // 节气与节日可为空，为空时整个词条不出现——不做「无节气」式填充：
        // 填充会把「没有」也包装成一条信息
        val meta = listOfNotNull(
            day.zodiac.takeIf { it.isNotBlank() }?.let { "属$it" },
            day.jieqi,
            day.festival,
            day.moonPhase.takeIf { it.isNotBlank() }
        )
        if (meta.isNotEmpty()) {
            Text(
                text = meta.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 宜/忌在引擎侧已截断到展示上限：这里只渲染拿到的内容，不再截一次，
        // 上限只保留一处，免得日后两处各改各的
        if (day.recommends.isNotEmpty()) {
            TabooRow(prefix = "宜", items = day.recommends)
        }
        if (day.avoids.isNotEmpty()) {
            TabooRow(prefix = "忌", items = day.avoids)
        }
    }
}

/** 宜/忌单行：前缀弱化、条目用正文色——条目是内容而不是标签 */
@Composable
private fun TabooRow(prefix: String, items: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = items.joinToString("  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
