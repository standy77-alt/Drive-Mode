package com.haval.akppmodes.can;

import android.content.Context;
import android.util.Log;

/**
 * Заготовка источника CAN.
 *
 * На user-APK прямой /dev/can0 обычно недоступен.
 * Варианты на ГУ (нужно проверить dumpsys / наличие):
 *  1) android.car property / vendor extension
 *  2) binder-сервис OEM, который стримит raw frames
 *  3) root + SocketCAN
 *
 * Пока {@link #start()} только логирует; когда найдёте канал —
 * в callback вызывайте monitor.onCanFrame(0x244, payload8).
 */
public class CanBusSource {

    private static final String TAG = "CanBusSource";

    private final Context context;
    private final CanKeyMonitor monitor;
    private volatile boolean running;

    public CanBusSource(Context context, CanKeyMonitor monitor) {
        this.context = context.getApplicationContext();
        this.monitor = monitor;
    }

    public void start() {
        running = true;
        Log.w(TAG, "CanBusSource: нет привязки к шине. "
                + "Подключите onCanFrame(0x244, data[8]) из CarService/native.");
        // Пример, когда появится byte[] frame:
        // monitor.onCanFrame(CanWheelFrame.CAN_ID, frame);
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    /** Прокинуть кадр снаружи (тест / другой сервис) */
    public void feed(int canId, byte[] data) {
        if (running && monitor != null) {
            monitor.onCanFrame(canId, data);
        }
    }
}
