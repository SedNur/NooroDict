package com.sntg.dictionary;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

/**
 * Activity شناور برای ACTION_PROCESS_TEXT.
 * صفحهٔ پشت دیده می‌شود؛ دکمه‌های ناوبری سیستم همیشه نمایان می‌مانند.
 *
 * launchMode = singleInstance + taskAffinity="" →
 * فقط یک نمونه در کل سیستم وجود دارد.
 * هر انتخاب متن جدید (حتی از داخل خود پاپ‌آپ) → onNewIntent
 * و کلمهٔ جدید جایگزین می‌شود؛ پنجرهٔ روی‌هم ساخته نمی‌شود.
 */
public class ProcessTextActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);

        setupTransparentWindow();
        showSystemBars();

        View content = findViewById(android.R.id.content);
        if (content != null) {
            makeViewTreeTransparent(content);
        }

        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView != null) {
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.setLayerType(View.LAYER_TYPE_NONE, null);
            makeViewTreeTransparent(webView);

            webView.addJavascriptInterface(new Object() {
                @JavascriptInterface
                public void close() {
                    runOnUiThread(ProcessTextActivity.this::finish);
                }
            }, "AndroidPopup");

            // بارگذاری اولیه
            loadWordFromIntent(getIntent());

            webView.post(() -> {
                webView.setBackgroundColor(Color.TRANSPARENT);
                makeViewTreeTransparent(webView);
                View root = findViewById(android.R.id.content);
                if (root != null) makeViewTreeTransparent(root);
                showSystemBars();
            });
        }
    }

    /**
     * وقتی پاپ‌آپ از قبل باز است و کاربر دوباره کلمه‌ای را انتخاب می‌کند
     * (حتی از داخل خود پاپ‌آپ)، سیستم به‌جای ساختن Activity جدید
     * این متد را صدا می‌زند.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadWordFromIntent(intent);
    }

    /**
     * کلمه را از Intent استخراج و صفحه را با آن بارگذاری می‌کند.
     * هم در onCreate و هم در onNewIntent استفاده می‌شود.
     */
    private void loadWordFromIntent(Intent intent) {
        if (intent == null) return;

        CharSequence selected = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        String selectedText = (selected != null) ? selected.toString().trim() : "";
        String encodedWord = Uri.encode(selectedText);

        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView == null) return;

        // اگر صفحه از قبل در حالت popup لود شده، فقط کلمه را عوض کن (سریع‌تر)
        // در غیر این صورت صفحه را کامل بارگذاری کن.
        String currentUrl = webView.getUrl();
        if (currentUrl != null && currentUrl.contains("popup=1")) {
            // تزریق JS برای به‌روزرسانی بدون رفرش کامل (اختیاری ولی بهتر)
            String js = "javascript:(function(){" +
                    "var input=document.getElementById('search-input');" +
                    "if(input){input.value=" + jsonString(selectedText) + ";}" +
                    "if(typeof showMeaning==='function'){showMeaning(" + jsonString(selectedText) + ");}" +
                    "})();";
            webView.loadUrl(js);
        } else {
            webView.loadUrl("https://localhost/index.html?popup=1&word=" + encodedWord);
        }
    }

    /** رشته را برای قرار دادن داخل JS امن می‌کند. */
    private static String jsonString(String s) {
        if (s == null) return "\"\"";
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private void setupTransparentWindow() {
        Window window = getWindow();
        if (window == null) return;

        window.setFormat(PixelFormat.TRANSLUCENT);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0f);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.dimAmount = 0f;
        lp.format = PixelFormat.TRANSLUCENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        }
        window.setAttributes(lp);

        WindowCompat.setDecorFitsSystemWindows(window, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setNavigationBarColor(Color.BLACK);
            window.setStatusBarColor(Color.TRANSPARENT);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        showSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            showSystemBars();
        }
    }

    private void showSystemBars() {
        Window window = getWindow();
        if (window == null) return;

        View decor = window.getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, decor);
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private static void makeViewTreeTransparent(View view) {
        if (view == null) return;
        view.setBackgroundColor(Color.TRANSPARENT);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            group.setBackground(null);
            group.setBackgroundColor(Color.TRANSPARENT);
            for (int i = 0; i < group.getChildCount(); i++) {
                makeViewTreeTransparent(group.getChildAt(i));
            }
        }
        View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
        int guard = 0;
        while (parent != null && guard++ < 12) {
            parent.setBackgroundColor(Color.TRANSPARENT);
            parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
