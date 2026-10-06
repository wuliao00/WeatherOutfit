#!/usr/bin/env python3
"""从外部历史日期编目中只抽取「事实骨架」：(year, month, day, type)。

刻意丢弃源文件的 data 字段（见 spec 2.7）：源数据声明为维基百科衍生内容，
其 MIT LICENSE 覆盖不了它；而事实本身不受版权保护，受保护的是表述。
所以我们只拿骨架当选题清单，句子全部由本项目另行撰写。

同时把 spec 2.7 里因 blob 截断未能确认的「366 天全覆盖」在这一步钉死。
"""
import json, sys, calendar, pathlib, collections

SRC = pathlib.Path(__file__).parent / "vendor/history_in_today.json"
OUT = pathlib.Path(__file__).parent / "out/today_facts.jsonl"

TYPE_EVENT, TYPE_BIRTH, TYPE_DEATH = 1, 2, 3   # 语义已在 spec 2.7 实测确认


def load(path):
    raw = path.read_text(encoding="utf-8")
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        sys.exit(f"源文件解析失败：{exc}\n"
                 f"多半是取到了截断版（{len(raw)} 字节）。期望 6079304 字节。")


def main():
    recs = load(SRC)
    print(f"源记录数：{len(recs)}")

    facts, byday = [], collections.defaultdict(int)
    # 源数据瑕疵：实测 7/31882 条记录的 year 字段被人名/实体名污染或为空
    # （如 "王啸坤"、"南苏丹共和国"、""），直接 int() 会崩。没有年份就构不成
    # (y,m,d) 事实骨架，只能跳过——但显式汇报条数与明细，不做静默丢弃；
    # 也不计入覆盖统计，免得瑕疵记录盖住真正的缺日。
    malformed = []
    for i, r in enumerate(recs):
        try:
            y, m, d = int(r["year"]), int(r["month"]), int(r["day"])
        except (TypeError, ValueError):
            malformed.append((i, r))
            continue
        try:
            t = int(r["type"])
        except (TypeError, ValueError):
            sys.exit(f"type 非整数：{r['type']!r} on {y}-{m}-{d}，源结构与 spec 2.7 的记录不符，需先核对")
        if t not in (TYPE_EVENT, TYPE_BIRTH, TYPE_DEATH):
            sys.exit(f"未知 type={t} on {y}-{m}-{d}，源结构与 spec 2.7 的记录不符，需先核对")
        facts.append({"y": y, "m": m, "d": d, "t": t})
        byday[(m, d)] += 1

    if malformed:
        print(f"跳过 {len(malformed)} 条 year 无法转成整数的记录（源数据瑕疵，无年份不成事实，不计入覆盖）：")
        for i, r in malformed[:8]:
            print(f"  recs[{i}] year={r['year']!r} month={r['month']!r} day={r['day']!r} type={r['type']!r}")
        if len(malformed) > 8:
            print(f"  …其余 {len(malformed) - 8} 条同类")

    # 覆盖断言：spec 2.7 遗留项，此处正式关闭
    expected = {(m, d) for m in range(1, 13) for d in range(1, calendar.mdays[m] + 1)}
    expected.add((2, 29))
    missing = sorted(expected - set(byday))
    if missing:
        sys.exit(f"缺日 {len(missing)} 个：{missing[:10]}\n"
                 f"说明源数据集不完备，需人工补这些日子的候选事实，不能带着缺口进下一步。")
    print(f"日期覆盖：{len(byday)}/{len(expected)} 完整")

    years = [f["y"] for f in facts]
    print(f"年份范围：{min(years)}..{max(years)}")
    recent = sum(1 for y in years if y >= 2020)
    print(f"2020 年及以后：{recent} 条（源采集于 2020-04，此处偏少属预期）")
    print(f"每日候选中位数：{int(collections.Counter(byday.values()).most_common(1)[0][0])}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8") as fh:
        for f in sorted(facts, key=lambda x: (x["m"], x["d"], -x["y"])):
            fh.write(json.dumps(f, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"写出 {OUT}：{len(facts)} 行")


if __name__ == "__main__":
    main()
