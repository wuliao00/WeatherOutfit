#!/usr/bin/env python3
"""把 tools/out/drafts/ 下的按月分片合并成正式数据集 today_in_history.json。

为什么分片与合并分开：内容按月起草、按月校验（Task 7 的批次节奏），
合并只做机械工作——拼接、排序、紧凑序列化；质量判断不在这里做，
句子质量由 docs/content/today-history-style.md 与 validate_today_dataset.py 把关。

可重复运行：每次从分片全量重建并覆盖输出文件，不在原文件上追加。
缺月不报错——只合并现有分片，缺日由校验脚本的「缺日」断言兜底；
在这里替它报错会掩盖真正的问题（到底是还没写，还是写错了）。
"""
import json
import pathlib
import sys

BASE = pathlib.Path(__file__).parent
DRAFTS = BASE / "out/drafts"
DS = BASE.parent / "shared/src/commonMain/composeResources/files/today_in_history.json"


def main():
    shards = sorted(DRAFTS.glob("*.jsonl"))
    if not shards:
        sys.exit(f"没有找到任何分片：{DRAFTS}/*.jsonl\n先按月写分片再合并。")

    data = []
    for shard in shards:
        n = 0
        with shard.open(encoding="utf-8") as fh:
            for lineno, line in enumerate(fh, 1):
                if not line.strip():
                    continue
                try:
                    data.append(json.loads(line))
                except json.JSONDecodeError as exc:
                    sys.exit(f"{shard.name} 第 {lineno} 行不是合法 JSON：{exc}\n"
                             f"（分片一行一条，修好这一行再重跑，不要带着坏行合并）")
                n += 1
        print(f"  {shard.name}: {n} 条")

    # 排序口径与骨架一致：按月、日升序，同日按年份降序——卡片自上而下先看近的
    data.sort(key=lambda e: (e["m"], e["d"], -e["y"]))

    DS.parent.mkdir(parents=True, exist_ok=True)
    DS.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    size_kb = DS.stat().st_size / 1024
    print(f"合并 {len(data)} 条 → {DS}（{size_kb:.0f}KB）")
    print("下一步：python tools/validate_today_dataset.py 全量校验")


if __name__ == "__main__":
    main()
