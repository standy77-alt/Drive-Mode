package com.haval.akppmodes.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Перехват клавиш руля через Accessibility (flagRequestFilterKeyEvents).
 * GWM HardKey/GA нам 2006 не отдаёт — пробуем системный фильтр клавиш.
 */
public class KeyAccessibilityService extends AccessibilityService {

    private static final String TAG = "KeyAccessibility";
    private static final int KEY_BACK = 2006;

    public interface KeyListener {
        void onKey(int keyCode, int action, int repeatCount);
    }

    private static volatile KeyListener sListener;

    public static void setListener(KeyListener listener) {
        sListener = listener;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);
        Log.i(TAG, "onServiceConnected, filterKeyEvents enabled");
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event == null) return super.onKeyEvent(event);
        int keyCode = event.getKeyCode();
        int action = event.getAction(); // 0=DOWN, 1=UP
        int repeat = event.getRepeatCount();
        Log.i(TAG, "onKeyEvent keyCode=" + keyCode + " action=" + action
                + " repeat=" + repeat + " source=" + event.getSource());

        KeyListener l = sListener;
        if (l != null) {
            try {
                l.onKey(keyCode, action, repeat);
            } catch (Throwable t) {
                Log.e(TAG, "listener error", t);
            }
        }
        // Не блокируем систему, только слушаем (для Back можно true при оверлее)
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // не требуется
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "onInterrupt");
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        sListener = null;
        super.onDestroy();
    }
}
