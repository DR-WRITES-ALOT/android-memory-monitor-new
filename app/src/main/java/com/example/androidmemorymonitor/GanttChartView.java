package com.example.androidmemorymonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple animated Gantt chart for the simulated scheduler timeline.
 *
 * <p>Each block is one contiguous slice of simulated CPU time; idle slices are grey. The
 * chart reveals slices progressively while the scheduler playback runs, so the class only
 * ever draws <i>simulated</i> data — never real kernel scheduling measurements.</p>
 */
public class GanttChartView extends View {

    private static final int[] PALETTE = {
            Color.parseColor("#176B87"),
            Color.parseColor("#2E7D32"),
            Color.parseColor("#6A1B9A"),
            Color.parseColor("#EF6C00"),
            Color.parseColor("#AD1457"),
            Color.parseColor("#00838F"),
    };

    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint idlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint intervalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private List<ScheduleResult.Slice> slices = new ArrayList<>();
    private final Map<String, Integer> jobColors = new HashMap<>();
    private int revealedCount;

    public GanttChartView(Context context) {
        this(context, null);
    }

    public GanttChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GanttChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        blockPaint.setStyle(Paint.Style.FILL);
        idlePaint.setColor(Color.parseColor("#CFD8DC"));
        idlePaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(sp(12));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        intervalPaint.setColor(Color.parseColor("#455A64"));
        intervalPaint.setTextSize(sp(10));
        intervalPaint.setTextAlign(Paint.Align.CENTER);
        borderPaint.setColor(Color.parseColor("#0B3C53"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
    }

    /** Sets the full timeline; {@code revealed} is how many slices playback has shown. */
    public void setTimeline(List<ScheduleResult.Slice> newSlices, int revealed) {
        slices = newSlices == null ? new ArrayList<>() : new ArrayList<>(newSlices);
        revealedCount = Math.max(0, Math.min(revealed, slices.size()));
        jobColors.clear();
        int colorIndex = 0;
        for (ScheduleResult.Slice slice : slices) {
            if (!slice.isIdle() && !jobColors.containsKey(slice.jobId)) {
                jobColors.put(slice.jobId, PALETTE[colorIndex % PALETTE.length]);
                colorIndex++;
            }
        }
        requestLayout();
        invalidate();
    }

    public int getSliceCount() {
        return slices.size();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int unit = (int) dp(46);
        int width = (int) dp(16);
        for (ScheduleResult.Slice slice : slices) {
            width += Math.max(1, slice.duration()) * unit;
        }
        int height = (int) dp(92);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float x = dp(8);
        float top = dp(8);
        float blockHeight = dp(52);
        float bottom = top + blockHeight;
        int unit = (int) dp(46);

        for (int i = 0; i < slices.size(); i++) {
            ScheduleResult.Slice slice = slices.get(i);
            float width = Math.max(1, slice.duration()) * unit;
            boolean revealed = i < revealedCount;

            rect.set(x, top, x + width, bottom);
            if (slice.isIdle()) {
                idlePaint.setAlpha(revealed ? 255 : 90);
                canvas.drawRoundRect(rect, dp(6), dp(6), idlePaint);
            } else {
                Integer color = jobColors.get(slice.jobId);
                blockPaint.setColor(color == null ? PALETTE[0] : color);
                blockPaint.setAlpha(revealed ? 255 : 90);
                canvas.drawRoundRect(rect, dp(6), dp(6), blockPaint);
            }
            if (revealed && i == revealedCount - 1) {
                canvas.drawRoundRect(rect, dp(6), dp(6), borderPaint);
            }

            String label = slice.isIdle() ? "IDLE" : slice.jobId;
            float textY = top + blockHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            textPaint.setAlpha(revealed ? 255 : 120);
            canvas.drawText(label, x + width / 2f, textY, textPaint);
            intervalPaint.setAlpha(revealed ? 255 : 120);
            canvas.drawText(slice.startTime + "-" + slice.endTime,
                    x + width / 2f, bottom + dp(18), intervalPaint);
            x += width;
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }
}
