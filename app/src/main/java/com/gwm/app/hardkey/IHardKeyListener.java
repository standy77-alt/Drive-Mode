package com.gwm.app.hardkey;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Колбэк-интерфейс низкоуровневых аппаратных клавиш.
 * Дескриптор должен точно совпадать с системным.
 */
public interface IHardKeyListener extends IInterface {

    String DESCRIPTOR = "com.gwm.app.hardkey.IHardKeyListener";

    /**
     * @return true — событие обработано, штатная реакция ГУ блокируется
     */
    boolean onKeyEvent(HardKeyEvent event) throws RemoteException;

    abstract class Stub extends Binder implements IHardKeyListener {

        static final int TRANSACTION_onKeyEvent = IBinder.FIRST_CALL_TRANSACTION;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IHardKeyListener asInterface(IBinder obj) {
            if (obj == null) return null;
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IHardKeyListener) {
                return (IHardKeyListener) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    if (reply != null) reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_onKeyEvent: {
                    data.enforceInterface(DESCRIPTOR);
                    HardKeyEvent event = null;
                    if (data.readInt() != 0) {
                        event = HardKeyEvent.CREATOR.createFromParcel(data);
                    }
                    boolean result = this.onKeyEvent(event);
                    if (reply != null) {
                        reply.writeNoException();
                        reply.writeInt(result ? 1 : 0);
                    }
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements IHardKeyListener {
            private final IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            @Override
            public boolean onKeyEvent(HardKeyEvent event) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                boolean result;
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (event != null) {
                        data.writeInt(1);
                        event.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    mRemote.transact(TRANSACTION_onKeyEvent, data, reply, 0);
                    reply.readException();
                    result = (reply.readInt() != 0);
                } finally {
                    reply.recycle();
                    data.recycle();
                }
                return result;
            }
        }
    }
}
