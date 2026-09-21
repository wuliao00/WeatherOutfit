"""Cheap static sanity check for Kotlin sources: bracket balance + obvious leftovers.

Catches the class of mistake that costs a 6-minute Gradle cycle to discover
(mismatched ()/{}/[] , stray "TODO" markers, `scope.let { }` no-ops, etc.).

Usage (from repo root):  py -3 tools/kotlin_lint.py [path ...]
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "app" / "src"

# 需要人工确认的可疑写法
SUSPECTS = [
    (re.compile(r"\.let\s*\{\s*\}"), "empty .let {} — leftover no-op"),
    (re.compile(r"\bTODO\("), "TODO() call"),
    (re.compile(r"return this\.then\(Modifier\)\s*$"), "then(Modifier) — does nothing"),
    (re.compile(r"^\s*import .*\*\s*$"), "wildcard import"),
]


def scan(text: str):
    """Yield (line, message) problems. Tracks strings and both comment styles."""
    depth = {"(": 0, "{": 0, "[": 0}
    pairs = {")": "(", "}": "{", "]": "["}
    owners = {}  # opener char -> line it opened on
    in_block = False
    stack = []

    for ln, line in enumerate(text.splitlines(), 1):
        i = 0
        n = len(line)
        while i < n:
            c = line[i]
            if in_block:
                if c == "*" and i + 1 < n and line[i + 1] == "/":
                    in_block = False
                    i += 2
                    continue
                i += 1
                continue
            if c == "/" and i + 1 < n and line[i + 1] == "*":
                in_block = True
                i += 2
                continue
            if c == "/" and i + 1 < n and line[i + 1] == "/":
                break
            if c == '"':
                # 三引号原始字符串里也可能有括号，这里按普通字符串处理足够用
                i += 1
                while i < n and line[i] != '"':
                    if line[i] == "\\":
                        i += 1
                    i += 1
                i += 1
                continue
            if c in "({[":
                stack.append((c, ln))
            elif c in ")}]":
                if not stack:
                    yield ln, f"stray '{c}' with nothing to match"
                else:
                    opener, oln = stack.pop()
                    want = pairs[c]
                    if opener != want:
                        yield ln, f"'{c}' closes '{opener}' opened at line {oln}"
                    else:
                        depth[opener] -= 1
                i += 1
                continue
            i += 1

    for opener, oln in stack:
        yield oln, f"unclosed '{opener}'"


def main() -> int:
    targets = [Path(a) for a in sys.argv[1:]] or [SRC]
    files: list[Path] = []
    for t in targets:
        if t.is_dir():
            files += sorted(t.rglob("*.kt"))
        else:
            files.append(t)

    problems = 0
    for f in files:
        text = f.read_text(encoding="utf-8")
        for ln, msg in scan(text):
            print(f"{f.relative_to(REPO)}:{ln}: {msg}")
            problems += 1
        for rx, msg in SUSPECTS:
            for m in rx.finditer(text):
                ln = text[: m.start()].count("\n") + 1
                print(f"{f.relative_to(REPO)}:{ln}: {msg}")
                problems += 1

    print(f"\nchecked {len(files)} files, {problems} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
