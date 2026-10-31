# MD Reader（小米平板1 适配版）

单文件离线 Markdown 阅读器，专为 MI PAD 1（mocha / Tegra K1 / armeabi-v7a / LineageOS 7.1.2 / WebView 115 / 1536×2048 @320dpi）调优。

## 产物

- `mdreader.html`（约 0.82 MB）—— 完全自包含：markdown-it 14.1.0 + KaTeX 0.16.11（字体以 base64 内嵌），无任何网络请求。

## 设备端使用

文件已推送至 `/sdcard/Download/mdreader.html`。在 Kiwi Browser 地址栏输入：

```
file:///sdcard/Download/mdreader.html
```

首次打开需授予 Kiwi 存储权限（已用 `pm grant` 预先授权）。

## 功能

| 功能 | 说明 |
|---|---|
| 公式渲染 | KaTeX 0.16（CSS 排版，非 MathJax），775 个公式实例零报错 |
| 目录 | 左上角按钮唤出侧边抽屉，按 h2/h3 生成，滚动时高亮当前章节 |
| 字号 | 顶栏 `A-` / `A+`，14–27px，持久化 |
| 主题 | 默认跟随系统深色，可循环切换 自动 → 浅色 → 深色 |
| 进度 | 顶部进度条，按文档记忆滚动位置（localStorage，key `md.pos:<文档>`） |
| 打开文件 | 右上角文件夹按钮，可选 `/sdcard` 上任意 `.md` |
| 公式溢出 | 块级公式横向滚动，不截断 |

## 源数据缺陷修复（重要）

`test.md` 中存在 **24 处** `\ne`（≠）被错误转义为"换行 + `e`"的损坏，例如：

```
| 第一类间断点 | 跳跃间断点 | 左右极限均存在，但 $f(x_0^-)     ← 断行
e f(x_0^+)$ | 图像断裂呈台阶状 ...
```

阅读器内置 `repairMathBreaks()`：当某行行内 `$` 未闭合且下一行以 `e ` 开头时，将两行以 `\ne` 重新拼接（代码块内不处理）。此修复仅作用于渲染，不改动源文件。

## 重新构建

```bash
python -I src/build.py     # 读 .temp/deps + test.md，输出 mdreader.html
python -I src/check_md.py test.md   # 扫描源文件结构缺陷（奇数 $、断行表格等）
```

依赖缓存：`.temp/deps`（markdown-it / katex.min.js / katex.min.css / fonts）。删除后需重新下载。

## 后续（阶段二，未执行）

打包为原生 APK（WebView 外壳、可选文件、沉浸式全屏）需要 Android SDK（约 250MB，10–30 分钟）。用户当前未授权下载，故未进行。

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

