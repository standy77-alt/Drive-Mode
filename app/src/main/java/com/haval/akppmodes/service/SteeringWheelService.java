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

import com.gwm.app.hardkey.HardKeyEvent;
import com.gwm.app.hardkey.HardKeyManager;
import com.haval.akppmodes.car.CarModeManager;
import com.haval.akppmodes.car.DrivingMode;
import com.haval.akppmodes.trip.TripAccumulator;
import com.haval.akppmodes.trip.TripSummary;
import com.haval.akppmodes.trip.TripSummaryView;
import com.haval.akppmodes.trip.TripTelemetry;

public class SteeringWheelService extends Service {

    private static final String TAG = "SteeringWheelService";
    private static final String REMOTE_PACKAGE = "com.gwm.app.hardkey.server";
    private static final String REMOTE_SERVICE = "com.gwm.app.hardkey.server.HardKeyService";
    private static final String CHANNEL_ID = "akpp_modes_channel";

    private static final int KEY_BACK = 2006;
    private static final int KEY_BACK_MEDIA = 1006;

    private static final int ACTION_DOWN = 0;
    private static final int ACTION_UP = 1;
    private static final int ACTION_HOLD = 2;
    private static final int ACTION_CANCEL = 3;

    // === НАСТРОЙКИ ВРЕМЕНИ УДЕРЖАНИЯ ===
    private static final long PUSH_COOLDOWN_MS = 500L;  
    private static final long MUTE_HOLD_MS = 1000L;     // ИЗМЕНЕНО НА 1 СЕКУНДУ (1000 мс)

    private HardKeyManager mKeyManager;
    private CarModeManager mCarModeManager;
    private boolean mIsBound = false;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean mMuteTriggered = false;
    private long mLastHoldTs = 0L;
    private DrivingMode mSelectedMode = DrivingMode.STANDARD;

    // === ПЕРЕМЕННЫЕ ДЛЯ СТАТИСТИКИ ===
    private Presentation mStatsPresentation;
    private TripAccumulator mTripAccumulator;
    private Display mClusterDisplay;
    private float mCurrentOdo = 0f;
    private float mCurrentFuel = 0f;
    private boolean mHasOdo = false;
    private boolean mHasFuel = false;

    // Задача для Mute
    private final Runnable mMuteHoldRunnable = () -> {
        mMuteTriggered = true;
        mLastHoldTs = System.currentTimeMillis();
        mSelectedMode = mCarModeManager.getNextSelectedMode(mSelectedMode);
        mCarModeManager.applyMode(mSelectedMode);
        Log.e(TAG, "Mute HOLD 1.0s -> switched to " + mSelectedMode.getTitle());
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e(TAG, "Connected to HardKeyService");
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
                        handleKey(kc, act);
                        return isHandledKey(kc);
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
        startInForeground();

        mTripAccumulator = TripAccumulator.start(System.currentTimeMillis());

        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        mClusterDisplay = dm.getDisplay(2);

        subscribeToKeyEvents();

        KeyAccessibilityService.setListener((keyCode, action, repeat) -> {
            if (!isHandledKey(keyCode)) return;
            if (action == KeyEvent.ACTION_DOWN) {
                if (repeat == 0) handleKey(keyCode, ACTION_DOWN);
                // Убрали repeat >= 15, так как у нас свой таймер
            } else if (action == KeyEvent.ACTION_UP) {
                handleKey(keyCode, ACTION_UP);
            }
        });

        bindToHardKeyService();
    }

    private void subscribeToKeyEvents() {
        mCarModeManager.testSubscribeKeyEvents((key, value) -> {
            if ("sys.other.keyevent_notify".equals(key)) {
                int[] ev = parseKeyEvent(value);
                if (ev != null) handleKey(ev[0], ev[1]);
            }
            
            if (value != null) {
                if ("car.basic.cur_journey_odometer".equals(key)) {
                    try { 
                        mCurrentOdo = Float.parseFloat(value.trim()); 
                        mHasOdo = true;
                        mTripAccumulator.update(new TripTelemetry(System.currentTimeMillis(), mCurrentOdo, true, mCurrentFuel, mHasFuel));
                    } catch (Exception ignored) {}
                } else if ("car.basic.cur_journey_avg_fuel_consumption_a".equals(key)) {
                    try { 
                        mCurrentFuel = Float.parseFloat(value.trim()); 
                        mHasFuel = true;
                        mTripAccumulator.update(new TripTelemetry(System.currentTimeMillis(), mCurrentOdo, mHasOdo, mCurrentFuel, true));
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private int[] parseKeyEvent(String value) {
        if (value == null) return null;
        String v = value.trim().replace("[", "").replace("]", "");
        String[] parts = v.split(",");
        if (parts.length < 2) return null;
        try {
            return new int[]{ Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
        } catch (NumberFormatException e) { return null; }
    }

    private void startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Drive Mode", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
            Notification n = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Drive Mode")
                    .setContentText("Hold Mute → переключение режимов")
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

    private static boolean isHandledKey(int keyCode) {
        return keyCode == KEY_BACK || keyCode == KEY_BACK_MEDIA;
    }

    private void handleKey(int keyCode, int action) {
        if (!isHandledKey(keyCode)) return;
        if (keyCode == KEY_BACK) handleBack(action);
        else handleMute(action);
    }

    private synchronized void handleBack(int action) {
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            toggleTripStats();
        }
    }

    // === ОБРАБОТКА MUTE (1 СЕКУНДА) ===
    private synchronized void handleMute(int action) {
        if (action == ACTION_DOWN) {
            mMuteTriggered = false;
            mHandler.postDelayed(mMuteHoldRunnable, MUTE_HOLD_MS);
            return;
        }

        if (action == ACTION_UP || action == ACTION_CANCEL) {
            mHandler.removeCallbacks(mMuteHoldRunnable);
            return;
        }

        if (action == ACTION_HOLD) {
            return; 
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
            TripSummary summary = mTripAccumulator.finish(System.currentTimeMillis());
            
            mStatsPresentation = new Presentation(this, mClusterDisplay) {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    
                    Window window = getWindow();
                    if (window != null) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                        window.addFlags(
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        );
                        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    }

                    TripSummaryView view = new TripSummaryView(getContext(), summary);
                    view.setOnDismissListener(() -> hideTripSummary());
                    
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