#!/usr/bin/env python3
"""今日数据集结构校验（撰写之前先写好，这是内容质量的第一道闸门）。

为什么先写闸门：数据集是纯内容资产，没有可执行断言就会悄悄劣化。
这里每条规则都对应 spec 2.7/3.2 或第 7/8 节的一个具体失败模式：
- 缺日 / 每日条数不符 → 卡片在某些日子整体不显示（spec 7.1 的运行时防线不该被触发）
- 同一天年份重复 → 三条挤在同一年，卡片信息量变单薄
- 长度越界 / 不以句号结尾 / 「。」多于一个（不是单句） → 格式不齐，观感像从别处剪贴
- [n]/[1, 2]/［1］/[注] 等引用标记、尖括号、\\u 残留 → 搬运外部文本没搬干净的物证
  （spec 2.7 的许可前提是文本全部自撰）
- 未来年份 → 必是编造

失败（退出码 1）时逐条列出「哪个日期、哪条、违反什么」，起草者不必读脚本即可照单修。
"""
import collections
import json
import pathlib
import re
import sys

DS = pathlib.Path(__file__).parent.parent / \
    "shared/src/commonMain/composeResources/files/today_in_history.json"

FIELD_KEYS = {"m", "d", "y", "t", "s"}
PER_DAY = 3
LEN_MIN, LEN_MAX = 14, 26
# 2 月取 29：这里覆盖的是 366 个 MM-DD（含闰日），不是某个具体年份的日历，
# 用 calendar.mdays 会把合法的 2/29 判成非法日期，与覆盖断言自相矛盾
DAYS_IN_MONTH = [0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
TYPE_EVENT, TYPE_BIRTH, TYPE_DEATH = 1, 2, 3
# 数据集撰写于 2026 年，更晚的年份无从查证。故意写死而不取系统时间：
# 闸门跨时间可复现；若将来继续补写，改这一处即可。
LAST_WRITABLE_YEAR = 2026
# 源骨架止于 2020（spec 2.7），近三年全靠自补；数量不足只说明内容单薄，不说明数据坏了
RECENT_FROM, RECENT_MIN = 2023, 60
# 规范：每日至少 1 条事件。占比过低说明出生/逝世泛滥，但逐日配额是目标而非硬闸，故只警告
EVENT_RATIO_MIN = 0.4
# spec 3.2 估约 130KB；到 2 倍即说明句子里塞了水，但仍是内容判断，不拦截
SIZE_KB_WARN = 260
# 单次最多展开的错误条数：数据集崩成一片时，列满屏幕反而看不到重点
ERR_SHOW_MAX = 40
# 引用标记的形态不止半角 [n]（最初只拦这一种）：[1, 2]/[1-3]（多注、区间）、［1］（全角
# 括号）、[注]/[來源]（非数字标记）都是外部源排版残留的典型形态。这个检查守住「文本全部
# 自撰」的许可前提，漏一种形态就等于放走一类物证。全角 ［］ 在中文正文里没有别的用途，
# 一律拦；半角 [...] 只拦「数字+分隔/区间」与常见注引词，普通正文里的方括号补注不会误伤
CITE_MARKER = re.compile(
    r"\[\s*\d+(?:\s*[,，、;；\-–—~至]\s*\d+)*\s*\]"                      # [n] [1, 2] [1-3] [1、2] [1至3]
    r"|［[^］]*］"                                                         # ［1］ 等任何全角方括号
    r"|\[(?:注|註|來源|来源|出處|出处|需要来源|需要來源|请求来源|請求來源"
    r"|待考|待查|citation needed)[^\]]*\]"                                # [注] [注 1] [來源] [来源请求]
)


def head(s, n=20):
    """引用描述片段；换行会打断一行一条的可读性，先压平再截断。"""
    t = s.replace("\n", " ").replace("\r", " ")
    return t[:n] + ("…" if len(t) > n else "")


def tail(s, n=12):
    t = s.replace("\n", " ").replace("\r", " ")
    return ("…" if len(t) > n else "") + t[-n:]


def main():
    if not DS.exists():
        sys.exit(f"数据集不存在：{DS}")

    raw = DS.read_text(encoding="utf-8")
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        sys.exit(f"数据集不是合法 JSON：{exc}\n"
                 f"（第 {exc.lineno} 行第 {exc.colno} 列；常见原因是合片时漏了逗号或括号）")
    if not isinstance(data, list):
        sys.exit(f"数据集顶层应为数组，实为 {type(data).__name__}")

    errs, warns = [], []
    expected = {(m, d) for m in range(1, 13) for d in range(1, DAYS_IN_MONTH[m] + 1)}
    seen = collections.defaultdict(list)   # (m, d) -> 当日各条年份，按出现顺序

    for i, e in enumerate(data):
        if not isinstance(e, dict):
            errs.append(f"#{i} 不是 JSON 对象（{type(e).__name__}）")
            continue
        keys = set(e)
        if keys != FIELD_KEYS:
            # 该条不参与后续统计，注明这一点，免得同一天连带出现「条数不足」的次生错误
            errs.append(f"#{i} 字段应为 m/d/y/t/s，实到 {sorted(keys)}（该条未计入当日条数）")
            continue
        m, d, y, t, s = e["m"], e["d"], e["y"], e["t"], e["s"]
        # 类型不合规时先报类型并跳过：否则下面的比较/切片会抛异常，闸门自己先崩就拦不住任何东西
        bad_type = False
        for name, v in (("m", m), ("d", d), ("y", y), ("t", t)):
            if not isinstance(v, int) or isinstance(v, bool):
                errs.append(f"#{i} {name}={v!r} 应为整数")
                bad_type = True
        if bad_type:
            continue
        if not (1 <= m <= 12 and 1 <= d <= DAYS_IN_MONTH[m]):
            errs.append(f"#{i} 非法日期 {m}/{d}")
            continue
        day = f"{m:02d}-{d:02d}"
        seen[(m, d)].append(y)

        if t not in (TYPE_EVENT, TYPE_BIRTH, TYPE_DEATH):
            errs.append(f"{day} #{i} type={t} 非法（1 事件 / 2 出生 / 3 逝世）")
        if y > LAST_WRITABLE_YEAR:
            errs.append(f"{day} #{i} 未来年份 y={y}（撰写于 {LAST_WRITABLE_YEAR} 年，更晚的年份必是编造）")
        # 公元前的年份用负数，UI 按「公元前 |y|」渲染；0 会渲染成「公元前0」这种两种纪年法都不认的串，
        # 而且没有 0 年，出现即必是笔误
        if y == 0:
            errs.append(f"{day} #{i} y=0 非法（无公元 0 年；公元前请用负数）")
        if not isinstance(s, str) or not s.strip():
            errs.append(f"{day} #{i} 描述为空或非字符串")
            continue
        n = len(s)
        if not (LEN_MIN <= n <= LEN_MAX):
            errs.append(f"{day} #{i} 长度 {n} 不在 {LEN_MIN}~{LEN_MAX}：{head(s)}")
        if not s.endswith("。"):
            errs.append(f"{day} #{i} 未以句号结尾：{tail(s)}")
        # 「每条一句」是硬约束：以「。」计数近似句数，必须恰好 1 个。引文里嵌的句号会被
        # 一并算中，这是有意接受的取舍——逐例豁免的成本高于对 1098 条的偶发误报
        stops = s.count("。")
        if stops != 1:
            errs.append(f"{day} #{i} 句号 {stops} 个，应恰好 1 个（每条一句）：{head(s)}")
        # 以下三条是外部源痕迹检查，各自对应一种搬运没搬干净的形态
        if CITE_MARKER.search(s):
            errs.append(f"{day} #{i} 残留引用标记（[n]、[1, 2]、［1］、[注] 等）：{head(s)}")
        if "<" in s or ">" in s:
            errs.append(f"{day} #{i} 残留 HTML 尖括号：{head(s)}")
        if "\\u" in s:
            errs.append(f"{day} #{i} 残留未解 \\u 转义：{head(s)}")

    for md in sorted(seen):
        day = f"{md[0]:02d}-{md[1]:02d}"
        ys = seen[md]
        if len(ys) != PER_DAY:
            errs.append(f"{day} 有 {len(ys)} 条，应为 {PER_DAY} 条")
        if len(set(ys)) != len(ys):
            dupes = sorted({y for y in ys if ys.count(y) > 1})
            errs.append(f"{day} 年份重复：{dupes}")

    missing = sorted(expected - set(seen))
    if missing:
        shown = "、".join(f"{m:02d}-{d:02d}" for m, d in missing[:12])
        more = f"（其余 {len(missing) - 12} 个）" if len(missing) > 12 else ""
        errs.append(f"缺日 {len(missing)} 个：{shown}{more}")

    # 以下三项只警告：它们反映内容取向（偏古代 / 出生逝世过多 / 句子灌水），
    # 内容可以是有意为之，硬拦会挡下合法内容；但完全不看又会悄悄劣化，故必须发声
    events = sum(1 for e in data if isinstance(e, dict) and e.get("t") == TYPE_EVENT)
    ratio = events / len(data) if data else 0
    if ratio < EVENT_RATIO_MIN:
        warns.append(f"事件类仅占 {ratio:.0%}（低于 {EVENT_RATIO_MIN:.0%}），出生/逝世过多——"
                     f"规范要求每日至少 1 条事件")
    recent = sum(1 for e in data if isinstance(e, dict)
                 and isinstance(e.get("y"), int) and e["y"] >= RECENT_FROM)
    if recent < RECENT_MIN:
        warns.append(f"{RECENT_FROM} 年及以后仅 {recent} 条（少于 {RECENT_MIN}）——"
                     f"源骨架止于 2020，这些年全靠自补，少了卡片会显得停在古代")
    size_kb = len(raw.encode("utf-8")) / 1024
    if size_kb > SIZE_KB_WARN:
        warns.append(f"体积 {size_kb:.0f}KB 超出预期（spec 3.2 估 130KB，阈值 {SIZE_KB_WARN}KB）——"
                     f"检查是否有长句灌水")

    print(f"记录 {len(data)} 条 | 覆盖 {len(seen)}/{len(expected)} 天 | 事件占比 {ratio:.0%} | "
          f"{RECENT_FROM}+ 共 {recent} 条 | 体积 {size_kb:.0f}KB")
    for w in warns:
        print("WARN:", w)

    if errs:
        print(f"\nFAIL：{len(errs)} 项")
        for x in errs[:ERR_SHOW_MAX]:
            print("  -", x)
        if len(errs) > ERR_SHOW_MAX:
            print(f"  …其余 {len(errs) - ERR_SHOW_MAX} 项未展开")
        sys.exit(1)
    print("\nOK：结构校验通过")


if __name__ == "__main__":
    main()
