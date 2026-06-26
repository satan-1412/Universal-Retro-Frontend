package com.retro.frontend;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

    // 自定义输入框外观
    private int inputBgColor = Color.WHITE;
    private int inputTextColor = Color.BLACK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupImmersive();
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        inputBgColor = prefs.getInt("inputBgColor", Color.WHITE);
        inputTextColor = prefs.getInt("inputTextColor", Color.BLACK);

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#111111"));
        setContentView(rootLayout);

        setupWebView();
        showStartupSelector();
    }

    private void setupImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    // ================= 3模式启动器 =================
    private void showStartupSelector() {
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

        layout.addView(createBigButton("🌐 在线 Web/H5/Flash 游戏", v -> showUrlInputDialog("https://m.4399.com")));
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(600, 120);
        params.setMargins(0, 20, 0, 20);
        btn.setLayoutParams(params);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void loadLocalCore(String type) {
        // 预留的本地核心加载接口
        Toast.makeText(this, "正在调用本地 " + type + " 核心... (需配置底层存储权限)", Toast.LENGTH_SHORT).show();
        // 此处可调用 FilePicker 选择本地文件，然后扔给 WebView 或 J2ME 核心
        startGameContainer("file:///android_asset/local_" + type + "_player.html");
    }

    // ================= URL 弹窗与自定义输入框 =================
    public void showUrlInputDialog(String defaultUrl) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.argb(200, 0, 0, 0));

        final EditText urlInput = new EditText(this);
        urlInput.setText(defaultUrl);
        urlInput.setTextColor(inputTextColor);
        urlInput.setHint("输入网址...");
        urlInput.setHintTextColor(Color.GRAY);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(inputBgColor);
        bg.setCornerRadius(15f);
        urlInput.setBackground(bg);
        urlInput.setPadding(40, 30, 40, 30);
        layout.addView(urlInput, new LinearLayout.LayoutParams(800, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button goBtn = new Button(this);
        goBtn.setText("🚀 载入游戏");
        goBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        goBtn.setTextColor(Color.WHITE);
        goBtn.setOnClickListener(v -> {
            String url = urlInput.getText().toString();
            if (!url.startsWith("http")) url = "https://" + url;
            dialog.dismiss();
            startGameContainer(url);
        });
        
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(800, 120);
        bp.setMargins(0, 30, 0, 0);
        layout.addView(goBtn, bp);
        dialog.setContentView(layout);
        dialog.show();
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

    // ================= 双击返回退出 =================
    @Override
    public void onBackPressed() {
        if (gamepadView != null && gamepadView.isEditMode) {
            Toast.makeText(this, "请先保存按键配置再退出", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - backPressedTime < 2000) {
            super.onBackPressed();
        } else {
            Toast.makeText(this, "再按一次退出游戏", Toast.LENGTH_SHORT).show();
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

        // 虚拟鼠标引擎
        public boolean isMouseMode = false;
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
            prefs = context.getSharedPreferences("IkemenGamepad_Pro_V6", MODE_PRIVATE);
            
            paintText.setTextAlign(Paint.Align.CENTER);
            paintText.setTypeface(Typeface.DEFAULT_BOLD);
            dashPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setStrokeWidth(4f);
            dashPaint.setColor(Color.YELLOW);
            
            mousePaint.setColor(Color.RED);
            mousePaint.setShadowLayer(5, 0, 0, Color.BLACK);

            loadConfig();
        }

        // ================= JS 注入引擎 =================
        private void injectKeyEvent(String keyString, boolean isDown) {
            if (keyString == null || keyString.isEmpty()) return;
            String action = isDown ? "keydown" : "keyup";
            for (String key : keyString.split("\\+")) {
                String jsCode = String.format("window.dispatchEvent(new KeyboardEvent('%s', {'key':'%s', 'code':'%s', 'bubbles':true}));", action, key.trim(), key.trim());
                activity.runOnUiThread(() -> targetEngine.evaluateJavascript(jsCode, null));
            }
        }
        
        private void injectMouseClick(boolean isRight) {
            String btn = isRight ? "2" : "0";
            String js = "var el = document.elementFromPoint(" + mouseX + "," + mouseY + ");" +
                        "if(el){ var ev = new MouseEvent('click', {clientX:" + mouseX + ", clientY:" + mouseY + ", button:" + btn + ", bubbles:true}); el.dispatchEvent(ev); }";
            activity.runOnUiThread(() -> targetEngine.evaluateJavascript(js, null));
        }

        // ================= 绘制引擎 =================
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            // 绘制悬浮菜单按钮
            menuRect.set(menuX, menuY, menuX + 150, menuY + 150);
            paintBtn.setColor(Color.parseColor("#444444"));
            paintBtn.setAlpha(150);
            canvas.drawRoundRect(menuRect, 30, 30, paintBtn);
            paintText.setColor(Color.WHITE);
            paintText.setTextSize(40);
            canvas.drawText("⚙️", menuRect.centerX(), menuRect.centerY() + 15, paintText);

            // 绘制虚拟鼠标
            if (isMouseMode) {
                canvas.drawCircle(mouseX, mouseY, 15, mousePaint);
                // 绘制左侧控制摇杆
                paintBtn.setColor(Color.parseColor("#333333"));
                paintBtn.setAlpha(100);
                canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
                paintBtn.setColor(Color.WHITE);
                paintBtn.setAlpha(150);
                canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.4f, paintBtn);
            }

            // 绘制所有按键
            for (VirtualButton btn : buttons) {
                int currentAlpha = isEditMode ? 150 : (btn.isPressed ? 220 : btn.alpha);
                
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
                paintText.setTextSize(50);
                paintText.setColor(Color.GREEN);
                canvas.drawText("【编辑模式】拖拽调整位置，轻触按键进行设置", getWidth()/2f, 100, paintText);
                
                // 屏幕上绘制大大的“保存退出”按钮
                paintBtn.setColor(Color.RED); paintBtn.setAlpha(200);
                canvas.drawRoundRect(new RectF(getWidth()/2f - 150, 150, getWidth()/2f + 150, 250), 20, 20, paintBtn);
                paintText.setColor(Color.WHITE);
                canvas.drawText("💾 保存并退出", getWidth()/2f, 215, paintText);
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
                menuX = event.getX(actionIndex) - 75; menuY = event.getY(actionIndex) - 75; invalidate();
            }
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && isDraggingMenu) {
                if (isMenuDown) showFloatingToolMenu();
                isDraggingMenu = false; return true;
            }

            // 鼠标摇杆逻辑
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
                            
                            // 极速更新鼠标位置
                            mouseX += (dx / joyRadius) * 15f;
                            mouseY += (dy / joyRadius) * 15f;
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
                    
                    if (btn.id.equals("L-Click")) injectMouseClick(false);
                    else if (btn.id.equals("R-Click")) injectMouseClick(true);
                    else if (btn.isTurbo) btn.startTurbo(this);
                    else injectKeyEvent(btn.keyMapStr, true);
                    
                } else if (btn.isPressed && !isTouchedNow) {
                    btn.isPressed = false;
                    if (!btn.id.equals("L-Click") && !btn.id.equals("R-Click")) {
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
                if (x > getWidth()/2f - 150 && x < getWidth()/2f + 150 && y > 150 && y < 250) {
                    isEditMode = false; saveConfig();
                    Toast.makeText(getContext(), "布局已保存！", Toast.LENGTH_SHORT).show();
                    invalidate(); return;
                }
                downX = x; downY = y;
                for (int i = buttons.size() - 1; i >= 0; i--) {
                    if (Math.hypot(x - buttons.get(i).cx, y - buttons.get(i).cy) < buttons.get(i).radius * 1.5f) {
                        draggedButton = buttons.get(i); break;
                    }
                }
            } else if (action == MotionEvent.ACTION_MOVE && draggedButton != null) {
                draggedButton.cx = x; draggedButton.cy = y; invalidate();
            } else if (action == MotionEvent.ACTION_UP) {
                if (draggedButton != null && Math.hypot(x - downX, y - downY) < 10) {
                    showButtonSettingsDialog(draggedButton);
                }
                draggedButton = null;
            }
        }

        // ================= 悬浮工具菜单与设置 =================
        private void showFloatingToolMenu() {
            Dialog dialog = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 50, 50, 50);
            
            layout.addView(createMenuButton("🛠️ 编辑虚拟按键", v -> { isEditMode = true; dialog.dismiss(); invalidate(); }));
            layout.addView(createMenuButton("➕ 新增自定义按键", v -> { 
                buttons.add(new VirtualButton("New", getWidth()/2f, getHeight()/2f, 80, "Space"));
                isEditMode = true; dialog.dismiss(); invalidate(); 
            }));
            layout.addView(createMenuButton(isMouseMode ? "🖱️ 关闭鼠标模式" : "🖱️ 开启鼠标指针模式", v -> { isMouseMode = !isMouseMode; saveConfig(); dialog.dismiss(); invalidate(); }));
            layout.addView(createMenuButton("🌗 切换网页白天/黑夜模式", v -> { activity.toggleWebTheme(); dialog.dismiss(); }));
            layout.addView(createMenuButton("🌐 重新输入网址", v -> { dialog.dismiss(); activity.showUrlInputDialog(targetEngine.getUrl()); }));
            layout.addView(createMenuButton("⚙️ 输入框颜色自定义", v -> { dialog.dismiss(); showInputBoxSettings(); }));
            layout.addView(createMenuButton("🏠 返回主菜单(3选1)", v -> { dialog.dismiss(); activity.showStartupSelector(); }));

            dialog.setContentView(layout);
            dialog.show();
        }

        private void showInputBoxSettings() {
            Dialog dialog = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 50, 50, 50);
            
            TextView tv = new TextView(getContext());
            tv.setText("输入框背景色和字体色(例如 #FFFFFF)");
            tv.setTextColor(Color.WHITE);
            layout.addView(tv);

            EditText bgEt = new EditText(getContext());
            bgEt.setText(String.format("#%06X", (0xFFFFFF & activity.inputBgColor)));
            bgEt.setTextColor(Color.WHITE);
            layout.addView(bgEt);

            EditText txtEt = new EditText(getContext());
            txtEt.setText(String.format("#%06X", (0xFFFFFF & activity.inputTextColor)));
            txtEt.setTextColor(Color.WHITE);
            layout.addView(txtEt);

            Button saveBtn = new Button(getContext());
            saveBtn.setText("保存颜色 (未来可支持载入本地字体)");
            saveBtn.setOnClickListener(v -> {
                try {
                    activity.inputBgColor = Color.parseColor(bgEt.getText().toString());
                    activity.inputTextColor = Color.parseColor(txtEt.getText().toString());
                    activity.getSharedPreferences("AppPrefs", MODE_PRIVATE).edit()
                        .putInt("inputBgColor", activity.inputBgColor)
                        .putInt("inputTextColor", activity.inputTextColor).apply();
                    Toast.makeText(getContext(), "已保存，下次打开输入框生效", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } catch (Exception e) { Toast.makeText(getContext(), "颜色代码错误", Toast.LENGTH_SHORT).show(); }
            });
            layout.addView(saveBtn);
            dialog.setContentView(layout);
            dialog.show();
        }

        private Button createMenuButton(String text, OnClickListener listener) {
            Button btn = new Button(getContext());
            btn.setText(text);
            btn.setTextColor(Color.WHITE);
            btn.setBackgroundColor(Color.parseColor("#4CAF50"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
            lp.setMargins(0, 10, 0, 10);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(listener);
            return btn;
        }

        // 按钮单体高级设置 (核心移植)
        private void showButtonSettingsDialog(VirtualButton btn) {
            Dialog dialog = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
            ScrollView scroll = new ScrollView(getContext());
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 50, 50, 50);

            TextView title = new TextView(getContext()); title.setText("配置按键: " + btn.id); title.setTextColor(Color.WHITE); title.setTextSize(20); layout.addView(title);
            
            final EditText nameInput = new EditText(getContext()); nameInput.setText(btn.id); nameInput.setTextColor(Color.WHITE); nameInput.setHint("按键名称"); layout.addView(nameInput);
            final EditText keyInput = new EditText(getContext()); keyInput.setText(btn.keyMapStr); keyInput.setTextColor(Color.WHITE); keyInput.setHint("映射键值 (如 A+B)"); layout.addView(keyInput);
            
            // 外观定制
            final EditText colorInput = new EditText(getContext()); colorInput.setText(String.format("#%06X", (0xFFFFFF & btn.color))); colorInput.setTextColor(Color.WHITE); colorInput.setHint("常态颜色代码"); layout.addView(colorInput);
            final EditText pressedColorInput = new EditText(getContext()); pressedColorInput.setText(String.format("#%06X", (0xFFFFFF & btn.pressedColor))); pressedColorInput.setTextColor(Color.WHITE); pressedColorInput.setHint("按下颜色代码"); layout.addView(pressedColorInput);
            
            final EditText alphaInput = new EditText(getContext()); alphaInput.setText(String.valueOf(btn.alpha)); alphaInput.setTextColor(Color.WHITE); alphaInput.setHint("透明度 0-255"); layout.addView(alphaInput);
            final EditText radiusInput = new EditText(getContext()); radiusInput.setText(String.valueOf((int)btn.radius)); radiusInput.setTextColor(Color.WHITE); radiusInput.setHint("按键大小"); layout.addView(radiusInput);

            Button pickImgBtn = new Button(getContext()); pickImgBtn.setText("选择自定义皮肤图片 (从图库)"); 
            pickImgBtn.setOnClickListener(v -> {
                currentlyEditingButton = btn;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                activity.startActivityForResult(intent, 43); // 回调由 MainActivity 处理
            });
            layout.addView(pickImgBtn);

            Button turboBtn = new Button(getContext());
            turboBtn.setText(btn.isTurbo ? "🔥 连发已开启" : "⚪ 连发已关闭");
            turboBtn.setOnClickListener(v -> { btn.isTurbo = !btn.isTurbo; turboBtn.setText(btn.isTurbo ? "🔥 连发已开启" : "⚪ 连发已关闭"); });
            layout.addView(turboBtn);

            Button saveBtn = new Button(getContext()); saveBtn.setText("💾 保存修改"); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
            saveBtn.setOnClickListener(v -> {
                try {
                    btn.id = nameInput.getText().toString();
                    btn.keyMapStr = keyInput.getText().toString();
                    btn.color = Color.parseColor(colorInput.getText().toString());
                    btn.pressedColor = Color.parseColor(pressedColorInput.getText().toString());
                    btn.alpha = Integer.parseInt(alphaInput.getText().toString());
                    btn.radius = Integer.parseInt(radiusInput.getText().toString());
                    btn.hitboxRadius = btn.radius * 1.5f;
                    saveConfig(); invalidate(); dialog.dismiss();
                } catch(Exception e) { Toast.makeText(getContext(), "数据格式错误", Toast.LENGTH_SHORT).show(); }
            });
            layout.addView(saveBtn);

            Button delBtn = new Button(getContext()); delBtn.setText("🗑️ 删除此按键"); delBtn.setBackgroundColor(Color.parseColor("#F44336"));
            delBtn.setOnClickListener(v -> { buttons.remove(btn); saveConfig(); invalidate(); dialog.dismiss(); });
            layout.addView(delBtn);

            scroll.addView(layout);
            dialog.setContentView(scroll);
            dialog.show();
        }

        public void onImagePicked(Uri uri) {
            if (currentlyEditingButton != null) {
                try {
                    InputStream is = getContext().getContentResolver().openInputStream(uri);
                    currentlyEditingButton.customImageUri = uri.toString();
                    currentlyEditingButton.skinBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(is), (int)(currentlyEditingButton.radius*2), (int)(currentlyEditingButton.radius*2), true);
                    is.close();
                    saveConfig(); invalidate();
                    Toast.makeText(getContext(), "皮肤应用成功！", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {}
            }
        }

        // ================= 持久化逻辑 =================
        private void saveConfig() {
            try {
                JSONArray array = new JSONArray();
                for (VirtualButton b : buttons) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", b.id); obj.put("cx", b.cx); obj.put("cy", b.cy);
                    obj.put("radius", b.radius); obj.put("color", b.color);
                    obj.put("pressedColor", b.pressedColor); obj.put("alpha", b.alpha);
                    obj.put("keyMap", b.keyMapStr); obj.put("isTurbo", b.isTurbo);
                    obj.put("skin", b.customImageUri == null ? "" : b.customImageUri);
                    obj.put("shape", b.shape);
                    array.put(obj);
                }
                prefs.edit().putString("layout", array.toString()).putBoolean("mouseMode", isMouseMode).apply();
            } catch (Exception e) {}
        }

        private void loadConfig() {
            String json = prefs.getString("layout", null);
            isMouseMode = prefs.getBoolean("mouseMode", false);
            if (json == null) {
                // 默认在线游戏配置，包含鼠标 L/R
                buttons.add(new VirtualButton("UP", 200, 500, 80, "ArrowUp"));
                buttons.add(new VirtualButton("DOWN", 200, 800, 80, "ArrowDown"));
                buttons.add(new VirtualButton("LEFT", 50, 650, 80, "ArrowLeft"));
                buttons.add(new VirtualButton("RIGHT", 350, 650, 80, "ArrowRight"));
                buttons.add(new VirtualButton("A", 1600, 650, 90, "a"));
                buttons.add(new VirtualButton("B", 1800, 500, 90, "b"));
                buttons.add(new VirtualButton("L-Click", 1800, 800, 80, ""));
                buttons.add(new VirtualButton("R-Click", 1600, 400, 80, ""));
            } else {
                try {
                    JSONArray array = new JSONArray(json);
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        VirtualButton btn = new VirtualButton(o.optString("id"), (float)o.optDouble("cx"), (float)o.optDouble("cy"), (float)o.optDouble("radius"), o.optString("keyMap"));
                        btn.color = o.optInt("color", Color.GRAY); btn.pressedColor = o.optInt("pressedColor", Color.WHITE);
                        btn.alpha = o.optInt("alpha", 150); btn.isTurbo = o.optBoolean("isTurbo", false);
                        btn.customImageUri = o.optString("skin", ""); btn.shape = o.optInt("shape", 0);
                        if (!btn.customImageUri.isEmpty()) {
                            try { InputStream is = getContext().getContentResolver().openInputStream(Uri.parse(btn.customImageUri));
                                btn.skinBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(is), (int)(btn.radius*2), (int)(btn.radius*2), true);
                                is.close(); } catch(Exception e) {}
                        }
                        buttons.add(btn);
                    }
                } catch (Exception e) {}
            }
        }

        // ================= 内置按钮实体 =================
        public static class VirtualButton {
            public String id; public float cx, cy, radius, hitboxRadius;
            public String keyMapStr; public boolean isPressed = false;
            public int color = Color.GRAY, pressedColor = Color.WHITE, alpha = 150, shape = 0, textColor = Color.WHITE;
            public String customImageUri = ""; public Bitmap skinBitmap = null;
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
                        try { parentView.injectKeyEvent(keyMapStr, true); Thread.sleep(turboInterval); parentView.injectKeyEvent(keyMapStr, false); Thread.sleep(turboInterval); } catch (InterruptedException e) { break; }
                    }
                });
            }
            public void stopTurbo() { turboRunning = false; }
        }
    }

    // 接收系统图库选择返回
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 43 && resultCode == RESULT_OK && data != null && gamepadView != null) {
            gamepadView.onImagePicked(data.getData());
        }
    }
}
