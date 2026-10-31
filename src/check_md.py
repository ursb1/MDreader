# -*- coding: utf-8 -*-
"""扫描 test.md 里会破坏 Markdown 结构的行级缺陷。"""
import io
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "test.md"
lines = io.open(path, encoding="utf-8").read().split("\n")

issues = []
for i, line in enumerate(lines):
    s = line.rstrip()
    if not s:
        continue
    n = i + 1
    if s.endswith("\\") and not s.endswith("\\\\"):
        issues.append((n, "trailing-backslash", s[-70:]))
    if s.startswith("|") and not s.endswith("|"):
        issues.append((n, "table-row-split", s[-70:]))
    if s.strip().startswith("$") and s.strip().count("$") % 2 == 1:
        issues.append((n, "odd-dollar", s[:90]))
    else:
        plain = s.replace("$$", "")
        if plain.count("$") % 2 == 1:
            issues.append((n, "odd-dollar", s[:90]))

for n, kind, frag in issues:
    print("%5d  %-18s %s" % (n, kind, frag))
print("issues:", len(issues))
