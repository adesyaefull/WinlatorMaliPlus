package com.winlator.cmod.widget;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public final class GlassWindowEffects {
    private GlassWindowEffects() {}

    public static void applyDialogBlur(Window window, Context context) {
        applyDialogBlur(window, context, 18, 0.08f);
    }

    public static void applyFullDialogBlur(Window window, Context context) {
        applyDialogBlur(window, context, 30, 0.16f);
    }

    public static boolean isNativeBlurAvailable(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        WindowManager windowManager = context.getSystemService(WindowManager.class);
        return windowManager != null && windowManager.isCrossWindowBlurEnabled();
    }

    public static Runnable applyPopupBackdropBlur(View anchor) {
        if (anchor == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || !com.winlator.cmod.ThemeManager.isFullLiquidGlassEnabled(anchor.getContext())) {
            return () -> {};
        }
        View backdrop = anchor.getRootView();
        float radius = 12f * anchor.getResources().getDisplayMetrics().density;
        backdrop.setRenderEffect(RenderEffect.createBlurEffect(
                radius, radius, Shader.TileMode.CLAMP));
        return () -> backdrop.setRenderEffect(null);
    }

    private static void applyDialogBlur(Window window, Context context,
                                       int radiusDp, float dimAmount) {
        if (window == null) return;

        boolean liquidGlass = com.winlator.cmod.ThemeManager.isFullLiquidGlassEnabled(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;

        WindowManager.LayoutParams attributes = window.getAttributes();
        int blurRadius = Math.round(radiusDp * context.getResources().getDisplayMetrics().density);
        window.setElevation(0f);
        attributes.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        if (isNativeBlurAvailable(context)) {
            attributes.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            attributes.setBlurBehindRadius(blurRadius);
            window.setBackgroundBlurRadius(blurRadius);
        } else {
            attributes.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            attributes.setBlurBehindRadius(0);
            window.setBackgroundBlurRadius(0);
            if (liquidGlass) applyActivityContentFallback(window, context, blurRadius);
        }
        attributes.dimAmount = dimAmount;
        window.setAttributes(attributes);
    }

    private static void applyActivityContentFallback(Window dialogWindow, Context context,
                                                     int blurRadius) {
        Activity activity = findActivity(context);
        if (activity == null || activity.isFinishing()) return;
        View activityDecor = activity.getWindow().getDecorView();
        View dialogDecor = dialogWindow.getDecorView();
        if (activityDecor == dialogDecor) return;

        activityDecor.setRenderEffect(RenderEffect.createBlurEffect(
                blurRadius, blurRadius, Shader.TileMode.CLAMP));
        View.OnAttachStateChangeListener restoreListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {}

            @Override
            public void onViewDetachedFromWindow(View view) {
                activityDecor.setRenderEffect(null);
                dialogDecor.removeOnAttachStateChangeListener(this);
            }
        };
        dialogDecor.addOnAttachStateChangeListener(restoreListener);
        if (!dialogDecor.isAttachedToWindow()) {
            dialogDecor.post(() -> {
                if (!dialogDecor.isAttachedToWindow()) {
                    activityDecor.setRenderEffect(null);
                    dialogDecor.removeOnAttachStateChangeListener(restoreListener);
                }
            });
        }
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return current instanceof Activity ? (Activity) current : null;
    }
}
