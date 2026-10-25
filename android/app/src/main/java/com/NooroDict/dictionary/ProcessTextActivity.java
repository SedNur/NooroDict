package com.noorodict.dictionary;

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
 * صفحهٔ پشت دیده می‌شود؛ دکمه‌های ناوبری سیستم (بازگشت / خانه / اخیرها) باید همیشه نمایان بمانند.
 *
 * launchMode = singleTask → اگر پاپ‌آپ از قبل باز باشد، پنجرهٔ جدید ساخته نمی‌شود
 * و فقط onNewIntent صدا زده می‌شود تا کلمهٔ جدید جایگزین شود.
 */
public class ProcessTextActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (window != null) {
            window.setFormat(PixelFormat.TRANSLUCENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0f);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // تمام‌صفحه / immersive را عمداً برمی‌داریم تا نوار ناوبری مخفی نشود
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            lp.dimAmount = 0f;
            lp.format = PixelFormat.TRANSLUCENT;
            // از layout زیر نوار سیستم خارج نشو
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            }
            window.setAttributes(lp);

            // محتوا را زیر status/nav نکش (edge-to-edge خاموش)
            WindowCompat.setDecorFitsSystemWindows(window, true);

            // رنگ نوار ناوبری را به حالت سیستم برگردان تا دکمه‌ها دیده شوند
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setNavigationBarColor(Color.BLACK); // یا رنگ پیش‌فرض تم؛ مهم این است که نوار خودش نمایان باشد
                window.setStatusBarColor(Color.TRANSPARENT);
            }
        }

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

            // بارگذاری اولیه با کلمهٔ انتخاب‌شده
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
        setIntent(intent);          // intent فعلی را به‌روز کن
        loadWordFromIntent(intent); // کلمهٔ جدید را جایگزین کن
    }

    /**
     * کلمه را از Intent استخراج می‌کند و صفحه را با آن بارگذاری می‌کند.
     * هم در onCreate و هم در onNewIntent استفاده می‌شود.
     */
    private void loadWordFromIntent(Intent intent) {
        if (intent == null) return;

        CharSequence selected = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        String selectedText = (selected != null) ? selected.toString().trim() : "";
        String encodedWord = Uri.encode(selectedText);

        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView == null) return;

        webView.loadUrl("https://localhost/index.html?popup=1&word=" + encodedWord);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Capacitor گاهی بعد از resume دوباره immersive می‌گذارد
        showSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            showSystemBars();
        }
    }

    /**
     * دکمه‌های سیستم (Back / Home / Recents) و نوار وضعیت را حتماً نشان بده.
     * هر پرچم immersive که Capacitor یا تم گذاشته باشد پاک می‌شود.
     */
    private void showSystemBars() {
        Window window = getWindow();
        if (window == null) return;

        View decor = window.getDecorView();
        // حالت قدیمی: همهٔ پرچم‌های مخفی‌کننده را بردار
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

    /** همهٔ لایه‌های View تا ریشه را پس‌زمینهٔ شفاف می‌کند (بدون دست زدن به system bars). */
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
            // DecorView را کامل خالی نکن؛ فقط background
            parent.setBackgroundColor(Color.TRANSPARENT);
            parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
