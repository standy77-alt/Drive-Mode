package com.haval.akppmodes.car;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Управление режимом АКПП через gwm_adapter.
 *
 * setData (tx=2): spoof packageName=vehicle.center (у него SET_DATA granted=true).
 * getData (tx=1): тот же spoof (иначе Permission Denial).
 * register (tx=3): spoof + GwmDataListener (push sys.other.keyevent_notify).
 * unregister (tx=4): spoof + тот же listener.
 */
public class CarModeManager {

    private static final String TAG = "CarModeManager";

    private static final String KEY_DRIVE_MODE = "car.drive_setting.drive_mode";
    private static final String KEY_EVENT = "sys.other.keyevent_notify";

    private static final String ADAPTER = "gwm_adapter";
    private static final String DESCRIPTOR = "com.gwm.android.adapter.IGwmAdapterService";
    private static final String SPOOF_PKG = "com.gwm.app.vehicle.center";

    private static final int TX_GET_DATA = 1;
    private static final int TX_SET_DATA = 2;
    private static final int TX_REGISTER = 3;
    private static final int TX_UNREGISTER = 4;

    private static final String PREFS = "drive_mode_prefs";
    private static final String PREF_LAST_MODE = "last_mode_code";
    // Новые константы для сохранения выбранных режимов (по умолчанию 0,1 = Normal, Sport)
    private static final String PREF_SELECTED_MODES = "selected_modes";
    private static final String DEFAULT_SELECTED_MODES = "0,1";

    private final Context context;
    private final SharedPreferences prefs;
    private IBinder binder;
    private GwmDataListener keyListener;
    private boolean registered = false;

    public CarModeManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        bind();
    }

    private void bind() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);
            binder = (IBinder) getService.invoke(null, ADAPTER);
            Log.i(TAG, binder != null ? "gwm_adapter binder OK" : "gwm_adapter null");
        } catch (Throwable t) {
            Log.e(TAG, "bind failed", t);
            binder = null;
        }
    }

    private boolean ensureBinder() {
        if (binder == null || !binder.isBinderAlive()) {
            bind();
        }
        return binder != null;
    }

    // ============================================================
    //  ЗАПИСЬ (setData, tx=2)
    // ============================================================

    public void applyMode(DrivingMode mode) {
        if (mode == null) return;
        String value = String.valueOf(mode.getCode());
        Log.i(TAG, "applyMode " + mode.getTitle() + " code=" + value);

        if (!ensureBinder()) {
            Log.e(TAG, "applyMode: no binder");
            return;
        }

        if (setDataTx(value)) {
            Log.i(TAG, "SUCCESS setData drive_mode=" + value);
            saveModeToPrefs(mode.getCode());
        } else {
            Log.e(TAG, "FAILED setData drive_mode=" + value);
        }
    }

    private boolean setDataTx(String value) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(1);
            data.writeString(SPOOF_PKG);
            data.writeStringArray(new String[]{KEY_DRIVE_MODE});
            data.writeStringArray(new String[]{value});
            data.writeInt(0);

            boolean ok = binder.transact(TX_SET_DATA, data, reply, 0);
            if (!ok) {
                Log.e(TAG, "setData transact false");
                return false;
            }
            reply.readException();
            int result = reply.dataAvail() >= 4 ? reply.readInt() : 0;
            Log.i(TAG, "setData reply result=" + result);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "setData exception: " + e);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    // ============================================================
    //  ЧТЕНИЕ (getData, tx=1)
    // ============================================================

    public String readKey(String key) {
        if (!ensureBinder()) return null;

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(1);
            data.writeString(SPOOF_PKG);
            data.writeStringArray(new String[]{key});
            data.writeStringArray(new String[0]);
            data.writeInt(1);

            boolean ok = binder.transact(TX_GET_DATA, data, reply, 0);
            if (!ok) {
                Log.w(TAG, "getData transact false key=" + key);
                return null;
            }
            reply.readException();

            int avail = reply.dataAvail();
            if (avail == 0) {
                Log.w(TAG, "getData empty reply for key=" + key);
                return null;
            }

            int startPos = reply.dataPosition();

            try {
                reply.setDataPosition(startPos);
                String[] arr = reply.createStringArray();
                if (arr != null && arr.length > 0) {
                    String v = arr[0] != null ? arr[0] : "";
                    Log.i(TAG, "getData String[] key=" + key + " -> " + v);
                    return v;
                }
            } catch (Exception ignored) {}

            try {
                reply.setDataPosition(startPos);
                String s = reply.readString();
                if (s != null) {
                    Log.i(TAG, "getData String key=" + key + " -> " + s);
                    return s;
                }
            } catch (Exception ignored) {}

            try {
                reply.setDataPosition(startPos);
                int[] ints = reply.createIntArray();
                if (ints != null && ints.length > 0) {
                    String v = Arrays.toString(ints);
                    Log.i(TAG, "getData int[] key=" + key + " -> " + v);
                    return v;
                }
            } catch (Exception ignored) {}

            try {
                reply.setDataPosition(startPos);
                int i = reply.readInt();
                String v = Integer.toString(i);
                Log.i(TAG, "getData int key=" + key + " -> " + v);
                return v;
            } catch (Exception ignored) {}

            Log.w(TAG, "getData: unknown format key=" + key + " avail=" + avail);
            return null;

        } catch (Exception e) {
            Log.e(TAG, "getData exception key=" + key + ": " + e);
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public String testReadKeyEvent() {
        String v = readKey(KEY_EVENT);
        Log.i(TAG, "testReadKeyEvent [" + KEY_EVENT + "] = " + v);
        return v;
    }

    // ============================================================
    //  ПОДПИСКА (register, tx=3)
    // ============================================================

    public boolean testSubscribeKeyEvents(GwmDataListener.Callback cb) {
        if (!ensureBinder()) {
            Log.e(TAG, "subscribe: no binder");
            return false;
        }

        keyListener = new GwmDataListener((key, value) -> {
            Log.i(TAG, "PUSH key=" + key + " value=" + value);
            if (cb != null) cb.onDataChanged(key, value);
        });

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(SPOOF_PKG);
            data.writeStringArray(new String[]{KEY_EVENT});
            data.writeStrongBinder(keyListener);

            boolean ok = binder.transact(TX_REGISTER, data, reply, 0);
            if (!ok) {
                Log.e(TAG, "register transact false");
                return false;
            }
            reply.readException();
            int result = reply.dataAvail() >= 4 ? reply.readInt() : 0;
            Log.i(TAG, "register reply result=" + result);
            registered = (result == 1 || result == 0);
            return registered;

        } catch (Exception e) {
            Log.e(TAG, "register exception: " + e);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    // ============================================================
    //  ОТПИСКА (unregister, tx=4)
    // ============================================================

    public void unregister() {
        if (!registered) return;
        if (binder == null || !binder.isBinderAlive()) return;
        if (keyListener == null) return;

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(SPOOF_PKG);
            data.writeStrongBinder(keyListener);

            boolean ok = binder.transact(TX_UNREGISTER, data, reply, 0);
            if (!ok) {
                Log.w(TAG, "unregister transact false");
                return;
            }
            reply.readException();
            int result = reply.dataAvail() >= 4 ? reply.readInt() : 0;
            Log.i(TAG, "unregister reply result=" + result);
        } catch (Exception e) {
            Log.w(TAG, "unregister exception: " + e);
        } finally {
            registered = false;
            data.recycle();
            reply.recycle();
        }
    }

    // ============================================================
    //  НОВОЕ: Работа с выбранными режимами (галочки)
    // ============================================================

    /** Возвращает массив кодов выбранных пользователем режимов */
    public int[] getSelectedModes() {
        String saved = prefs.getString(PREF_SELECTED_MODES, DEFAULT_SELECTED_MODES);
        if (saved == null || saved.isEmpty()) saved = DEFAULT_SELECTED_MODES;
        String[] parts = saved.split(",");
        int[] result = new int[parts.length];
        int count = 0;
        for (String p : parts) {
            try {
                result[count++] = Integer.parseInt(p.trim());
            } catch (NumberFormatException ignored) {}
        }
        if (count == 0) return new int[]{0, 1}; // Fallback
        if (count < result.length) {
            int[] trimmed = new int[count];
            System.arraycopy(result, 0, trimmed, 0, count);
            return trimmed;
        }
        return result;
    }

    /** Сохраняет массив кодов выбранных режимов */
    public void saveSelectedModes(int[] codes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codes.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(codes[i]);
        }
        prefs.edit().putString(PREF_SELECTED_MODES, sb.toString()).apply();
        Log.i(TAG, "Saved selected modes: " + sb.toString());
    }

    /** Вычисляет следующий режим из списка выбранных */
    public DrivingMode getNextSelectedMode(DrivingMode current) {
        int[] selected = getSelectedModes();
        if (selected.length == 0) return current;

        int currentIdx = -1;
        for (int i = 0; i < selected.length; i++) {
            if (selected[i] == current.getCode()) {
                currentIdx = i;
                break;
            }
        }

        // Если текущий режим не в списке, переключаем на первый из списка
        if (currentIdx == -1) {
            return DrivingMode.fromCode(selected[0]);
        }

        // Иначе берем следующий по кругу
        int nextIdx = (currentIdx + 1) % selected.length;
        return DrivingMode.fromCode(selected[nextIdx]);
    }

    // ============================================================
    //  SharedPreferences (базовые)
    // ============================================================

    public DrivingMode getCurrentMode() {
        int saved = prefs.getInt(PREF_LAST_MODE, 0);
        DrivingMode mode = DrivingMode.fromCode(saved);
        Log.i(TAG, "getCurrentMode (from prefs) -> " + mode.getTitle()
                + " (code=" + saved + ")");
        return mode;
    }

    public void resetToStandard() {
        saveModeToPrefs(DrivingMode.STANDARD.getCode());
        Log.i(TAG, "resetToStandard");
    }

    private void saveModeToPrefs(int code) {
        prefs.edit().putInt(PREF_LAST_MODE, code).apply();
        Log.i(TAG, "Saved mode to prefs: " + code);
    }

    // ============================================================
    //  destroy
    // ============================================================

    public void destroy() {
        unregister();
        binder = null;
        keyListener = null;
    }
}