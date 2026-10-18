package com.haval.akppmodes.service;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Читает sys.other.keyevent_notify через gwm_adapter (getData),
 * т.к. registerListener с нашего uid сервер в подписчики не добавляет.
 * Опрос ~50 мс — достаточно, чтобы поймать HOLD (action=2).
 */
public class GaKeyMonitor {

    private static final String TAG = "GaKeyMonitor";
    private static final String ADAPTER_SERVICE = "gwm_adapter";
    private static final String ADAPTER_DESCRIPTOR = "com.gwm.android.adapter.IGwmAdapterService";
    private static final String KEY_EVENT_ID = "sys.other.keyevent_notify";
    private static final long POLL_MS = 50L;

    public interface Callback {
        void onHardKey(int keyCode, int action);
    }

    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private IBinder adapterBinder;
    private String lastValue = "";
    private boolean running = false;
    private int workingGetTx = -1;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                String value = readKeyEvent();
                if (value != null && !value.equals(lastValue)) {
                    Log.i(TAG, "Changed: " + lastValue + " -> " + value);
                    lastValue = value;
                    parseAndDispatch(value);
                }
            } catch (Throwable t) {
                Log.w(TAG, "poll error", t);
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    public GaKeyMonitor(Context context, Callback callback) {
        this.callback = callback;
    }

    public void start() {
        if (!bindAdapter()) {
            Log.e(TAG, "gwm_adapter unavailable, retry");
            handler.postDelayed(this::start, 2000);
            return;
        }
        workingGetTx = findGetDataTransaction();
        if (workingGetTx < 0) {
            Log.e(TAG, "No working getData transaction");
            handler.postDelayed(this::start, 3000);
            return;
        }
        Log.i(TAG, "Polling " + KEY_EVENT_ID + " via tx=" + workingGetTx);
        running = true;
        lastValue = "";
        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(pollRunnable);
        adapterBinder = null;
    }

    private boolean bindAdapter() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);
            IBinder b = (IBinder) getService.invoke(null, ADAPTER_SERVICE);
            if (b != null && b.isBinderAlive()) {
                adapterBinder = b;
                return true;
            }
        } catch (Throwable t) {
            Log.e(TAG, "bindAdapter", t);
        }
        return false;
    }

    private int findGetDataTransaction() {
        for (int tx = 1; tx <= 25; tx++) {
            String v = tryGetData(tx);
            // даже null/пусто может быть валидным ответом без exception
            if (v != null) {
                Log.i(TAG, "getData probe tx=" + tx + " sample=[" + v + "]");
                return tx;
            }
        }
        return -1;
    }

    private String readKeyEvent() {
        if (workingGetTx < 0 || adapterBinder == null) return null;
        return tryGetData(workingGetTx);
    }

    private String tryGetData(int tx) {
        if (adapterBinder == null || !adapterBinder.isBinderAlive()) return null;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(ADAPTER_DESCRIPTOR);
            data.writeString(KEY_EVENT_ID);
            boolean ok = adapterBinder.transact(tx, data, reply, 0);
            if (!ok) return null;
            reply.readException();
            return reply.readString();
        } catch (Exception e) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void parseAndDispatch(String value) {
        if (value == null) return;
        String v = value.trim();
        if (v.startsWith("[")) v = v.substring(1);
        if (v.endsWith("]")) v = v.substring(0, v.length() - 1);
        String[] parts = v.split(",");
        if (parts.length < 2) return;
        try {
            int keyCode = Integer.parseInt(parts[0].trim());
            int action = Integer.parseInt(parts[1].trim());
            Log.i(TAG, "Parsed keyCode=" + keyCode + " action=" + action);
            if (callback != null) {
                callback.onHardKey(keyCode, action);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "bad value: " + value);
        }
    }
}
