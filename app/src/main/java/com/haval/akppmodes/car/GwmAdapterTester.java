package com.haval.akppmodes.car;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import java.lang.reflect.Method;

public class GwmAdapterTester {
    private static final String TAG = "GwmTest";
    private static final String ADAPTER = "gwm_adapter";
    private static final String DESCRIPTOR = "com.gwm.android.adapter.IGwmAdapterService";
    private static final String SPOOF_PKG = "com.gwm.app.vehicle.center";
    private static final int TX_GET_DATA = 1;

    public static void test() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, ADAPTER);
            
            if (binder == null) {
                Log.e(TAG, "Binder gwm_adapter равен null!");
                return;
            }

            // Пробуем ключи, в которых может лежать конфигурация
            String[] keysToTest = new String[]{
                    "car.config.vcfg",
                    "car.vehicle.config",
                    "sys.vcfg",
                    "car.config.raw"
            };

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(1);
                data.writeString(SPOOF_PKG);
                data.writeStringArray(keysToTest);
                data.writeStringArray(null); // null = читаем
                data.writeInt(1);

                if (binder.transact(TX_GET_DATA, data, reply, 0)) {
                    reply.readException();
                    String[] result = reply.createStringArray();
                    if (result != null) {
                        for (int i = 0; i < keysToTest.length; i++) {
                            Log.i(TAG, "Ключ: " + keysToTest[i] + " -> Значение: " + result[i]);
                        }
                    } else {
                        Log.e(TAG, "Сервис вернул null вместо массива.");
                    }
                } else {
                    Log.e(TAG, "Транзакция не удалась (binder.transact вернул false)");
                }
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при тесте: ", e);
        }
    }
}