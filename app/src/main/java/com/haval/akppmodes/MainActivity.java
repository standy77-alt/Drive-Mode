package com.haval.akppmodes;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.haval.akppmodes.car.CarModeManager;
import com.haval.akppmodes.car.GwmAdapterTester;
import com.haval.akppmodes.car.DrivingMode;
import com.haval.akppmodes.service.SteeringWheelService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int BG              = Color.parseColor("#090A0F"); 
    private static final int CARD_TOP        = Color.parseColor("#161821"); 
    private static final int CARD_BOTTOM     = Color.parseColor("#111219"); 
    private static final int CARD_BORDER     = Color.parseColor("#262936"); 

    private static final int ITEM_OFF_BG     = Color.parseColor("#1A1C26");
    private static final int ITEM_OFF_BORDER = Color.parseColor("#252836");
    private static final int ITEM_ON_BG      = Color.parseColor("#19333A"); 
    private static final int ITEM_ON_BORDER  = Color.parseColor("#3898A6"); 

    private static final int TEXT_PRIMARY    = Color.parseColor("#F2F4F8");
    private static final int TEXT_SECONDARY  = Color.parseColor("#8A90A2");
    private static final int TEXT_MUTED      = Color.parseColor("#5C6170");
    private static final int ACCENT          = Color.parseColor("#287D8A"); 
    private static final int ACCENT_PRESSED  = Color.parseColor("#1D5C66");
    private static final int BTN_SEC_BG      = Color.parseColor("#1F222E"); 
    private static final int BTN_SEC_BORDER  = Color.parseColor("#32374A");

    private static final int STATUS_OK       = Color.parseColor("#4ADE80");
    private static final int STATUS_OFF      = Color.parseColor("#F87171");
    private static final int WARNING_COLOR   = Color.parseColor("#FBBF24"); 

    private TextView mStatusTitle;
    private TextView mStatusDetail;
    private Button mMainBtn;
    private Button mOverlayBtn;
    private CarModeManager mCarMode;

    private LinearLayout mSteeringSelectionLayout;
    private Button btnSteerComfort, btnSteerSport, btnSteerLight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        GwmAdapterTester.test();

        int pad = dp(12); 
        int padTop = dp(4);
        mCarMode = new CarModeManager(this);

        int colWidth       = dp(290);                     
        int colGap         = dp(42);                      
        int totalGridWidth = colWidth * 2 + colGap;       
        int cardWidth      = totalGridWidth + dp(64);     

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(pad, padTop, pad, pad); 
        root.setGravity(Gravity.CENTER); 

        TextView title = new TextView(this);
        title.setText("DRIVE MODE");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(26f); 
        styleText(title, fontMedium(), 0.18f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("УПРАВЛЕНИЕ РЕЖИМАМИ ДВИЖЕНИЯ");
        subtitle.setTextColor(TEXT_SECONDARY);
        subtitle.setTextSize(12f);
        styleText(subtitle, fontMedium(), 0.12f);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(2), 0, dp(8)); 
        root.addView(subtitle, matchWrap());

        // ===== 1. Карточка статуса =====
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(cardDrawable(dp(18)));
        statusCard.setPadding(dp(24), dp(12), dp(24), dp(12)); 
        statusCard.setGravity(Gravity.CENTER);

        mStatusTitle = new TextView(this);
        mStatusTitle.setTextSize(17f); 
        styleText(mStatusTitle, fontMedium(), 0.06f);
        mStatusTitle.setGravity(Gravity.CENTER);
        statusCard.addView(mStatusTitle, matchWrap());

        mStatusDetail = new TextView(this);
        mStatusDetail.setTextColor(TEXT_SECONDARY);
        mStatusDetail.setTextSize(14f); 
        styleText(mStatusDetail, fontRegular(), 0.02f);
        mStatusDetail.setGravity(Gravity.CENTER);
        mStatusDetail.setPadding(0, dp(4), 0, dp(10));
        statusCard.addView(mStatusDetail, matchWrap());

        mMainBtn = new Button(this);
        mMainBtn.setText("Перезапустить службу");
        mMainBtn.setTextColor(TEXT_PRIMARY);
        mMainBtn.setTextSize(15f); 
        styleText(mMainBtn, fontMedium(), 0.03f);
        mMainBtn.setAllCaps(false);
        mMainBtn.setBackground(borderedBox(BTN_SEC_BG, BTN_SEC_BORDER, dp(10), dp(1)));
        mMainBtn.setOnClickListener(v -> {
            mMainBtn.setBackground(borderedBox(ACCENT_PRESSED, ITEM_ON_BORDER, dp(10), dp(1)));
            launchService();
            mMainBtn.postDelayed(() -> mMainBtn.setBackground(
                    borderedBox(BTN_SEC_BG, BTN_SEC_BORDER, dp(10), dp(1))), 180);
        });

        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(totalGridWidth, dp(42));
        restartLp.gravity = Gravity.CENTER_HORIZONTAL;
        statusCard.addView(mMainBtn, restartLp);

        mOverlayBtn = new Button(this);
        mOverlayBtn.setText("Разрешить оверлей");
        mOverlayBtn.setTextColor(TEXT_PRIMARY);
        mOverlayBtn.setTextSize(15f); 
        styleText(mOverlayBtn, fontMedium(), 0.03f);
        mOverlayBtn.setAllCaps(false);
        mOverlayBtn.setBackground(borderedBox(BTN_SEC_BG, BTN_SEC_BORDER, dp(10), dp(1)));
        
        mOverlayBtn.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Toast.makeText(this, "Разрешение уже выдано", Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout.LayoutParams overlayLp = new LinearLayout.LayoutParams(totalGridWidth, dp(42));
        overlayLp.gravity = Gravity.CENTER_HORIZONTAL;
        overlayLp.setMargins(0, dp(8), 0, 0);
        statusCard.addView(mOverlayBtn, overlayLp);

        TextView nextSectionHint = new TextView(this);
        nextSectionHint.setText("▼ выбрать режимы вождения ▼");
        nextSectionHint.setTextColor(TEXT_SECONDARY);
        nextSectionHint.setTextSize(13f);
        styleText(nextSectionHint, fontMedium(), 0.1f);
        nextSectionHint.setGravity(Gravity.CENTER);
        nextSectionHint.setPadding(0, dp(12), 0, 0);
        statusCard.addView(nextSectionHint, matchWrap());

        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.setMargins(0, 0, 0, dp(12)); 
        root.addView(statusCard, statusLp);

        // ===== 2. Карточка выбора режимов =====
        LinearLayout modesCard = new LinearLayout(this);
        modesCard.setOrientation(LinearLayout.VERTICAL);
        modesCard.setBackground(cardDrawable(dp(18)));
        modesCard.setPadding(dp(24), dp(12), dp(24), dp(12));
        modesCard.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout modesGrid = new LinearLayout(this);
        modesGrid.setOrientation(LinearLayout.VERTICAL);
        modesGrid.setGravity(Gravity.CENTER_HORIZONTAL);

        int[] currentSelected = mCarMode.getSelectedModes();
        DrivingMode[] allModes = DrivingMode.values();
        Switch[] switches = new Switch[allModes.length];

        int[][] gridOrder = { {0, 3}, {1, 4}, {2, 5} };

        for (int row = 0; row < 3; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            rowLayout.setPadding(0, dp(3), 0, dp(3)); 

            for (int col = 0; col < 2; col++) {
                int index = gridOrder[row][col];
                if (index >= allModes.length) continue;

                DrivingMode mode = allModes[index];

                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(20), dp(10), dp(18), dp(10)); 

                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(colWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (col == 0) itemLp.setMargins(0, 0, colGap / 2, 0);
                else itemLp.setMargins(colGap / 2, 0, 0, 0);

                TextView name = new TextView(this);
                name.setText(mode.getTitle());
                name.setTextSize(16f); 
                styleText(name, fontMedium(), 0.08f);
                name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                Switch sw = new Switch(this);
                boolean isChecked = false;
                for (int code : currentSelected) {
                    if (code == mode.getCode()) { isChecked = true; break; }
                }
                sw.setChecked(isChecked);
                switches[index] = sw;

                applyItemStyle(item, name, isChecked); 
                sw.setOnCheckedChangeListener((buttonView, checked) -> applyItemStyle(item, name, checked));
                item.setOnClickListener(v -> sw.toggle());

                item.addView(name);
                item.addView(sw);
                rowLayout.addView(item, itemLp);
            }

            modesGrid.addView(rowLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        modesCard.addView(modesGrid, matchWrap());

        Button saveBtn = new Button(this);
        saveBtn.setText("Сохранить");
        saveBtn.setTextColor(TEXT_PRIMARY);
        saveBtn.setTextSize(16f); 
        styleText(saveBtn, fontMedium(), 0.04f);
        saveBtn.setAllCaps(false);
        saveBtn.setBackground(borderedBox(ACCENT, ITEM_ON_BORDER, dp(12), dp(1)));

        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(totalGridWidth, dp(46)); 
        saveLp.gravity = Gravity.CENTER_HORIZONTAL;
        saveLp.setMargins(0, dp(12), 0, 0);
        modesCard.addView(saveBtn, saveLp);

        saveBtn.setOnClickListener(v -> {
            saveBtn.setBackground(borderedBox(ACCENT_PRESSED, ITEM_ON_BORDER, dp(12), dp(1)));
            saveBtn.postDelayed(() -> saveBtn.setBackground(borderedBox(ACCENT, ITEM_ON_BORDER, dp(12), dp(1))), 180);

            List<Integer> selected = new ArrayList<>();
            for (int i = 0; i < switches.length; i++) {
                if (switches[i] != null && switches[i].isChecked()) {
                    selected.add(allModes[i].getCode());
                }
            }
            if (selected.isEmpty()) {
                Toast.makeText(this, "Выберите хотя бы один режим", Toast.LENGTH_SHORT).show();
                return;
            }
            int[] codes = new int[selected.size()];
            for (int i = 0; i < codes.length; i++) codes[i] = selected.get(i);
            mCarMode.saveSelectedModes(codes);
            Toast.makeText(this, "Сохранено режимов: " + selected.size(), Toast.LENGTH_SHORT).show();
        });

        LinearLayout.LayoutParams modesLp = new LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        modesLp.setMargins(0, 0, 0, dp(12)); 
        root.addView(modesCard, modesLp);

        // ===== 3. Карточка настроек (БЕЗ ОБЩЕГО ЗАГОЛОВКА) =====
        LinearLayout settingsCard = new LinearLayout(this);
        settingsCard.setOrientation(LinearLayout.VERTICAL);
        settingsCard.setBackground(cardDrawable(dp(18)));
        settingsCard.setPadding(dp(24), dp(12), dp(24), dp(12));
        settingsCard.setGravity(Gravity.CENTER_HORIZONTAL);

        // 1. Переключатель Back (сразу идет первым)
        boolean isBackEnabled = mCarMode.isBackKeyTripEnabled();
        LinearLayout backSwitchItem = createSwitchItem("Перехват кнопки Back", isBackEnabled, (checked) -> {
            mCarMode.setBackKeyTripEnabled(checked);
        });
        settingsCard.addView(backSwitchItem, matchWrap());

        // РАЗДЕЛИТЕЛЬ "УПРАВЛЕНИЕ РУЛЕМ"
        TextView steeringSettingsTitle = new TextView(this);
        steeringSettingsTitle.setText("УПРАВЛЕНИЕ РУЛЕМ"); 
        steeringSettingsTitle.setTextColor(TEXT_SECONDARY);
        steeringSettingsTitle.setTextSize(13f); 
        styleText(steeringSettingsTitle, fontMedium(), 0.1f);
        steeringSettingsTitle.setGravity(Gravity.CENTER);
        steeringSettingsTitle.setPadding(0, dp(12), 0, dp(6));
        settingsCard.addView(steeringSettingsTitle, matchWrap());

        // 2. Переключатель Руль
        boolean isSteeringSyncEnabled = mCarMode.isSteeringSyncEnabled();
        
        LinearLayout steeringSwitchItem = new LinearLayout(this);
        steeringSwitchItem.setOrientation(LinearLayout.HORIZONTAL);
        steeringSwitchItem.setGravity(Gravity.CENTER_VERTICAL);
        steeringSwitchItem.setPadding(dp(20), dp(10), dp(18), dp(10)); 

        LinearLayout.LayoutParams steeringLp = new LinearLayout.LayoutParams(totalGridWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        steeringLp.gravity = Gravity.CENTER_HORIZONTAL;
        steeringLp.setMargins(0, 0, 0, 0);

        TextView steeringName = new TextView(this);
        steeringName.setText("Синхронизировать руль"); 
        steeringName.setTextSize(16f); 
        styleText(steeringName, fontMedium(), 0.08f);
        steeringName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch steeringSwitch = new Switch(this);
        steeringSwitch.setChecked(isSteeringSyncEnabled);

        applyItemStyle(steeringSwitchItem, steeringName, isSteeringSyncEnabled);

        // Кнопки выбора руля
        mSteeringSelectionLayout = new LinearLayout(this);
        mSteeringSelectionLayout.setOrientation(LinearLayout.HORIZONTAL);
        mSteeringSelectionLayout.setGravity(Gravity.CENTER);
        mSteeringSelectionLayout.setPadding(0, dp(8), 0, 0);
        mSteeringSelectionLayout.setVisibility(isSteeringSyncEnabled ? android.view.View.GONE : android.view.View.VISIBLE);

        btnSteerComfort = new Button(this);
        btnSteerComfort.setText("Комфорт");
        btnSteerSport = new Button(this);
        btnSteerSport.setText("Спорт");
        btnSteerLight = new Button(this);
        btnSteerLight.setText("Лёгкий");

        int btnWidth = (totalGridWidth - dp(20)) / 3;
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(btnWidth, dp(42)); 
        bLp.setMargins(dp(5), 0, dp(5), 0);

        mSteeringSelectionLayout.addView(btnSteerComfort, bLp);
        mSteeringSelectionLayout.addView(btnSteerSport, bLp);
        mSteeringSelectionLayout.addView(btnSteerLight, bLp);

        Runnable updateButtons = () -> {
            int current = mCarMode.getFixedSteeringMode();
            updateSteerBtn(btnSteerComfort, current == 0);
            updateSteerBtn(btnSteerSport, current == 1);
            updateSteerBtn(btnSteerLight, current == 2);
        };

        btnSteerComfort.setOnClickListener(v -> { mCarMode.setFixedSteeringMode(0); updateButtons.run(); });
        btnSteerSport.setOnClickListener(v -> { mCarMode.setFixedSteeringMode(1); updateButtons.run(); });
        btnSteerLight.setOnClickListener(v -> { mCarMode.setFixedSteeringMode(2); updateButtons.run(); });
        updateButtons.run();

        steeringSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            mCarMode.setSteeringSyncEnabled(checked); 
            applyItemStyle(steeringSwitchItem, steeringName, checked);
            mSteeringSelectionLayout.setVisibility(checked ? android.view.View.GONE : android.view.View.VISIBLE);
        });

        steeringSwitchItem.setOnClickListener(v -> steeringSwitch.toggle());
        steeringSwitchItem.addView(steeringName);
        steeringSwitchItem.addView(steeringSwitch);
        settingsCard.addView(steeringSwitchItem, steeringLp);
        settingsCard.addView(mSteeringSelectionLayout, matchWrap());

        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        settingsLp.setMargins(0, 0, 0, dp(12));
        root.addView(settingsCard, settingsLp);

        // ===== 4. Подсказка =====
        ScrollView scroll = new ScrollView(this);
        TextView help = new TextView(this);
        help.setTextColor(TEXT_SECONDARY);
        help.setTextSize(13f); 
        styleText(help, fontRegular(), 0.02f);
        help.setGravity(Gravity.CENTER);
        help.setLineSpacing(dp(2), 1.1f); 
        
        help.setText(
                "Удерживайте Mute (1 сек) для смены режима.\n" +
                "В режиме БК нажмите Back для вывода статистики на приборку.\n" +
                "Для появления цифр пробега и расхода начните движение."
        );

        LinearLayout helpBox = new LinearLayout(this);
        helpBox.setOrientation(LinearLayout.VERTICAL);
        helpBox.setBackground(cardDrawable(dp(14)));
        helpBox.setPadding(dp(24), dp(12), dp(24), dp(12)); 
        helpBox.setGravity(Gravity.CENTER);
        helpBox.addView(help, matchWrap());
        scroll.addView(helpBox);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(scroll, scrollLp);

        setContentView(root);
        launchService();
    }

    private void applyItemStyle(LinearLayout item, TextView name, boolean checked) {
        if (checked) {
            item.setBackground(borderedBox(ITEM_ON_BG, ITEM_ON_BORDER, dp(12), dp(1)));
            name.setTextColor(TEXT_PRIMARY);
        } else {
            item.setBackground(borderedBox(ITEM_OFF_BG, ITEM_OFF_BORDER, dp(12), dp(1)));
            name.setTextColor(TEXT_MUTED);
        }
    }

    private void updateSteerBtn(Button btn, boolean isActive) {
        btn.setTextColor(isActive ? TEXT_PRIMARY : TEXT_MUTED);
        btn.setBackground(borderedBox(isActive ? ITEM_ON_BG : ITEM_OFF_BG, isActive ? ITEM_ON_BORDER : ITEM_OFF_BORDER, dp(12), dp(1)));
        btn.setTextSize(16f);
        btn.setAllCaps(false);
        styleText(btn, fontMedium(), 0.08f);
    }

    private LinearLayout createSwitchItem(String title, boolean isEnabled, java.util.function.Consumer<Boolean> onToggle) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(20), dp(10), dp(18), dp(10)); 

        TextView name = new TextView(this);
        name.setText(title); 
        name.setTextSize(16f); 
        styleText(name, fontMedium(), 0.08f);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(isEnabled);

        applyItemStyle(item, name, isEnabled);

        sw.setOnCheckedChangeListener((buttonView, checked) -> {
            onToggle.accept(checked); 
            applyItemStyle(item, name, checked);
        });

        item.setOnClickListener(v -> sw.toggle());
        item.addView(name);
        item.addView(sw);
        return item;
    }

    private static void styleText(TextView tv, Typeface tf, float letterSpacing) {
        tv.setTypeface(tf);
        tv.setLetterSpacing(letterSpacing);
        tv.setPaintFlags(tv.getPaintFlags() | Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    }

    private static Typeface fontMedium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }
    private static Typeface fontRegular() { return Typeface.create("sans-serif", Typeface.NORMAL); }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        if (mCarMode != null) {
            mCarMode.destroy();
        }
        super.onDestroy();
    }

    private void launchService() {
        Intent intent = new Intent(this, SteeringWheelService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
            Toast.makeText(this, "Служба запущена", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        updateStatus();
    }

    private void updateStatus() {
        boolean running = isServiceRunning(SteeringWheelService.class);
        boolean hasOverlay = Settings.canDrawOverlays(this);

        if (running) {
            mStatusTitle.setText("●  СЛУЖБА АКТИВНА");
            mStatusTitle.setTextColor(STATUS_OK);
            mStatusDetail.setText("Mute / Back на руле");
        } else {
            mStatusTitle.setText("●  СЛУЖБА ОСТАНОВЛЕНА");
            mStatusTitle.setTextColor(STATUS_OFF);
            mStatusDetail.setText("Нажмите кнопку для запуска");
        }

        if (hasOverlay) {
            mOverlayBtn.setText("Оверлей разрешен ✓");
            mOverlayBtn.setTextColor(STATUS_OK);
            mOverlayBtn.setBackground(borderedBox(BTN_SEC_BG, STATUS_OK, dp(10), dp(1)));
        } else {
            mOverlayBtn.setText("Разрешить оверлей ⚠️");
            mOverlayBtn.setTextColor(WARNING_COLOR);
            mOverlayBtn.setBackground(borderedBox(BTN_SEC_BG, WARNING_COLOR, dp(10), dp(1)));
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(s.service.getClassName())) return true;
        }
        return false;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable cardDrawable(float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{CARD_TOP, CARD_BOTTOM});
        d.setCornerRadius(radius);
        d.setStroke(dp(1), CARD_BORDER);
        return d;
    }

    private static GradientDrawable borderedBox(int bgColor, int borderColor, float radius, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(bgColor);
        d.setCornerRadius(radius);
        d.setStroke(strokeWidth, borderColor);
        return d;
    }
}