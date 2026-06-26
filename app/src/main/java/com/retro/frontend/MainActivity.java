package com.retro.frontend;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Vibrator;
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
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private WebView webView;
    private GamepadView gamepadView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 沉浸式全屏
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        // 1. 初始化引擎层 (WebView)
        setupWebView();
        rootLayout.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. 初始化控制层 (原生虚拟按键层，盖在 WebView 上)
        gamepadView = new GamepadView(this, webView);
        rootLayout.addView(gamepadView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 3. 顶部简易调试栏 (用于手动输入网址)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.argb(150, 0, 0, 0));
        
        EditText urlInput = new EditText(this);
        urlInput.setHint("输入 H5/在线 Flash 网址");
        urlInput.setTextColor(Color.WHITE);
        urlInput.setHintTextColor(Color.GRAY);
        urlInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        Button goBtn = new Button(this);
        goBtn.setText("加载");
        goBtn.setOnClickListener(v -> {
            String url = urlInput.getText().toString();
            if (!url.startsWith("http")) url = "https://" + url;
            webView.loadUrl(url);
            topBar.setVisibility(View.GONE); // 加载后自动隐藏顶部栏以防烧屏
        });

        Button editBtn = new Button(this);
        editBtn.setText("编辑按键");
        editBtn.setOnClickListener(v -> {
            gamepadView.toggleEditMode();
            topBar.setVisibility(View.GONE);
        });

        topBar.addView(urlInput);
        topBar.addView(goBtn);
        topBar.addView(editBtn);
        rootLayout.addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(rootLayout);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        // 强制开启硬件加速
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.setBackgroundColor(Color.BLACK);
    }

    // ==========================================
    // 你的原味移植：极速原生按键控制器层
    // ==========================================
    public static class GamepadView extends View {
        private final WebView targetEngine;
        private final Paint paintBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint();
        private final List<VirtualButton> buttons = new ArrayList<>();
        
        public boolean isEditMode = false;
        private VirtualButton draggedButton = null;
        private float downX, downY;
        private Vibrator vibrator;

        public GamepadView(Context context, WebView webView) {
            super(context);
            this.targetEngine = webView;
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            
            paintText.setTextAlign(Paint.Align.CENTER);
            dashPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setStrokeWidth(3f);
            dashPaint.setColor(Color.YELLOW);
            dashPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10f, 10f}, 0));

            // 初始化默认按键 (支持宏和连发)
            buttons.add(new VirtualButton("UP", 200, 500, 80, "ArrowUp"));
            buttons.add(new VirtualButton("DOWN", 200, 800, 80, "ArrowDown"));
            buttons.add(new VirtualButton("LEFT", 50, 650, 80, "ArrowLeft"));
            buttons.add(new VirtualButton("RIGHT", 350, 650, 80, "ArrowRight"));
            
            VirtualButton btnA = new VirtualButton("A\n(Turbo)", 1600, 650, 90, "a");
            btnA.isTurbo = true; // 默认开启 A 键连发
            buttons.add(btnA);
            
            buttons.add(new VirtualButton("B", 1800, 500, 90, "b"));
            
            VirtualButton btnMacro = new VirtualButton("MACRO\n(A+B)", 1400, 400, 70, "a+b");
            buttons.add(btnMacro);
        }

        public void toggleEditMode() {
            isEditMode = !isEditMode;
            Toast.makeText(getContext(), isEditMode ? "编辑模式开启：拖动修改位置" : "编辑模式关闭，按键位置已保存", Toast.LENGTH_SHORT).show();
            invalidate();
        }

        // --- 核心注入逻辑：将原生点击转化为 JS 键盘事件注入游戏 ---
        private void injectKeyEvent(String keyString, boolean isDown) {
            if (keyString == null || keyString.isEmpty()) return;
            
            // 解析加号，处理宏映射 (例如 "A+B")
            String[] keys = keyString.split("\\+");
            String action = isDown ? "keydown" : "keyup";
            
            for (String key : keys) {
                String jsCode = String.format(
                    "window.dispatchEvent(new KeyboardEvent('%s', {'key':'%s', 'code':'%s', 'bubbles':true}));", 
                    action, key.trim(), key.trim()
                );
                ((Activity)getContext()).runOnUiThread(() -> targetEngine.evaluateJavascript(jsCode, null));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (VirtualButton btn : buttons) {
                // 彻底不透明度支持，非按压时半透，防烧屏
                int currentAlpha = isEditMode ? 150 : (btn.isPressed ? 200 : 80);
                
                // 绘制按钮底色
                paintBtn.setColor(btn.isPressed ? Color.WHITE : Color.DKGRAY);
                paintBtn.setAlpha(currentAlpha);
                canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);

                // 绘制文字
                paintText.setColor(btn.isPressed ? Color.BLACK : Color.WHITE);
                paintText.setAlpha(currentAlpha + 50);
                float textOffset = (paintText.descent() - paintText.ascent()) / 2 - paintText.descent();
                
                if (btn.id.contains("\n")) {
                    String[] lines = btn.id.split("\n");
                    paintText.setTextSize(btn.radius * 0.4f);
                    canvas.drawText(lines[0], btn.cx, btn.cy - 10, paintText);
                    canvas.drawText(lines[1], btn.cx, btn.cy + 30, paintText);
                } else {
                    paintText.setTextSize(btn.radius * 0.6f);
                    canvas.drawText(btn.id, btn.cx, btn.cy + textOffset, paintText);
                }

                // 编辑模式外框
                if (isEditMode) {
                    canvas.drawCircle(btn.cx, btn.cy, btn.hitboxRadius, dashPaint);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int actionIndex = event.getActionIndex();

            if (isEditMode) {
                handleEditTouch(event, action);
                return true;
            }

            // --- 防卡键高频扫描引擎 ---
            for (VirtualButton btn : buttons) {
                boolean isTouchedNow = false;
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) && i == actionIndex) continue;
                    float px = event.getX(i), py = event.getY(i);
                    if (Math.hypot(px - btn.cx, py - btn.cy) < btn.hitboxRadius) {
                        isTouchedNow = true;
                    }
                }

                if (!btn.isPressed && isTouchedNow) {
                    btn.isPressed = true;
                    if (vibrator != null) vibrator.vibrate(20);
                    
                    if (btn.isTurbo) btn.startTurbo(this);
                    else injectKeyEvent(btn.keyMapStr, true);
                    
                } else if (btn.isPressed && !isTouchedNow) {
                    btn.isPressed = false;
                    if (btn.isTurbo) btn.stopTurbo();
                    else injectKeyEvent(btn.keyMapStr, false);
                }
            }
            invalidate();
            return true;
        }

        private void handleEditTouch(MotionEvent event, int action) {
            float x = event.getX(0), y = event.getY(0);
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    downX = x; downY = y;
                    for (int i = buttons.size() - 1; i >= 0; i--) {
                        if (Math.hypot(x - buttons.get(i).cx, y - buttons.get(i).cy) < buttons.get(i).radius * 1.5f) {
                            draggedButton = buttons.get(i); break;
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (draggedButton != null) {
                        draggedButton.cx = x; draggedButton.cy = y;
                        invalidate();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    draggedButton = null;
                    break;
            }
        }

        // ================= 内置按钮逻辑实体 =================
        public static class VirtualButton {
            public String id;
            public float cx, cy, radius, hitboxRadius;
            public String keyMapStr;
            public boolean isPressed = false;
            
            // 连发控制
            public boolean isTurbo = false;
            public int turboInterval = 50; 
            private volatile boolean turboRunning = false;
            private static final ExecutorService threadPool = Executors.newCachedThreadPool();

            public VirtualButton(String id, float cx, float cy, float radius, String keyMapStr) {
                this.id = id; this.cx = cx; this.cy = cy;
                this.radius = radius; this.keyMapStr = keyMapStr;
                this.hitboxRadius = radius * 1.5f; // 触控热区放大
            }

            public void startTurbo(GamepadView parentView) {
                if (turboRunning || keyMapStr == null) return;
                turboRunning = true;
                threadPool.execute(() -> {
                    while (turboRunning) {
                        try {
                            parentView.injectKeyEvent(keyMapStr, true);
                            Thread.sleep(turboInterval);
                            parentView.injectKeyEvent(keyMapStr, false);
                            Thread.sleep(turboInterval);
                        } catch (InterruptedException e) { break; }
                    }
                });
            }

            public void stopTurbo() {
                turboRunning = false;
            }
        }
    }
}
