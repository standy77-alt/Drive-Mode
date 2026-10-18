package com.gwm.app.hardkey;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * Parcelable событие аппаратной клавиши GWM.
 * Поддерживает оба возможных порядка полей (action/keyCode) и
 * расширенный формат (action, keyCode, flags, repeatCount).
 */
public class HardKeyEvent implements Parcelable {

    private static final String TAG = "HardKeyEvent";

    private int mAction;
    private int mKeyCode;
    private int mFlags;
    private int mRepeatCount;

    public HardKeyEvent() {}

    public HardKeyEvent(int action, int keyCode) {
        this.mAction = action;
        this.mKeyCode = keyCode;
    }

    protected HardKeyEvent(Parcel in) {
        // Читаем до 4 int-ов. На практике сервер может писать 2 или 4.
        int available = in.dataAvail() / 4;
        if (available >= 4) {
            // Формат из GWMPUI_IPC_SPEC: action, keyCode, flags, repeat
            mAction = in.readInt();
            mKeyCode = in.readInt();
            mFlags = in.readInt();
            mRepeatCount = in.readInt();
        } else if (available >= 2) {
            int first = in.readInt();
            int second = in.readInt();
            // Детект инверсии: если first == 2006 или похож на keyCode (> 100),
            // а second маленький (0/1/2) — это keyCode + action
            if (first > 100 && second < 10) {
                mKeyCode = first;
                mAction = second;
            } else {
                // action + keyCode
                mAction = first;
                mKeyCode = second;
            }
        } else {
            Log.w(TAG, "HardKeyEvent parcel too short, available ints=" + available);
        }
        Log.d(TAG, "Parsed event: keyCode=" + mKeyCode + " action=" + mAction
                + " flags=" + mFlags + " repeat=" + mRepeatCount);
    }

    public int getKeyCode() {
        return mKeyCode;
    }

    public int getAction() {
        return mAction;
    }

    public int getFlags() {
        return mFlags;
    }

    public int getRepeatCount() {
        return mRepeatCount;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAction);
        dest.writeInt(mKeyCode);
        dest.writeInt(mFlags);
        dest.writeInt(mRepeatCount);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<HardKeyEvent> CREATOR = new Creator<HardKeyEvent>() {
        @Override
        public HardKeyEvent createFromParcel(Parcel in) {
            return new HardKeyEvent(in);
        }

        @Override
        public HardKeyEvent[] newArray(int size) {
            return new HardKeyEvent[size];
        }
    };
}
