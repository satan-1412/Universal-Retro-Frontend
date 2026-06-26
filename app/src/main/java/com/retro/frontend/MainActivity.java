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
import android.os.Vibrator;
import android.text.Editable;
import android.view.Gravity;
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

    // 自定义输入框外观
    public int inputBgColor = Color.WHITE;
    public int inputTextColor = Color.BLACK;
    // 屏幕方向：0=横屏, 1=竖屏, 2=自动
    public int screenOrientationMode = 0; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        inputBgColor = prefs.getInt("inputBgColor", Color.WHITE);
        inputTextColor = prefs.getInt("inputTextColor", Color.BLACK);
        screenOrientationMode = prefs.getInt("screenOrientation", 0);
        applyScreenOrientation();

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#111111"));
        setContentView(rootLayout);

        setupWebView();
        showStartupSelector();
    }

    private void applyScreenOrientation() {
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
        Toast.makeText(this, "正在调用本地 " + type + " 核心... (需后续补全本地文件解析引擎)", Toast.LENGTH_SHORT).show();
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
            prefs.edit().putString("lastUrl", url).apply(); // 记忆网址
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

    // 生成预设颜色调色板 (供颜色设置使用)
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
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.setBackgroundColor(Color.BLACK);
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

    // ================= 核心：移植的虚拟按键引擎 =================
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
        private VirtualButton currentlyEditingButton = null;
        private float downX, downY;
        private Vibrator vibrator;

        // 悬浮工具菜单
        private float menuX = 20, menuY = 20;
        private final RectF menuRect = new RectF();
        private boolean isDraggingMenu = false;
        private boolean isMenuDown = false;

        // 虚拟鼠标引擎与模式切换
        public boolean isMouseMode = false;
        public boolean isPureTouchMode = false; // 纯触屏模式变量
        private boolean isDraggingJoy = false;  // 鼠标摇杆拖拽变量
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
            prefs = context.getSharedPreferences("IkemenGamepad_Pro_V7", MODE_PRIVATE);
            
            paintText.setTextAlign(Paint.Align.CENTER);
            paintText.setTypeface(Typeface.DEFAULT_BOLD);
            dashPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setStrokeWidth(4f);
            dashPaint.setColor(Color.YELLOW);
            
            mousePaint.setColor(Color.RED);
            mousePaint.setShadowLayer(5, 0, 0, Color.WHITE);

            loadConfig();
        }

        // ================= JS 注入引擎 (真实鼠标机制) =================
        private void injectKeyEvent(String keyString, boolean isDown) {
            if (keyString == null || keyString.isEmpty()) return;
            String action = isDown ? "keydown" : "keyup";
            for (String key : keyString.split("\\+")) {
                String jsCode = String.format("window.dispatchEvent(new KeyboardEvent('%s', {'key':'%s', 'code':'%s', 'bubbles':true}));", action, key.trim(), key.trim());
                activity.runOnUiThread(() -> targetEngine.evaluateJavascript(jsCode, null));
            }
        }
        
        private void injectMouseClick(boolean isRight, boolean isDown) {
            String btnCode = isRight ? "2" : "0";
            String t1 = isDown ? "touchstart" : "touchend";
            String t2 = isDown ? "mousedown" : "mouseup";
            String t3 = isDown ? "pointerdown" : "pointerup";
            
            // 三合一物理模拟，强制穿透所有 H5 游戏引擎
            String js = "try { " +
                        "  var el = document.elementFromPoint(" + mouseX + "," + mouseY + ") || document.body;" +
                        "  var touchObj = new Touch({identifier: 0, target: el, clientX: " + mouseX + ", clientY: " + mouseY + "});" +
                        "  var touchEv = new TouchEvent('" + t1 + "', {bubbles: true, cancelable: true, touches: [" + (isDown?"touchObj":"") + "], targetTouches: [" + (isDown?"touchObj":"") + "], changedTouches: [touchObj]});" +
                        "  var mouseEv = new MouseEvent('" + t2 + "', {bubbles: true, cancelable: true, clientX: " + mouseX + ", clientY: " + mouseY + ", button: " + btnCode + "});" +
                        "  var pointerEv = new PointerEvent('" + t3 + "', {bubbles: true, cancelable: true, clientX: " + mouseX + ", clientY: " + mouseY + ", button: " + btnCode + ", pointerType: 'touch'});" +
                        "  el.dispatchEvent(pointerEv);" +
                        "  el.dispatchEvent(touchEv);" +
                        "  el.dispatchEvent(mouseEv);" +
                        "  if (!" + isDown + ") { el.click(); }" + // 松开时补充一个纯粹的click
                        "} catch(e) {}";
            activity.runOnUiThread(() -> targetEngine.evaluateJavascript(js, null));
        }

        // ================= 绘制引擎 =================
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            // 纯触屏模式下，直接隐藏其余所有按键，只保留悬浮齿轮
            if (isPureTouchMode && !isEditMode) {
                menuRect.set(menuX, menuY, menuX + 150, menuY + 150);
                paintBtn.setColor(Color.parseColor("#444444")); paintBtn.setAlpha(150);
                canvas.drawRoundRect(menuRect, 30, 30, paintBtn);
                paintText.setColor(Color.WHITE); paintText.setTextSize(50);
                canvas.drawText("⚙️", menuRect.centerX(), menuRect.centerY() + 18, paintText);
                return; // 拦截，不再绘制下面的摇杆和按键
            }
            
            // 绘制悬浮菜单按钮
            menuRect.set(menuX, menuY, menuX + 150, menuY + 150);
            paintBtn.setColor(Color.parseColor("#444444"));
            paintBtn.setAlpha(150);
            canvas.drawRoundRect(menuRect, 30, 30, paintBtn);
            paintText.setColor(Color.WHITE);
            paintText.setTextSize(50);
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

                if (isEditMode) {
                    canvas.drawCircle(btn.cx, btn.cy, btn.hitboxRadius, dashPaint);
                }
            }
            
            if (isEditMode) {
                paintText.setTextSize(40);
                paintText.setColor(Color.GREEN);
                paintText.setShadowLayer(5, 0, 0, Color.BLACK);
                canvas.drawText("【编辑模式】拖拽位置，轻触按键设置。点下方按钮保存。", getWidth()/2f, 80, paintText);
                paintText.clearShadowLayer();
                
                // 屏幕上绘制大大的“保存退出”按钮 (防呆设计)
                paintBtn.setColor(Color.RED); paintBtn.setAlpha(200);
                canvas.drawRoundRect(new RectF(getWidth()/2f - 200, 120, getWidth()/2f + 200, 220), 20, 20, paintBtn);
                paintText.setColor(Color.WHITE);
                paintText.setTextSize(45);
                canvas.drawText("💾 保存并退出", getWidth()/2f, 185, paintText);
            }
        }

        // ================= 触控引擎 =================
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int actionIndex = event.getActionIndex();

            if (isEditMode) {
                handleEditTouch(event, action, actionIndex);
                return true;
            }

            // 悬浮菜单逻辑
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

            // 如果是纯触屏模式，停止拦截所有手势，直接把事件放行给底层的 WebView
            if (isPureTouchMode) return false; 

            // 鼠标摇杆逻辑
            if (isMouseMode) {
                boolean joyTouched = false;
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                    if (event.getPointerId(actionIndex) == joyPointerId) { joyPointerId = -1; joyKnobX = joyBaseX; joyKnobY = joyBaseY; }
                }
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && i == actionIndex) continue;
                    float px = event.getX(i), py = event.getY(i);
                    if (px < getWidth() / 2f) { // 限制鼠标摇杆只能在左半屏生效
                        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                            if (Math.hypot(px - joyBaseX, py - joyBaseY) < joyRadius * 1.5f) joyPointerId = event.getPointerId(i);
                        }
                        if (event.getPointerId(i) == joyPointerId) {
                            joyTouched = true;
                            float dx = px - joyBaseX, dy = py - joyBaseY;
                            float dist = (float) Math.hypot(dx, dy);
                            if (dist > joyRadius) { joyKnobX = joyBaseX + (dx/dist)*joyRadius; joyKnobY = joyBaseY + (dy/dist)*joyRadius; }
                            else { joyKnobX = px; joyKnobY = py; }
                            
                            mouseX += (dx / joyRadius) * 20f; // 鼠标灵敏度
                            mouseY += (dy / joyRadius) * 20f;
                            mouseX = Math.max(0, Math.min(mouseX, getWidth()));
                            mouseY = Math.max(0, Math.min(mouseY, getHeight()));
                        }
                    }
                }
                if (!joyTouched) { joyPointerId = -1; joyKnobX = joyBaseX; joyKnobY = joyBaseY; }
            }

            // 全局按键扫描
            for (VirtualButton btn : buttons) {
                boolean isTouchedNow = false;
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (event.getPointerId(i) == joyPointerId) continue;
                    if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) && i == actionIndex) continue;
                    float px = event.getX(i), py = event.getY(i);
                    if (Math.hypot(px - btn.cx, py - btn.cy) < btn.hitboxRadius) isTouchedNow = true;
                }

                if (!btn.isPressed && isTouchedNow) {
                    btn.isPressed = true;
                    if (vibrator != null) vibrator.vibrate(20);
                    
                    if (btn.id.equals("L-Click")) injectMouseClick(false, true);
                    else if (btn.id.equals("R-Click")) injectMouseClick(true, true);
                    else if (btn.isTurbo) btn.startTurbo(this);
                    else injectKeyEvent(btn.keyMapStr, true);
                    
                } else if (btn.isPressed && !isTouchedNow) {
                    btn.isPressed = false;
                    if (btn.id.equals("L-Click")) injectMouseClick(false, false);
                    else if (btn.id.equals("R-Click")) injectMouseClick(true, false);
                    else {
                        if (btn.isTurbo) btn.stopTurbo();
                        else injectKeyEvent(btn.keyMapStr, false);
                    }
                }
            }
            invalidate();
            return true;
        }

        private void handleEditTouch(MotionEvent event, int action, int actionIndex) {
            float x = event.getX(actionIndex), y = event.getY(actionIndex);
            if (action == MotionEvent.ACTION_DOWN) {
                // 点击保存按钮区域
                if (x > getWidth()/2f - 200 && x < getWidth()/2f + 200 && y > 120 && y < 220) {
                    isEditMode = false; saveConfig();
                    Toast.makeText(getContext(), "布局已保存并退出编辑！", Toast.LENGTH_SHORT).show();
                    activity.setupImmersive();
                    invalidate(); return;
                }
                downX = x; downY = y;
                // 新增：检测是否点中了左侧鼠标摇杆区
                if (isMouseMode && Math.hypot(x - joyBaseX, y - joyBaseY) < joyRadius * 1.5f) {
                    isDraggingJoy = true;
                    return;
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

        // ================= 悬浮工具菜单与设置 (加入平滑滚动与方向设置) =================
        private void showFloatingToolMenu() {
            Dialog dialog = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
            ScrollView scroll = new ScrollView(getContext());
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(60, 60, 60, 60);
            
            TextView title = new TextView(getContext());
            title.setText("⚙️ 全局设置菜单");
            title.setTextColor(Color.WHITE);
            title.setTextSize(22f);
            title.setPadding(0, 0, 0, 30);
            layout.addView(title);
            
            layout.addView(createMenuButton("🛠️ 编辑虚拟按键布局", v -> { isEditMode = true; dialog.dismiss(); invalidate(); }));
            layout.addView(createMenuButton("➕ 新增自定义按键", v -> { 
                buttons.add(new VirtualButton("新键", getWidth()/2f, getHeight()/2f, 80, "Space"));
                isEditMode = true; dialog.dismiss(); invalidate(); 
            }));
            layout.addView(createMenuButton(isMouseMode ? "🖱️ 关闭鼠标模式" : "🖱️ 开启鼠标指针模式", v -> { isMouseMode = !isMouseMode; saveConfig(); dialog.dismiss(); invalidate(); }));
            
            layout.addView(createMenuButton(isPureTouchMode ? "🎮 退出触屏模式 (恢复手柄)" : "👆 开启纯触屏模式 (隐去按键)", v -> { 
                isPureTouchMode = !isPureTouchMode; 
                dialog.dismiss(); 
                invalidate(); 
                Toast.makeText(getContext(), isPureTouchMode ? "已开启纯触屏模式，可直接操作网页" : "已恢复虚拟按键", Toast.LENGTH_SHORT).show();
            }));
            
            // 屏幕方向控制
            String currentOri = activity.screenOrientationMode == 0 ? "横屏" : (activity.screenOrientationMode == 1 ? "竖屏" : "自动识别");
            layout.addView(createMenuButton("📱 切换屏幕方向 (当前: " + currentOri + ")", v -> {
                activity.screenOrientationMode = (activity.screenOrientationMode + 1) % 3;
                activity.prefs.edit().putInt("screenOrientation", activity.screenOrientationMode).apply();
                activity.applyScreenOrientation();
                dialog.dismiss();
                Toast.makeText(getContext(), "方向已切换，请重新打开菜单以刷新 UI", Toast.LENGTH_SHORT).show();
            }));

            layout.addView(createMenuButton("🌗 切换网页白天/黑夜滤镜", v -> { activity.toggleWebTheme(); dialog.dismiss(); }));
            layout.addView(createMenuButton("🌐 重新输入网址", v -> { dialog.dismiss(); activity.showUrlInputDialog(targetEngine.getUrl()); }));
            layout.addView(createMenuButton("🎨 UI 颜色与背景自定义", v -> { dialog.dismiss(); showInputBoxSettings(); }));
            layout.addView(createMenuButton("🏠 返回主菜单 (核心选择)", v -> { dialog.dismiss(); activity.showStartupSelector(); }));

            scroll.addView(layout);
            dialog.setContentView(scroll);
            
            // 限制弹窗高度，确保能在小屏上滑动
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
            
            TextView tv1 = new TextView(getContext());
            tv1.setText("输入框背景色 (填色号或点下方选色)");
            tv1.setTextColor(Color.WHITE);
            layout.addView(tv1);

            EditText bgEt = new EditText(getContext());
            bgEt.setText(String.format("#%06X", (0xFFFFFF & activity.inputBgColor)));
            bgEt.setTextColor(Color.BLACK); // 保证文字可读
            bgEt.setBackgroundColor(Color.WHITE);
            bgEt.setPadding(20, 20, 20, 20);
            layout.addView(bgEt, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            layout.addView(activity.createColorPalette(bgEt));

            TextView tv2 = new TextView(getContext());
            tv2.setText("输入框字体色 (填色号或点下方选色)");
            tv2.setTextColor(Color.WHITE);
            tv2.setPadding(0, 40, 0, 0);
            layout.addView(tv2);

            EditText txtEt = new EditText(getContext());
            txtEt.setText(String.format("#%06X", (0xFFFFFF & activity.inputTextColor)));
            txtEt.setTextColor(Color.BLACK); // 保证文字可读
            txtEt.setBackgroundColor(Color.WHITE);
            txtEt.setPadding(20, 20, 20, 20);
            layout.addView(txtEt, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            layout.addView(activity.createColorPalette(txtEt));

            Button saveBtn = new Button(getContext());
            saveBtn.setText("💾 保存颜色并应用");
            saveBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
            saveBtn.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
            bp.setMargins(0, 50, 0, 0);
            saveBtn.setLayoutParams(bp);
            
            saveBtn.setOnClickListener(v -> {
                try {
                    activity.inputBgColor = Color.parseColor(bgEt.getText().toString());
                    activity.inputTextColor = Color.parseColor(txtEt.getText().toString());
                    activity.prefs.edit()
                        .putInt("inputBgColor", activity.inputBgColor)
                        .putInt("inputTextColor", activity.inputTextColor).apply();
                    Toast.makeText(getContext(), "已保存全局生效", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } catch (Exception e) { Toast.makeText(getContext(), "颜色代码有误，请重试", Toast.LENGTH_SHORT).show(); }
            });
            layout.addView(saveBtn);
            
            scroll.addView(layout);
            dialog.setContentView(scroll);
            dialog.show();
        }

        private Button createMenuButton(String text, OnClickListener listener) {
            Button btn = new Button(getContext());
            btn.setText(text);
            btn.setTextColor(Color.WHITE);
            btn.setBackgroundColor(Color.parseColor("#333333"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 130);
            lp.setMargins(0, 10, 0, 10);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(listener);
            return btn;
        }

        // ================= 按钮单体高级设置 (防呆防误触版) =================
        private void showButtonSettingsDialog(VirtualButton btn) {
            Dialog dialog = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
            ScrollView scroll = new ScrollView(getContext());
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(60, 40, 60, 60);

            TextView title = new TextView(getContext()); title.setText("配置按键: " + btn.id); title.setTextColor(Color.WHITE); title.setTextSize(22); layout.addView(title);
            
            final EditText nameInput = createBlackTextEdit(btn.id, "按键名称 (L-Click/R-Click为鼠标专有)"); layout.addView(nameInput);
            final EditText keyInput = createBlackTextEdit(btn.keyMapStr, "映射键值 (如 A 或 A+B)"); layout.addView(keyInput);
            
            final EditText 
