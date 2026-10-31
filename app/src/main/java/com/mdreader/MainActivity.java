package com.mdreader;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 极简 WebView 外壳：把打包进 assets 的单文件阅读器（mdreader.html）加载到
 * https://mdreader.local/ 这个虚拟源下，从而保证 localStorage 可用。
 * 无第三方依赖、无任何运行时权限。
 */
public class MainActivity extends Activity {

    private static final String BASE_URL = "https://mdreader.local/";
    private static final int REQ_PICK = 1001;

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        web.setBackgroundColor(night ? 0xFF0C0F13 : 0xFFF6F7F9);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith(BASE_URL);
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "AndroidHost");

        String html = readAsset("mdreader.html");
        if (html == null) {
            toast("资源加载失败");
            return;
        }
        web.loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null);
    }

    /* ---------- 与页面交互 ---------- */

    private class Bridge {
        @JavascriptInterface
        public void pickFile() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    try {
                        startActivityForResult(i, REQ_PICK);
                    } catch (Exception e) {
                        toast("没有可用的文件选择器");
                    }
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        String name = displayName(uri);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("stream");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            String text = new String(bo.toByteArray(), "UTF-8");
            String label = name.replaceAll("\\.(md|markdown|txt)$", "");
            web.evaluateJavascript("window.MDReader && window.MDReader.load("
                    + JSONObject.quote(text) + "," + JSONObject.quote(label) + ")", null);
        } catch (Exception e) {
            toast("读取失败：" + e.getMessage());
        }
    }

    /* ---------- 工具 ---------- */

    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String v = c.getString(idx);
                    if (v != null && !v.isEmpty()) return v;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        String last = uri.getLastPathSegment();
        return last == null ? "文件" : last;
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.setVisibility(android.view.View.GONE);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
