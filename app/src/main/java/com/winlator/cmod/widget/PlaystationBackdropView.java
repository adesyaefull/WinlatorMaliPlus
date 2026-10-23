package com.winlator.cmod.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class PlaystationBackdropView extends View {
    private static final int FRAME_DELAY_MS = 40;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();
    private final RectF gradientBounds = new RectF();
    private final float density;
    private long startTime;
    private boolean attached;

    private final Runnable animationFrame = new Runnable() {
        @Override
        public void run() {
            if (attached && getVisibility() == VISIBLE) {
                invalidate();
                postDelayed(this, FRAME_DELAY_MS);
            }
        }
    };

    public PlaystationBackdropView(Context context) {
        this(context, null);
    }

    public PlaystationBackdropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        gradientBounds.set(0, 0, width, height);
        paint.setShader(new LinearGradient(0, 0, width * 0.85f, height,
                new int[]{Color.rgb(7, 26, 62), Color.rgb(3, 12, 35), Color.rgb(2, 8, 25)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(gradientBounds, paint);
        paint.setShader(null);

        long elapsed = SystemClock.uptimeMillis() - startTime;
        float phase = elapsed / 5200f;
        drawWave(canvas, width, height, phase, 0.48f, 0.12f, Color.rgb(42, 135, 255), 26f, 34);
        drawWave(canvas, width, height, phase + 1.7f, 0.56f, 0.08f, Color.rgb(27, 105, 245), 14f, 52);
        drawWave(canvas, width, height, phase + 3.2f, 0.64f, 0.045f, Color.rgb(86, 179, 255), 4f, 175);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(90, 129, 193, 255));
        for (int i = 0; i < 34; i++) {
            float x = ((i * 137.5f) % 997f) / 997f * width;
            float y = ((i * 79.3f + (float) Math.sin(phase + i) * 15f) % 997f) / 997f * height;
            float radius = (i % 3 + 1) * 0.55f * density;
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private void drawWave(Canvas canvas, float width, float height, float phase, float center,
                          float amplitude, int color, float strokeDp, int alpha) {
        float offset = (float) Math.sin(phase) * height * 0.025f;
        float y0 = height * center + offset;
        float crestA = (float) Math.sin(phase + 1.2f) * height * amplitude * 0.35f;
        float crestB = (float) Math.sin(phase + 2.8f) * height * amplitude * 0.4f;
        wavePath.reset();
        wavePath.moveTo(0, y0);
        wavePath.cubicTo(width * 0.22f, y0 - height * amplitude + crestA,
                width * 0.31f, y0 + height * amplitude + crestB,
                width * 0.52f, y0 + offset * 0.35f + crestA * 0.45f);
        wavePath.cubicTo(width * 0.73f, y0 + height * amplitude * 1.3f + crestB,
                width * 0.82f, y0 - height * amplitude * 0.9f + crestA,
                width, y0 - offset * 0.4f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(strokeDp * density);
        paint.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawPath(wavePath, paint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        startTime = SystemClock.uptimeMillis();
        removeCallbacks(animationFrame);
        post(animationFrame);
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(animationFrame);
        super.onDetachedFromWindow();
    }
}
