package com.winlator.cmod.contentdialog;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.winlator.cmod.ThemeManager;
import com.winlator.cmod.ThemePreset;

public final class ThemeSelectionDialog {
    private static final String[] DESCRIPTIONS = {
            "Liquid surfaces, animated backdrop, and enhanced blur",
            "A sophisticated deep ocean hue for focused work.",
            "Electric purple accents for a futuristic vibe.",
            "Gentle, high-contrast light mode to reduce eye strain.",
            "Minimalist monochromatic scheme for reduced distractions.",
            "Passionate and dynamic red elements for high visibility.",
            "True black surfaces with crisp white contrast for AMOLED screens."
    };
    private static final int[] SWATCH_SURFACES = {
            0xFF44334F, 0xFF3E4784, 0xFF7A5AF8, 0xFFFBF1EA, 0xFF536162, 0xFFD82148,
            0xFF000000
    };
    private static final int[] SWATCH_ACCENTS = {
            0xFFD8B4FE, 0xFFFBF1EA, 0xFFFBF1EA, 0xFF3E4784, 0xFFFBF1EA, 0xFFFBF1EA,
            0xFFFFFFFF
    };

    private ThemeSelectionDialog() {}

    public static void show(Activity activity, boolean requireSelection, Runnable onThemeSelected) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setCancelable(!requireSelection);
        dialog.setCanceledOnTouchOutside(!requireSelection);

        int selectedIndex = ThemeManager.getSelectedPresetIndex(activity);
        ThemePreset selectedPreset = ThemeManager.getSelectedPreset(activity);
        int secondaryTextColor = selectedPreset.onSurfaceVariant;
        int textColor = selectedPreset.onSurface;

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 24), dp(activity, 12), dp(activity, 24), dp(activity, 24));
        content.setBackground(ThemeManager.createThemedSurfaceDrawable(activity, 28));

        View handle = new View(activity);
        GradientDrawable handleDrawable = new GradientDrawable();
        handleDrawable.setColor(secondaryTextColor);
        handleDrawable.setCornerRadius(dp(activity, 4));
        handle.setBackground(handleDrawable);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 5));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(activity, 26);
        content.addView(handle, handleParams);

        TextView title = new TextView(activity);
        title.setText("Interface Styling");
        title.setTextColor(textColor);
        title.setTextSize(25);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);

        TextView description = new TextView(activity);
        description.setText("Personalize your workspace aesthetics. Selections take effect in real-time.");
        description.setTextColor(secondaryTextColor);
        description.setTextSize(14);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(activity, 8);
        descriptionParams.bottomMargin = dp(activity, 18);
        content.addView(description, descriptionParams);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);
        scrollView.setNestedScrollingEnabled(true);
        scrollView.setClipToPadding(false);
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(dp(activity, 420), Math.max(dp(activity, 132),
                        activity.getResources().getDisplayMetrics().heightPixels - dp(activity, 240))));
        scrollParams.topMargin = dp(activity, 12);
        content.addView(scrollView, scrollParams);

        LinearLayout options = new LinearLayout(activity);
        options.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(options, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < ThemeManager.THEME_PRESETS.size(); i++) {
            final int themeIndex = i;
            ThemePreset preset = ThemeManager.THEME_PRESETS.get(i);
            boolean isSelected = i == selectedIndex;
            LinearLayout row = createOptionRow(activity, preset, selectedPreset, i, isSelected);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 80));
            if (i > 0) rowParams.topMargin = dp(activity, 8);
            options.addView(row, rowParams);
            row.setOnClickListener(view -> {
                ThemeManager.selectTheme(activity, themeIndex);
                dialog.dismiss();
                if (onThemeSelected != null && !activity.isFinishing()) onThemeSelected.run();
            });
        }

        dialog.setContentView(content);
        dialog.show();
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(Color.TRANSPARENT);
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setDraggable(false);
        }
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private static LinearLayout createOptionRow(Activity activity, ThemePreset preset,
                                                 ThemePreset activePreset, int index, boolean selected) {
        int textColor = activePreset.onSurface;
        int secondaryTextColor = activePreset.onSurfaceVariant;
        int accentColor = preset.primary;
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(createRowBackground(activity, activePreset, selected, accentColor));

        FrameLayout swatch = new FrameLayout(activity);
        GradientDrawable swatchBackground = new GradientDrawable();
        swatchBackground.setColor(SWATCH_SURFACES[index]);
        swatchBackground.setCornerRadius(dp(activity, 14));
        swatchBackground.setStroke(dp(activity, 1), ThemePreset.lerp(SWATCH_ACCENTS[index], Color.BLACK, 0.25f));
        swatch.setBackground(swatchBackground);

        View inset = new View(activity);
        GradientDrawable insetBackground = new GradientDrawable();
        insetBackground.setColor(ThemePreset.lerp(SWATCH_SURFACES[index], SWATCH_ACCENTS[index], 0.10f));
        insetBackground.setCornerRadius(dp(activity, 5));
        FrameLayout.LayoutParams insetParams = new FrameLayout.LayoutParams(dp(activity, 19), dp(activity, 19));
        insetParams.gravity = Gravity.TOP | Gravity.START;
        insetParams.setMargins(dp(activity, 8), dp(activity, 8), 0, 0);
        inset.setBackground(insetBackground);
        swatch.addView(inset, insetParams);

        View accentDot = new View(activity);
        GradientDrawable dotBackground = new GradientDrawable();
        dotBackground.setShape(GradientDrawable.OVAL);
        dotBackground.setColor(SWATCH_ACCENTS[index]);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(activity, 18), dp(activity, 18));
        dotParams.gravity = Gravity.BOTTOM | Gravity.END;
        dotParams.setMargins(0, 0, dp(activity, 7), dp(activity, 7));
        accentDot.setBackground(dotBackground);
        swatch.addView(accentDot, dotParams);

        row.addView(swatch, new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 52)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.setMargins(dp(activity, 14), 0, dp(activity, 8), 0);
        row.addView(labels, labelsParams);

        TextView name = new TextView(activity);
        name.setText(preset.name);
        name.setTextColor(textColor);
        name.setTextSize(16);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(name);

        TextView subtitle = new TextView(activity);
        subtitle.setText(DESCRIPTIONS[index] + (index == ThemeManager.THEME_LIQUID_GLASS ? " · Default" : ""));
        subtitle.setTextColor(secondaryTextColor);
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(activity, 2);
        labels.addView(subtitle, subtitleParams);

        if (selected) {
            TextView check = new TextView(activity);
            check.setText("✓");
            check.setTextColor(accentColor);
            check.setTextSize(24);
            check.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(check, new LinearLayout.LayoutParams(dp(activity, 28),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        return row;
    }

    private static GradientDrawable createRowBackground(Activity activity, ThemePreset preset,
                                                         boolean selected, int accentColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected
                ? ThemePreset.lerp(preset.surface, preset.onSurface, 0.10f)
                : preset.surface);
        background.setCornerRadius(dp(activity, 18));
        if (selected) {
            background.setStroke(dp(activity, 2), accentColor);
        }
        return background;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
