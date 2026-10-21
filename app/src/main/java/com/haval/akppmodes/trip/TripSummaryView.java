package com.haval.akppmodes.trip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

public final class TripSummaryView extends View {
    public interface OnDismissListener { void onDismiss(); }

    private static final int CARD_TOP        = 0xFF161821;
    private static final int CARD_BOTTOM     = 0xFF111219;
    private static final int CARD_BORDER     = 0xFF262936;
    private static final int TEXT_PRIMARY    = 0xFFF2F4F8;
    private static final int TEXT_SECONDARY  = 0xFF8A90A2;
    private static final int ACCENT          = 0xFF00E5FF;

    private static final float TITLE_SIZE = 18.0f;
private static final float LABEL_SIZE = 12.0f;
private static final float VALUE_SIZE = 26.0f;
private static final float UNIT_SIZE = 12.0f;
private static final float METRIC_GAP = 8.0f;
private static final float VALUE_UNIT_GAP = 6.0f;
private static final float DURATION_GROUP_GAP = 8.0f;

    private final TripSummary summary;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Typeface valueTypeface = Typeface.create("sans-serif-medium", Typeface.BOLD);
    private final Typeface labelTypeface = Typeface.create("sans-serif", Typeface.NORMAL);
    private OnDismissListener onDismissListener;

    public TripSummaryView(Context context, TripSummary summary) {
        super(context);
        this.summary = summary;
        setBackgroundColor(Color.TRANSPARENT);
        
        // Автоматическое закрытие через 5.5 секунд (5500 мс)
        postDelayed(() -> {
            if (onDismissListener != null) onDismissListener.onDismiss();
        }, 5500);
    }

    public void setOnDismissListener(OnDismissListener listener) { onDismissListener = listener; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        TripSummaryDrawPlan plan = TripSummaryDrawPlan.forSize(getWidth(), getHeight());
        drawPanel(canvas, plan.panel, plan.scaleY);
        drawSummary(canvas, plan);
    }

    private void drawPanel(Canvas canvas, TripSummaryDrawPlan.Box box, float scaleY) {
        float radius = 16.0f * scaleY;
        
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
                box.left, box.top, box.left, box.bottom,
                CARD_TOP, CARD_BOTTOM, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(box.left, box.top, box.right, box.bottom, radius, radius, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE); 
        paint.setStrokeWidth(Math.max(1.0f, scaleY)); 
        paint.setColor(CARD_BORDER);
        canvas.drawRoundRect(box.left, box.top, box.right, box.bottom, radius, radius, paint);
    }

    private void drawSummary(final Canvas canvas, final TripSummaryDrawPlan plan) {
        configureText(TITLE_SIZE * plan.scaleY, ACCENT, valueTypeface, Paint.Align.CENTER);
        canvas.drawText("СТАТИСТИКА ПОЕЗДКИ", plan.title.centerX(), centeredBaseline(plan.title), paint);
        
        drawDivider(canvas, plan.panel.left, plan.panel.right, plan.metricRows[0].top, plan);
        for (int index = 1; index < plan.metricRows.length; index++) {
            drawDivider(canvas, plan.innerDividerLeft, plan.innerDividerRight, plan.metricRows[index].top, plan);
        }
        
        drawMetric(canvas, plan.metricRows[0], "РАССТОЯНИЕ", (target, cx, b, p) -> drawValueWithUnit(target, TripSummaryFormatter.distance(summary), "км", summary.distanceValid, cx, b, p), plan);
        drawMetric(canvas, plan.metricRows[1], "ВРЕМЯ В ПУТИ", (target, cx, b, p) -> drawDuration(target, TripSummaryFormatter.durationParts(summary), cx, b, p), plan);
        drawMetric(canvas, plan.metricRows[2], "СРЕДНИЙ РАСХОД", (target, cx, b, p) -> drawValueWithUnit(target, TripSummaryFormatter.consumption(summary), "л/100 км", summary.consumptionValid, cx, b, p), plan);
        drawMetric(canvas, plan.metricRows[3], "ИЗРАСХОДОВАНО", (target, cx, b, p) -> drawValueWithUnit(target, TripSummaryFormatter.fuelUsed(summary), "л", summary.fuelUsedValid, cx, b, p), plan);
    }

    private void drawMetric(Canvas canvas, TripSummaryDrawPlan.Box row, String label, ValueDrawer valueDrawer, TripSummaryDrawPlan plan) {
        float gap = METRIC_GAP * plan.scaleY;
        
        configureText(LABEL_SIZE * plan.scaleY, TEXT_SECONDARY, labelTypeface, Paint.Align.CENTER);
        Paint.FontMetrics labelMetrics = paint.getFontMetrics();
        float labelHeight = labelMetrics.descent - labelMetrics.ascent;
        
        configureText(VALUE_SIZE * plan.scaleY, TEXT_PRIMARY, valueTypeface, Paint.Align.CENTER);
        Paint.FontMetrics valueMetrics = paint.getFontMetrics();
        float valueHeight = valueMetrics.descent - valueMetrics.ascent;
        
        float top = (row.top + row.bottom - labelHeight - gap - valueHeight) * 0.5f;
        float labelBaseline = top - labelMetrics.ascent;
        float valueBaseline = top + labelHeight + gap - valueMetrics.ascent;
        
        configureText(LABEL_SIZE * plan.scaleY, TEXT_SECONDARY, labelTypeface, Paint.Align.CENTER);
        canvas.drawText(label, row.centerX(), labelBaseline, paint);
        valueDrawer.draw(canvas, row.centerX(), valueBaseline, plan);
    }

    private void drawValueWithUnit(Canvas canvas, String value, String unit, boolean valid, float centerX, float baseline, TripSummaryDrawPlan plan) {
        if (!valid) { 
            drawValue(canvas, value, centerX, baseline, plan, Paint.Align.CENTER); 
            return; 
        }
        float valueWidth = measureText(value, VALUE_SIZE, valueTypeface, plan);
        float unitWidth = measureText(unit, UNIT_SIZE, labelTypeface, plan);
        float gap = VALUE_UNIT_GAP * plan.scaleX;
        float cursor = centerX - (valueWidth + gap + unitWidth) * 0.5f;
        
        drawValue(canvas, value, cursor, baseline, plan, Paint.Align.LEFT);
        cursor += valueWidth + gap;
        drawUnit(canvas, unit, cursor, baseline, plan);
    }

    private void drawDuration(Canvas canvas, TripSummaryFormatter.DurationParts duration, float centerX, float baseline, TripSummaryDrawPlan plan) {
        if (!duration.valid) { 
            drawValue(canvas, "—", centerX, baseline, plan, Paint.Align.CENTER); 
            return; 
        }
        String hours = Long.toString(duration.hours);
        String minutes = Long.toString(duration.minutes);
        float valueUnitGap = VALUE_UNIT_GAP * plan.scaleX;
        float groupGap = DURATION_GROUP_GAP * plan.scaleX;
        
        float hoursWidth = duration.hours > 0L ? measureText(hours, VALUE_SIZE, valueTypeface, plan) : 0.0f;
        float hourUnitWidth = duration.hours > 0L ? measureText("ч", UNIT_SIZE, labelTypeface, plan) : 0.0f;
        float minutesWidth = measureText(minutes, VALUE_SIZE, valueTypeface, plan);
        float minuteUnitWidth = measureText("мин", UNIT_SIZE, labelTypeface, plan);
        
        float totalWidth = minutesWidth + valueUnitGap + minuteUnitWidth;
        if (duration.hours > 0L) totalWidth += hoursWidth + valueUnitGap + hourUnitWidth + groupGap;
        
        float cursor = centerX - totalWidth * 0.5f;
        if (duration.hours > 0L) {
            drawValue(canvas, hours, cursor, baseline, plan, Paint.Align.LEFT); cursor += hoursWidth + valueUnitGap;
            drawUnit(canvas, "ч", cursor, baseline, plan); cursor += hourUnitWidth + groupGap;
        }
        drawValue(canvas, minutes, cursor, baseline, plan, Paint.Align.LEFT); cursor += minutesWidth + valueUnitGap;
        drawUnit(canvas, "мин", cursor, baseline, plan);
    }

    private float centeredBaseline(TripSummaryDrawPlan.Box box) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return (box.top + box.bottom - metrics.ascent - metrics.descent) * 0.5f;
    }

    private float measureText(String text, float referenceSize, Typeface typeface, TripSummaryDrawPlan plan) {
        configureText(referenceSize * plan.scaleY, TEXT_PRIMARY, typeface, Paint.Align.LEFT);
        return paint.measureText(text);
    }

    private void drawValue(Canvas canvas, String text, float x, float baseline, TripSummaryDrawPlan plan, Paint.Align align) {
        configureText(VALUE_SIZE * plan.scaleY, TEXT_PRIMARY, valueTypeface, align);
        canvas.drawText(text, x, baseline, paint);
    }

    private void drawUnit(Canvas canvas, String text, float x, float baseline, TripSummaryDrawPlan plan) {
        configureText(UNIT_SIZE * plan.scaleY, TEXT_SECONDARY, labelTypeface, Paint.Align.LEFT);
        canvas.drawText(text, x, baseline, paint);
    }

    private void drawDivider(Canvas canvas, float left, float right, float y, TripSummaryDrawPlan plan) {
        paint.setStyle(Paint.Style.STROKE); 
        paint.setStrokeWidth(Math.max(1.0f, plan.scaleY)); 
        paint.setColor(CARD_BORDER);
        canvas.drawLine(left, y, right, y, paint);
    }

    private void configureText(float size, int color, Typeface typeface, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL); 
        paint.setTextSize(size); 
        paint.setColor(color); 
        paint.setTypeface(typeface); 
        paint.setTextAlign(align);
    }

    private interface ValueDrawer { 
        void draw(Canvas canvas, float centerX, float baseline, TripSummaryDrawPlan plan); 
    }
}