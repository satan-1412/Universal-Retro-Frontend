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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private FrameLayout rootLayout;
    private WebView webView;
    private GamepadView gamepadView;
    private long backPressedTime = 0;
    private boolean isDarkMode = false;
    private SharedPreferences prefs;

    // 自定义外观与模式
    public int inputBgColor = Color.WHITE;
    public int inputTextColor = Color.BLACK;
    public int screenOrientationMode = 0; // 0=横屏, 1=竖屏, 2=自动
    public int uaMode = 0; // 0=手机, 1=电脑, 2=平板

    // 预设 UA 字符串
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
        uaMode = prefs.getInt("uaMode", 0);
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
        layout.addView(createBigButton("🎞️ 本地 Flash (.swf) 播放器", v -> loadLocalCore("swf")));
        layout.addView(createBigButton("☕ 本地 Java (.jar) 模拟器", v -> loadLocalCore("jar")));

        rootLayout.addView(layout);
    }

    private Button createBigButton(String text, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(18f);
        btn.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(800, 150);
        params.setMargins(0, 20, 0, 20);
        btn.setLayoutParams(params);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void loadLocalCore(String type) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, type.equals("swf") ? 101 : 102);
        Toast.makeText(this, "请选择本地的 ." + type + " 游戏文件", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (requestCode == 101) { 
                loadSwfWithRuffle(uri); 
            } else if (requestCode == 102) { 
                Toast.makeText(this, "本地 Java 核心已预留，后续接入即可。", Toast.LENGTH_LONG).show();
            } else if (requestCode == 43 && gamepadView != null) {
                // 用于后续选皮肤
            }
        }
    }

    private void loadSwfWithRuffle(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            is.close();
            String base64Swf = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
            
            String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>" +
                    "<style>body, html { margin: 0; padding: 0; width: 100%; height: 100%; background: black; overflow: hidden; }</style>" +
                    "<script src='https://unpkg.com/@ruffle-rs/ruffle'></script></head><body>" +
                    "<div id='ruffle-container' style='width:100%; height:100%;'></div>" +
                    "<script>" +
                    "window.RufflePlayer = window.RufflePlayer || {};" +
                    "window.addEventListener('load', (event) => {" +
                    "  const ruffle = window.RufflePlayer.newest();" +
                    "  const player = ruffle.createPlayer();" +
                    "  const container = document.getElementById('ruffle-container');" +
                    "  container.appendChild(player);" +
                    "  player.style.width = '100%'; player.style.height = '100%';" +
                    "  player.load({data: Uint8Array.from(atob('" + base64Swf + "'), c => c.charCodeAt(0))});" +
                    "});" +
                    "</script></body></html>";
            
            rootLayout.removeAllViews();
            webView.loadDataWithBaseURL("https://localhost", html, "text/html", "UTF-8", null);
            rootLayout.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (gamepadView == null) gamepadView = new GamepadView(this, webView);
            rootLayout.addView(gamepadView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setupImmersive();
            Toast.makeText(this, "Ruffle 核心注入成功！即将运行本地 SWF！", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "文件读取失败，可能没有权限", Toast.LENGTH_SHORT).show(); }
    }

    // ================= URL 弹窗与预设颜色工具 =================
    public void showUrlInputDialog(String defaultUrl) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.argb(200, 0, 0, 0));

        final EditText urlInput = new EditText(this);
        urlInput.setText(defaultUrl);
        urlInput.setTextColor(inputTextColor);
        urlInput.setHint("输入网址 (会自动记忆)...");
        urlInput.setHintTextColor(Color.GRAY);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(inputBgColor);
        bg.setCornerRadius(15f);
        urlInput.setBackground(bg);
        urlInput.setPadding(40, 40, 40, 40);
        layout.addView(urlInput, new LinearLayout.LayoutParams(900, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button goBtn = new Button(this);
        goBtn.setText("🚀 载入网页引擎");
        goBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        goBtn.setTextColor(Color.WHITE);
        goBtn.setOnClickListener(v -> {
            String url = urlInput.getText().toString();
            if (url.isEmpty()) return;
            if (!url.startsWith("http")) url = "https://" + url;
            prefs.edit().putString("lastUrl", url).apply(); 
            dialog.dismiss();
            startGameContainer(url);
        });
        
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(900, 130);
        bp.setMargins(0, 40, 0, 0);
        layout.addView(goBtn, bp);
        
        Button cancelBtn = new Button(this);
        cancelBtn.setText("🔙 返回");
        cancelBtn.setBackgroundColor(Color.parseColor("#777777"));
        cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        layout.addView(cancelBtn, bp);

        dialog.setContentView(layout);
        dialog.show();
    }

    public HorizontalScrollView createColorPalette(EditText targetInput) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        String[] colors = {"#FFFFFF", "#000000", "#F44336", "#4CAF50", "#2196F3", "#FFEB3B", "#9C27B0", "#795548", "#9E9E9E"};
        for (String c : colors) {
            Button btn = new Button(this);
            btn.setBackgroundColor(Color.parseColor(c));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(80, 80);
            p.setMargins(10, 10, 10, 10);
            btn.setLayoutParams(p);
            btn.setOnClickListener(v -> targetInput.setText(c));
            layout.addView(btn);
        }
        scroll.addView(layout);
        return scroll;
    }

    private void startGameContainer(String url) {
        rootLayout.removeAllViews();
        applyUASettings(); // 应用 UA
        webView.loadUrl(url);
        rootLayout.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        if (gamepadView == null) gamepadView = new GamepadView(this, webView);
        rootLayout.addView(gamepadView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setupImmersive();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.setBackgroundColor(Color.BLACK);
        applyUASettings();
    }

    public void applyUASettings() {
        WebSettings settings = webView.getSettings();
        if (uaMode == 1) settings.setUserAgentString(UA_PC);
        else if (uaMode == 2) settings.setUserAgentString(UA_TABLET);
        else settings.setUserAgentString(UA_MOBILE);
    }

    @Override
    public void onBackPressed() {
        if (gamepadView != null && gamepadView.isEditMode) {
            Toast.makeText(this, "请先点击屏幕上方的【保存并退出编辑】", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - backPressedTime < 2000) {
            super.onBackPressed();
        } else {
            Toast.makeText(this, "再按一次退出 App", Toast.LENGTH_SHORT).show();
            backPressedTime = now;
        }
    }

    public void toggleWebTheme() {
        isDarkMode = !isDarkMode;
        String js = isDarkMode ? "document.body.style.filter = 'invert(1) hue-rotate(180deg)';" : "document.body.style.filter = 'none';";
        webView.evaluateJavascript(js, null);
        Toast.makeText(this, isDarkMode ? "已切换至护眼暗色模式" : "已恢复亮色模式", Toast.LENGTH_SHORT).show();
    }

    // ================= 核心：极低延迟虚拟按键引擎 =================
    public static class GamepadView extends View {
        private MainActivity activity;
        private final WebView targetEngine;
        private final Paint paintBtn = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint();
        private final List<VirtualButton> buttons = new ArrayList<>();
        private final SharedPreferences prefs;

        public boolean isEditMode = false;
        private VirtualButton draggedButton = null;
        private float downX, downY;
        private Vibrator vibrator;

        private float menuX = 20, menuY = 20;
        private final RectF menuRect = new RectF();
        private boolean isDraggingMenu = false;
        private boolean isMenuDown = false;

        // 虚拟鼠标引擎与纯触屏模式
        public boolean isMouseMode = false;
        public boolean isPureTouchMode = false; // 触屏模式变量
        private boolean isDraggingJoy = false;  // 摇杆拖拽变量
        public float mouseX = 500, mouseY = 500;
        private float joyBaseX = 250, joyBaseY = 700, joyKnobX = 250, joyKnobY = 700;
        private float joyRadius = 150;
        private int joyPointerId = -1;
        private Paint mousePaint = new Paint();

        public GamepadView(MainActivity context, WebView webView) {
            super(context);
            this.activity = context;
            this.targetEngine = webView;
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            prefs = context.getSharedPreferences("IkemenGamepad_Pro_V8", MODE_PRIVATE);
            
            paintText.setTextAlign(Paint.Align.CENTER);
            paintText.setTypeface(Typeface.DEFAULT_BOLD);
            dashPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setStrokeWidth(4f);
            dashPaint.setColor(Color.YELLOW);
            
            mousePaint.setColor(Color.RED);
            mousePaint.setShadowLayer(5, 0, 0, Color.WHITE);
            loadConfig();
        }

        // --- 核心改动：键值转换器 ---
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

        // --- 核心改动：物理级键盘注入 (无视游戏防作弊) ---
        private void injectNativeKey(String keyString, boolean isDown) {
            if (keyString == null || keyString.isEmpty()) return;
            int action = isDown ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
            for (String key : keyString.split("\\+")) {
                int code = getKeyCode(key.trim().toUpperCase());
                if (code != KeyEvent.KEYCODE_UNKNOWN) {
                    final KeyEvent event = new KeyEvent(action, code);
                    activity.runOnUiThread(() -> targetEngine.dispatchKeyEvent(event)); // 物理级穿透！
                }
            }
        }
        
        // --- 核心改动：物理级触摸模拟 (代替无效的 MouseEvent) ---
        private void injectNativeTouch(boolean isDown) {
            long time = SystemClock.uptimeMillis();
            int action = isDown ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP;
            // 直接向网页派发真实的系统级触摸事件，任何H5引擎必定响应
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
                return; // 拦截，只画齿轮，不画摇杆和按键
            }

            // 绘制悬浮菜单按钮
            menuRect.set(menuX, menuY, menuX + 150, menuY + 150);
            paintBtn.setColor(Color.parseColor("#444444"));
            paintBtn.setAlpha(150);
            canvas.drawRoundRect(menuRect, 30, 30, paintBtn);
            paintText.setColor(Color.WHITE);
            paintText.setTextSize(50); paintText.clearShadowLayer();
            canvas.drawText("⚙️", menuRect.centerX(), menuRect.centerY() + 18, paintText);

            // 绘制虚拟鼠标
            if (isMouseMode) {
                canvas.drawCircle(mouseX, mouseY, 15, mousePaint);
                paintBtn.setColor(Color.parseColor("#333333"));
                paintBtn.setAlpha(100);
                canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
                paintBtn.setColor(Color.WHITE);
                paintBtn.setAlpha(150);
                canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.4f, paintBtn);
                if (isEditMode) {
                    canvas.drawCircle(joyBaseX, joyBaseY, joyRadius * 1.5f, dashPaint);
                }
            }

            // 绘制所有按键
            for (VirtualButton btn : buttons) {
                int currentAlpha = isEditMode ? 180 : (btn.isPressed ? 255 : btn.alpha);
                
                if (btn.skinBitmap != null && !btn.skinBitmap.isRecycled()) {
                    paintBtn.setAlpha(currentAlpha);
                    RectF r = new RectF(btn.cx - btn.radius, btn.cy - btn.radius, btn.cx + btn.radius, btn.cy + btn.radius);
                    canvas.drawBitmap(btn.skinBitmap, null, r, paintBtn);
                } else {
                    int c = btn.isPressed ? btn.pressedColor : btn.color;
                    paintBtn.setColor(c);
                    paintBtn.setAlpha(currentAlpha);
                    RadialGradient gradient = new RadialGradient(btn.cx - btn.radius * 0.3f, btn.cy - btn.radius * 0.3f, btn.radius * 1.2f, c, Color.BLACK, Shader.TileMode.CLAMP);
                    paintBtn.setShader(gradient);
                    if (btn.shape == 0) canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
                    else canvas.drawRoundRect(new RectF(btn.cx - btn.radius, btn.cy - btn.radius, btn.cx + btn.radius, btn.cy + btn.radius), 20, 20, paintBtn);
                    paintBtn.setShader(null);
                }

                paintText.setColor(btn.textColor);
                paintText.setAlpha(currentAlpha);
                paintText.setTextSize(btn.radius * 0.6f);
                float textOffset = (paintText.descent() - paintText.ascent()) / 2 - paintText.descent();
                canvas.drawText(btn.id, btn.cx, btn.cy + textOffset, paintText);

                if (isEditMode) canvas.drawCircle(btn.cx, btn.cy, btn.hitboxRadius, dashPaint);
            }
            
            if (isEditMode) {
                paintText.setTextSize(40); paintText.setColor(Color.GREEN); paintText.setShadowLayer(5, 0, 0, Color.BLACK);
                canvas.drawText("【编辑模式】拖拽位置，轻触按键设置。点下方按钮保存。", getWidth()/2f, 80, paintText);
                paintText.clearShadowLayer();
                
                paintBtn.setColor(Color.RED); paintBtn.setAlpha(200);
                canvas.drawRoundRect(new RectF(getWidth()/2f - 200, 120, getWidth()/2f + 200, 220), 20, 20, paintBtn);
                paintText.setColor(Color.WHITE); paintText.setTextSize(45);
                canvas.drawText("💾 保存并退出", getWidth()/2f, 185, paintText);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int actionIndex = event.getActionIndex();

            if (isEditMode) {
                handleEditTouch(event, action, actionIndex);
                return true;
            }

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

            if (isPureTouchMode) return false; // 触屏模式拦截终止，放行所有原生手势给游戏

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
                            joyTouched = true;
                            float dx = px - joyBaseX, dy = py - joyBaseY;
                            float dist = (float) Math.hypot(dx, dy);
                            if (dist > joyRadius) { joyKnobX = joyBaseX + (dx/dist)*joyRadius; joyKnobY = joyBaseY + (dy/dist)*joyRadius; }
                            else { joyKnobX = px; joyKnobY = py; }
                            mouseX += (dx / joyRadius) * 20f; 
                            mouseY += (dy / joyRadius) * 20f;
                            mouseX = Math.max(0, Math.min(mouseX, getWidth()));
                            mouseY = Math.max(0, Math.min(mouseY, getHeight()));
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
                    btn.isPressed = true;
                    if (vibrator != null) vibrator.vibrate(20);
                    
                    if (btn.id.equals("L-Click")) injectNativeTouch(true);
                    else if (btn.id.equals("R-Click")) injectNativeTouch(true); // H5一般不认右键，统一派发点击
                    else if (btn.isTurbo) btn.startTurbo(this);
                    else injectNativeKey(btn.keyMapStr, true);
                    
                } else if (btn.isPressed && !isTouchedNow) {
                    btn.isPressed = false;
                    if (btn.id.equals("L-Click")) injectNativeTouch(false);
                    else if (btn.id.equals("R-Click")) injectNativeTouch(false);
                    else {
                        if (btn.isTurbo) btn.stopTurbo();
                        else injectNativeKey(btn.keyMapStr, false);
                    }
                }
            }
            invalidate();
            return true;
        }

        private void handleEditTouch(MotionEvent event, int action, int actionIndex) {
            float x = event.getX(actionIndex), y = event.getY(actionIndex);
            if (action == MotionEvent.ACTION_DOWN) {
                if (x > getWidth()/2f - 200 && x < getWidth()/2f + 200 && y > 120 && y < 220) {
                    isEditMode = false; saveConfig();
                    Toast.makeText(getContext(), "布局已保存！", Toast.LENGTH_SHORT).show();
                    activity.setupImmersive(); invalidate(); return;
                }
                downX = x; downY = y;
                if (isMouseMode && Math.hypot(x - joyBaseX, y - joyBaseY) < joyRadius * 1.5f) {
                    isDraggingJoy = true; return;
                }
                for (int i = buttons.size() - 1; i >= 0; i--) {
                    if (Math.hypot(x - buttons.get(i).cx, y - buttons.get(i).cy) < buttons.get(i).radius * 1.5f) {
                        draggedButton = buttons.get(i); break;
                    }
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (isDraggingJoy) {
                    joyBaseX = x; joyBaseY = y; joyKnobX = x; joyKnobY = y; invalidate();
                } else if (draggedButton != null) {
                    draggedButton.cx = x; draggedButton.cy = y; invalidate();
                }
            } else if (action == MotionEvent.ACTION_UP) {
                if (isDraggingJoy) {
                    isDraggingJoy = false; saveConfig();
                } else if (draggedButton != null && Math.hypot(x - downX, y - downY) < 10) {
                    showButtonSettingsDialog(draggedButton);
                }
                draggedButton = null;
            }
        }

        private void showFloatingToolMenu() {
            Dialog dialog = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
            ScrollView scroll = new ScrollView(getContext());
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(60, 60, 60, 60);
            
            TextView title = new TextView(getContext());
            title.setText("⚙️ 全局设置菜单"); title.setTextColor(Color.WHITE); title.setTextSize(22f); title.setPadding(0, 0, 0, 30);
            layout.addView(title);
            
            layout.addView(createMenuButton("🛠️ 编辑虚拟按键布局", v -> { isEditMode = true; dialog.dismiss(); invalidate(); }));
            layout.addView(createMenuButton("➕ 新增自定义按键", v -> { 
                buttons.add(new VirtualButton("新键", getWidth()/2f, getHeight()/2f, 80, "Space"));
                isEditMode = true; dialog.dismiss(); invalidate(); 
            }));
            layout.addView(createMenuButton(isMouseMode ? "🖱️ 关闭鼠标模式" : "🖱️ 开启鼠标指针模式", v -> { isMouseMode = !isMouseMode; saveConfig(); dialog.dismiss(); invalidate(); }));
            
            layout.addView(createMenuButton(isPureTouchMode ? "🎮 退出触屏模式 (恢复手柄)" : "👆 开启纯触屏模式 (隐去按键)", v -> { 
                isPureTouchMode = !isPureTouchMode; dialog.dismiss(); invalidate(); 
                Toast.makeText(getContext(), isPureTouchMode ? "已开启纯触屏模式" : "已恢复虚拟按键", Toast.LENGTH_SHORT).show();
            }));

            String[] uas = {"手机模式", "电脑模式 (PC版网页)", "平板模式 (大屏网页)"};
            layout.addView(createMenuButton("💻 切换浏览器标识 (当前: " + uas[activity.uaMode] + ")", v -> {
                activity.uaMode = (activity.uaMode + 1) % 3;
                activity.prefs.edit().putInt("uaMode", activity.uaMode).apply();
                activity.applyUASettings(); activity.webView.reload(); dialog.dismiss();
                Toast.makeText(getContext(), "已切换 UA，网页正在重新加载", Toast.LENGTH_SHORT).show();
            }));

            String[] oris = {"横屏", "竖屏", "自动识别"};
            layout.addView(createMenuButton("📱 切换屏幕方向 (当前: " + oris[activity.screenOrientationMode] + ")", v -> {
                activity.screenOrientationMode = (activity.screenOrientationMode + 1) % 3;
                activity.prefs.edit().putInt("screenOrientation", activity.screenOrientationMode).apply();
                activity.applyScreenOrientation(); dialog.dismiss();
                Toast.makeText(getContext(), "方向已切换", Toast.LENGTH_SHORT).show();
            }));

            layout.addView(createMenuButton("🌗 切换网页白天/黑夜滤镜", v -> { activity.toggleWebTheme(); dialog.dismiss(); }));
            layout.addView(createMenuButton("🌐 重新输入网址", v -> { dialog.dismiss(); activity.showUrlInputDialog(targetEngine.getUrl()); }));
            layout.addView(createMenuButton("🎨 UI 颜色与背景自定义", v -> { dialog.dismiss(); showInputBoxSettings(); }));
            layout.addView(createMenuButton("🏠 返回主菜单 (核心选择)", v -> { dialog.dismiss(); activity.showStartupSelector(); }));

            scroll.addView(layout); dialog.setContentView(scroll);
            android.view.Window window = dialog.getWindow();
            if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (int)(getHeight() * 0.85));
            dialog.show();
        }

        private void showInputBoxSettings() {
            Dialog dialog = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
            ScrollView scroll = new ScrollView(getContext());
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(60, 60, 60, 60);
            
            TextView tv1 = new TextView(getContext()); tv1.setText("输入框背景色"); tv1.setTextColor(Color.WHITE); layout.addView(tv1);
            EditText bgEt = createBlackTextEdit(String.format("#%06X", (0xFFFFFF & activity.inputBgColor)), ""); layout.addView(bgEt);
            layout.addView(activity.createColorPalette(bgEt));

            TextView tv2 = new TextView(getContext()); tv2.setText("输入框字体色"); tv2.setTextColor(Color.WHITE); tv2.setPadding(0, 40, 0, 0); layout.addView(tv2);
            EditText txtEt = createBlackTextEdit(String.format("#%06X", (0xFFFFFF & activity.inputTextColor)), ""); layout.addView(txtEt);
            layout.addView(activity.createColorPalette(txtEt));

            Button saveBtn = new Button(getContext()); saveBtn.setText("💾 保存颜色并应用"); saveBtn.setBackgroundColor(Color.parseColor("#4CAF50")); saveBtn.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120); bp.setMargins(0, 50, 0, 0); saveBtn.setLayoutParams(bp);
            saveBtn.setOnClickListener(v -> {
                try {
                    activity.inputBgColor = Color.parseColor(bgEt.getText().toString());
                    activity.inputTextColor = Color.parseColor(txtEt.getText().toString());
                    activity.prefs.edit().putInt("inputBgColor", activity.inputBgColor).putInt("inputTextColor", activity.inputTextColor).apply();
                    Toast.makeText(getContext(), "已保存全局生效", Toast.LENGTH_SHORT).show(); dialog.dismiss();
                } catch (Exception e) { Toast.makeText(getContext(), "颜色代码有误", Toast.LENGTH_SHORT).show(); }
            });
            layout.addView(saveBtn);
            scroll.addView(layout); dialog.setContentView(scroll); dialog.show();
        }

        private Button createMenuButton(String text, OnClickListener listener) {
            Button btn = new Button(getContext()); btn.setText(text); btn.setTextColor(Color.WHITE); btn.setBackgroundColor(Color.parseColor("#333333"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 130); lp.setMargins(0, 10, 0, 10); btn.setLayoutParams(lp);
            btn.setOnClickListener(listener); return btn;
        }

        private void showButtonSettingsDialog(VirtualButton btn) {
            Dialog dialog = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
            ScrollView scroll = new ScrollView(getContext()); LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(60, 40, 60, 60);

            TextView title = new TextView(getContext()); title.setText("配置按键: " + btn.id); title.setTextColor(Color.WHITE); title.setTextSize(22); layout.addView(title);
            
            final EditText nameInput = createBlackTextEdit(btn.id, "按键名称 (L-Click为鼠标专有)"); layout.addView(nameInput);
            final EditText keyInput = createBlackTextEdit(btn.keyMapStr, "映射键值 (如 A 或 DOWN)"); layout.addView(keyInput);
            
            final EditText colorInput = createBlackTextEdit(String.format("#%06X", (0xFFFFFF & btn.color)), "常态颜色代码"); layout.addView(colorInput);
            layout.addView(activity.createColorPalette(colorInput));
            
            final EditText pressedColorInput = createBlackTextEdit(String.format("#%06X", (0xFFFFFF & btn.pressedColor)), "按下颜色代码"); layout.addView(pressedColorInput);
            layout.addView(activity.createColorPalette(pressedColorInput));

            final EditText alphaInput = createBlackTextEdit(String.valueOf(btn.alpha), "透明度 0-255"); layout.addView(alphaInput);
            final EditText radiusInput = createBlackTextEdit(String.valueOf((int)btn.radius), "按键大小"); layout.addView(radiusInput);

            final boolean[] isSaved = {false};
            Button saveBtn = new Button(getContext()); saveBtn.setText("💾 保存修改"); saveBtn.setBackgroundColor(Color.parseColor("#1976D2")); saveBtn.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120); lp.setMargins(0, 20, 0, 10); saveBtn.setLayoutParams(lp);
            saveBtn.setOnClickListener(v -> {
                try {
                    btn.id = nameInput.getText().toString(); btn.keyMapStr = keyInput.getText().toString();
                    btn.color = Color.parseColor(colorInput.getText().toString()); btn.pressedColor = Color.parseColor(pressedColorInput.getText().toString());
                    btn.alpha = Integer.parseInt(alphaInput.getText().toString()); btn.radius = Integer.parseInt(radiusInput.getText().toString());
                    btn.hitboxRadius = btn.radius * 1.5f; isSaved[0] = true; saveConfig(); invalidate(); dialog.dismiss();
                } catch(Exception e) { Toast.makeText(getContext(), "格式错误", Toast.LENGTH_SHORT).show(); }
            });
            layout.addView(saveBtn);

            Button delBtn = new Button(getContext()); delBtn.setText("🗑️ 删除此按键"); delBtn.setBackgroundColor(Color.parseColor("#F44336")); delBtn.setTextColor(Color.WHITE);
            delBtn.setLayoutParams(lp); delBtn.setOnClickListener(v -> { isSaved[0] = true; buttons.remove(btn); saveConfig(); invalidate(); dialog.dismiss(); });
            layout.addView(delBtn);

            scroll.addView(layout); dialog.setContentView(scroll);
            android.view.Window window = dialog.getWindow(); if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (int)(getHeight() * 0.85));
            
            dialog.setCanceledOnTouchOutside(true);
            dialog.setOnCancelListener(d -> {
                if (!isSaved[0]) {
                    new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("⚠️ 未保存修改").setMessage("保存刚刚的修改吗？")
                        .setPositiveButton("💾 保存", (dI, i) -> saveBtn.performClick()).setNeutralButton("🔙 继续", (dI, i) -> dialog.show())
                        .setNegativeButton("❌ 放弃", (dI, i) -> dialog.dismiss()).show();
                }
            });
            dialog.show();
        }

        private EditText createBlackTextEdit(String text, String hint) {
            EditText et = new EditText(getContext()); et.setText(text); et.setHint(hint); et.setTextColor(Color.BLACK); et.setHintTextColor(Color.GRAY); et.setBackgroundColor(Color.WHITE);
            et.setPadding(30, 20, 30, 20); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 15, 0, 15); et.setLayoutParams(lp);
            return et;
        }

        private void saveConfig() {
            try {
                JSONArray array = new JSONArray();
                for (VirtualButton b : buttons) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", b.id); obj.put("cx", b.cx); obj.put("cy", b.cy); obj.put("radius", b.radius); obj.put("color", b.color);
                    obj.put("pressedColor", b.pressedColor); obj.put("alpha", b.alpha); obj.put("keyMap", b.keyMapStr); obj.put("shape", b.shape);
                    array.put(obj);
                }
                prefs.edit().putString("layout", array.toString()).putBoolean("mouseMode", isMouseMode).apply();
            } catch (Exception e) {}
        }

        private void loadConfig() {
            String json = prefs.getString("layout", null);
            isMouseMode = prefs.getBoolean("mouseMode", false);
            if (json == null || json.equals("[]")) {
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
                        btn.color = o.optInt("color", Color.GRAY); btn.pressedColor = o.optInt("pressedColor", Color.WHITE);
                        btn.alpha = o.optInt("alpha", 150); btn.shape = o.optInt("shape", 0); buttons.add(btn);
                    }
                } catch (Exception e) {}
            }
        }

        public static class VirtualButton {
            public String id; public float cx, cy, radius, hitboxRadius;
            public String keyMapStr; public boolean isPressed = false;
            public int color = Color.GRAY, pressedColor = Color.WHITE, alpha = 150, shape = 0, textColor = Color.WHITE;
            public Bitmap skinBitmap = null; public boolean isTurbo = false; public int turboInterval = 50;
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
