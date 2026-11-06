package com.haval.akppmodes.car;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;

public class CarModeManager {

    private static final String TAG = "CarModeManager";

    private static final String KEY_DRIVE_MODE = "car.drive_setting.drive_mode";
    private static final String KEY_STEERING_MODE = "car.drive_setting.steering_wheel_assist_mode"; // Ключ руля
    private static final String KEY_EVENT = "sys.other.keyevent_notify";

    public static final String KEY_JOURNEY_ODO = "car.basic.cur_journey_odometer";
    public static final String KEY_TOTAL_ODO   = "car.basic.total_odometer";
    public static final String KEY_AVG_FUEL_A  = "car.basic.cur_journey_avg_fuel_consumption_a";
    public static final String KEY_AVG_FUEL_B  = "car.basic.avg_fuel_consumption";

    private static final String[] MONITORED_KEYS = new String[] {
            KEY_EVENT,
            KEY_JOURNEY_ODO,
            KEY_TOTAL_ODO,
            KEY_AVG_FUEL_A,
            KEY_AVG_FUEL_B,
            KEY_STEERING_MODE
    };

    private static final String ADAPTER = "gwm_adapter";
    private static final String DESCRIPTOR = "com.gwm.android.adapter.IGwmAdapterService";
    private static final String SPOOF_PKG = "com.gwm.app.vehicle.center";

    private static final int TX_GET_DATA = 1;
    private static final int TX_SET_DATA = 2;
    private static final int TX_REGISTER = 3;
    private static final int TX_UNREGISTER = 4;

    private static final String PREFS = "drive_mode_prefs";
    private static final String PREF_LAST_MODE = "last_mode_code";
    private static final String PREF_SELECTED_MODES = "selected_modes";
    private static final String PREF_BACK_KEY_ENABLED = "back_key_trip_enabled"; 
    
    // НАСТРОЙКИ ДЛЯ РУЛЯ
    private static final String PREF_STEERING_SYNC = "steering_sync_enabled";
    private static final String PREF_FIXED_STEERING_MODE = "fixed_steering_mode";
    
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

    public void applyMode(DrivingMode mode) {
        if (mode == null) return;
        String value = String.valueOf(mode.getCode());
        if (!ensureBinder()) return;

        if (setDataTx(KEY_DRIVE_MODE, value)) {
            saveModeToPrefs(mode.getCode());
        }
    }

    public void applySteeringMode(int modeCode) {
        if (!ensureBinder()) return;
        setDataTx(KEY_STEERING_MODE, String.valueOf(modeCode));
        Log.i(TAG, "Applied steering mode: " + modeCode);
    }

    private boolean setDataTx(String key, String value) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(1);
            data.writeString(SPOOF_PKG);
            data.writeStringArray(new String[]{key});
            data.writeStringArray(new String[]{value});
            data.writeInt(0);

            boolean ok = binder.transact(TX_SET_DATA, data, reply, 0);
            if (!ok) return false;
            reply.readException();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "setData exception: " + e);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public String[] readKeys(String[] keys) {
        if (!ensureBinder() || keys == null || keys.length == 0) return null;

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(1);
            data.writeString(SPOOF_PKG);
            data.writeStringArray(keys);
            data.writeStringArray(null); 
            data.writeInt(1);

            if (!binder.transact(TX_GET_DATA, data, reply, 0)) {
                return null;
            }
            reply.readException();
            return reply.createStringArray();
        } catch (Exception e) {
            Log.e(TAG, "readKeys exception: " + e);
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public String readKey(String key) {
        String[] res = readKeys(new String[]{key});
        if (res != null && res.length > 0) {
            return res[0];
        }
        return null;
    }

    public boolean testSubscribeKeyEvents(GwmDataListener.Callback cb) {
        if (!ensureBinder()) return false;

        keyListener = new GwmDataListener((key, value) -> {
            if (cb != null) cb.onDataChanged(key, value);
        });

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(SPOOF_PKG);
            data.writeStringArray(MONITORED_KEYS);
            data.writeStrongBinder(keyListener);

            boolean ok = binder.transact(TX_REGISTER, data, reply, 0);
            if (!ok) return false;
            reply.readException();
            int result = reply.dataAvail() >= 4 ? reply.readInt() : 0;
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

    public void unregister() {
        if (!registered || binder == null || !binder.isBinderAlive() || keyListener == null) return;

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(SPOOF_PKG);
            data.writeStrongBinder(keyListener);
            if (binder.transact(TX_UNREGISTER, data, reply, 0)) {
                reply.readException();
            }
        } catch (Exception ignored) {
        } finally {
            registered = false;
            data.recycle();
            reply.recycle();
        }
    }

    public int[] getSelectedModes() {
        String saved = prefs.getString(PREF_SELECTED_MODES, DEFAULT_SELECTED_MODES);
        if (saved == null || saved.isEmpty()) saved = DEFAULT_SELECTED_MODES;
        String[] parts = saved.split(",");
        int[] result = new int[parts.length];
        int count = 0;
        for (String p : parts) {
            try { result[count++] = Integer.parseInt(p.trim()); } catch (NumberFormatException ignored) {}
        }
        if (count == 0) return new int[]{0, 1};
        if (count < result.length) {
            int[] trimmed = new int[count];
            System.arraycopy(result, 0, trimmed, 0, count);
            return trimmed;
        }
        return result;
    }

    public void saveSelectedModes(int[] codes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codes.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(codes[i]);
        }
        prefs.edit().putString(PREF_SELECTED_MODES, sb.toString()).apply();
    }

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
        if (currentIdx == -1) return DrivingMode.fromCode(selected[0]);
        return DrivingMode.fromCode(selected[(currentIdx + 1) % selected.length]);
    }

    public DrivingMode getCurrentMode() {
        int saved = prefs.getInt(PREF_LAST_MODE, 0);
        return DrivingMode.fromCode(saved);
    }

    public void resetToStandard() {
        saveModeToPrefs(DrivingMode.STANDARD.getCode());
    }

    private void saveModeToPrefs(int code) {
        prefs.edit().putInt(PREF_LAST_MODE, code).apply();
    }

    public boolean isBackKeyTripEnabled() {
        return prefs.getBoolean(PREF_BACK_KEY_ENABLED, true);
    }

    public void setBackKeyTripEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_BACK_KEY_ENABLED, enabled).apply();
    }

    public boolean isSteeringSyncEnabled() {
        return prefs.getBoolean(PREF_STEERING_SYNC, true);
    }

    public void setSteeringSyncEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_STEERING_SYNC, enabled).apply();
    }

    public int getFixedSteeringMode() {
        return prefs.getInt(PREF_FIXED_STEERING_MODE, 1);
    }

    public void setFixedSteeringMode(int modeCode) {
        prefs.edit().putInt(PREF_FIXED_STEERING_MODE, modeCode).apply();
    }

    public void destroy() {
        unregister();
        binder = null;
        keyListener = null;
    }
}