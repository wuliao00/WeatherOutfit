"""`kotlin_lint.py` 的自检：真阳性还得响，假阳性别再叫。

为什么这个文件必须存在：那个工具是这条分支上少数的"门"之一，而**一个会喊错的门
最后教会大家的是忽略它** —— 它曾对着一份语法完全正常的测试文件报 4 条括号错
（`\"\"\"{\"versionCode\":9}\"\"\"` 这种 JSON 夹具里的花括号进了括号栈），
也曾因为只跳过跨行块注释，把写在**单行 KDoc** 里的 `BuildConfig` 判成平台泄漏，
而计划正文里恰好逐字给过那么一行 —— 照抄就得到一条假问题，实现者只能改注释绕开它。

更要紧的是反方向：修假阳性的过程很容易顺手把真阳性也屏蔽掉
（"跳过原始字符串"写错一个条件，commonMain 里真出现 `java.util.Calendar` 就没人报了）。
所以这里两头都验：7 类真问题必须响，7 类不该响的必须沉默。

跑法（1 秒内）：`py tools/kotlin_lint_selftest.py`，非零退出码 = 门坏了。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kotlin_lint as k  # noqa: E402

# (名字, 源码, 用的扫描器)。每条都必须**至少报一个问题**。
MUST_FIRE = {
    "括号没闭合": ("fun a() {\n", k.scan),
    "闭错类型": ("fun a() {\n]\n", k.scan),
    "commonMain 里 import android": ("import android.os.Bundle\n", k.scan_platform_leaks),
    "commonMain 里用 System.currentTimeMillis": (
        "val t = System.currentTimeMillis()\n", k.scan_platform_leaks),
    "块注释里嵌套 /*（路径通配符）": ("/* a\n b /* c\n d\n", k.scan),
    "KDoc 已闭合后的游离 * 续行": ("val x = 1\n * still\n", k.scan),
    "实参位置写 val a = 1": ("foo(\n    val a = 1,\n)\n", k.scan),
}

# 每条都必须**一个都不报**。
MUST_STAY_QUIET = {
    "JSON 夹具的花括号在原始字符串里": (
        'private const val M = """\n{"versionCode":9}\n"""\n', k.scan),
    "同一行里的嵌套花括号": ('val m = """{"a":{"b":1}}"""\n', k.scan),
    "禁用词出现在原始字符串内容里": (
        'val doc = """\njava.util.Calendar here\n"""\n', k.scan_platform_leaks),
    "禁用词写在单行 KDoc 里": (
        "/** 装机包版本（Android 由 BuildConfig 注入） */\nval v = 1\n", k.scan_platform_leaks),
    "禁用词写在跨行块注释里": (
        "/*\n * java.util.Calendar\n */\nval v = 1\n", k.scan_platform_leaks),
    "禁用词写在行注释里": (
        "// System.currentTimeMillis() 太旧\nval v = 1\n", k.scan_platform_leaks),
    "原始字符串里以 * 开头的行": ('val x = """\n* not orphan\n"""\n', k.scan),
}


def main() -> int:
    problems = 0
    for name, (text, fn) in MUST_FIRE.items():
        hits = list(fn(text))
        if not hits:
            problems += 1
            print(f"FAIL 真阳性没报: {name}")
    for name, (text, fn) in MUST_STAY_QUIET.items():
        hits = list(fn(text))
        if hits:
            problems += 1
            print(f"FAIL 假阳性没消除: {name} -> {hits}")
    total = len(MUST_FIRE) + len(MUST_STAY_QUIET)
    print(f"checked {total} cases, {problems} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
