package com.winlator.cmod.widget;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Animated, particle-filled wave background inspired by the classic PlayStation startup screens. */
public final class PlayStationWaveBackgroundView extends View implements SensorEventListener {
    private static final int PARTICLE_COUNT = 90;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ribbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();
    private Shader backgroundShader;
    private Shader glowShader;
    private Shader waveLineShader;
    private Shader ribbonShader;
    private Shader specularShader;
    private final float[] particleX = new float[PARTICLE_COUNT];
    private final float[] particleY = new float[PARTICLE_COUNT];
    private final float[] particleSpeed = new float[PARTICLE_COUNT];
    private final float[] particlePhase = new float[PARTICLE_COUNT];
    private final float density;
    private long startTimeNanos;
    private boolean attached;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float motionX;
    private float motionY;

    public PlayStationWaveBackgroundView(Context context) {
        this(context, null);
    }

    public PlayStationWaveBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleX[i] = fractional(i * 0.618034f + 0.17f);
            particleY[i] = fractional(i * 0.754878f + 0.31f);
            particleSpeed[i] = 0.012f + fractional(i * 0.414214f + 0.23f) * 0.025f;
            particlePhase[i] = i * 2.399963f;
        }
    }

    private static float fractional(float value) {
        return value - (float) Math.floor(value);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        startTimeNanos = System.nanoTime();
        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            }
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        if (sensorManager != null) sensorManager.unregisterListener(this);
        sensorManager = null;
        accelerometer = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) return;
        backgroundShader = new LinearGradient(0, 0, width, height,
                new int[]{0xFF10051D, 0xFF21092F, 0xFF10051C, 0xFF080611},
                new float[]{0f, 0.34f, 0.72f, 1f}, Shader.TileMode.CLAMP);
        glowShader = new RadialGradient(width * 0.08f, height * 0.88f,
                Math.max(width, height) * 0.82f,
                new int[]{0xAA7B1B91, 0x554B145F, 0x0013091D},
                new float[]{0f, 0.46f, 1f}, Shader.TileMode.CLAMP);
        waveLineShader = new LinearGradient(0, 0, width, 0,
                new int[]{0x00F4A9FF, 0xAAF2B7FF, 0xFFE9A5FF, 0xAAE1B5FF, 0x00E5A7FF},
                new float[]{0f, 0.19f, 0.49f, 0.78f, 1f}, Shader.TileMode.CLAMP);
        ribbonShader = new LinearGradient(0, 0, width, 0,
                new int[]{0x001C092A, 0x557A258E, 0x667D398B, 0x33180824, 0x001C092A},
                new float[]{0f, 0.24f, 0.52f, 0.81f, 1f}, Shader.TileMode.CLAMP);
        updateSpecularShader(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float time = (System.nanoTime() - startTimeNanos) / 1_000_000_000f;
        backgroundPaint.setShader(backgroundShader);
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        glowPaint.setShader(glowShader);
        canvas.drawRect(0, 0, width, height, glowPaint);
        glowPaint.setShader(null);

        specularPaint.setShader(specularShader);
        canvas.drawRect(0, 0, width, height, specularPaint);
        specularPaint.setShader(null);

        drawWaves(canvas, width, height, time);
        drawParticles(canvas, width, height, time);

        if (attached) postInvalidateOnAnimation();
    }

    private void drawWaves(Canvas canvas, float width, float height, float time) {
        linePaint.setShader(waveLineShader);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStyle(Paint.Style.STROKE);

        ribbonPaint.setShader(ribbonShader);
        ribbonPaint.setStyle(Paint.Style.FILL);

        float[] offsets = {-0.105f, -0.047f, 0.025f, 0.092f};
        for (int layer = 0; layer < offsets.length; layer++) {
            float phase = time * (0.72f + layer * 0.09f) + layer * 1.43f + motionX * 0.12f;
            float center = height * (0.51f + offsets[layer]);
            float amplitude = height * (0.045f + (layer % 2) * 0.018f);

            wavePath.reset();
            wavePath.moveTo(0, waveY(0, width, center, amplitude, phase, height));
            int steps = Math.max(36, (int) (width / (8 * density)));
            for (int i = 1; i <= steps; i++) {
                float x = width * i / steps;
                wavePath.lineTo(x, waveY(x, width, center, amplitude, phase, height));
            }

            wavePath.lineTo(width, waveY(width, width, center, amplitude, phase, height)
                    + height * 0.045f);
            for (int i = steps - 1; i >= 0; i--) {
                float x = width * i / steps;
                wavePath.lineTo(x, waveY(x, width, center, amplitude, phase, height)
                        + height * 0.045f);
            }
            wavePath.close();
            canvas.drawPath(wavePath, ribbonPaint);

            wavePath.reset();
            wavePath.moveTo(0, waveY(0, width, center, amplitude, phase, height));
            for (int i = 1; i <= steps; i++) {
                float x = width * i / steps;
                wavePath.lineTo(x, waveY(x, width, center, amplitude, phase, height));
            }
            linePaint.setStrokeWidth(12 * density);
            linePaint.setAlpha(35);
            canvas.drawPath(wavePath, linePaint);
            linePaint.setStrokeWidth(4.2f * density);
            linePaint.setAlpha(145);
            canvas.drawPath(wavePath, linePaint);
            linePaint.setStrokeWidth(1.35f * density);
            linePaint.setAlpha(235);
            canvas.drawPath(wavePath, linePaint);
        }

        linePaint.setShader(null);
        ribbonPaint.setShader(null);
    }

    private float waveY(float x, float width, float center, float amplitude, float phase, float height) {
        float position = x / width;
        return center
                + (float) Math.sin(position * 6.2f + phase) * amplitude
                + (float) Math.sin(position * 12.7f - phase * 0.61f) * height * 0.018f
                + (float) Math.sin(position * 2.6f + phase * 0.37f) * height * 0.026f;
    }

    private void drawParticles(Canvas canvas, float width, float height, float time) {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float x = fractional(particleX[i] + time * particleSpeed[i]) * width;
            float y = particleY[i] * height
                    + (float) Math.sin(time * 0.48f + particlePhase[i]) * 9f * density;
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.8f + particlePhase[i]);
            float radius = (0.7f + pulse * 1.3f) * density;

            particlePaint.setColor(0x30EEC1FF);
            canvas.drawCircle(x, y, radius * 3.8f, particlePaint);
            particlePaint.setColor(0x884F2361);
            canvas.drawCircle(x, y, radius * 1.8f, particlePaint);
            particlePaint.setColor(pulse > 0.72f ? 0xFFFFD7FF : 0xFFD992FF);
            canvas.drawCircle(x, y, radius, particlePaint);
        }
    }

    private void updateSpecularShader(float width, float height) {
        float radius = Math.max(width, height) * 0.62f;
        specularShader = new RadialGradient(
                width * (0.5f - motionX * 0.2f),
                height * (0.46f + motionY * 0.18f),
                radius,
                new int[]{0x24FFFFFF, 0x0DFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.48f, 1f},
                Shader.TileMode.CLAMP);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float targetX = clamp(event.values[0] / SensorManager.GRAVITY_EARTH);
        float targetY = clamp(event.values[1] / SensorManager.GRAVITY_EARTH);
        float nextX = motionX + (targetX - motionX) * 0.12f;
        float nextY = motionY + (targetY - motionY) * 0.12f;
        if (Math.abs(nextX - motionX) < 0.004f && Math.abs(nextY - motionY) < 0.004f) return;
        motionX = nextX;
        motionY = nextY;
        if (getWidth() > 0 && getHeight() > 0) {
            updateSpecularShader(getWidth(), getHeight());
        }
    }

    private static float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
