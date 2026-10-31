# 交接文档（HANDOFF）

> 生成时间：2026-10-31。下一个会话从这里继续，避免重新踩坑。

## 1. 任务目标

为**小米平板 1** 写一个 Markdown 阅读器，能在设备上完美阅读高数讲义（含大量数学公式）。
交付路线（用户已确认）：**阶段一** 单文件 HTML（已验证）→ **阶段二** 原生 APK（GitHub Actions 构建，本地不装 SDK）。
最终验收：设备上能流畅阅读 `test.md`／同类讲义，公式、目录、图表正常。

## 2. 设备事实（已用 adb 实测）

| 项 | 值 |
|---|---|
| 型号 | MI PAD（`mocha`，Tegra K1，**armeabi-v7a 32 位**） |
| 系统 | LineageOS **7.1.2 / SDK 25**，Magisk root（`/sbin/su`） |
| 屏幕 | 1536×2048 物理 @320dpi → **768×1024dp**，4:3 竖屏 |
| 内存 | 2GB（空闲 ~340MB） |
| WebView | `com.android.webview` **115.0.5781.0** |
| 存储 | /data 12G，剩 10G |
| 已装 | Kiwi Browser、Via、KOReader |
| adb | `.tools/ADB/adb.exe`（**1.0.31 老版本：无 `exec-out`，需 `screencap` 落盘后 `pull`**） |

**Git Bash 必看**：调 adb 前 `export MSYS_NO_PATHCONV=1`，否则 `/sdcard/...` 会被改写成 `D:/Software/Git/sdcard/...`。

**设备上打开 HTML 的唯一可行方式**：Kiwi 地址栏输入 `file:///sdcard/Download/mdreader.html`
（Kiwi 不接受 `file://` 的 VIEW Intent；只有 KOReader 声明了该 filter，但它不支持 JS）。
Kiwi 的存储权限已 `pm grant` 授予。当前文件：`/sdcard/Download/mdreader.html`。

## 3. 架构

```
src/template.html          阅读器源码（HTML/CSS/JS 单文件模板，占位符注入）
src/build.py               构建：内联 KaTeX CSS(字体 base64) + markdown-it + KaTeX + 文档
src/check_md.py            源文件缺陷自查（奇数 $ / 断行表格 / 行尾反斜杠）
.temp/deps/                依赖缓存：markdown-it 14.1.0、katex 0.16.11、20 个 woff2
mdreader.html              产物①浏览器版（内置 test.md，857KB）
app/src/main/assets/mdreader.html  产物②APK 版（无内置文档，787KB）
app/…/MainActivity.java    WebView 外壳
.github/workflows/android.yml  AGP 8.5.2 + Gradle 8.7 + JDK 17，Actions 出 APK
.docs/README.md            用户向文档（功能、规范、签名、安装）
```

构建：`python -I src/build.py`（**必须 `-I`**；同时写两份产物）。

## 4. 关键技术决策（不要回退）

1. **KaTeX 而非 MathJax**：Tegra K1 + 2GB RAM，MathJax 的 JS 排布会明显卡顿。KaTeX 是 CSS 排版，776 个公式一次性渲染 <1s。
2. **渲染严格遵循 CommonMark**（用户明确要求）：曾加过 4 项容错（围栏规整、孤立围栏清理、首行序号剥离、`\ne` 重拼），**已全部删除**。源文件不合规就如实显示坏结果。`src/template.html` 里现在没有 `prepare()`。
3. **行内公式不跨行**：`$...$` 扫到 `\n` 即放弃，坏文件会露出字面 `$`（否则会把断裂公式静默拼成错误数学）。
4. **APK 纯阅读器**：assets 版 `DOC_SRC` 为空，由原生层推文档；根目录版保留 test.md 供浏览器/调试。
5. **WebView 用 `loadDataWithBaseURL("https://mdreader.local/", html, …)`**：file:// 源下 localStorage 不可靠，虚拟 https 源才能记住字号/主题/位置。
6. **打开文件走 SAF**（`ACTION_OPEN_DOCUMENT` + `takePersistableUriPermission`）：零运行时权限，重启后仍可读上次文件。
7. 阅读位置键 `md.pos:uri:<uri.hashCode() 十六进制>`（不是文件名，避免同名串台）。

## 5. 已实现功能

目录抽屉（h2/h3，滚动高亮）、字号 A-/A+（14–27px 持久化）、主题 自动/浅色/深色（默认跟随系统）、顶部进度条、回到顶部、块级公式横向滚动、空状态页、`AndroidHost{ onReady, pickFile }` 桥、`window.MDReader.load(text,label,key)` / `.empty()`。

## 6. 已验证结果（桌面 Chromium）

- test.md（浏览器版）：754 KaTeX、20 目录项、0 报错；18 处字面 `$`（源文件缺陷如实暴露）。
- APK 版（stub 原生桥）：空状态正常；`onReady` 触发 1 次；`load()` 后 40 公式/40 目录项；位置保存 `0.5558` → 重载恢复 scrollY 4123/7419；两个打开按钮均调 `pickFile()`。
- 真机（阶段一 HTML，Kiwi）：深色主题、公式、目录抽屉、cases、ASCII 图均正常。

## 7. 未验证 / 待办

1. **Gradle 构建从未跑过**（本机无 SDK，用户暂不授权下载 ~250MB）。首次 Actions 失败就把日志贴来修。
2. **`MainActivity` 的 Java 代码未编译过**，只做了人工复核（SAF、UI 线程、转义）。重点盯：`takePersistableUriPermission`、`evaluateJavascript` 线程、`EXTRA_MIME_TYPES`。
3. **git 未推送**：本地有提交 `ca0cae0`（"md reader + ci"），分支当时是 `master`，已建议 `git branch -M main` 后 push 到 `github.com/ursb1/MDreader`。**pure reader 这批改动尚未提交**。
4. 签名 secrets 未配置（`KEYSTORE_BASE64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD`，见 `.docs/README.md`）；未配则用 debug 签名。
5. 用户自己的 md 文件有缺陷，**用户选择修 md 而非加容错**：
   - `test.md`：24 处 `\ne` 被写成"换行+e"。
   - `E:\Download\模块03_…精读全书.md`：第 56 行 ````text 与第 71 行 ``` 不配对（整篇变代码块）、首行 `1. # 标题`、末尾孤立 ````、`\ne` 断行。
   - 已提议批量修复脚本，用户当时说"不需要"。

## 8. 下一步（按优先级）

1. 用户 push → Actions 构建 → 装 APK（`adb install -r`，签名不同需先 `adb uninstall com.mdreader`）。
2. 真机验证「打开 App 自动回到上次文档与位置」；重点看首次空状态、选文件、杀进程后重开。
3. 若 Gradle 报错：按日志修 `app/build.gradle` / workflow（AGP 8.5.2 ↔ Gradle 8.7 是匹配组合）。
4. 阶段二可选增强（未做，勿擅自加）：沉浸式全屏、状态栏配色、打开其他 App 分享的 .md（VIEW intent filter）。

## 9. 常用命令

```bash
export MSYS_NO_PATHCONV=1
.tools/ADB/adb.exe devices
.tools/ADB/adb.exe push mdreader.html /sdcard/Download/mdreader.html
.tools/ADB/adb.exe shell input tap X Y
.tools/ADB/adb.exe shell screencap -p /sdcard/Download/_shot.png && .tools/ADB/adb.exe pull /sdcard/Download/_shot.png .temp/shot.png
.tools/ADB/adb.exe shell rm -f /sdcard/Download/_shot.png     # 用完清理
python -I src/build.py
python -I src/check_md.py test.md
```

本地验证 HTML 的办法：把 `app/src/main/assets/mdreader.html` 复制到 `.temp/verify/`，用
`python -I -m http.server` 起服务 + Chrome DevTools MCP，注入 `window.AndroidHost` stub 模拟原生桥。
