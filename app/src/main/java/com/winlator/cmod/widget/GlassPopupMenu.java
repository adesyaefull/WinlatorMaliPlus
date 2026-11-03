package com.winlator.cmod.widget;

import android.content.Context;
import android.view.View;
import android.widget.PopupMenu;

public final class GlassPopupMenu extends PopupMenu {
    private final View anchor;
    private OnDismissListener dismissListener;
    private Runnable restoreBackdrop;

    public GlassPopupMenu(Context context, View anchor) {
        super(context, anchor);
        this.anchor = anchor;
        super.setOnDismissListener(menu -> {
            if (restoreBackdrop != null) {
                restoreBackdrop.run();
                restoreBackdrop = null;
            }
            if (dismissListener != null) dismissListener.onDismiss(menu);
        });
    }

    @Override
    public void setOnDismissListener(OnDismissListener listener) {
        dismissListener = listener;
    }

    @Override
    public void show() {
        restoreBackdrop = GlassWindowEffects.applyPopupBackdropBlur(anchor);
        super.show();
    }
}
