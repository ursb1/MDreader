# -*- coding: utf-8 -*-
"""把 template.html + 依赖 + test.md 打包成一个自包含 HTML。"""
import base64
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEPS = os.path.join(ROOT, ".temp", "deps")
SRC = os.path.join(ROOT, "src")
OUT = os.path.join(ROOT, "mdreader.html")
ASSET = os.path.join(ROOT, "app", "src", "main", "assets", "mdreader.html")


def read(p, mode="r", **kw):
    if "b" not in mode:
        kw.setdefault("encoding", "utf-8")
    with open(p, mode, **kw) as f:
        return f.read()


def inline_css(css):
    fonts_dir = os.path.join(DEPS, "fonts")
    names = sorted(os.listdir(fonts_dir))
    for name in names:
        if not name.endswith(".woff2"):
            continue
        stem = name[:-6]
        with open(os.path.join(fonts_dir, name), "rb") as f:
            b64 = base64.b64encode(f.read()).decode("ascii")
        data_uri = 'url(data:font/woff2;base64,%s) format("woff2")' % b64
        css = css.replace('url(fonts/%s.woff2) format("woff2")' % stem, data_uri)
        css = re.sub(r',\s*url\(fonts/%s\.woff\)\s*format\("woff"\)' % re.escape(stem), "", css)
        css = re.sub(r',\s*url\(fonts/%s\.ttf\)\s*format\("truetype"\)' % re.escape(stem), "", css)
    left = re.findall(r"url\(fonts/[^)]*\)", css)
    if left:
        print("[warn] 未内联的字体引用: %d" % len(left))
    return css


def strip_script_close(js):
    return js.replace("</script", "<\\/script")


def assemble(tpl, css, mdit, katex, doc):
    doc_js = json.dumps(doc, ensure_ascii=False).replace("</", "<\\/")
    out = tpl.replace("/*__KATEX_CSS__*/", css)
    out = out.replace("/*__MDIT_JS__*/", mdit)
    out = out.replace("/*__KATEX_JS__*/", katex)
    out = out.replace("/*__DOC_JSON__*/", doc_js)
    return out


def main():
    tpl = read(os.path.join(SRC, "template.html"))
    css = inline_css(read(os.path.join(DEPS, "katex.min.css")))
    mdit = strip_script_close(read(os.path.join(DEPS, "markdown-it.min.js")))
    katex = strip_script_close(read(os.path.join(DEPS, "katex.min.js")))
    doc = read(os.path.join(ROOT, "test.md"))

    # 浏览器版：内置 test.md
    out = assemble(tpl, css, mdit, katex, doc)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write(out)
    print("ok -> %s (%.2f MB)" % (OUT, os.path.getsize(OUT) / 1048576.0))

    # APK 版：不内置任何文档，由原生层推送上次阅读的文件
    asset = assemble(tpl, css, mdit, katex, "")
    os.makedirs(os.path.dirname(ASSET), exist_ok=True)
    with open(ASSET, "w", encoding="utf-8", newline="\n") as f:
        f.write(asset)
    print("ok -> %s (empty doc)" % ASSET)


if __name__ == "__main__":
    sys.exit(main())
