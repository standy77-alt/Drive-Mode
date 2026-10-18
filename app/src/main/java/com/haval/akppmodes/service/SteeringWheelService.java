package com.haval.akppmodes.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.gwm.app.hardkey.HardKeyEvent;
import com.gwm.app.hardkey.HardKeyManager;
import com.haval.akppmodes.car.CarModeManager;
import com.haval.akppmodes.car.DrivingMode;
import android.view.KeyEvent;

/**
 * Триггер: удержание Mute (1006) / Back (2006) на руле.
 * Приём: PUSH через GA listener (com.gwm.android.adapter.IDataChangedListener).
 * Логика: HOLD → переключение на следующий выбранный режим → applyMode.
 */
public class SteeringWheelService extends Service {

    private static final String TAG = "SteeringWheelService";
    private static final String REMOTE_PACKAGE = "com.gwm.app.hardkey.server";
    private static final String REMOTE_SERVICE = "com.gwm.app.hardkey.server.HardKeyService";
    private static final String CHANNEL_ID = "akpp_modes_channel";

    /** Кнопка Back в режиме БК */
    private static final int KEY_BACK = 2006;
    /** Кнопка Mute / Back в мультимедиа — работает везде */
    private static final int KEY_BACK_MEDIA = 1006;

    private static final int ACTION_DOWN = 0;
    private static final int ACTION_UP = 1;
    private static final int ACTION_HOLD = 2;
    private static final int ACTION_CANCEL = 3;

    /** Защита от повторных push-событий с одинаковым action */
    private static final long PUSH_COOLDOWN_MS = 500L;

    private HardKeyManager mKeyManager;
    private CarModeManager mCarModeManager;
    private boolean mIsBound = false;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean mHoldHandled = false;
    private long mLastHoldTs = 0L;
    private DrivingMode mSelectedMode = DrivingMode.STANDARD;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "Connected to HardKeyService");
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
                        Log.i(TAG, "HardKey IPC keyCode=" + kc + " action=" + act);
                        handleKey(kc, act);
                        return isToggleKey(kc);
                    },
                    SteeringWheelService.this::scheduleRebind
            );
            boolean ok = mKeyManager.registerKeys(HardKeyManager.STEERING_KEYS);
            Log.i(TAG, "HardKey registerKeys result=" + ok);
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
        Log.i(TAG, "onCreate");
        mCarModeManager = new CarModeManager(this);
        mSelectedMode = mCarModeManager.getCurrentMode();
        startInForeground();

        // ГЛАВНЫЙ ПУТЬ: push через GA listener
        subscribeToKeyEvents();

        // Accessibility как запасной путь
        KeyAccessibilityService.setListener((keyCode, action, repeat) -> {
            if (!isToggleKey(keyCode)) return;
            if (action == KeyEvent.ACTION_DOWN) {
                if (repeat == 0) {
                    handleKey(keyCode, ACTION_DOWN);
                } else if (repeat >= 15) {
                    handleKey(keyCode, ACTION_HOLD);
                }
            } else if (action == KeyEvent.ACTION_UP) {
                handleKey(keyCode, ACTION_UP);
            }
        });

        // HardKey IPC — тоже запасной (для медиа-кнопок)
        bindToHardKeyService();
    }

    /** Подписка на sys.other.keyevent_notify через GwmAdapterService */
    private void subscribeToKeyEvents() {
        boolean ok = mCarModeManager.testSubscribeKeyEvents((key, value) -> {
            if (!"sys.other.keyevent_notify".equals(key)) return;
            int[] ev = parseKeyEvent(value);
            if (ev == null) return;
            handleKey(ev[0], ev[1]);
        });
        Log.i(TAG, "subscribeToKeyEvents result=" + ok);
    }

    /** "[1006,2]" → [1006, 2] */
    private int[] parseKeyEvent(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith("[")) v = v.substring(1);
        if (v.endsWith("]")) v = v.substring(0, v.length() - 1);
        String[] parts = v.split(",");
        if (parts.length < 2) return null;
        try {
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Drive Mode", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
            Notification n = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Drive Mode")
                    .setContentText("Hold Mute/Back → переключение режимов")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .build();
            startForeground(201, n);
        }
    }

    private void bindToHardKeyService() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(REMOTE_PACKAGE, REMOTE_SERVICE));
        try {
            mIsBound = bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            Log.i(TAG, "HardKey bindService result=" + mIsBound);
        } catch (Exception e) {
            Log.e(TAG, "HardKey bind failed", e);
        }
    }

    private static boolean isToggleKey(int keyCode) {
        return keyCode == KEY_BACK || keyCode == KEY_BACK_MEDIA;
    }

    private void handleKey(int keyCode, int action) {
        if (!isToggleKey(keyCode)) return;
        handleBack(action);
    }

    private synchronized void handleBack(int action) {
        if (action == ACTION_DOWN) {
            mHoldHandled = false;
            return;
        }

        if (action == ACTION_HOLD) {
            if (mHoldHandled) return;

            long now = System.currentTimeMillis();
            if (now - mLastHoldTs < PUSH_COOLDOWN_MS) {
                Log.i(TAG, "HOLD ignored (cooldown " + (now - mLastHoldTs) + "ms)");
                return;
            }
            mHoldHandled = true;
            mLastHoldTs = now;

            // ИЗМЕНЕНИЕ ЗДЕСЬ: теперь берем следующий режим из списка выбранных
            mSelectedMode = mCarModeManager.getNextSelectedMode(mSelectedMode);
            
            Log.i(TAG, "HOLD → switch to " + mSelectedMode.getTitle()
                    + " code=" + mSelectedMode.getCode());
            mCarModeManager.applyMode(mSelectedMode);
        }
    }

    private void scheduleRebind() {
        if (mIsBound) {
            try { unbindService(mConnection); } catch (Exception ignored) {}
            mIsBound = false;
        }
        mHandler.postDelayed(this::bindToHardKeyService, 3000);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        mHandler.removeCallbacksAndMessages(null);
        KeyAccessibilityService.setListener(null);
        if (mKeyManager != null) mKeyManager.release();
        if (mCarModeManager != null) mCarModeManager.destroy();
        if (mIsBound) {
            try { unbindService(mConnection); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}