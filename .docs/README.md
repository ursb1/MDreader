# MD Reader（小米平板1 适配版）

单文件离线 Markdown 阅读器，专为 MI PAD 1（mocha / Tegra K1 / armeabi-v7a / LineageOS 7.1.2 / WebView 115 / 1536×2048 @320dpi）调优。

## 产物

- `mdreader.html`（约 0.82 MB）—— 浏览器版，内置 `test.md` 作默认文档；完全自包含：markdown-it 14.1.0 + KaTeX 0.16.11（字体 base64 内嵌），零网络请求。
- `app/src/main/assets/mdreader.html`（约 787 KB）—— APK 版，**不内置任何文档**（纯阅读器），由 `build.py` 自动生成。

## 设备端使用

文件已推送至 `/sdcard/Download/mdreader.html`。在 Kiwi Browser 地址栏输入：

```
file:///sdcard/Download/mdreader.html
```

首次打开需授予 Kiwi 存储权限（已用 `pm grant` 预先授权）。

## 功能

| 功能 | 说明 |
|---|---|
| 公式渲染 | KaTeX 0.16（CSS 排版，非 MathJax），数千公式实例零报错 |
| 目录 | 左上角按钮唤出侧边抽屉，按 h2/h3 生成，滚动时高亮当前章节 |
| 字号 | 顶栏 `A-` / `A+`，14–27px，持久化 |
| 主题 | 默认跟随系统深色，可循环切换 自动 → 浅色 → 深色 |
| 进度 | 顶部进度条，按文档记忆滚动位置（localStorage，key `md.pos:<文档>`） |
| 打开文件 | 右上角文件夹按钮或空状态按钮，可选设备上任意 `.md` |
| 续读 | 打开 App 自动恢复上次的文件（SAF 持久授权）与该位置 |
| 公式溢出 | 块级公式横向滚动，不截断 |

## 状态存储

| 内容 | 位置 |
|---|---|
| 上次文件 URI / 文件名 | APK：`SharedPreferences("mdreader")` 的 `last_uri` / `last_name` |
| 每个文档的阅读位置 | 页面 localStorage：`md.pos:uri:<uri hash>` |
| 字号 / 主题 | 页面 localStorage：`md.fs` / `md.theme` |

浏览器版（`mdreader.html`）没有原生层，位置键为 `md.pos:builtin`，文件通过 `<input type=file>` 选择。

## 渲染规范（严格 CommonMark）

阅读器**不做任何猜测性修复**，源文件不合规就如实显示坏结果。行内公式额外约束：`$...$` 必须在同一行闭合（不跨软换行），否则原样输出 `$`。

源文件必须满足：

1. **代码围栏成对，且闭合围栏长度 ≥ 开启围栏**。````text 开启就必须 ```` 关闭；写成 ``` 关闭会导致此后全文被吞进代码块（表现为**侧栏目录为空、公式全部原样输出**）。
2. 首行标题前不能有 `1. ` 之类的列表序号，否则 h1 退化成列表项。
3. `\ne`（≠）不能写成"换行 + e"，否则行内公式断裂、显示为字面 `$`。
4. 表格行不能跨行（单元格内不能有裸换行）。
5. 行内 `$` 数量必须成对。

自查工具：

```bash
python -I src/check_md.py <file.md>    # 报告奇数 $、断行表格、行尾反斜杠
```

当前已知不合规文件（需修源文件）：

- `test.md` —— 24 处 `\ne` 断行，严格渲染下约 18 处显示为字面 `$`。
- `模块03_连续性与闭区间函数性质_精读全书.md` —— 第 56 行 ````text 与第 71 行 ``` 不配对，整篇变代码块；首行 `1. # …`；另有 `\ne` 断行。

## 重新构建

```bash
python -I src/build.py     # 读 .temp/deps + test.md，输出 mdreader.html 与 assets 副本
```

依赖缓存：`.temp/deps`（markdown-it / katex.min.js / katex.min.css / fonts）。删除后需重新下载。

## APK 构建（GitHub Actions，本地无需 SDK）

工程结构：

```
.github/workflows/android.yml      # push 到 main/master 或手动触发
build.gradle / settings.gradle     # AGP 8.5.2 + Gradle 8.7 + JDK 17
app/build.gradle                   # minSdk 25 / targetSdk 34 / 零第三方依赖
app/src/main/AndroidManifest.xml
app/src/main/java/com/mdreader/MainActivity.java
app/src/main/assets/mdreader.html  # 由 src/build.py 生成，勿手改
```

`MainActivity` 用 `loadDataWithBaseURL("https://mdreader.local/", ...)` 加载 HTML，
因此 localStorage（字号/主题/阅读位置）在 Android WebView 下可用；打开本地 `.md`
走 SAF（`ACTION_OPEN_DOCUMENT`），**不需要任何运行时权限**；无 native 代码，单个
APK 全 ABI 通用。

### 签名（可选但推荐）

未配置 secrets 时用 debug 签名（可安装，但每次换机/重装签名不同）。
要固定签名，在仓库 Settings → Secrets 添加：

| Secret | 值 |
|---|---|
| `KEYSTORE_BASE64` | `python -c "import base64;print(base64.b64encode(open('.tools/jm.keystore','rb').read()).decode())"` |
| `KEYSTORE_PASSWORD` | `android` |
| `KEY_ALIAS` | `jm` |
| `KEY_PASSWORD` | `android` |

`.tools/` 已在 `.gitignore` 中，密钥不会进仓库。

### 安装

构建产物在 Actions 的 `mdreader-apk` artifact 里。安装：

```bash
adb install -r mdreader-<run>.apk
```

若之前装过签名不同的版本，需先 `adb uninstall com.mdreader`。
