package com.haval.akppmodes.can;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * Детект HOLD Mute / Back по кадрам 0x244.
 *
 * action-коды совместимы с LogcatKeyMonitor / SteeringWheelService:
 *   0 = DOWN, 1 = UP, 2 = HOLD, 3 = CANCEL
 *
 * Источник кадров — {@link #onCanFrame(int, byte[])}:
 * подключи сюда то, что реально отдаёт CAN на ГУ
 * (CarProperty, native socket, dump из другого сервиса).
 */
public class CanKeyMonitor {

    private static final String TAG = "CanKeyMonitor";

    /** Совместимость с KEY_BACK / KEY_MUTE в сервисе */
    public static final int KEY_BACK = 2006;
    public static final int KEY_MUTE = 1006;

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_HOLD = 2;

    /** Сколько мс бит должен быть 1, чтобы считать HOLD */
    private static final long HOLD_MS = 1200L;

    public interface Listener {
        void onHardKey(int keyCode, int action);
    }

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private boolean muteDown;
    private boolean backDown;
    private boolean muteHoldSent;
    private boolean backHoldSent;
    private long muteDownAt;
    private long backDownAt;
    private int lastCounter = -1;

    public CanKeyMonitor(Listener listener) {
        this.listener = listener;
    }

    /**
     * Вызывать на каждый принятый CAN-кадр.
     * @param canId  ожидается 0x244
     * @param data   8 байт payload
     */
    public void onCanFrame(int canId, byte[] data) {
        if (canId != CanWheelFrame.CAN_ID) return;
        CanWheelFrame f = CanWheelFrame.parse(data);
        if (f == null) return;

        // отсев дублей одного и того же счётчика
        if (f.counter == lastCounter) return;
        lastCounter = f.counter;

        Log.d(TAG, f.toString());
        handleButton(KEY_MUTE, f.mute);
        handleButton(KEY_BACK, f.back);
    }

    private void handleButton(int keyCode, boolean pressed) {
        long now = SystemClock.uptimeMillis();
        if (keyCode == KEY_MUTE) {
            if (pressed && !muteDown) {
                muteDown = true;
                muteHoldSent = false;
                muteDownAt = now;
                emit(keyCode, ACTION_DOWN);
            } else if (pressed && muteDown && !muteHoldSent
                    && (now - muteDownAt) >= HOLD_MS) {
                muteHoldSent = true;
                emit(keyCode, ACTION_HOLD);
            } else if (!pressed && muteDown) {
                muteDown = false;
                emit(keyCode, ACTION_UP);
            }
        } else if (keyCode == KEY_BACK) {
            if (pressed && !backDown) {
                backDown = true;
                backHoldSent = false;
                backDownAt = now;
                emit(keyCode, ACTION_DOWN);
            } else if (pressed && backDown && !backHoldSent
                    && (now - backDownAt) >= HOLD_MS) {
                backHoldSent = true;
                emit(keyCode, ACTION_HOLD);
            } else if (!pressed && backDown) {
                backDown = false;
                emit(keyCode, ACTION_UP);
            }
        }
    }

    private void emit(int keyCode, int action) {
        Log.i(TAG, "emit keyCode=" + keyCode + " action=" + action);
        main.post(() -> {
            if (listener != null) listener.onHardKey(keyCode, action);
        });
    }

    /** Для теста без реальной шины */
    public void injectTestFrame(boolean mute, boolean back, int counter) {
        byte[] d = new byte[8];
        d[6] = (byte) ((mute ? (1 << 4) : 0) | (back ? (1 << 0) : 0)); // bit5=1<<4, bit1=1<<0
        d[7] = (byte) (counter & 0x0F);
        onCanFrame(CanWheelFrame.CAN_ID, d);
    }
}
