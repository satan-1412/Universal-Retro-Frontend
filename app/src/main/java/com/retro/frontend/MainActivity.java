package com.retro.frontend;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.Editable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private FrameLayout rootLayout;
    public WebView webView;
    private GamepadView gamepadView;
    private long backPressedTime = 0;
    public SharedPreferences prefs;

    // 当前运行的引擎模式: 0=空闲, 1=Web网页, 2=本地Flash, 3=本地Java
    public int currentEngineMode = 0;
    
    // 独立设置参数
    public int inputBgColor = Color.WHITE;
    public int inputTextColor = Color.BLACK;
    public int screenOrientationMode = 0; // 0=横屏, 1=竖屏, 2=自动
    public int webUAMode = 0; // Web专属: 0=手机, 1=电脑, 2=平板
    public int flashCoreMode = 0; // Flash专属: 0=Ruffle(WASM), 1=LightSpark
    public int javaCoreMode = 0; // Java专属: 0=KEmulator, 1=CheerpJ
    
    // 用于本地流媒体拦截的 URI
    private Uri pendingLocalGameUri = null;

    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String UA_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String UA_TABLET = "Mozilla/5.0 (iPad; CPU OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        inputBgColor = prefs.getInt("inputBgColor", Color.WHITE);
        inputTextColor = prefs.getInt("inputTextColor", Color.BLACK);
        screenOrientationMode = prefs.getInt("screenOrientation", 0);
        webUAMode = prefs.getInt("webUAMode", 0);
        flashCoreMode = prefs.getInt("flashCoreMode", 0);
        javaCoreMode = prefs.getInt("javaCoreMode", 0);
        
        applyScreenOrientation();

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#111111"));
        setContentView(rootLayout);

        setupWebView();
        showStartupSelector();
    }

    public void applyScreenOrientation() {
        if (screenOrientationMode == 0) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        else if (screenOrientationMode == 1) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }

    public void setupImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
    }

    // ================= 3模式启动器 =================
    public void showStartupSelector() {
        currentEngineMode = 0;
        setupImmersive();
        rootLayout.removeAllViews();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        
        TextView title = new TextView(this);
        title.setText("选择游戏核心");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28f);
        title.setPadding(0, 0, 0, 50);
        layout.addView(title);

        layout.addView(createBigButton("🌐 在线 Web/H5/Flash 游戏", v -> {
            String lastUrl = prefs.getString("lastUrl", "");
            showUrlInputDialog(lastUrl);
        }));
        layout.addView(createBigButton("🎞️ 本地 Flash (.swf) 播放器", v -> {
            currentEngineMode = 2; loadLocalFile("*/*", 101);
        }));
        layout.addView(createBigButton("☕ 本地 Java (.jar) 模拟器", v -> {
            currentEngineMode = 3; loadLocalFile("*/*", 102);
        }));

        rootLayout.addView(layout);
    }

    private Button createBigButton(String text, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text); btn.setTextColor(Color.WHITE); btn.setTextSize(18f);
        btn.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(800, 150);
        params.setMargins(0, 20, 0, 20); btn.setLayoutParams(params);
        btn.setOnClickListener(listener); return btn;
    }

    private void loadLocalFile(String type, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        startActivityForResult(intent, requestCode);
        Toast.makeText(this, "请选择本地游戏文件", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            pendingLocalGameUri = data.getData();
            if (requestCode == 101) { 
                currentEngineMode = 2;
                startGameContainer("https://app.local/flash_player.html"); 
            } else if (requestCode == 102) { 
                currentEngineMode = 3;
                startGameContainer("https://app.local/java_player.html"); 
            }
        }
    }

    // ================= URL 输入弹窗 =================
    public void showUrlInputDialog(String defaultUrl) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.argb(200, 0, 0, 0));

        final EditText urlInput = new EditText(this);
        urlInput.setText(defaultUrl); urlInput.setTextColor(inputTextColor);
        urlInput.setHint("输入网址..."); urlInput.setHintTextColor(Color.GRAY);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(inputBgColor); bg.setCornerRadius(15f); urlInput.setBackground(bg);
        urlInput.setPadding(40, 40, 40, 40);
        layout.addView(urlInput, new LinearLayout.LayoutParams(900, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button goBtn = new Button(this);
        goBtn.setText("🚀 载入网页引擎"); goBtn.setBackgroundColor(Color.parseColor("#4CAF50")); goBtn.setTextColor(Color.WHITE);
        goBtn.setOnClickListener(v -> {
            String url = urlInput.getText().toString();
            if (url.isEmpty()) return;
            if (!url.startsWith("http")) url = "https://" + url;
            prefs.edit().putString("lastUrl", url).apply(); 
            dialog.dismiss();
            currentEngineMode = 1;
            startGameContainer(url);
        });
        
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(900, 130); bp.setMargins(0, 40, 0, 0);
        layout.addView(goBtn, bp);
        
        Button cancelBtn = new Button(this);
        cancelBtn.setText("🔙 返回"); cancelBtn.setBackgroundColor(Color.parseColor("#777777")); cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        layout.addView(cancelBtn, bp);
        dialog.setContentView(layout); dialog.show();
    }

    // ================= 核心：硬件加速与拦截引擎 =================
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        // 允许 WebAssembly 运行必备 (禁止媒体播放必须由用户点击触发)
        settings.setMediaPlaybackRequiresUserGesture(false); 

        // ================= 动态性能自适应引擎 =================
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { 
            // 安卓 8.0 (API 26) 及以上现代机型：火力全开
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE); // 本地直读不缓存
        } else {
            // 安卓 5/6/7 老爷机：防 OOM 闪退保护模式
            // 老旧 WebView 的硬件加速极易导致白屏和内存溢出，强制降级为软件渲染保障不死机
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }
        // ======================================================

        webView.setBackgroundColor(Color.BLACK);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                // 1. 核心路由：将网页或游戏对 Flash 核心的请求，强行重定向到我们刚刚打包进 APK 的离线文件！
                if (url.startsWith("https://app.local/ruffle/")) {
                    String assetPath = url.replace("https://app.local/", "");
                    try {
                        InputStream is = getAssets().open(assetPath);
                        String mime = "application/javascript";
                        // WebAssembly 对 MimeType 要求极其严格，必须是 application/wasm，否则核心崩溃
                        if (assetPath.endsWith(".wasm")) mime = "application/wasm"; 
                        else if (assetPath.endsWith(".json")) mime = "application/json";
                        return new WebResourceResponse(mime, "UTF-8", is);
                    } catch (Exception e) { e.printStackTrace(); }
                }

                // 2. 本地流媒体拦截：喂给浏览器大文件（几十MB的SWF不再闪退）
                if (url.equals("https://app.local/stream_game_data") && pendingLocalGameUri != null) {
                    try {
                        InputStream is = getContentResolver().openInputStream(pendingLocalGameUri);
                        return new WebResourceResponse("application/octet-stream", "UTF-8", is);
                    } catch (Exception e) { e.printStackTrace(); }
                }
                
                // 3. 自动生成本地 Flash 容器页面 (注意这里改成了调用离线本地核心)
                if (url.equals("https://app.local/flash_player.html")) {
                    String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>" +
                            "<style>body,html{margin:0;padding:0;width:100%;height:100%;background:black;overflow:hidden;}</style>" +
                            "<script src='https://app.local/ruffle/ruffle.js'></script></head><body>" + // <-- 离线极速读取
                            "<div id='ruffle-container' style='width:100%; height:100%;'></div>" +
                            "<script>" +
                            "window.RufflePlayer = window.RufflePlayer || {};" +
                            "window.addEventListener('load', () => {" +
                            "  const ruffle = window.RufflePlayer.newest();" +
                            "  const player = ruffle.createPlayer();" +
                            "  document.getElementById('ruffle-container').appendChild(player);" +
                            "  player.style.width = '100%'; player.style.height = '100%';" +
                            "  player.load('https://app.local/stream_game_data');" +
                            "});" +
                            "</script></body></html>";
                    return new WebResourceResponse("text/html", "UTF-8", new java.io.ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                }

                // 4. 自动生成本地 Java (J2ME) 容器页面
                if (url.equals("https://app.local/java_player.html")) {
                    String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                            "<style>body{margin:0;background:black;color:white;display:flex;justify-content:center;align-items:center;height:100vh;flex-direction:column;}</style></head><body>" +
                            "<h2>☕ Java/J2ME Core</h2><p>流媒体通道已就绪。</p><p>正在等待 Java 核心对接...</p>" +
                            "</body></html>";
                    return new WebResourceResponse("text/html", "UTF-8", new java.io.ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                }

                return super.shouldInterceptRequest(view, request);
            }

            // 网页 Flash 注入器 (调用离线核心)
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (currentEngineMode == 1) { 
                    String js = "javascript:(function() {" +
                        "if(window.injected)return; window.injected=true;" +
                        "var script = document.createElement('script');" +
                        "script.src = 'https://app.local/ruffle/ruffle.js';" + // <-- 离线极速读取
                        "document.head.appendChild(script);" +
                        "window.RufflePlayer = window.RufflePlayer || {};" +
                        "window.RufflePlayer.config = { 'autoplay': 'on', 'unmuteOverlay': 'hidden' };" +
                        "var frames = document.getElementsByTagName('iframe');" +
                        "for(var i=0; i<frames.length; i++) {" +
                        "   try { frames[i].contentWindow.document.head.appendChild(script.cloneNode(true)); } catch(e){}" +
                        "}})()";
                    view.evaluateJavascript(js, null);
                }
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient());
    }


    private void startGameContainer(String url) {
        rootLayout.removeAllViews();
        // 如果是 Web 模式才应用 UA
        if (currentEngineMode == 1) applyWebUASettings(); 
        
        webView.loadUrl(url);
        rootLayout.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        if (gamepadView == null) gamepadView = new GamepadView(this, webView);
        gamepadView.isPureTouchMode = false; // 启动时重置为手柄模式
        rootLayout.addView(gamepadView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setupImmersive();
    }

    public void applyWebUASettings() {
        WebSettings settings = webView.getSettings();
        if (webUAMode == 1) settings.setUserAgentString(UA_PC);
        else if (webUAMode == 2) settings.setUserAgentString(UA_TABLET);
        else settings.setUserAgentString(UA_MOBILE);
    }

    @Override
    public void onBackPressed() {
        if (gamepadView != null && gamepadView.isEditMode) {
            Toast.makeText(this, "请先保存按键配置", Toast.LENGTH_SHORT).show(); return;
        }
        long now = System.currentTimeMillis();
        if (now - backPressedTime < 2000) { super.onBackPressed(); } 
        else { Toast.makeText(this, "再按一次退出 App", Toast.LENGTH_SHORT).show(); backPressedTime = now; }
    }

    public void toggleWebTheme() {
        isDarkMode = !isDarkMode;
        String js = isDarkMode ? "document.body.style.filter = 'invert(1) hue-rotate(180deg)';" : "document.body.style.filter = 'none';";
        webView.evaluateJavascript(js, null);
    }

    // ================= 极低延迟虚拟按键引擎 =================
    public static class GamepadView extends View {
        private MainActivity activity;
        private final WebView targetEngine;
        private final Paint paintBtn = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint();
        private final List<VirtualButton> buttons = new ArrayList<>();
        
        public boolean isEditMode = false;
        private VirtualButton draggedButton = null;
        private float downX, downY;
        private Vibrator vibrator;

        private float menuX = 20, menuY = 20;
        private final RectF menuRect = new RectF();
        private boolean isDraggingMenu = false;
        private boolean isMenuDown = false;

        public boolean isMouseMode = false;
        public boolean isPureTouchMode = false; 
        private boolean isDraggingJoy = false; 
        public float mouseX = 500, mouseY = 500;
        private float joyBaseX = 250, joyBaseY = 700, joyKnobX = 250, joyKnobY = 700;
        private float joyRadius = 150;
        private int joyPointerId = -1;
        private Paint mousePaint = new Paint();

        public GamepadView(MainActivity context, WebView webView) {
            super(context);
            this.activity = context; this.targetEngine = webView;
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            paintText.setTextAlign(Paint.Align.CENTER); paintText.setTypeface(Typeface.DEFAULT_BOLD);
            dashPaint.setStyle(Paint.Style.STROKE); dashPaint.setStrokeWidth(4f); dashPaint.setColor(Color.YELLOW);
            mousePaint.setColor(Color.RED); mousePaint.setShadowLayer(5, 0, 0, Color.WHITE);
            loadConfig();
        }

        private int getKeyCode(String k) {
            if(k.equals("UP")) return KeyEvent.KEYCODE_DPAD_UP;
            if(k.equals("DOWN")) return KeyEvent.KEYCODE_DPAD_DOWN;
            if(k.equals("LEFT")) return KeyEvent.KEYCODE_DPAD_LEFT;
            if(k.equals("RIGHT")) return KeyEvent.KEYCODE_DPAD_RIGHT;
            if(k.equals("SPACE")) return KeyEvent.KEYCODE_SPACE;
            if(k.equals("ENTER")||k.equals("RETURN")) return KeyEvent.KEYCODE_ENTER;
            if(k.equals("ESC")||k.equals("ESCAPE")) return KeyEvent.KEYCODE_ESCAPE;
            if(k.equals("SHIFT")) return KeyEvent.KEYCODE_SHIFT_LEFT;
            if(k.equals("CTRL")) return KeyEvent.KEYCODE_CTRL_LEFT;
            if(k.equals("ALT")) return KeyEvent.KEYCODE_ALT_LEFT;
            if(k.length()==1) {
                char c = k.charAt(0);
                if(c>='A' && c<='Z') return KeyEvent.KEYCODE_A + (c-'A');
                if(c>='0' && c<='9') return KeyEvent.KEYCODE_0 + (c-'0');
            }
            return KeyEvent.KEYCODE_UNKNOWN;
        }

        private void injectNativeKey(String keyString, boolean isDown) {
            if (keyString == null || keyString.isEmpty()) return;
            int action = isDown ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
            for (String key : keyString.split("\\+")) {
                int code = getKeyCode(key.trim().toUpperCase());
                if (code != KeyEvent.KEYCODE_UNKNOWN) {
                    final KeyEvent event = new KeyEvent(action, code);
                    activity.runOnUiThread(() -> targetEngine.dispatchKeyEvent(event));
                }
            }
        }
        
        private void injectNativeTouch(boolean isDown) {
            long time = SystemClock.uptimeMillis();
            int action = isDown ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP;
            MotionEvent ev = MotionEvent.obtain(time, time, action, mouseX, mouseY, 0);
            activity.runOnUiThread(() -> targetEngine.dispatchTouchEvent(ev));
            ev.recycle();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            if (isPureTouchMode && !isEditMode) {
                menuRect.set(menuX, menuY, menuX + 150, menuY + 150);
                paintBtn.setColor(Color.parseColor("#444444")); paintBtn.setAlpha(150);
                canvas.drawRoundRect(menuRect, 30, 30, paintBtn);
                paintText.setColor(Color.WHITE); paintText.setTextSize(50); paintText.clearShadowLayer();
                canvas.drawText("⚙️", menuRect.centerX(), menuRect.centerY() + 18, paintText);
                return; 
            }

            menuRect.set(menuX, menuY, menuX + 150, menuY + 150);
            paintBtn.setColor(Color.parseColor("#444444")); paintBtn.setAlpha(150); canvas.drawRoundRect(menuRect, 30, 30, paintBtn);
            paintText.setColor(Color.WHITE); paintText.setTextSize(50); paintText.clearShadowLayer();
            canvas.drawText("⚙️", menuRect.centerX(), menuRect.centerY() + 18, paintText);

            if (isMouseMode) {
                canvas.drawCircle(mouseX, mouseY, 15, mousePaint);
                paintBtn.setColor(Color.parseColor("#333333")); paintBtn.setAlpha(100); canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
                paintBtn.setColor(Color.WHITE); paintBtn.setAlpha(150); canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.4f, paintBtn);
                if (isEditMode) canvas.drawCircle(joyBaseX, joyBaseY, joyRadius * 1.5f, dashPaint);
            }

            for (VirtualButton btn : buttons) {
                int currentAlpha = isEditMode ? 180 : (btn.isPressed ? 255 : btn.alpha);
                int c = btn.isPressed ? btn.pressedColor : btn.color;
                paintBtn.setColor(c); paintBtn.setAlpha(currentAlpha);
                RadialGradient gradient = new RadialGradient(btn.cx - btn.radius * 0.3f, btn.cy - btn.radius * 0.3f, btn.radius * 1.2f, c, Color.BLACK, Shader.TileMode.CLAMP);
                paintBtn.setShader(gradient);
                if (btn.shape == 0) canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
                else canvas.drawRoundRect(new RectF(btn.cx - btn.radius, btn.cy - btn.radius, btn.cx + btn.radius, btn.cy + btn.radius), 20, 20, paintBtn);
                paintBtn.setShader(null);

                paintText.setColor(btn.textColor); paintText.setAlpha(currentAlpha); paintText.setTextSize(btn.radius * 0.6f);
                float textOffset = (paintText.descent() - paintText.ascent()) / 2 - paintText.descent();
                canvas.drawText(btn.id, btn.cx, btn.cy + textOffset, paintText);
                if (isEditMode) canvas.drawCircle(btn.cx, btn.cy, btn.hitboxRadius, dashPaint);
            }
            
            if (isEditMode) {
                paintText.setTextSize(40); paintText.setColor(Color.GREEN); paintText.setShadowLayer(5, 0, 0, Color.BLACK);
                canvas.drawText("【编辑模式】", getWidth()/2f, 80, paintText); paintText.clearShadowLayer();
                paintBtn.setColor(Color.RED); paintBtn.setAlpha(200);
                canvas.drawRoundRect(new RectF(getWidth()/2f - 200, 120, getWidth()/2f + 200, 220), 20, 20, paintBtn);
                paintText.setColor(Color.WHITE); paintText.setTextSize(45); canvas.drawText("💾 保存并退出", getWidth()/2f, 185, paintText);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked(); int actionIndex = event.getActionIndex();

            if (isEditMode) { handleEditTouch(event, action, actionIndex); return true; }

            if (action == MotionEvent.ACTION_DOWN && menuRect.contains(event.getX(actionIndex), event.getY(actionIndex))) {
                isDraggingMenu = true; isMenuDown = true; downX = event.getX(); downY = event.getY(); return true;
            }
            if (action == MotionEvent.ACTION_MOVE && isDraggingMenu) {
                if (Math.hypot(event.getX(actionIndex) - downX, event.getY(actionIndex) - downY) > 20) isMenuDown = false;
                menuX = event.getX(actionIndex) - 75; menuY = event.getY(actionIndex) - 75; invalidate(); return true;
            }
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && isDraggingMenu) {
                if (isMenuDown) showFloatingToolMenu();
                isDraggingMenu = false; return true;
            }

            if (isPureTouchMode) return false;

            if (isMouseMode) {
                boolean joyTouched = false;
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                    if (event.getPointerId(actionIndex) == joyPointerId) { joyPointerId = -1; joyKnobX = joyBaseX; joyKnobY = joyBaseY; }
                }
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && i == actionIndex) continue;
                    float px = event.getX(i), py = event.getY(i);
                    if (px < getWidth() / 2f) { 
                        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                            if (Math.hypot(px - joyBaseX, py - joyBaseY) < joyRadius * 1.5f) joyPointerId = event.getPointerId(i);
                        }
                        if (event.getPointerId(i) == joyPointerId) {
                            joyTouched = true; float dx = px - joyBaseX, dy = py - joyBaseY; float dist = (float) Math.hypot(dx, dy);
                            if (dist > joyRadius) { joyKnobX = joyBaseX + (dx/dist)*joyRadius; joyKnobY = joyBaseY + (dy/dist)*joyRadius; }
                            else { joyKnobX = px; joyKnobY = py; }
                            mouseX += (dx / joyRadius) * 20f; mouseY += (dy / joyRadius) * 20f;
                            mouseX = Math.max(0, Math.min(mouseX, getWidth())); mouseY = Math.max(0, Math.min(mouseY, getHeight()));
                        }
                    }
                }
                if (!joyTouched) { joyPointerId = -1; joyKnobX = joyBaseX; joyKnobY = joyBaseY; }
            }

            for (VirtualButton btn : buttons) {
                boolean isTouchedNow = false;
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (event.getPointerId(i) == joyPointerId) continue;
                    if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) && i == actionIndex) continue;
                    if (Math.hypot(event.getX(i) - btn.cx, event.getY(i) - btn.cy) < btn.hitboxRadius) isTouchedNow = true;
                }

                if (!btn.isPressed && isTouchedNow) {
                    btn.isPressed = true; if (vibrator != null) vibrator.vibrate(20);
                    if (btn.id.equals("L-Click") || btn.id.equals("R-Click")) injectNativeTouch(true);
                    else if (btn.isTurbo) btn.startTurbo(this); else injectNativeKey(btn.keyMapStr, true);
                } else if (btn.isPressed && !isTouchedNow) {
                    btn.isPressed = false;
                    if (btn.id.equals("L-Click") || btn.id.equals("R-Click")) injectNativeTouch(false);
                    else { if (btn.isTurbo) btn.stopTurbo(); else injectNativeKey(btn.keyMapStr, false); }
                }
            }
            invalidate(); return true;
        }

        private void handleEditTouch(MotionEvent event, int action, int actionIndex) {
            float x = event.getX(actionIndex), y = event.getY(actionIndex);
            if (action == MotionEvent.ACTION_DOWN) {
                if (x > getWidth()/2f - 200 && x < getWidth()/2f + 200 && y > 120 && y < 220) {
                    isEditMode = false; activity.prefs.edit().putString("layout", serializeConfig()).apply();
                    Toast.makeText(getContext(), "布局已保存！", Toast.LENGTH_SHORT).show();
                    activity.setupImmersive(); invalidate(); return;
                }
                downX = x; downY = y;
                if (isMouseMode && Math.hypot(x - joyBaseX, y - joyBaseY) < joyRadius * 1.5f) { isDraggingJoy = true; return; }
                for (int i = buttons.size() - 1; i >= 0; i--) {
                    if (Math.hypot(x - buttons.get(i).cx, y - buttons.get(i).cy) < buttons.get(i).radius * 1.5f) { draggedButton = buttons.get(i); break; }
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (isDraggingJoy) { joyBaseX = x; joyBaseY = y; joyKnobX = x; joyKnobY = y; invalidate(); } 
                else if (draggedButton != null) { draggedButton.cx = x; draggedButton.cy = y; invalidate(); }
            } else if (action == MotionEvent.ACTION_UP) {
                if (isDraggingJoy) { isDraggingJoy = false; activity.prefs.edit().putString("layout", serializeConfig()).apply(); } 
                else if (draggedButton != null && Math.hypot(x - downX, y - downY) < 10) { showButtonSettingsDialog(draggedButton); }
                draggedButton = null;
            }
        }

        // ================= 智能核心独立设置菜单 =================
        private void showFloatingToolMenu() {
            Dialog dialog = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
            ScrollView scroll = new ScrollView(getContext());
            LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(60, 60, 60, 60);
            
            TextView title = new TextView(getContext()); title.setText("⚙️ 全局设置菜单"); title.setTextColor(Color.WHITE); title.setTextSize(22f); title.setPadding(0, 0, 0, 30); layout.addView(title);
            layout.addView(createMenuButton("🛠️ 编辑虚拟按键布局", v -> { isEditMode = true; dialog.dismiss(); invalidate(); }));
            layout.addView(createMenuButton("➕ 新增自定义按键", v -> { buttons.add(new VirtualButton("新键", getWidth()/2f, getHeight()/2f, 80, "Space")); isEditMode = true; dialog.dismiss(); invalidate(); }));
            layout.addView(createMenuButton(isMouseMode ? "🖱️ 关闭鼠标摇杆" : "🖱️ 开启鼠标摇杆", v -> { isMouseMode = !isMouseMode; activity.prefs.edit().putBoolean("mouseMode", isMouseMode).apply(); dialog.dismiss(); invalidate(); }));
            layout.addView(createMenuButton(isPureTouchMode ? "🎮 退出触屏模式" : "👆 开启纯触屏模式 (隐去按键)", v -> { isPureTouchMode = !isPureTouchMode; dialog.dismiss(); invalidate(); }));

            // 根据当前运行的引擎，动态显示不同的专属设置区！
            if (activity.currentEngineMode == 1) {
                // Web 模式专属
                String[] uas = {"手机模式", "电脑模式 (PC版网页)", "平板模式 (大屏网页)"};
                layout.addView(createMenuButton("💻 【Web设置】切换标识: " + uas[activity.webUAMode], v -> {
                    activity.webUAMode = (activity.webUAMode + 1) % 3;
                    activity.prefs.edit().putInt("webUAMode", activity.webUAMode).apply();
                    activity.applyWebUASettings(); activity.webView.reload(); dialog.dismiss();
                    Toast.makeText(getContext(), "已切换 UA", Toast.LENGTH_SHORT).show();
                }));
            } else if (activity.currentEngineMode == 2) {
                // Flash 模式专属
                String[] flashCores = {"Ruffle (WebAssembly高速)", "LightSpark (备用兼容)"};
                layout.addView(createMenuButton("⚡ 【Flash设置】当前核心: " + flashCores[activity.flashCoreMode], v -> {
                    activity.flashCoreMode = (activity.flashCoreMode + 1) % 2;
                    activity.prefs.edit().putInt("flashCoreMode", activity.flashCoreMode).apply(); dialog.dismiss();
                    Toast.makeText(getContext(), "核心已切换，下次载入 Flash 生效", Toast.LENGTH_SHORT).show();
                }));
            } else if (activity.currentEngineMode == 3) {
                // Java 模式专属
                String[] javaCores = {"KEmulator (基础2D)", "CheerpJ (完整JVM)"};
                layout.addView(createMenuButton("☕ 【Java设置】当前引擎: " + javaCores[activity.javaCoreMode], v -> {
                    activity.javaCoreMode = (activity.javaCoreMode + 1) % 2;
                    activity.prefs.edit().putInt("javaCoreMode", activity.javaCoreMode).apply(); dialog.dismiss();
                    Toast.makeText(getContext(), "引擎已切换，下次载入 JAR 生效", Toast.LENGTH_SHORT).show();
                }));
            }

            layout.addView(createMenuButton("📱 切换横竖屏", v -> {
                activity.screenOrientationMode = (activity.screenOrientationMode + 1) % 3;
                activity.prefs.edit().putInt("screenOrientation", activity.screenOrientationMode).apply();
                activity.applyScreenOrientation(); dialog.dismiss();
            }));

            layout.addView(createMenuButton("🏠 返回主菜单 (核心选择)", v -> { dialog.dismiss(); activity.showStartupSelector(); }));

            scroll.addView(layout); dialog.setContentView(scroll);
            android.view.Window window = dialog.getWindow(); if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (int)(getHeight() * 0.85));
            dialog.show();
        }

        private Button createMenuButton(String text, OnClickListener listener) {
            Button btn = new Button(getContext()); btn.setText(text); btn.setTextColor(Color.WHITE); btn.setBackgroundColor(Color.parseColor("#333333"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 130); lp.setMargins(0, 10, 0, 10); btn.setLayoutParams(lp);
            btn.setOnClickListener(listener); return btn;
        }

        private void showButtonSettingsDialog(VirtualButton btn) {
            Dialog dialog = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
            LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(60, 40, 60, 60);

            EditText nameInput = new EditText(getContext()); nameInput.setText(btn.id); nameInput.setTextColor(Color.WHITE); layout.addView(nameInput);
            EditText keyInput = new EditText(getContext()); keyInput.setText(btn.keyMapStr); keyInput.setTextColor(Color.WHITE); layout.addView(keyInput);
            
            Button saveBtn = new Button(getContext()); saveBtn.setText("💾 保存修改"); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
            saveBtn.setOnClickListener(v -> {
                btn.id = nameInput.getText().toString(); btn.keyMapStr = keyInput.getText().toString();
                activity.prefs.edit().putString("layout", serializeConfig()).apply(); invalidate(); dialog.dismiss();
            }); layout.addView(saveBtn);
            
            Button delBtn = new Button(getContext()); delBtn.setText("🗑️ 删除此键"); delBtn.setBackgroundColor(Color.parseColor("#F44336"));
            delBtn.setOnClickListener(v -> { buttons.remove(btn); activity.prefs.edit().putString("layout", serializeConfig()).apply(); invalidate(); dialog.dismiss(); }); layout.addView(delBtn);

            dialog.setContentView(layout); dialog.show();
        }

        private String serializeConfig() {
            try {
                JSONArray array = new JSONArray();
                for (VirtualButton b : buttons) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", b.id); obj.put("cx", b.cx); obj.put("cy", b.cy); obj.put("radius", b.radius); 
                    obj.put("keyMap", b.keyMapStr); array.put(obj);
                }
                return array.toString();
            } catch (Exception e) { return "[]"; }
        }

        private void loadConfig() {
            String json = activity.prefs.getString("layout", "[]");
            isMouseMode = activity.prefs.getBoolean("mouseMode", false);
            if (json.equals("[]")) {
                buttons.add(new VirtualButton("UP", 200, 500, 80, "UP")); buttons.add(new VirtualButton("DOWN", 200, 800, 80, "DOWN"));
                buttons.add(new VirtualButton("LEFT", 50, 650, 80, "LEFT")); buttons.add(new VirtualButton("RIGHT", 350, 650, 80, "RIGHT"));
                buttons.add(new VirtualButton("A", 1600, 650, 90, "a")); buttons.add(new VirtualButton("B", 1800, 500, 90, "b"));
                buttons.add(new VirtualButton("L-Click", 1800, 800, 80, ""));
            } else {
                try {
                    JSONArray array = new JSONArray(json);
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        VirtualButton btn = new VirtualButton(o.optString("id"), (float)o.optDouble("cx"), (float)o.optDouble("cy"), (float)o.optDouble("radius"), o.optString("keyMap"));
                        buttons.add(btn);
                    }
                } catch (Exception e) {}
            }
        }

        public static class VirtualButton {
            public String id; public float cx, cy, radius, hitboxRadius;
            public String keyMapStr; public boolean isPressed = false;
            public int color = Color.GRAY, pressedColor = Color.WHITE, alpha = 150, shape = 0, textColor = Color.WHITE;
            public boolean isTurbo = false; public int turboInterval = 50;
            private volatile boolean turboRunning = false;
            private static final ExecutorService threadPool = Executors.newCachedThreadPool();

            public VirtualButton(String id, float cx, float cy, float radius, String keyMapStr) {
                this.id = id; this.cx = cx; this.cy = cy; this.radius = radius; this.keyMapStr = keyMapStr; this.hitboxRadius = radius * 1.5f;
            }

            public void startTurbo(GamepadView parentView) {
                if (turboRunning || keyMapStr == null || keyMapStr.isEmpty()) return;
                turboRunning = true;
                threadPool.execute(() -> {
                    while (turboRunning) {
                        try { parentView.injectNativeKey(keyMapStr, true); Thread.sleep(turboInterval); parentView.injectNativeKey(keyMapStr, false); Thread.sleep(turboInterval); } catch (InterruptedException e) { break; }
                    }
                });
            }
            public void stopTurbo() { turboRunning = false; }
        }
    }
}
