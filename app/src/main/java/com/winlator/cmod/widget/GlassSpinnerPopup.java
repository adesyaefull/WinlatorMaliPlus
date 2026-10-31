package com.winlator.cmod.widget;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.winlator.cmod.R;

public final class GlassSpinnerPopup {
    private GlassSpinnerPopup() {}

    public static void attach(Spinner spinner) {
        Object boundTag = spinner.getTag(R.id.tag_glass_spinner_popup);
        if (Boolean.TRUE.equals(boundTag)) return;
        spinner.setTag(R.id.tag_glass_spinner_popup, true);
        spinner.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                show(spinner);
                spinner.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
            }
            return true;
        });
        spinner.setOnKeyListener((view, keyCode, event) -> {
            if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                    && event.getAction() == KeyEvent.ACTION_UP) {
                show(spinner);
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
        });
    }

    private static void show(Spinner spinner) {
        SpinnerAdapter source = spinner.getAdapter();
        if (source == null || source.getCount() == 0 || !spinner.isEnabled()) return;

        Context context = spinner.getContext();
        Dialog dialog = new Dialog(context, R.style.GlassSpinnerDialog);
        FrameLayout surface = new FrameLayout(context);
        surface.setBackgroundResource(R.drawable.liquid_glass_popup);

        ListView list = new ListView(context);
        list.setAdapter(new DropDownAdapter(source));
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(0);
        int verticalInset = dp(context, 8);
        list.setPadding(0, verticalInset, 0, verticalInset);
        list.setClipToPadding(false);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setSelection(spinner.getSelectedItemPosition());
        list.setOnItemClickListener((parent, view, position, id) -> {
            spinner.setSelection(position);
            dialog.dismiss();
        });
        surface.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setContentView(surface);
        dialog.setOnDismissListener(ignored -> spinner.requestFocus());

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int width = Math.min(Math.max(spinner.getWidth(), dp(context, 280)), screenWidth - dp(context, 32));
        int rowHeight = dp(context, 52);
        int height = Math.min(source.getCount() * rowHeight + verticalInset * 2,
                Math.round(screenHeight * 0.72f));
        height = Math.max(dp(context, 120), height);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.gravity = Gravity.CENTER;
            attributes.width = width;
            attributes.height = height;
            window.setAttributes(attributes);
            window.setDimAmount(0.08f);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(width, height);
            GlassWindowEffects.applyDialogBlur(window, context);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class DropDownAdapter extends BaseAdapter {
        private final SpinnerAdapter source;
        private final ListAdapter listAdapter;

        private DropDownAdapter(SpinnerAdapter source) {
            this.source = source;
            this.listAdapter = source instanceof ListAdapter ? (ListAdapter) source : null;
        }

        @Override
        public int getCount() {
            return source.getCount();
        }

        @Override
        public Object getItem(int position) {
            return source.getItem(position);
        }

        @Override
        public long getItemId(int position) {
            return source.getItemId(position);
        }

        @Override
        public boolean hasStableIds() {
            return source.hasStableIds();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return source.getDropDownView(position, convertView, parent);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return listAdapter == null || listAdapter.areAllItemsEnabled();
        }

        @Override
        public boolean isEnabled(int position) {
            return listAdapter == null || listAdapter.isEnabled(position);
        }
    }
}
