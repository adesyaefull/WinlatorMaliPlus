package com.winlator.cmod.widget;

import android.content.Context;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

public final class GlassWindowEffects {
    private GlassWindowEffects() {}

    public static void applyDialogBlur(Window window, Context context) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;

        WindowManager.LayoutParams attributes = window.getAttributes();
        int blurRadius = Math.round(18 * context.getResources().getDisplayMetrics().density);
        attributes.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        attributes.dimAmount = 0.08f;
        attributes.setBlurBehindRadius(blurRadius);
        window.setBackgroundBlurRadius(blurRadius);
        window.setAttributes(attributes);
    }
}
