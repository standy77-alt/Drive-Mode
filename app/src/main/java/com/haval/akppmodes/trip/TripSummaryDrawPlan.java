package com.haval.akppmodes.trip;

public final class TripSummaryDrawPlan {
    private static final float REFERENCE_WIDTH = 1_920.0f;
    private static final float REFERENCE_HEIGHT = 720.0f;

    public final Box panel;
    public final Box title;
    public final Box[] metricRows;
    public final float innerDividerLeft;
    public final float innerDividerRight;
    public final float scaleX;
    public final float scaleY;

    private TripSummaryDrawPlan(Box panel, Box title, Box[] metricRows, 
                                float innerDividerLeft, float innerDividerRight, 
                                float scaleX, float scaleY) {
        this.panel = panel;
        this.title = title;
        this.metricRows = metricRows;
        this.innerDividerLeft = innerDividerLeft;
        this.innerDividerRight = innerDividerRight;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public static TripSummaryDrawPlan forSize(int width, int height) {
        float scaleX = width / REFERENCE_WIDTH;
        float scaleY = height / REFERENCE_HEIGHT;
        
        // ОКНО СТАЛО УЖЕ И ПОДНЯТО ВЫШЕ
        // Ширина: 1160 - 760 = 400
        // Высота: 540 - 100 = 440 (снизу остается 180px для машинки)
        return new TripSummaryDrawPlan(
                box(760.0f, 100.0f, 1_160.0f, 540.0f, scaleX, scaleY), // Карточка
                box(760.0f, 100.0f, 1_160.0f, 160.0f, scaleX, scaleY), // Заголовок
                new Box[] {
                    box(760.0f, 160.0f, 1_160.0f, 255.0f, scaleX, scaleY), // Расстояние
                    box(760.0f, 255.0f, 1_160.0f, 350.0f, scaleX, scaleY), // Время
                    box(760.0f, 350.0f, 1_160.0f, 445.0f, scaleX, scaleY), // Расход
                    box(760.0f, 445.0f, 1_160.0f, 540.0f, scaleX, scaleY)  // Израсходовано
                },
                780.0f * scaleX, // Левая линия разделителя
                1_140.0f * scaleX, // Правая линия разделителя
                scaleX, scaleY);
    }

    private static Box box(float left, float top, float right, float bottom, float scaleX, float scaleY) {
        return new Box(left * scaleX, top * scaleY, right * scaleX, bottom * scaleY);
    }

    public static final class Box {
        public final float left, top, right, bottom;
        Box(float left, float top, float right, float bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        public float centerX() { return (left + right) * 0.5f; }
    }
}