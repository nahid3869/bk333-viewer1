package com.bk333.viewer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BK333_Viewer";
    private static final String HOME_URL = "https://www.www-bk333.com/m/home";
    private static final String[] PROXY_LIST = {
            "https://api.allorigins.win/raw?url=",
            "https://corsproxy.io/?",
            "https://api.codetabs.com/v1/proxy?quest=",
            "https://corsproxy.org/?"
    };
    private static final String[] PROXY_NAMES = {
            "AllOrigins",
            "CORSProxy.io",
            "CodeTabs",
            "CORSProxy.org"
    };

    // UI Components
    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private LinearProgressIndicator progressBar;
    private MaterialTextView statusText;
    private MaterialTextView urlTextView;
    private TextInputEditText urlInput;
    private BottomNavigationView bottomNav;
    private Toolbar toolbar;
    private MaterialButtonToggleGroup modeToggle;
    private MaterialButton btnProxyMode;
    private MaterialButton btnDirectMode;

    // State
    private String currentUrl = HOME_URL;
    private Stack<String> historyStack = new Stack<>();
    private int currentProxyIndex = 0;
    private boolean useProxy = true;
    private boolean isLoading = false;
    private boolean nightMode = false;
    private Set<String> bookmarks = new HashSet<>();
    private List<HistoryItem> browsingHistory = new ArrayList<>();
    private boolean isLoadingFromHistory = false;

    // Proxy
    private ProxyManager proxyManager;
    private Handler handler = new Handler();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check dark mode preference
        nightMode = getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("night_mode", false);
        if (nightMode) {
            setTheme(R.style.Theme_BK333Viewer_Dark);
        }

        setContentView(R.layout.activity_main);

        // Handle incoming URL from intent
        handleIntent(getIntent());

        // Initialize
        initViews();
        setupToolbar();
        setupWebView();
        setupSwipeRefresh();
        setupBottomNav();
        setupModeToggle();
        setupUrlInput();
        setupBookmarks();

        // Load initial URL
        loadUrl(currentUrl);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                currentUrl = data.toString();
            }
        }
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        urlTextView = findViewById(R.id.urlTextView);
        urlInput = findViewById(R.id.urlInput);
        bottomNav = findViewById(R.id.bottomNav);
        toolbar = findViewById(R.id.toolbar);
        modeToggle = findViewById(R.id.modeToggle);
        btnProxyMode = findViewById(R.id.btnProxyMode);
        btnDirectMode = findViewById(R.id.btnDirectMode);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Core settings
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAppCacheEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setBlockNetworkLoads(false);

        // Viewport & zoom
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        settings.setTextZoom(100);

        // Security bypass
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // JavaScript popups
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        // User Agent spoofing (Desktop UA for full site)
        String ua = settings.getUserAgentString();
        String desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36";
        settings.setUserAgentString(desktopUA + " BK333-Viewer/2.0");

        // Cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        // Database paths
        String dbPath = getApplicationContext().getDir("database", Context.MODE_PRIVATE).getPath();
        settings.setDatabasePath(dbPath);
        settings.setGeolocationDatabasePath(dbPath);

        // WebViewClient
        webView.setWebViewClient(new BK333WebViewClient());

        // WebChromeClient
        webView.setWebChromeClient(new BK333WebChromeClient());

        // Download listener
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            // Handle downloads - open in browser or download manager
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(url), mimeType != null ? mimeType : "*/*");
            startActivity(Intent.createChooser(intent, "Download file"));
        });

        // Scroll listener for URL bar
        webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY > oldScrollY && urlInput.getVisibility() == View.VISIBLE) {
                urlInput.setVisibility(View.GONE);
            } else if (scrollY < oldScrollY && urlInput.getVisibility() == View.GONE) {
                urlInput.setVisibility(View.VISIBLE);
            }
        });

        // Touch listener for right-click / long press
        webView.setOnLongClickListener(v -> {
            WebView.HitTestResult hit = webView.getHitTestResult();
            if (hit != null) {
                int type = hit.getType();
                if (type == WebView.HitTestResult.IMAGE_TYPE ||
                        type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    showImageMenu(hit.getExtra());
                    return true;
                }
                if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                    showLinkMenu(hit.getExtra());
                    return true;
                }
            }
            return false;
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
                getColor(R.color.accent),
                getColor(R.color.green),
                getColor(R.color.red)
        );
        swipeRefresh.setProgressBackgroundColorSchemeColor(
                getColor(R.color.card_background)
        );
        swipeRefresh.setOnRefreshListener(() -> {
            if (!isLoading) {
                loadUrl(currentUrl);
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void setupBottomNav() {
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_back) {
                goBack();
                return true;
            } else if (id == R.id.nav_forward) {
                goForward();
                return true;
            } else if (id == R.id.nav_refresh) {
                loadUrl(currentUrl);
                return true;
            } else if (id == R.id.nav_home) {
                loadUrl(HOME_URL);
                return true;
            } else if (id == R.id.nav_menu) {
                showMainMenu();
                return true;
            }

            return false;
        });
    }

    private void setupModeToggle() {
        modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                useProxy = (checkedId == R.id.btnProxyMode);
                updateStatus();
                showSnackbar(useProxy ? "🌐 Proxy Mode: " + PROXY_NAMES[currentProxyIndex] : "🟢 Direct Mode");
            }
        });

        // Toggle proxy on long press
        btnProxyMode.setOnLongClickListener(v -> {
            cycleProxy();
            return true;
        });
    }

    private void setupUrlInput() {
        urlInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                String url = urlInput.getText().toString().trim();
                if (!url.isEmpty()) {
                    loadUrl(url);
                }
                urlInput.clearFocus();
                hideKeyboard();
                return true;
            }
            return false;
        });

        urlInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                urlInput.selectAll();
            }
        });
    }

    private void setupBookmarks() {
        // Load bookmarks from preferences
        String saved = getSharedPreferences("bookmarks", MODE_PRIVATE).getString("list", "");
        if (!saved.isEmpty()) {
            String[] items = saved.split(",");
            for (String item : items) {
                bookmarks.add(item.trim());
            }
        }
    }

    // ─── Core Load Function ───

    private void loadUrl(String url) {
        if (url == null || url.isEmpty()) return;

        // Normalize URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        // Save to history stack before changing
        if (!currentUrl.equals(url)) {
            historyStack.push(currentUrl);
        }

        currentUrl = url;
        isLoading = true;

        // Update UI
        urlInput.setText(url);
        urlTextView.setText(url);
        statusText.setText("🔄 Loading...");
        swipeRefresh.setRefreshing(true);
        progressBar.setVisibility(View.VISIBLE);

        if (useProxy) {
            // Try with proxy
            String proxyUrl = getProxyUrl(url);
            Log.d(TAG, "Loading with proxy: " + proxyUrl.substring(0, Math.min(100, proxyUrl.length())) + "...");
            webView.loadUrl(proxyUrl);

            // Add timeout fallback
            handler.postDelayed(() -> {
                if (isLoading) {
                    Log.d(TAG, "Proxy timeout, trying next proxy...");
                    cycleProxy();
                    loadUrl(currentUrl);
                }
            }, 15000);
        } else {
            // Direct load (usually blocked)
            Log.d(TAG, "Loading directly: " + url);
            webView.loadUrl(url);
        }

        // Add to browsing history
        addToHistory(url);
    }

    private String getProxyUrl(String url) {
        String proxy = PROXY_LIST[currentProxyIndex];
        return proxy + Uri.encode(url);
    }

    private void cycleProxy() {
        currentProxyIndex = (currentProxyIndex + 1) % PROXY_LIST.length;
        String name = PROXY_NAMES[currentProxyIndex];

        // Update chip
        btnProxyMode.setText("🌐 " + name);

        showSnackbar("🔄 Proxy switched to: " + name);

        if (isLoading && useProxy) {
            loadUrl(currentUrl);
        }
    }

    private void addToHistory(String url) {
        // Remove duplicate
        browsingHistory.removeIf(item -> item.url.equals(url));
        browsingHistory.add(0, new HistoryItem(url, new Date()));

        // Keep max 200 items
        if (browsingHistory.size() > 200) {
            browsingHistory = browsingHistory.subList(0, 200);
        }
    }

    // ─── Navigation ───

    private void goBack() {
        if (!historyStack.isEmpty()) {
            String prevUrl = historyStack.pop();
            isLoadingFromHistory = true;
            loadUrl(prevUrl);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            showSnackbar("No previous page");
        }
    }

    private void goForward() {
        if (webView.canGoForward()) {
            webView.goForward();
        } else {
            showSnackbar("No next page");
        }
    }

    // ─── Inject Bypass Script ───

    @SuppressLint("ObsoleteSdkInt")
    private void injectBypassScript() {
        String script = "(function() {" +
                "  try {" +
                "    // Remove blocking meta tags" +
                "    var metas = document.querySelectorAll('meta');" +
                "    for(var i=0; i<metas.length; i++) {" +
                "      var m = metas[i];" +
                "      if(m.getAttribute('http-equiv') && " +
                "         (m.getAttribute('http-equiv').toLowerCase().indexOf('x-frame') >= 0 ||" +
                "          m.getAttribute('http-equiv').toLowerCase().indexOf('content-security') >= 0)) {" +
                "        m.remove();" +
                "      }" +
                "    }" +
                "    // Override frame busting" +
                "    var overrideTop = function() {" +
                "      try {" +
                "        Object.defineProperty(window, 'top', {get: function(){return window.self;}});" +
                "        Object.defineProperty(window, 'parent', {get: function(){return window.self;}});" +
                "      } catch(e) {}" +
                "    };" +
                "    overrideTop();" +
                "    // Block location redirect attempts" +
                "    var _orig = window.location.href;" +
                "    Object.defineProperty(window, 'location', {" +
                "      get: function() { return { href: _orig, hash: '', host: '', hostname: '', " +
                "        pathname: '', port: '', protocol: '', search: '', " +
                "        assign: function(){}, replace: function(){}, reload: function(){} };" +
                "      }," +
                "      set: function(v) { console.log('🔒 BK333 blocked redirect:', v); }" +
                "    });" +
                "    console.log('✅ BK333 bypass script injected');" +
                "  } catch(e) { console.log('BK333 bypass error:', e.message); }" +
                "})();";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(script, null);
        } else {
            webView.loadUrl("javascript:" + script);
        }
    }

    // ─── UI Helpers ───

    private void updateStatus() {
        String mode = useProxy ? "🌐 " + PROXY_NAMES[currentProxyIndex] : "🟢 Direct";
        String url = currentUrl.length() > 50 ?
                currentUrl.substring(0, 47) + "..." :
                currentUrl;
        statusText.setText(mode + " | " + url);
    }

    private void showSnackbar(String msg) {
        View root = findViewById(android.R.id.content);
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getColor(R.color.card_background))
                .setTextColor(getColor(R.color.text_primary))
                .setActionTextColor(getColor(R.color.accent))
                .show();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showImageMenu(String url) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Image")
                .setItems(new String[]{
                        "Open Image",
                        "Copy URL",
                        "Share"
                }, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            loadUrl(url);
                            break;
                        case 1:
                            android.content.ClipboardManager clipboard =
                                    (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            android.content.ClipData clip =
                                    android.content.ClipData.newPlainText("URL", url);
                            clipboard.setPrimaryClip(clip);
                            showSnackbar("✅ URL copied");
                            break;
                        case 2:
                            Intent share = new Intent(Intent.ACTION_SEND);
                            share.setType("text/plain");
                            share.putExtra(Intent.EXTRA_TEXT, url);
                            startActivity(Intent.createChooser(share, "Share"));
                            break;
                    }
                })
                .show();
    }

    private void showLinkMenu(String url) {
        boolean isBookmarked = bookmarks.contains(url);

        String[] items = isBookmarked ?
                new String[]{"Open", "Copy URL", "Share", "★ Remove Bookmark"} :
                new String[]{"Open", "Copy URL", "Share", "☆ Add Bookmark"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Link")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            loadUrl(url);
                            break;
                        case 1:
                            android.content.ClipboardManager clipboard =
                                    (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            android.content.ClipData clip =
                                    android.content.ClipData.newPlainText("URL", url);
                            clipboard.setPrimaryClip(clip);
                            showSnackbar("✅ URL copied");
                            break;
                        case 2:
                            Intent share = new Intent(Intent.ACTION_SEND);
                            share.setType("text/plain");
                            share.putExtra(Intent.EXTRA_TEXT, url);
                            startActivity(Intent.createChooser(share, "Share"));
                            break;
                        case 3:
                            toggleBookmark(url);
                            break;
                    }
                })
                .show();
    }

    private void showMainMenu() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("📌 BK333 Viewer")
                .setItems(new String[]{
                        "🏠 Home",
                        "📋 History",
                        "⭐ Bookmarks",
                        "🔁 Switch Proxy (" + PROXY_NAMES[currentProxyIndex] + ")",
                        "🌙 " + (nightMode ? "Light Mode" : "Dark Mode"),
                        "📸 Screenshot",
                        "🖨️ Print",
                        "⚙️ Settings"
                }, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            loadUrl(HOME_URL);
                            break;
                        case 1:
                            showHistoryDialog();
                            break;
                        case 2:
                            showBookmarksDialog();
                            break;
                        case 3:
                            cycleProxy();
                            break;
                        case 4:
                            toggleNightMode();
                            break;
                        case 5:
                            takeScreenshot();
                            break;
                        case 6:
                            printPage();
                            break;
                        case 7:
                            showSettingsDialog();
                            break;
                    }
                })
                .show();
    }

    private void toggleBookmark(String url) {
        if (bookmarks.contains(url)) {
            bookmarks.remove(url);
            showSnackbar("⭐ Bookmark removed");
        } else {
            bookmarks.add(url);
            showSnackbar("✅ Bookmark added");
        }
        saveBookmarks();
    }

    private void saveBookmarks() {
        StringBuilder sb = new StringBuilder();
        for (String b : bookmarks) {
            if (sb.length() > 0) sb.append(",");
            sb.append(b);
        }
        getSharedPreferences("bookmarks", MODE_PRIVATE)
                .edit()
                .putString("list", sb.toString())
                .apply();
    }

    private void showBookmarksDialog() {
        if (bookmarks.isEmpty()) {
            showSnackbar("No bookmarks yet");
            return;
        }

        String[] items = bookmarks.toArray(new String[0]);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("⭐ Bookmarks (" + items.length + ")");

        builder.setItems(items, (dialog, which) -> {
            loadUrl(items[which]);
        });

        builder.setNeutralButton("Clear All", (d, w) -> {
            bookmarks.clear();
            saveBookmarks();
            showSnackbar("All bookmarks cleared");
        });

        builder.show();
    }

    private void showHistoryDialog() {
        if (browsingHistory.isEmpty()) {
            showSnackbar("No browsing history");
            return;
        }

        String[] items = new String[browsingHistory.size()];
        for (int i = 0; i < browsingHistory.size(); i++) {
            String url = browsingHistory.get(i).url;
            items[i] = url.length() > 60 ? url.substring(0, 57) + "..." : url;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("📋 History (" + items.length + ")");

        builder.setItems(items, (dialog, which) -> {
            loadUrl(browsingHistory.get(which).url);
        });

        builder.setNeutralButton("Clear", (d, w) -> {
            browsingHistory.clear();
            showSnackbar("History cleared");
        });

        builder.show();
    }

    private void toggleNightMode() {
        nightMode = !nightMode;
        getSharedPreferences("settings", MODE_PRIVATE)
                .edit()
                .putBoolean("night_mode", nightMode)
                .apply();
        recreate();
    }

    private void takeScreenshot() {
        // Get WebView bitmap
        webView.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(webView.getDrawingCache());
        webView.setDrawingCacheEnabled(false);

        // Save to file
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String filename = "BK333_" + timeStamp + ".png";

        try {
            java.io.FileOutputStream fos = openFileOutput(filename, Context.MODE_PRIVATE);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            // Make visible in gallery
            java.io.File file = new java.io.File(getFilesDir(), filename);
            Intent mediaScan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScan.setData(Uri.fromFile(file));
            sendBroadcast(mediaScan);

            showSnackbar("📸 Screenshot saved: " + filename);
        } catch (Exception e) {
            showSnackbar("❌ Screenshot failed: " + e.getMessage());
        }
    }

    private void printPage() {
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter("BK333_Page");
        PrintJob job = printManager.print("BK333 Page", adapter, new PrintAttributes.Builder().build());
        showSnackbar("🖨️ Sending to print...");
    }

    private void showSettingsDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        SwitchMaterial swProxy = view.findViewById(R.id.swProxyMode);
        SwitchMaterial swNight = view.findViewById(R.id.swNightMode);
        SwitchMaterial swDesktopUA = view.findViewById(R.id.swDesktopUA);

        swProxy.setChecked(useProxy);
        swNight.setChecked(nightMode);
        swDesktopUA.setChecked(true);

        new MaterialAlertDialogBuilder(this)
                .setTitle("⚙️ Settings")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {
                    useProxy = swProxy.isChecked();
                    nightMode = swNight.isChecked();
                    getSharedPreferences("settings", MODE_PRIVATE)
                            .edit()
                            .putBoolean("night_mode", nightMode)
                            .apply();
                    if (swNight.isChecked() != nightMode) {
                        recreate();
                    }
                    showSnackbar("✅ Settings saved");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── WebViewClient ───

    private class BK333WebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Handle special schemes
            if (url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("mailto:")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Log.w(TAG, "Could not open: " + url);
                }
                return true;
            }

            // For bk333 domains - always load in app
            if (url.contains("bk333") || url.contains("bk33") || url.contains("bk33")) {
                loadUrl(url);
                return true;
            }

            // For payment URLs - open in browser
            if (url.contains("bkash") || url.contains("nagad") || url.contains("rocket")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    loadUrl(url);
                }
                return true;
            }

            // Default: let WebView handle it
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            isLoading = true;
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            urlTextView.setText(url);
            urlInput.setText(url);
            currentUrl = url;
            updateStatus();

            // Reset timeout
            handler.removeCallbacksAndMessages(null);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            isLoading = false;
            swipeRefresh.setRefreshing(false);
            progressBar.setVisibility(View.GONE);
            isLoadingFromHistory = false;

            // Inject bypass script
            injectBypassScript();

            // Update navigation
            updateNavButtons();
            updateStatus();

            Log.d(TAG, "Page loaded: " + url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                progressBar.setVisibility(View.GONE);
                String msg = "Error: " + error.getDescription();
                statusText.setText("❌ " + msg);
                Log.e(TAG, msg);

                // Auto-try next proxy
                if (useProxy) {
                    handler.postDelayed(() -> {
                        cycleProxy();
                        loadUrl(currentUrl);
                    }, 1000);
                }
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // Accept all SSL certificates (needed for some proxy sites)
            handler.proceed();
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
        }
    }

    // ─── WebChromeClient ───

    private class BK333WebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            progressBar.setProgress(newProgress);
            if (newProgress == 100) {
                progressBar.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            if (title != null && !title.isEmpty()) {
                statusText.setText(title);
            }
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
            // Handle popup windows
            WebView newWebView = new WebView(MainActivity.this);
            WebSettings settings = newWebView.getSettings();
            settings.setJavaScriptEnabled(true);

            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(newWebView);
            resultMsg.sendToTarget();

            return true;
        }

        @Override
        public void onCloseWindow(WebView window) {
            super.onCloseWindow(window);
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle("BK333")
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> result.confirm())
                    .setOnDismissListener(d -> result.confirm())
                    .show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle("BK333")
                    .setMessage(message)
                    .setPositiveButton("Yes", (d, w) -> result.confirm())
                    .setNegativeButton("No", (d, w) -> result.cancel())
                    .show();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
            // For simplicity, return default value
            result.confirm(defaultValue);
            return true;
        }
    }

    private void updateNavButtons() {
        Menu menu = bottomNav.getMenu();
        menu.findItem(R.id.nav_back).setEnabled(!historyStack.isEmpty() || webView.canGoBack());
        menu.findItem(R.id.nav_forward).setEnabled(webView.canGoForward());
    }

    // ─── History Item ───

    private static class HistoryItem {
        String url;
        Date date;

        HistoryItem(String url, Date date) {
            this.url = url;
            this.date = date;
        }
    }

    // ─── Proxy Manager ───

    public static class ProxyManager {
        private Context context;

        public ProxyManager(Context context) {
            this.context = context;
        }

        @Nullable
        public String fetchViaProxy(String targetUrl, String proxyUrl) {
            try {
                String fullUrl = proxyUrl + Uri.encode(targetUrl);
                URL url = new URL(fullUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");

                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream is = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    return baos.toString("UTF-8");
                }
            } catch (Exception e) {
                Log.e(TAG, "Proxy fetch failed: " + e.getMessage());
            }
            return null;
        }
    }

    // ─── Lifecycle ───

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Back button = go back in WebView
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            // Ask before exit
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Exit BK333 Viewer?")
                    .setMessage("Do you want to exit the app?")
                    .setPositiveButton("Exit", (d, w) -> {
                        finishAffinity();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        }

        // Volume keys for proxy switching
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && useProxy) {
            cycleProxy();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && useProxy) {
            cycleProxy();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
        outState.putString("currentUrl", currentUrl);
        outState.putInt("proxyIndex", currentProxyIndex);
        outState.putBoolean("useProxy", useProxy);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
        currentUrl = savedInstanceState.getString("currentUrl", HOME_URL);
        currentProxyIndex = savedInstanceState.getInt("proxyIndex", 0);
        useProxy = savedInstanceState.getBoolean("useProxy", true);
        updateStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        webView.pauseTimers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }
}
