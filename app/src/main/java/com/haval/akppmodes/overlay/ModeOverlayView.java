package com.haval.akppmodes.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import com.haval.akppmodes.car.DrivingMode;

/**
 * Оверлей режимов АКПП на приборке (Display ID 2).
 *
 * Порядок как у gwm-split IPOverlayService:
 *   1) Presentation на getDisplay(2)
 *   2) fallback: WindowManager + TYPE_APPLICATION_OVERLAY (0x7f6)
 *      flags = 0x308 (NOT_FOCUSABLE | LAYOUT_IN_SCREEN | LAYOUT_NO_LIMITS)
 */
public class ModeOverlayView {

    private static final String TAG = "ModeOverlayView";
    /** Приборка Haval / GWM SA8155 — как в gwm-split IPOverlayService */
    private static final int TARGET_DISPLAY_ID = 2;

    /** TYPE_APPLICATION_OVERLAY */
    private static final int TYPE_OVERLAY = 0x7f6; // 2038
    /**
     * FLAG_NOT_FOCUSABLE (0x8)
     * | FLAG_LAYOUT_IN_SCREEN (0x100)
     * | FLAG_LAYOUT_NO_LIMITS (0x200)
     * = 0x308 — как TopOverlayService gwm-split
     */
    private static final int FLAGS_OVERLAY = 0x308;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Display targetDisplay;
    private Context displayContext;
    private WindowManager windowManager;
    private TextView textView;
    private WindowManager.LayoutParams layoutParams;

    private ClusterPresentation presentation;
    private boolean isAttached = false;
    private boolean usePresentation = false;

    public ModeOverlayView(Context context) {
        appContext = context.getApplicationContext();

        DisplayManager dm = (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null) {
            for (Display d : dm.getDisplays()) {
                Log.i(TAG, "display id=" + d.getDisplayId()
                        + " name=" + d.getName()
                        + " state=" + d.getState());
            }
            targetDisplay = dm.getDisplay(TARGET_DISPLAY_ID);
        }

        if (targetDisplay != null) {
            displayContext = appContext.createDisplayContext(targetDisplay);
            Log.i(TAG, "Display " + TARGET_DISPLAY_ID + " OK → Presentation path preferred");
        } else {
            displayContext = appContext;
            Log.w(TAG, "Display " + TARGET_DISPLAY_ID + " missing → WM on default display");
        }

        windowManager = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
        textView = createTextView();
        layoutParams = createLayoutParams();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean canDraw = Settings.canDrawOverlays(appContext);
            Log.i(TAG, "SYSTEM_ALERT_WINDOW=" + canDraw);
            if (!canDraw) {
                Log.e(TAG, "Нужно: appops set com.haval.akppmodes SYSTEM_ALERT_WINDOW allow");
            }
        }
    }

    private TextView createTextView() {
        TextView tv = new TextView(displayContext);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        tv.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        tv.setGravity(Gravity.CENTER);
        int padH = dpToPx(28);
        int padV = dpToPx(12);
        tv.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E6121722"));
        bg.setCornerRadius(dpToPx(14));
        bg.setStroke(dpToPx(2), Color.parseColor("#00E5FF"));
        tv.setBackground(bg);
        return tv;
    }

    private WindowManager.LayoutParams createLayoutParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                TYPE_OVERLAY,
                FLAGS_OVERLAY
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );
        // TOP | CENTER_HORIZONTAL — режимы по центру сверху приборки
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dpToPx(72);
        return params;
    }

    public void showSelecting(DrivingMode mode) {
        mainHandler.post(() -> {
            String text = "◀  " + mode.getTitle().toUpperCase() + "  ▶";
            setText(text);
            attachIfNeeded();
        });
    }

    public void showApplied(DrivingMode mode) {
        mainHandler.post(() -> {
            String text = "✓  " + mode.getTitle().toUpperCase();
            setText(text);
            attachIfNeeded();
        });
    }

    public void hide() {
        mainHandler.post(() -> {
            if (usePresentation && presentation != null) {
                try {
                    presentation.dismiss();
                } catch (Exception ignored) {}
                presentation = null;
                isAttached = false;
                usePresentation = false;
                Log.i(TAG, "Presentation dismissed");
                return;
            }
            if (isAttached && textView != null && windowManager != null) {
                try {
                    windowManager.removeView(textView);
                } catch (Exception ignored) {}
                isAttached = false;
                Log.i(TAG, "WM overlay removed");
            }
        });
    }

    public void destroy() {
        mainHandler.removeCallbacksAndMessages(null);
        hide();
    }

    private void setText(String text) {
        if (textView != null) {
            textView.setText(text);
        }
        if (usePresentation && presentation != null) {
            presentation.setModeText(text);
        }
    }

    /**
     * Как gwm-split IP: сначала Presentation(display 2), иначе WM overlay.
     */
    private void attachIfNeeded() {
        if (isAttached) return;

        if (tryPresentationFirst()) {
            return;
        }
        tryWindowManager();
    }

    /** Путь IPOverlayService: Presentation на display 2 */
    private boolean tryPresentationFirst() {
        if (targetDisplay == null) {
            Log.w(TAG, "No display 2 — skip Presentation");
            return false;
        }
        try {
            presentation = new ClusterPresentation(
                    appContext, targetDisplay,
                    textView != null ? textView.getText().toString() : "MODE");
            presentation.show();
            isAttached = true;
            usePresentation = true;
            Log.i(TAG, ">>> Presentation on display " + TARGET_DISPLAY_ID + " (gwm-split IP path)");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Presentation failed, fallback to WM", e);
            presentation = null;
            return false;
        }
    }

    private void tryWindowManager() {
        // flags как TopOverlayService: type 0x7f6, flags 0x308+
        layoutParams.type = TYPE_OVERLAY;
        try {
            windowManager.addView(textView, layoutParams);
            isAttached = true;
            usePresentation = false;
            Log.i(TAG, ">>> WM TYPE_APPLICATION_OVERLAY (0x7f6) flags=0x308");
            return;
        } catch (Exception e) {
            Log.w(TAG, "type 0x7f6 failed: " + e.getMessage());
        }

        int[] fallback = {2038, 2003, 2002, 2005};
        for (int type : fallback) {
            layoutParams.type = type;
            try {
                windowManager.addView(textView, layoutParams);
                isAttached = true;
                usePresentation = false;
                Log.i(TAG, ">>> WM fallback type=" + type);
                return;
            } catch (Exception e) {
                Log.w(TAG, "type " + type + " failed: " + e.getMessage());
            }
        }
        Log.e(TAG, "All overlay paths failed");
    }

    private int dpToPx(int dp) {
        float density = displayContext.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
