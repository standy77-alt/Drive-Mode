package com.haval.akppmodes;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.haval.akppmodes.car.CarModeManager;
import com.haval.akppmodes.car.DrivingMode;
import com.haval.akppmodes.service.SteeringWheelService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int BG = Color.parseColor("#0D1117");
    private static final int CARD = Color.parseColor("#161B22");
    private static final int TEXT_PRIMARY = Color.parseColor("#F0F6FC");
    private static final int TEXT_SECONDARY = Color.parseColor("#8B949E");
    private static final int ACCENT = Color.parseColor("#00E676");
    private static final int ACCENT_PRESSED = Color.parseColor("#00C853");
    private static final int STATUS_OK = Color.parseColor("#00E676");
    private static final int STATUS_OFF = Color.parseColor("#FF5252");

    private TextView mStatusTitle;
    private TextView mStatusDetail;
    private TextView mModeValue;
    private Button mMainBtn;
    private CarModeManager mCarMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(24);
        mCarMode = new CarModeManager(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("DRIVE MODE");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(28f);
        title.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.08f);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Управление режимами АКПП");
        subtitle.setTextColor(ACCENT);
        subtitle.setTextSize(18f);
        subtitle.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        root.addView(subtitle, matchWrap());

        // Карточка статуса
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(rounded(CARD, dp(16)));
        statusCard.setPadding(dp(20), dp(16), dp(20), dp(16));
        statusCard.setGravity(Gravity.CENTER);

        mStatusTitle = new TextView(this);
        mStatusTitle.setTextSize(22f);
        mStatusTitle.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        mStatusTitle.setGravity(Gravity.CENTER);
        statusCard.addView(mStatusTitle, matchWrap());

        mStatusDetail = new TextView(this);
        mStatusDetail.setTextColor(TEXT_SECONDARY);
        mStatusDetail.setTextSize(18f);
        mStatusDetail.setTypeface(Typeface.SANS_SERIF);
        mStatusDetail.setGravity(Gravity.CENTER);
        mStatusDetail.setPadding(0, dp(6), 0, 0);
        statusCard.addView(mStatusDetail, matchWrap());

        mModeValue = new TextView(this);
        mModeValue.setTextSize(30f);
        mModeValue.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        mModeValue.setGravity(Gravity.CENTER);
        mModeValue.setPadding(0, dp(12), 0, 0);
        statusCard.addView(mModeValue, matchWrap());

        LinearLayout.LayoutParams cardLp = matchWrap();
        cardLp.setMargins(0, 0, 0, dp(20));
        root.addView(statusCard, cardLp);

        // Кнопка «Перезапустить службу»
        mMainBtn = new Button(this);
        mMainBtn.setText("Перезапустить службу");
        mMainBtn.setTextColor(Color.BLACK);
        mMainBtn.setTextSize(20f);
        mMainBtn.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        mMainBtn.setAllCaps(false);
        mMainBtn.setBackground(rounded(ACCENT, dp(16)));
        mMainBtn.setOnClickListener(v -> {
            mMainBtn.setBackground(rounded(ACCENT_PRESSED, dp(16)));
            launchService();
            mMainBtn.postDelayed(() -> mMainBtn.setBackground(rounded(ACCENT, dp(16))), 200);
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
        btnLp.setMargins(dp(8), 0, dp(8), dp(20));
        root.addView(mMainBtn, btnLp);

        // === БЛОК ВЫБОРА РЕЖИМОВ ===
        LinearLayout modesCard = new LinearLayout(this);
        modesCard.setOrientation(LinearLayout.VERTICAL);
        modesCard.setBackground(rounded(CARD, dp(16)));
        modesCard.setPadding(dp(20), dp(16), dp(20), dp(16));
        
        TextView modesTitle = new TextView(this);
        modesTitle.setText("Режимы для переключения (Mute):");
        modesTitle.setTextColor(TEXT_PRIMARY);
        modesTitle.setTextSize(18f);
        modesTitle.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        modesTitle.setPadding(0, 0, 0, dp(10));
        modesCard.addView(modesTitle, matchWrap());

        // Получаем текущие выбранные режимы
        int[] currentSelected = mCarMode.getSelectedModes();
        DrivingMode[] allModes = DrivingMode.values();
        CheckBox[] checkBoxes = new CheckBox[allModes.length];
        
        for (int i = 0; i < allModes.length; i++) {
            DrivingMode mode = allModes[i];
            CheckBox cb = new CheckBox(this);
            cb.setText(mode.getTitle());
            cb.setTextColor(TEXT_PRIMARY);
            cb.setTextSize(18f);
            
            // Проверяем, выбран ли этот режим сейчас
            for (int code : currentSelected) {
                if (code == mode.getCode()) {
                    cb.setChecked(true);
                    break;
                }
            }
            checkBoxes[i] = cb;
            modesCard.addView(cb);
        }

        Button saveModesBtn = new Button(this);
        saveModesBtn.setText("Сохранить");
        saveModesBtn.setTextColor(Color.BLACK);
        saveModesBtn.setAllCaps(false);
        saveModesBtn.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        saveModesBtn.setBackground(rounded(ACCENT, dp(12)));
        saveModesBtn.setOnClickListener(v -> {
            List<Integer> selectedList = new ArrayList<>();
            for (int i = 0; i < checkBoxes.length; i++) {
                if (checkBoxes[i].isChecked()) {
                    selectedList.add(allModes[i].getCode());
                }
            }
            if (selectedList.isEmpty()) {
                Toast.makeText(this, "Выберите хотя бы один режим", Toast.LENGTH_SHORT).show();
                return;
            }
            int[] codes = new int[selectedList.size()];
            for (int i = 0; i < codes.length; i++) codes[i] = selectedList.get(i);
            
            mCarMode.saveSelectedModes(codes);
            Toast.makeText(this, "Сохранено: " + selectedList.size() + " реж.", Toast.LENGTH_SHORT).show();
        });
        
        LinearLayout.LayoutParams saveBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        saveBtnLp.setMargins(0, dp(12), 0, 0);
        modesCard.addView(saveModesBtn, saveBtnLp);

        LinearLayout.LayoutParams modesLp = matchWrap();
        modesLp.setMargins(0, 0, 0, dp(20));
        root.addView(modesCard, modesLp);
        // === КОНЕЦ БЛОКА ВЫБОРА ===

        // Подсказка
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        TextView help = new TextView(this);
        help.setTextColor(TEXT_SECONDARY);
        help.setTextSize(18f);
        help.setTypeface(Typeface.SANS_SERIF);
        help.setLineSpacing(dp(4), 1.15f);
        help.setText(
                "Как пользоваться\n\n" +
                "1. Отметьте галочками нужные вам режимы\n" +
                "   и нажмите «Сохранить».\n\n" +
                "2. Удерживайте ~1.5 секунды кнопку на руле:\n" +
                "   • Mute (работает везде)\n" +
                "   • Back (работает в режиме БК)\n" +
                "   → переключение по кругу между\n" +
                "     выбранными режимами.\n\n" +
                "3. Режимы сохраняются между запусками.\n\n" +
                "Дополнительно\n\n" +
                "• Разрешения не требуются — программа\n" +
                "  работает через штатный Binder-сервис.\n\n" +
                "• Служба запускается автоматически после\n" +
                "  перезагрузки ГУ."
        );

        LinearLayout helpBox = new LinearLayout(this);
        helpBox.setOrientation(LinearLayout.VERTICAL);
        helpBox.setBackground(rounded(CARD, dp(16)));
        helpBox.setPadding(dp(20), dp(16), dp(20), dp(16));
        helpBox.addView(help, matchWrap());
        scroll.addView(helpBox, matchWrap());
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        launchService();
    }

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
        Intent serviceIntent = new Intent(this, SteeringWheelService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "Служба запущена", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        updateStatus();
    }

    private void updateStatus() {
        boolean running = isServiceRunning(SteeringWheelService.class);

        if (running) {
            mStatusTitle.setText("●  СЛУЖБА АКТИВНА");
            mStatusTitle.setTextColor(STATUS_OK);
            mStatusDetail.setText("Удерживайте Mute / Back на руле");
        } else {
            mStatusTitle.setText("●  СЛУЖБА ОСТАНОВЛЕНА");
            mStatusTitle.setTextColor(STATUS_OFF);
            mStatusDetail.setText("Нажмите «Перезапустить службу»");
        }

        DrivingMode mode = mCarMode.getCurrentMode();
        mModeValue.setText(mode.getTitle());
        // Подсвечиваем зеленым, если режим не NORMAL
        mModeValue.setTextColor(mode == DrivingMode.STANDARD ? TEXT_PRIMARY : ACCENT);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (ActivityManager.RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }
}