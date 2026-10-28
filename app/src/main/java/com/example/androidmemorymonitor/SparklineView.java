package com.example.androidmemorymonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact sparkline for the latest system RAM usage samples (percent 0-100), with a dashed
 * line at the 80% automatic-cleanup threshold.
 */
public class SparklineView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<Integer> samples = new ArrayList<>();

    public SparklineView(Context context) {
        this(context, null);
    }

    public SparklineView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SparklineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        linePaint.setColor(Color.parseColor("#176B87"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2));
        fillPaint.setColor(Color.parseColor("#22176B87"));
        fillPaint.setStyle(Paint.Style.FILL);
        thresholdPaint.setColor(Color.parseColor("#C62828"));
        thresholdPaint.setStyle(Paint.Style.STROKE);
        thresholdPaint.setStrokeWidth(dp(1));
        thresholdPaint.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0));
        gridPaint.setColor(Color.parseColor("#DDE3EA"));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        dotPaint.setColor(Color.parseColor("#176B87"));
        dotPaint.setStyle(Paint.Style.FILL);
    }

    /** Replaces the displayed samples (oldest to newest). */
    public void setSamples(List<Integer> newSamples) {
        samples.clear();
        if (newSamples != null) {
            for (Integer sample : newSamples) {
                samples.add(Math.max(0, Math.min(100, sample)));
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(4);
        float top = dp(6);
        float right = getWidth() - dp(4);
        float bottom = getHeight() - dp(6);
        float height = bottom - top;

        // Grid: 0 / 50 / 100 %.
        for (int percent : new int[]{0, 50, 100}) {
            float y = bottom - height * percent / 100f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }
        // 80 % threshold.
        float thresholdY = bottom - height * 0.8f;
        canvas.drawLine(left, thresholdY, right, thresholdY, thresholdPaint);

        if (samples.isEmpty()) {
            return;
        }
        float step = samples.size() == 1 ? 0 : (right - left) / (samples.size() - 1);
        Path line = new Path();
        Path fill = new Path();
        for (int i = 0; i < samples.size(); i++) {
            float x = left + step * i;
            float y = bottom - height * samples.get(i) / 100f;
            if (i == 0) {
                line.moveTo(x, y);
                fill.moveTo(x, bottom);
                fill.lineTo(x, y);
            } else {
                line.lineTo(x, y);
                fill.lineTo(x, y);
            }
            if (i == samples.size() - 1) {
                canvas.drawCircle(x, y, dp(3), dotPaint);
            }
        }
        fill.lineTo(left + step * (samples.size() - 1), bottom);
        fill.close();
        canvas.drawPath(fill, fillPaint);
        canvas.drawPath(line, linePaint);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
