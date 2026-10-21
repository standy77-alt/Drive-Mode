package com.haval.akppmodes.overlay;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class ClusterPresentation extends Presentation {

    private TextView mModeTextView;
    private String mInitialModeText;

    public ClusterPresentation(Context outerContext, Display display, String modeText) {
        super(outerContext, display);
        this.mInitialModeText = modeText != null ? modeText : "DRIVE MODE";
        initWindowProperties();
    }

    private void initWindowProperties() {
        Window window = getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(getContext());
        root.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#E6121722"));
        cardBg.setCornerRadius(dpToPx(12));
        cardBg.setStroke(dpToPx(2), Color.parseColor("#00E5FF"));

        FrameLayout card = new FrameLayout(getContext());
        card.setBackground(cardBg);
        int padH = dpToPx(24);
        int padV = dpToPx(10);
        card.setPadding(padH, padV, padH, padV);

        mModeTextView = new TextView(getContext());
        mModeTextView.setText(mInitialModeText);
        mModeTextView.setTextColor(Color.WHITE);
        mModeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        mModeTextView.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        mModeTextView.setGravity(Gravity.CENTER);

        card.addView(mModeTextView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        cardParams.topMargin = dpToPx(80);
        root.addView(card, cardParams);

        setContentView(root);
    }

    public void setModeText(String text) {
        mInitialModeText = text;
        if (mModeTextView != null) {
            mModeTextView.setText(text);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                getContext().getResources().getDisplayMetrics());
    }
}