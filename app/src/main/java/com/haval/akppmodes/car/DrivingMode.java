package com.haval.akppmodes.car;

/**
 * Все штатные режимы вождения.
 * Коды получены из логов car.drive_setting.drive_mode:
 *   0 - NORMAL
 *   1 - SPORT
 *   2 - ECO
 *   3 - SNOW
 *   4 - SAND
 *   6 - OFF-ROAD
 */
public enum DrivingMode {
    STANDARD(0, "NORMAL"),
    SPORT(1, "SPORT"),
    ECO(2, "ECO"),
    SNOW(3, "SNOW"),
    SAND(4, "SAND"),
    OFF_ROAD(6, "OFF-ROAD");

    private final int code;
    private final String title;

    DrivingMode(int code, String title) {
        this.code = code;
        this.title = title;
    }

    public int getCode() { return code; }
    public String getTitle() { return title; }

    /** Следующий режим по кругу (для всех 6 режимов) */
    public DrivingMode next() {
        DrivingMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Предыдущий режим по кругу */
    public DrivingMode previous() {
        DrivingMode[] all = values();
        return all[(ordinal() - 1 + all.length) % all.length];
    }

    public static DrivingMode fromCode(int code) {
        for (DrivingMode m : values()) {
            if (m.code == code) return m;
        }
        return STANDARD;
    }

    public static DrivingMode[] defaultEnabled() {
        return values();
    }
}