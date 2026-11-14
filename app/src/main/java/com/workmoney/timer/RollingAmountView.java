package com.workmoney.timer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/** Per-character vertical roll when the amount changes; unchanged characters stay put. */
public final class RollingAmountView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String oldText = "¥0.0000", newText = "¥0.0000";
    private long animationStart = 0L;
    private static final long ANIM_MS = 360L;

    public RollingAmountView(Context c) { super(c); init(); }
    public RollingAmountView(Context c, AttributeSet attrs) { super(c, attrs); init(); }

    private void init() {
        paint.setColor(Color.rgb(237, 255, 248));
        paint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 49,
            getResources().getDisplayMetrics()));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setAmount(String amount) {
        String text = "¥" + amount;
        if (text.equals(newText)) return;
        oldText = newText;
        newText = text;
        animationStart = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        String display = newText;
        float maxWidth = paint.measureText(display);
        float previousWidth = paint.measureText(oldText);
        float scale = Math.min(1f, (getWidth() - 6f) / Math.max(1f, Math.max(maxWidth, previousWidth)));
        if (scale <= 0) return;
        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(scale, scale);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = -(fm.ascent + fm.descent) / 2f;
        float travel = fm.descent - fm.ascent;
        float x = -maxWidth / 2f;
        float t = Math.min(1f, (SystemClock.uptimeMillis() - animationStart) / (float) ANIM_MS);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        boolean sameLength = oldText.length() == display.length();
        for (int i = 0; i < display.length(); i++) {
            String newChar = display.substring(i, i + 1);
            float width = paint.measureText(newChar);
            String oldChar = sameLength ? oldText.substring(i, i + 1) : newChar;
            if (t < 1f && !newChar.equals(oldChar)
                && Character.isDigit(newChar.charAt(0)) && Character.isDigit(oldChar.charAt(0))) {
                canvas.save();
                canvas.clipRect(x - 1, -getHeight(), x + width + 1, getHeight());
                canvas.drawText(oldChar, x, baseline - travel * ease, paint);
                canvas.drawText(newChar, x, baseline + travel * (1f - ease), paint);
                canvas.restore();
            } else {
                canvas.drawText(newChar, x, baseline, paint);
            }
            x += width;
        }
        canvas.restore();
        if (t < 1f) postInvalidateOnAnimation();
    }
}
