package com.haval.akppmodes.car;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

/**
 * Stub для com.gwm.android.adapter.IDataChangedListener.
 * GA вызывает onDataChanged(key, value) при изменении подписанных ключей.
 *
 * Формат onTransact (из H9-cluster):
 *   enforceInterface(DESCRIPTOR)
 *   readString()  → key
 *   readString()  → value
 *   return true
 */
public class GwmDataListener extends Binder implements IInterface {

    private static final String TAG = "GwmDataListener";

    public static final String DESCRIPTOR =
            "com.gwm.android.adapter.IDataChangedListener";

    public interface Callback {
        void onDataChanged(String key, String value);
    }

    private final Callback callback;

    public GwmDataListener(Callback callback) {
        this.callback = callback;
        attachInterface(this, DESCRIPTOR);
    }

    @Override
    public IBinder asBinder() {
        return this;
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            if (reply != null) reply.writeString(DESCRIPTOR);
            return true;
        }

        if (code == 1) {
            data.enforceInterface(DESCRIPTOR);
            String key = data.readString();
            String value = data.readString();
            Log.i(TAG, "PUSH key=" + key + " value=" + value);
            if (callback != null) {
                callback.onDataChanged(
                        key != null ? key : "?",
                        value != null ? value : "");
            }
            // В H9-cluster reply не пишется — просто return true
            return true;
        }

        return super.onTransact(code, data, reply, flags);
    }
}