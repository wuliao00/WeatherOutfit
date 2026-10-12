package com.jianyi.outfit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                color = MaterialTheme.colorScheme.onSurface,
                // 这一串是整张卡的标题，也是卡里最不该被省略的东西。原来这行还有「干支日」
                // 和 Spacer(weight(1f)) 两个兄弟：三个带权重的兄弟一平分，日期只拿到 66dp，
                // 真机（font_scale 1.15）上整串被省略成「农历…」。控件树里文本仍完整，
                // 所以 dump 看不出问题，只有截图能看见——本仓库在 HomeScreen 那边记过同类事故。
                // 现在日期独占剩余宽度（weight(1f) 不再被 Spacer 抢），干支日移到元信息行。
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            SourceBadge(text = AlmanacDay.SOURCE_LABEL)
        }

        // 节气与节日可为空，为空时整个词条不出现——不做「无节气」式填充：
        // 填充会把「没有」也包装成一条信息。
        // 吉神方位与日支无关（由日柱干支推出），跟生肖/月相并列最自然。
        val meta = listOfNotNull(
            // 干支日在标题行放不下（标题得让位给农历日期那串长的），挪到这里并补一个「日」字，
            // 免得和后面的生肖「属马」连读成「己未属马」
            day.ganzhiDay.takeIf { it.isNotBlank() }?.let { "${it}日" },
            day.zodiac.takeIf { it.isNotBlank() }?.let { "属$it" },
            day.jieqi,
            day.festival,
            day.moonPhase.takeIf { it.isNotBlank() },
            day.jupiterDirection.takeIf { it.isNotBlank() }?.let { "吉神方位 $it" }
        )
        if (meta.isNotEmpty()) {
            MetaLine(meta)
        }

        // 择日三项：值日（建除十二神）、值神（黄道黑道十二神）、二十八宿。
        // 是同一天里互相独立的三套传统标注，主流黄历卡面就是这么并排写的；
        // 「朱雀」不带「(黑道)」就只是个人名，所以两者必须同格出现。
        val dayFacts = listOfNotNull(
            day.duty.takeIf { it.isNotBlank() }?.let { "值日 $it" },
            day.twelveStar.takeIf { it.isNotBlank() }?.let {
                if (day.ecliptic.isNotBlank()) "值神 $it(${day.ecliptic})" else "值神 $it"
            },
            day.twentyEightStar.takeIf { it.isNotBlank() }?.let {
                if (day.twentyEightStarLuck.isNotBlank()) "${it}宿(${day.twentyEightStarLuck})" else "${it}宿"
            }
        )
        if (dayFacts.isNotEmpty()) {
            MetaLine(dayFacts)
        }

        // 长辈翻黄历最先找的两样：今天冲谁、胎神在哪。冲煞的括号内干支是日柱退六位，
        // 格式与便民查询网逐字一致（见 AlmanacEngine.chongShaOf 的实测记录）
        val directions = listOfNotNull(
            day.chongSha.takeIf { it.isNotBlank() },
            day.fetusDay.takeIf { it.isNotBlank() }?.let { "胎神 $it" }
        )
        if (directions.isNotEmpty()) {
            MetaLine(directions)
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

/** 元信息行：同色同字号，只用「·」分隔——这些是同一层级的推算结论，不该有视觉层级差 */
@Composable
private fun MetaLine(items: List<String>) {
    Text(
        text = items.joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
