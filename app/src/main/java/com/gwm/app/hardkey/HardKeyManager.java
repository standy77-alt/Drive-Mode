package com.gwm.app.hardkey;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

/**
 * Клиент IPC к HardKeyService.
 * Коды с руля (из GA sys.other.keyevent_notify):
 *   2006 — Back (0=DOWN, 1=UP, 2=HOLD, 3=CANCEL)
 *   2002, 2003, 2004 — соседние кнопки
 */
public class HardKeyManager {

    private static final String TAG = "HardKeyManager";
    private static final String SERVICE_DESCRIPTOR = "com.gwm.app.hardkey.IHardKeyService";

    public static final int TRANSACTION_REGISTER = 1;
    public static final int TRANSACTION_UNREGISTER = 2;

    public static final int KEY_BACK = 2006;
    public static final int KEY_2002 = 2002;
    public static final int KEY_2003 = 2003;
    public static final int KEY_2004 = 2004;

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_HOLD = 2;
    public static final int ACTION_CANCEL = 3;

    public static final int[] STEERING_KEYS = new int[]{
            KEY_BACK, KEY_2002, KEY_2003, KEY_2004
    };

    public interface KeyCallback {
        boolean onKeyIntercepted(HardKeyEvent event);
    }

    private final IBinder mRemoteBinder;
    private final String mPackageName;
    private final KeyCallback mCallback;
    private final IBinder.DeathRecipient mDeathRecipient;

    private final IHardKeyListener.Stub mListenerStub = new IHardKeyListener.Stub() {
        @Override
        public boolean onKeyEvent(HardKeyEvent event) throws RemoteException {
            if (mCallback != null) {
                try {
                    return mCallback.onKeyIntercepted(event);
                } catch (Throwable t) {
                    Log.e(TAG, "Callback exception", t);
                }
            }
            return false;
        }
    };

    public HardKeyManager(IBinder remoteBinder, String packageName,
                          KeyCallback callback, Runnable onServiceDied) {
        this.mRemoteBinder = remoteBinder;
        this.mPackageName = packageName;
        this.mCallback = callback;
        this.mDeathRecipient = () -> {
            Log.w(TAG, "HardKeyService died");
            if (onServiceDied != null) onServiceDied.run();
        };
        try {
            this.mRemoteBinder.linkToDeath(mDeathRecipient, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "linkToDeath failed", e);
        }
    }

    public boolean registerKeys(int[] keyCodes) {
        if (mRemoteBinder == null || !mRemoteBinder.isBinderAlive()) {
            Log.e(TAG, "Binder dead");
            return false;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeString(mPackageName);
            data.writeIntArray(keyCodes);
            data.writeStrongBinder(mListenerStub.asBinder());
            boolean ok = mRemoteBinder.transact(TRANSACTION_REGISTER, data, reply, 0);
            if (!ok) {
                Log.e(TAG, "transact false");
                return false;
            }
            reply.readException();
            Log.i(TAG, "Registered keys count=" + keyCodes.length);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "registerKeys failed", e);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public boolean unregisterKeys() {
        if (mRemoteBinder == null || !mRemoteBinder.isBinderAlive()) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeString(mPackageName);
            data.writeStrongBinder(mListenerStub.asBinder());
            mRemoteBinder.transact(TRANSACTION_UNREGISTER, data, reply, 0);
            reply.readException();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "unregisterKeys failed", e);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public void release() {
        try { unregisterKeys(); } catch (Exception ignored) {}
        if (mRemoteBinder != null) {
            try { mRemoteBinder.unlinkToDeath(mDeathRecipient, 0); } catch (Exception ignored) {}
        }
    }
}
