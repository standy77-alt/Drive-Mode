package com.haval.akppmodes.service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Читает logcat и ловит GA/InputService:
 *   sys.other.keyevent_notify=[2006,2]
 *   InputService: setData keyCode = 2006, action = 2
 *
 * Нужно право READ_LOGS (на ГУ обычно выдаётся shell):
 *   pm grant com.haval.akppmodes android.permission.READ_LOGS
 *   или: appops set com.haval.akppmodes READ_LOGS allow
 */
public class LogcatKeyMonitor {

    private static final String TAG = "LogcatKeyMonitor";

    /** [keyCode, action] в notify */
    private static final Pattern PATTERN_NOTIFY = Pattern.compile(
            "keyevent_notify=\\[(\\d+)\\s*,\\s*(\\d+)\\]");

    /** InputService: keyCode = 2006, action = 2 */
    private static final Pattern PATTERN_INPUT = Pattern.compile(
            "keyCode\\s*=\\s*(\\d+)\\s*,\\s*action\\s*=\\s*(\\d+)");

    public interface Callback {
        void onHardKey(int keyCode, int action);
    }

    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread worker;
    private Process process;

    /** Антидребезг: не слать одно и то же событие дважды подряд */
    private int lastKey = -1;
    private int lastAction = -1;
    private long lastTs = 0;

    public LogcatKeyMonitor(Callback callback) {
        this.callback = callback;
    }

    public void start() {
        if (running.getAndSet(true)) return;
        worker = new Thread(this::loop, "LogcatKeyMonitor");
        worker.setDaemon(true);
        worker.start();
        Log.i(TAG, "started");
    }

    public void stop() {
        running.set(false);
        if (process != null) {
            try {
                process.destroy();
            } catch (Exception ignored) {}
            process = null;
        }
        if (worker != null) {
            try {
                worker.interrupt();
            } catch (Exception ignored) {}
            worker = null;
        }
        Log.i(TAG, "stopped");
    }

    private void loop() {
        while (running.get()) {
            try {
                // -v brief — короче строки; фильтр по тегам + общий буфер
                ProcessBuilder pb = new ProcessBuilder(
                        "logcat",
                        "-v", "brief",
                        "-T", "1",
                        "InputService:I",
                        "GA-server:I",
                        "GA-DataChangeTask:I",
                        "*:S"
                );
                pb.redirectErrorStream(true);
                process = pb.start();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()), 8192);

                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    parseLine(line);
                }
            } catch (Exception e) {
                Log.e(TAG, "logcat failed, retry in 2s", e);
            } finally {
                if (process != null) {
                    try {
                        process.destroy();
                    } catch (Exception ignored) {}
                    process = null;
                }
            }
            if (running.get()) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void parseLine(String line) {
        if (line == null || line.isEmpty()) return;

        Matcher m = PATTERN_NOTIFY.matcher(line);
        if (!m.find()) {
            m = PATTERN_INPUT.matcher(line);
            if (!m.find()) return;
        }

        try {
            int keyCode = Integer.parseInt(m.group(1));
            int action = Integer.parseInt(m.group(2));
            dispatch(keyCode, action);
        } catch (NumberFormatException ignored) {}
    }

    private void dispatch(int keyCode, int action) {
        long now = System.currentTimeMillis();
        // один и тот же event < 30 мс — дубль из двух тегов
        if (keyCode == lastKey && action == lastAction && (now - lastTs) < 30) {
            return;
        }
        lastKey = keyCode;
        lastAction = action;
        lastTs = now;

        Log.i(TAG, "Parsed keyCode=" + keyCode + " action=" + action);
        if (callback != null) {
            mainHandler.post(() -> {
                try {
                    callback.onHardKey(keyCode, action);
                } catch (Throwable t) {
                    Log.e(TAG, "callback error", t);
                }
            });
        }
    }
}
