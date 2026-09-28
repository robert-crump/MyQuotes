package com.example.myquotes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bar per month with its count above and a short month label below. Display only. */
public class MonthlyBarChartView extends View {
    private static final float HEIGHT_DP = 160f;
    private static final float TEXT_SP = 11f;
    private static final float GAP_FRACTION = 0.3f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final float cornerRadius;
    private final float textGap;

    private List<QuoteStatistics.MonthCount> months = new ArrayList<>();

    public MonthlyBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        barPaint.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary));
        int secondary = MaterialColors.getColor(this, android.R.attr.textColorSecondary);
        baselinePaint.setColor(secondary);
        baselinePaint.setStrokeWidth(dp(1));
        textPaint.setColor(secondary);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, TEXT_SP, getResources().getDisplayMetrics()));
        cornerRadius = dp(3);
        textGap = dp(4);
    }

    public void setMonths(List<QuoteStatistics.MonthCount> months) {
        this.months = months != null ? months : new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = resolveSize((int) dp(HEIGHT_DP), heightMeasureSpec);
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = months.size();
        if (n == 0) return;

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;
        float left = getPaddingLeft();
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float baseline = getHeight() - getPaddingBottom() - textHeight - textGap;
        float chartTop = getPaddingTop() + textHeight + textGap;
        float maxBarHeight = baseline - chartTop;

        int max = 0;
        for (QuoteStatistics.MonthCount m : months) max = Math.max(max, m.count);

        float slot = width / n;
        float barWidth = slot * (1 - GAP_FRACTION);
        Locale locale = Locale.getDefault();
        for (int i = 0; i < n; i++) {
            QuoteStatistics.MonthCount m = months.get(i);
            float center = left + slot * i + slot / 2;
            float barHeight = max == 0 ? 0 : maxBarHeight * m.count / max;
            float top = baseline - barHeight;
            if (barHeight > 0) {
                bar.set(center - barWidth / 2, top, center + barWidth / 2, baseline);
                canvas.drawRoundRect(bar, cornerRadius, cornerRadius, barPaint);
                // Square off the bottom corners so the bar sits flush on the baseline.
                bar.top = Math.max(top, baseline - cornerRadius);
                canvas.drawRect(bar, barPaint);
            }
            canvas.drawText(String.valueOf(m.count), center, top - textGap - fm.descent, textPaint);
            // Some locales abbreviate with a trailing dot ("Sept."); drop it, slots are narrow.
            String label = m.month.getMonth().getDisplayName(TextStyle.SHORT, locale)
                    .replaceAll("\\.$", "");
            canvas.drawText(label, center, baseline + textGap - fm.ascent, textPaint);
        }
        canvas.drawLine(left, baseline, left + width, baseline, baselinePaint);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
