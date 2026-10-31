# -*- coding: utf-8 -*-
"""验证 \ne 断裂修复逻辑：把 "行尾未闭合 $ + 下一行以 e 开头" 重新粘回。"""
import io
import re
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "test.md"
lines = io.open(path, encoding="utf-8").read().split("\n")


def single_dollar_count(s):
    return s.replace("$$", "").count("$")


fixed = 0
out = []
for line in lines:
    if out and single_dollar_count(out[-1]) % 2 == 1 and re.match(r"^e\s", line):
        out[-1] = out[-1] + "\\n" + line
        fixed += 1
        continue
    out.append(line)

text = "\n".join(out)
io.open(".temp/build/test.repaired.md", "w", encoding="utf-8", newline="\n").write(text)
print("merged lines:", fixed)

# 复查
bad = [i + 1 for i, l in enumerate(out) if l.strip() and single_dollar_count(l) % 2 == 1]
print("remaining odd-dollar lines:", bad)
