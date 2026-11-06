package com.haval.akppmodes.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Presentation;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

import com.gwm.app.hardkey.HardKeyManager;
import com.haval.akppmodes.car.CarModeManager;
import com.haval.akppmodes.car.DrivingMode;
import com.haval.akppmodes.trip.TripAccumulator;
import com.haval.akppmodes.trip.TripSummary;
import com.haval.akppmodes.trip.TripSummaryView;
import com.haval.akppmodes.trip.TripTelemetry;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SteeringWheelService extends Service {

    private static final String TAG = "SteeringWheelService";
    private static final String REMOTE_PACKAGE = "com.gwm.app.hardkey.server";
    private static final String REMOTE_SERVICE = "com.gwm.app.hardkey.server.HardKeyService";
    private static final String CHANNEL_ID = "akpp_modes_channel";

    private static final int KEY_BACK = 2006;
    private static final int KEY_BACK_MEDIA = 1006;

    private static final int ACTION_DOWN = 0;
    private static final int ACTION_UP = 1;
    private static final int ACTION_CANCEL = 3;

    private static final long MUTE_HOLD_MS = 1000L;

    private HardKeyManager mKeyManager;
    private CarModeManager mCarModeManager;
    private boolean mIsBound = false;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private DrivingMode mSelectedMode = DrivingMode.STANDARD;

    // === СТАТИСТИКА ПОЕЗДКИ ===
    private Presentation mStatsPresentation;
    private Display mClusterDisplay;
    private TripAccumulator mTripAccumulator;

    private float mCurrentTotalOdo = Float.NaN;
    private float mAvgFuelA = Float.NaN;
    private float mAvgFuelB = Float.NaN;

    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private final Runnable mMuteHoldRunnable = () -> {
        // 1. Переключаем режим двигателя
        mSelectedMode = mCarModeManager.getNextSelectedMode(mSelectedMode);
        mCarModeManager.applyMode(mSelectedMode);
        Log.e(TAG, "Mute HOLD 1.0s -> switched to " + mSelectedMode.getTitle());

        // 2. Проверяем, нужно ли фиксировать руль
        if (!mCarModeManager.isSteeringSyncEnabled()) {
            int fixedSteering = mCarModeManager.getFixedSteeringMode();
            // Даем машине 300 мс, чтобы применить режим двигателя, затем перебиваем руль
            mHandler.postDelayed(() -> {
                mCarModeManager.applySteeringMode(fixedSteering);
                Log.e(TAG, "Fixed steering mode applied: " + fixedSteering);
            }, 300);
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mKeyManager = new HardKeyManager(
                    service,
                    getPackageName(),
                    event -> {
                        if (event == null) return false;
                        int kc = event.getKeyCode();
                        int act = event.getAction();
                        if (kc <= 3 && act >= 2000) {
                            int t = kc; kc = act; act = t;
                        }
                        
                        // Если событие нужно обрабатывать нашему приложению
                        if (isHandledKey(kc)) {
                            handleKey(kc, act);
                            return true; // Съедаем событие
                        }
                        return false; // Отдаем событие другим приложениям
                    },
                    SteeringWheelService.this::scheduleRebind
            );
            mKeyManager.registerKeys(HardKeyManager.STEERING_KEYS);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mKeyManager = null;
            scheduleRebind();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mCarModeManager = new CarModeManager(this);
        mSelectedMode = mCarModeManager.getCurrentMode();

        mTripAccumulator = TripAccumulator.start(System.currentTimeMillis());

        startInForeground();

        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        mClusterDisplay = dm.getDisplay(2);

        subscribeToGwmData();

        KeyAccessibilityService.setListener((keyCode, action, repeat) -> {
            if (!isHandledKey(keyCode)) return;
            if (action == KeyEvent.ACTION_DOWN && repeat == 0) {
                handleKey(keyCode, ACTION_DOWN);
            } else if (action == KeyEvent.ACTION_UP) {
                handleKey(keyCode, ACTION_UP);
            }
        });

        bindToHardKeyService();
        mHandler.postDelayed(this::pollTelemetryAndFeedAccumulator, 1500L);
    }

    private void subscribeToGwmData() {
        mCarModeManager.testSubscribeKeyEvents((key, value) -> {
            if ("sys.other.keyevent_notify".equals(key)) {
                int[] ev = parseKeyEvent(value);
                if (ev != null) handleKey(ev[0], ev[1]);
                return;
            }
            processTelemetryKeyValue(key, value);
        });
    }

    private void processTelemetryKeyValue(String key, String rawValue) {
        float val = parseCleanFloat(rawValue);
        if (Float.isNaN(val)) return;

        long nowMs = System.currentTimeMillis();

        if (CarModeManager.KEY_TOTAL_ODO.equals(key)) {
            if (val > 0.0f) {
                mCurrentTotalOdo = val;
                feedAccumulator(nowMs);
            }
        } else if (CarModeManager.KEY_AVG_FUEL_A.equals(key)) {
            if (val > 0.0f && val < 50.0f) {
                mAvgFuelA = val;
                feedAccumulator(nowMs);
            }
        } else if (CarModeManager.KEY_AVG_FUEL_B.equals(key)) {
            if (val > 0.0f && val < 50.0f) {
                mAvgFuelB = val;
                feedAccumulator(nowMs);
            }
        }
    }

    private void feedAccumulator(long nowMs) {
        if (mTripAccumulator == null) return;
        boolean odoValid = !Float.isNaN(mCurrentTotalOdo) && mCurrentTotalOdo > 0.0f;
        float activeFuel = !Float.isNaN(mAvgFuelA) ? mAvgFuelA : mAvgFuelB;
        boolean fuelValid = !Float.isNaN(activeFuel) && activeFuel > 0.0f;

        TripTelemetry telemetry = new TripTelemetry(nowMs, odoValid ? (double) mCurrentTotalOdo : 0.0, odoValid, fuelValid ? activeFuel : 0.0f, fuelValid);
        mTripAccumulator.update(telemetry);
    }

    private void pollTelemetryAndFeedAccumulator() {
        String[] keys = new String[] { CarModeManager.KEY_TOTAL_ODO, CarModeManager.KEY_AVG_FUEL_A, CarModeManager.KEY_AVG_FUEL_B };
        String[] values = mCarModeManager.readKeys(keys);
        if (values != null) {
            int count = Math.min(keys.length, values.length);
            for (int i = 0; i < count; i++) processTelemetryKeyValue(keys[i], values[i]);
        }
    }

    private float parseCleanFloat(String value) {
        if (value == null) return Float.NaN;
        String norm = value.trim();
        if (norm.isEmpty() || "null".equalsIgnoreCase(norm) || "waiting".equalsIgnoreCase(norm)) return Float.NaN;
        try { return Float.parseFloat(norm); } catch (NumberFormatException ignored) {
            Matcher m = NUMBER_PATTERN.matcher(norm);
            if (m.find()) { try { return Float.parseFloat(m.group()); } catch (NumberFormatException ignored2) {} }
            return Float.NaN;
        }
    }

    private int[] parseKeyEvent(String value) {
        if (value == null) return null;
        String v = value.trim().replace("[", "").replace("]", "");
        String[] parts = v.split(",");
        if (parts.length < 2) return null;
        try { return new int[]{ Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) }; } 
        catch (NumberFormatException e) { return null; }
    }

    private void startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Drive Mode", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
            Notification n = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Drive Mode")
                    .setContentText("Hold Mute → режимы | Back → статистика")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .build();
            startForeground(201, n);
        }
    }

    private void bindToHardKeyService() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(REMOTE_PACKAGE, REMOTE_SERVICE));
        try { mIsBound = bindService(intent, mConnection, Context.BIND_AUTO_CREATE); } catch (Exception ignored) {}
    }

    private boolean isHandledKey(int keyCode) {
        if (keyCode == KEY_BACK) {
            return mCarModeManager != null && mCarModeManager.isBackKeyTripEnabled();
        }
        return keyCode == KEY_BACK_MEDIA;
    }

    private void handleKey(int keyCode, int action) {
        if (!isHandledKey(keyCode)) return;
        
        if (keyCode == KEY_BACK) {
            handleBack(action);
        } else {
            handleMute(action);
        }
    }

    private synchronized void handleBack(int action) {
        if (action == ACTION_UP) {
            toggleTripStats();
        }
    }

    private synchronized void handleMute(int action) {
        if (action == ACTION_DOWN) {
            mHandler.postDelayed(mMuteHoldRunnable, MUTE_HOLD_MS);
        } else if (action == ACTION_UP || action == ACTION_CANCEL) {
            mHandler.removeCallbacks(mMuteHoldRunnable);
        }
    }

    private void toggleTripStats() {
        mHandler.post(() -> {
            if (mStatsPresentation == null) {
                showTripSummary();
            } else {
                hideTripSummary();
            }
        });
    }

    private void showTripSummary() {
        if (mStatsPresentation != null || mClusterDisplay == null) return;
        try {
            pollTelemetryAndFeedAccumulator();
            long nowMs = System.currentTimeMillis();
            TripSummary summary = mTripAccumulator.finish(nowMs);

            mStatsPresentation = new Presentation(this, mClusterDisplay) {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    Window window = getWindow();
                    if (window != null) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    }
                    final TripSummaryView view = new TripSummaryView(getContext(), summary);
                    final Presentation self = this;
                    view.setOnDismissListener(() -> { if (mStatsPresentation == self) hideTripSummary(); });
                    setContentView(view);
                }
            };
            mStatsPresentation.show();
        } catch (Exception e) {
            Log.e(TAG, "CRASH PREVENTED in showTripSummary: " + e.getMessage(), e);
            mStatsPresentation = null;
        }
    }

    private void hideTripSummary() {
        if (mStatsPresentation != null) {
            try { mStatsPresentation.dismiss(); } catch (Exception ignored) {}
            mStatsPresentation = null;
        }
    }

    private void scheduleRebind() {
        if (mIsBound) { try { unbindService(mConnection); } catch (Exception ignored) {} mIsBound = false; }
        mHandler.postDelayed(this::bindToHardKeyService, 3000);
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        KeyAccessibilityService.setListener(null);
        if (mKeyManager != null) mKeyManager.release();
        if (mCarModeManager != null) mCarModeManager.destroy();
        if (mIsBound) { try { unbindService(mConnection); } catch (Exception ignored) {} }
        hideTripSummary();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}