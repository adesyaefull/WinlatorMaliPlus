package com.winlator.cmod.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.winlator.cmod.R;
import com.winlator.cmod.ThemeManager;

public final class LiquidGlassSurfaceDrawable extends Drawable {
    private static final String SURFACE_SHADER = ""
            + "uniform float2 size;\n"
            + "uniform float4 tint;\n"
            + "uniform float4 accent;\n"
            + "half4 main(float2 p) {\n"
            + "  float2 uv = p / size;\n"
            + "  float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));\n"
            + "  float2 highlight = (uv - float2(0.24, 0.08)) / float2(0.92, 0.30);\n"
            + "  float broadReflection = exp(-dot(highlight, highlight) * 2.4);\n"
            + "  float lowerReflection = exp(-dot((uv - float2(0.78, 0.96)) * float2(1.5, 3.0),\n"
            + "                                     (uv - float2(0.78, 0.96)) * float2(1.5, 3.0)) * 2.0);\n"
            + "  float edgeGlow = 1.0 - smoothstep(0.0, 0.075, edge);\n"
            + "  float rimLine = exp(-abs(edge - 0.035) * 80.0);\n"
            + "  float reflection = clamp(broadReflection * 0.17 + lowerReflection * 0.045\n"
            + "                           + edgeGlow * 0.055 + rimLine * 0.12, 0.0, 0.32);\n"
            + "  float colorShift = edgeGlow * 0.035;\n"
            + "  half3 body = tint.rgb * 0.30 + half3(0.50) + accent.rgb * half(colorShift * 0.45);\n"
            + "  half3 light = half3(reflection * 0.96, reflection, reflection * 1.04);\n"
            + "  return half4(min(body + light, half3(1.0)), 0.48);\n"
            + "}";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final Drawable fallback;
    private final float radiusPx;
    private RuntimeShader shader;
    private int tint;
    private int accent;
    private int alpha = 255;
    private ColorFilter colorFilter;

    public LiquidGlassSurfaceDrawable(@NonNull Context context, int fallbackResource, float cornerRadiusDp) {
        float density = context.getResources().getDisplayMetrics().density;
        radiusPx = cornerRadiusDp * density;
        fallback = ContextCompat.getDrawable(context, fallbackResource);
        tint = ThemeManager.getBackgroundColor(context);
        accent = ThemeManager.getAccentColor(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ThemeManager.isFullLiquidGlassEnabled(context)) {
            shader = createSurfaceShader();
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private RuntimeShader createSurfaceShader() {
        return new RuntimeShader(SURFACE_SHADER);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        rect.set(bounds);
        clipPath.reset();
        clipPath.addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW);
        if (fallback != null) fallback.setBounds(bounds);
        if (shader != null) {
            shader.setFloatUniform("size", Math.max(1f, rect.width()), Math.max(1f, rect.height()));
            shader.setFloatUniform("tint", Color.red(tint) / 255f, Color.green(tint) / 255f,
                    Color.blue(tint) / 255f, 0.48f);
            shader.setFloatUniform("accent", Color.red(accent) / 255f, Color.green(accent) / 255f,
                    Color.blue(accent) / 255f, 1f);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (shader == null) {
            if (fallback != null) {
                fallback.setAlpha(alpha);
                fallback.setColorFilter(colorFilter);
                fallback.draw(canvas);
            }
            return;
        }

        paint.setAlpha(alpha);
        paint.setColorFilter(colorFilter);
        paint.setShader(shader);
        int save = canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawRect(rect, paint);
        canvas.restoreToCount(save);
        paint.setShader(null);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }

}
