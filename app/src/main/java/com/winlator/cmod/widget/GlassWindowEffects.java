package com.winlator.cmod.widget;

import android.content.Context;
import android.os.Build;
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

    private static void applyDialogBlur(Window window, Context context,
                                       int radiusDp, float dimAmount) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;

        WindowManager.LayoutParams attributes = window.getAttributes();
        int blurRadius = Math.round(radiusDp * context.getResources().getDisplayMetrics().density);
        window.setElevation(0f);
        attributes.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        attributes.dimAmount = dimAmount;
        attributes.setBlurBehindRadius(blurRadius);
        window.setBackgroundBlurRadius(blurRadius);
        window.setAttributes(attributes);
    }
}
