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
import com.jianyi.outfit.data.model.Horoscope
import com.jianyi.outfit.ui.components.SourceBadge

/**
 * 星座卡。
 *
 * 四个分项各自独立成行、内容为空即整行不出现：文案池按日轮换，
 * 个别分项缺席时留白比写「暂无」更诚实。
 */
@Composable
fun HoroscopeCard(h: Horoscope, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // tyme4kt 给的是两字短名（天秤），标题缺「座」读起来像没写完，故在展示层补上。
            // 后缀是书写习惯而非数据内容，不进模型：模型与库保持逐字一致，避免同义两份数据
            Text(
                text = "${h.constellation}座",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            SourceBadge(text = Horoscope.SOURCE_LABEL)
        }

        HoroscopeItem(prefix = "整体", text = h.overall)
        HoroscopeItem(prefix = "爱情", text = h.love)
        HoroscopeItem(prefix = "事业", text = h.career)
        HoroscopeItem(prefix = "财运", text = h.wealth)

        // 幸运色与幸运数合成末尾一行：它们是给眼睛的小注脚，
        // 摊成和四个分项一样的行会把「运势」与「开运提示」混成同一层级
        val lucky = listOfNotNull(
            h.luckyColor.takeIf { it.isNotBlank() }?.let { "幸运色 $it" },
            "幸运数 ${h.luckyNumber}"
        )
        Text(
            text = lucky.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 分项行：前缀弱化、内容用正文色；内容为空则整行不渲染，不留占位 */
@Composable
private fun HoroscopeItem(prefix: String, text: String) {
    if (text.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
