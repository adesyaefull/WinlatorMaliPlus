package com.winlator.cmod;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.app.Activity;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.StyleRes;
import androidx.preference.PreferenceManager;
import com.google.android.material.tabs.TabLayout;
import com.winlator.cmod.widget.LiquidGlassSurfaceDrawable;
import com.winlator.cmod.widget.GlassSpinnerPopup;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ThemeManager {
    private static final String PREF_THEME_INDEX = "app_theme_index";
    public static final String PREF_CUSTOM_ACCENT = "custom_accent_color";
    public static final String PREF_CUSTOM_BG = "custom_background_color";
    private static final String PREF_THEME_SELECTION_COMPLETE = "app_theme_selection_complete";
    private static final String PREF_THEME_PALETTE_V2 = "app_theme_palette_v2";
    public static final int THEME_LIQUID_GLASS = 0;
    public static final int THEME_MIDNIGHT_NAVY = 1;
    public static final int THEME_NEON_AMETHYST = 2;
    public static final int THEME_VANILLA_PEARL = 3;
    public static final int THEME_GRAPHITE_SHADOW = 4;
    public static final int THEME_RUBY_FLARE = 5;
    public static final int THEME_BLACK_AMOLED = 6;

    // =========================================================================
    // Theme choices use one palette for the app, dialogs, and in-game overlays.
    // =========================================================================
    public static final List<ThemePreset> THEME_PRESETS = Arrays.asList(
        new ThemePreset("Liquid Glass Enhanced", 0xFF211829, 0xFF44334F, 0xFF624A72, 0xFFD8B4FE),
        new ThemePreset("Midnight Navy",          0xFF0D1426, 0xFF19243A, 0xFF293650, 0xFF3E4784),
        new ThemePreset("Neon Amethyst",          0xFF111020, 0xFF241C36, 0xFF382B50, 0xFF7A5AF8),
        new ThemePreset("Vanilla Pearl",          0xFFFBF1EA, 0xFFFFF9F4, 0xFFF2E6DE, 0xFF536162),
        new ThemePreset("Graphite Shadow",        0xFF12191A, 0xFF202829, 0xFF303A3B, 0xFF536162),
        new ThemePreset("Ruby Flare",             0xFF1B1016, 0xFF2A1B22, 0xFF3A222C, 0xFFD82148),
        new ThemePreset("Black AMOLED",           0xFF000000, 0xFF050505, 0xFF171717, 0xFFFFFFFF)
    );

    @StyleRes
    private static final int[] THEME_STYLES = {
        R.style.ThemePreset_LiquidGlassEnhanced,
        R.style.ThemePreset_MidnightNavy,
        R.style.ThemePreset_NeonAmethyst,
        R.style.ThemePreset_VanillaPearl,
        R.style.ThemePreset_GraphiteShadow,
        R.style.ThemePreset_RubyFlare,
        R.style.ThemePreset_BlackAmoled
    };

    private ThemeManager() {}

    public static boolean isDarkMode(Context context) {
        return getSelectedPresetIndex(context) != THEME_VANILLA_PEARL;
    }

    public static boolean isFullLiquidGlassEnabled(Context context) {
        return getSelectedPresetIndex(context) == THEME_LIQUID_GLASS;
    }

    public static boolean isInitialThemeSelectionRequired(Context context) {
        return !PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_THEME_SELECTION_COMPLETE, false);
    }

    public static void selectTheme(Context context, int index) {
        int selectedIndex = Math.max(0, Math.min(THEME_PRESETS.size() - 1, index));
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt(PREF_THEME_INDEX, selectedIndex)
                .putBoolean(PREF_THEME_SELECTION_COMPLETE, true)
                .putBoolean(PREF_THEME_PALETTE_V2, true)
                .apply();
    }

    public static void applyActivityTheme(Activity activity, boolean fullscreen) {
        boolean dark = isDarkMode(activity);
        int baseTheme = fullscreen
                ? (dark ? R.style.AppThemeFullscreen_Dark : R.style.AppThemeFullscreen)
                : (dark ? R.style.AppTheme_Dark : R.style.AppTheme);
        activity.setTheme(baseTheme);
        applyTheme(activity);
    }

    public static void applyToolbarBackground(View toolbar, Context context) {
        if (toolbar == null || context == null) return;
        if (isFullLiquidGlassEnabled(context)) {
            toolbar.setBackground(createThemedSurfaceDrawable(context, 16));
        } else {
            toolbar.setBackgroundColor(getSurfaceColor(context));
        }
    }

    public static void applyGlassBackdropEffect(Activity activity) {
        applyGlassBackdropEffect(activity, false);
    }

    public static void applyContainerEditorBackdropEffect(Activity activity) {
        applyGlassBackdropEffect(activity, true);
    }

    private static void applyGlassBackdropEffect(Activity activity, boolean forceBlur) {
        if (activity == null) return;
        View backdrop = activity.findViewById(R.id.GlassBackdrop);
        if (backdrop == null) return;
        boolean glassEnabled = isFullLiquidGlassEnabled(activity);
        backdrop.setVisibility(glassEnabled ? View.VISIBLE : View.GONE);
        if (!glassEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) backdrop.setRenderEffect(null);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        if (backdrop.getWidth() == 0 || backdrop.getHeight() == 0) {
            backdrop.post(() -> {
                if (backdrop.getWidth() > 0 && backdrop.getHeight() > 0) {
                    applyGlassBackdropEffect(activity, forceBlur);
                }
            });
            return;
        }

        if (forceBlur || glassEnabled) {
            float radius = 12f * activity.getResources().getDisplayMetrics().density;
            backdrop.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        }
    }

    public static android.graphics.drawable.Drawable createGlassSurfaceDrawable(
            Context context, int fallbackResource, float cornerRadiusDp) {
        return new LiquidGlassSurfaceDrawable(context, fallbackResource, cornerRadiusDp);
    }

    public static android.graphics.drawable.Drawable createThemedSurfaceDrawable(
            Context context, float cornerRadiusDp) {
        if (isFullLiquidGlassEnabled(context)) {
            return createGlassSurfaceDrawable(context, R.drawable.liquid_glass_dialog, cornerRadiusDp);
        }
        android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
        surface.setColor(getSurfaceColor(context));
        surface.setCornerRadius(cornerRadiusDp * context.getResources().getDisplayMetrics().density);
        surface.setStroke(Math.max(1, Math.round(context.getResources().getDisplayMetrics().density)),
                getDividerColor(context));
        return surface;
    }

    public static android.graphics.drawable.Drawable createThemedNavigationItemBackground(Context context) {
        int accent = getAccentColor(context);
        int surface = getSurfaceColor(context);
        int selectedColor = isFullLiquidGlassEnabled(context)
                ? (accent & 0x00FFFFFF) | 0x55000000
                : androidx.core.graphics.ColorUtils.blendARGB(surface, accent, 0.22f);
        int rippleColor = isFullLiquidGlassEnabled(context)
                ? (accent & 0x00FFFFFF) | 0x33000000
                : androidx.core.graphics.ColorUtils.blendARGB(surface, accent, 0.30f);

        android.graphics.drawable.GradientDrawable selected = new android.graphics.drawable.GradientDrawable();
        selected.setCornerRadius(10 * context.getResources().getDisplayMetrics().density);
        selected.setColor(selectedColor);

        android.graphics.drawable.GradientDrawable idle = new android.graphics.drawable.GradientDrawable();
        idle.setColor(android.graphics.Color.TRANSPARENT);

        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_checked}, selected);
        states.addState(new int[]{}, idle);

        android.graphics.drawable.GradientDrawable mask = new android.graphics.drawable.GradientDrawable();
        mask.setCornerRadius(10 * context.getResources().getDisplayMetrics().density);
        mask.setColor(android.graphics.Color.WHITE);
        return new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(rippleColor), states, mask);
    }

    public static List<ThemePreset> getPresets(Context context) {
        return Collections.unmodifiableList(THEME_PRESETS);
    }

    public static String[] getPresetNames(Context context) {
        List<ThemePreset> presets = getPresets(context);
        String[] names = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            names[i] = presets.get(i).name;
        }
        return names;
    }

    public static int getSelectedPresetIndex(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(PREF_THEME_PALETTE_V2, false)) {
            int previousIndex = prefs.getInt(PREF_THEME_INDEX, 0);
            int migratedIndex = 0;
            if (prefs.getBoolean(PREF_THEME_SELECTION_COMPLETE, false)) {
                int[] legacyPaletteMap = {
                        THEME_LIQUID_GLASS,
                        THEME_VANILLA_PEARL,
                        THEME_GRAPHITE_SHADOW,
                        THEME_GRAPHITE_SHADOW,
                        THEME_MIDNIGHT_NAVY
                };
                if (previousIndex >= 0 && previousIndex < legacyPaletteMap.length) {
                    migratedIndex = legacyPaletteMap[previousIndex];
                }
            }
            prefs.edit()
                    .putInt(PREF_THEME_INDEX, migratedIndex)
                    .putBoolean(PREF_THEME_PALETTE_V2, true)
                    .apply();
        }
        int max = THEME_PRESETS.size() - 1;
        int index = prefs.getInt(PREF_THEME_INDEX, 0);
        if (index < 0 || index > max) index = 0;
        return index;
    }

    public static void setSelectedPresetIndex(Context context, int index) {
        int max = THEME_PRESETS.size() - 1;
        if (index < 0 || index > max) index = 0;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt(PREF_THEME_INDEX, index)
                .putBoolean(PREF_THEME_PALETTE_V2, true)
                .apply();
    }

    public static boolean isCustomPresetSelected(Context context) {
        return false;
    }

    public static void selectCustomPreset(Context context) {
        int customIndex = THEME_PRESETS.size() - 1;
        setSelectedPresetIndex(context, customIndex);
    }

    public static int getCustomAccentColor(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(PREF_CUSTOM_ACCENT, 0xFF5483B3);
    }

    public static int getCustomBackgroundColor(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(PREF_CUSTOM_BG, 0xFF021024);
    }

    public static void setCustomColors(Context context, int accent, int background) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt(PREF_CUSTOM_ACCENT, accent)
                .putInt(PREF_CUSTOM_BG, background)
                .apply();
    }

    public static ThemePreset getSelectedPreset(Context context) {
        int index = getSelectedPresetIndex(context);
        return THEME_PRESETS.get(index);
    }

    public static void applyTheme(Context context) {
        Resources.Theme theme = context.getTheme();
        if (theme == null) return;
        int index = getSelectedPresetIndex(context);
        theme.applyStyle(THEME_STYLES[index], true);
        if (isFullLiquidGlassEnabled(context)) {
            theme.applyStyle(R.style.FullLiquidGlassTheme, true);
        } else {
            theme.applyStyle(R.style.StandardTheme, true);
        }
    }

    public static int getAccentColor(Context context) {
        if (isCustomPresetSelected(context)) {
            return getCustomAccentColor(context);
        }
        return getSelectedPreset(context).primary;
    }

    public static int getPrimaryColor(Context context) {
        return getSelectedPreset(context).surfaceVariant;
    }

    public static int getBackgroundColor(Context context) {
        if (isCustomPresetSelected(context)) {
            return getCustomBackgroundColor(context);
        }
        return getSelectedPreset(context).background;
    }

    public static int getSurfaceColor(Context context) {
        return getSelectedPreset(context).surface;
    }

    public static int getOnSurfaceTextColor(Context context) {
        return getSelectedPreset(context).onSurface;
    }

    public static int getDividerColor(Context context) {
        return getSelectedPreset(context).divider;
    }

    public static void applyThemeToView(View view, Context context) {
        if (view == null || context == null) return;
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        ThemePreset preset = getSelectedPreset(context);
        int onSurface = preset.onSurface;
        int onSurfaceVariant = preset.onSurfaceVariant;
        int accent = preset.primary;
        int surface = preset.surface;
        int divider = preset.divider;

        applyThemeRecursively(view, isDarkMode(context), onSurface, onSurfaceVariant, accent, surface, divider);
    }

    private static void applyThemeRecursively(View view, boolean isDarkMode, int onSurface, int onSurfaceVariant, int accent, int surface, int divider) {
        if (view == null) return;
        boolean liquidGlass = isFullLiquidGlassEnabled(view.getContext());

        if (view instanceof EditText) {
            EditText et = (EditText) view;
            et.setTextColor(onSurface);
            et.setHintTextColor(onSurfaceVariant);
            et.setBackgroundResource(isDarkMode ? R.drawable.edit_text_dark : R.drawable.edit_text);
        } else if (view instanceof CheckBox) {
            CheckBox cb = (CheckBox) view;
            cb.setTextColor(onSurface);
            cb.setButtonTintList(ColorStateList.valueOf(accent));
        } else if (view instanceof Spinner) {
            Spinner spinner = (Spinner) view;
            spinner.setPopupBackgroundResource(isFullLiquidGlassEnabled(view.getContext())
                    ? R.drawable.liquid_glass_popup : R.drawable.theme_dialog_background);
            spinner.setBackgroundResource(isDarkMode ? R.drawable.combo_box_dark : R.drawable.combo_box);
            GlassSpinnerPopup.attach(spinner);
            int padStart = (int) (12 * spinner.getResources().getDisplayMetrics().density);
            int padEnd = (int) (42 * spinner.getResources().getDisplayMetrics().density);
            spinner.setPaddingRelative(padStart, 0, padEnd, 0);
        } else if (view instanceof TabLayout) {
            TabLayout tabLayout = (TabLayout) view;
            tabLayout.setBackgroundResource(isDarkMode ? R.drawable.tab_layout_background_dark : R.drawable.tab_layout_background);
            tabLayout.setSelectedTabIndicatorColor(accent);
            int tabColor = onSurface;
            tabLayout.setTabTextColors(tabColor, tabColor);
            for (int i = 0; i < tabLayout.getTabCount(); i++) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab != null && tab.view != null) {
                    for (int c = 0; c < tab.view.getChildCount(); c++) {
                        View child = tab.view.getChildAt(c);
                        if (child instanceof TextView) {
                            ((TextView) child).setTextColor(tabColor);
                        }
                    }
                }
            }
        } else if (view instanceof SeekBar) {
            SeekBar sb = (SeekBar) view;
            sb.setThumbTintList(ColorStateList.valueOf(accent));
            sb.setProgressTintList(ColorStateList.valueOf(accent));
        } else if (view instanceof com.google.android.material.floatingactionbutton.FloatingActionButton) {
            com.google.android.material.floatingactionbutton.FloatingActionButton fab = (com.google.android.material.floatingactionbutton.FloatingActionButton) view;
            double lum = (0.299 * android.graphics.Color.red(accent) + 0.587 * android.graphics.Color.green(accent) + 0.114 * android.graphics.Color.blue(accent)) / 255.0;
            int iconColor = lum > 0.65 ? 0xFF121212 : 0xFFFFFFFF;
            fab.setBackgroundTintList(ColorStateList.valueOf(accent));
            fab.setImageTintList(ColorStateList.valueOf(iconColor));
            fab.setSupportBackgroundTintList(ColorStateList.valueOf(accent));
            fab.setSupportImageTintList(ColorStateList.valueOf(iconColor));
        } else if (view instanceof ImageButton) {
            ImageButton ib = (ImageButton) view;
            styleImageButton(ib, isDarkMode, onSurface, onSurfaceVariant, accent, surface, divider);
        } else if (view instanceof ImageView) {
            ImageView iv = (ImageView) view;
            int id = iv.getId();
            if (id == R.id.BTHelp || id == R.id.BTHelpApiKey || id == R.id.BTHelpDXWrapper || id == R.id.BTHelpFileProvider) {
                iv.setImageTintList(ColorStateList.valueOf(onSurfaceVariant));
            } else {
                View parent = (View) iv.getParent();
                boolean isCardArt = parent != null && (parent.getId() == R.id.LLInnerArea
                        || parent.getId() == R.id.FLShortcutCover
                        || parent instanceof androidx.cardview.widget.CardView);
                boolean isIconOrButton = (id == R.id.BTRun || id == R.id.BTMenu || id == R.id.IVIcon || id == R.id.BTRefresh ||
                        id == R.id.BTAdd || id == R.id.BTAddDrive || id == R.id.BTAddEnvVar || id == R.id.BTAddProfile ||
                        id == R.id.BTAddWheel || id == R.id.BTBCNConfig ||
                        id == R.id.BTGraphicsDriverConfig || id == R.id.BTDXWrapperConfig || id == R.id.BTDuplicateProfile ||
                        id == R.id.BTEditProfile || id == R.id.BTExportProfile || id == R.id.BTImportProfile ||
                        id == R.id.BTRemoveProfile || id == R.id.BTHide || id == R.id.BTInfo || id == R.id.BTMagnifier ||
                        id == R.id.BTMove || id == R.id.BTNewFolder || id == R.id.BTOpacity || id == R.id.BTPause ||
                        id == R.id.BTRadialWheel || id == R.id.BTRemove || id == R.id.BTReset || id == R.id.BTRestoreSaves ||
                        id == R.id.BTSearch || id == R.id.BTSettings ||
                        id == R.id.BTToggleFullscreen || id == R.id.BTToggleSelectAll || id == R.id.BTUpDir ||
                        id == R.id.BTZoomMinus || id == R.id.BTZoomPlus || id == R.id.IVDriveIcon || id == R.id.IVDeviceIcon ||
                        id == R.id.BTFileMenu || id == R.id.BTCloseSearch || id == R.id.ImageView);
                if (isIconOrButton && (!isCardArt || id != R.id.ImageView)) {
                    iv.setImageTintList(ColorStateList.valueOf(accent));
                }
            }
        } else if (view instanceof android.widget.ProgressBar) {
            android.widget.ProgressBar pb = (android.widget.ProgressBar) view;
            pb.setProgressTintList(ColorStateList.valueOf(accent));
            pb.setIndeterminateTintList(ColorStateList.valueOf(accent));
        } else if (view instanceof android.widget.ToggleButton) {
            // Keep ToggleButton's dedicated toggle drawable, do NOT style as push button
            android.widget.ToggleButton tb = (android.widget.ToggleButton) view;
            tb.setTextColor(onSurface);
        } else if (view instanceof androidx.appcompat.widget.SwitchCompat) {
            androidx.appcompat.widget.SwitchCompat sc = (androidx.appcompat.widget.SwitchCompat) view;
            sc.setTextColor(onSurface);
            sc.setThumbTintList(ColorStateList.valueOf(accent));
            sc.setTrackTintList(ColorStateList.valueOf(liquidGlass
                    ? androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 100)
                    : androidx.core.graphics.ColorUtils.blendARGB(surface, accent, 0.30f)));
        } else if (view instanceof android.widget.Switch) {
            android.widget.Switch sw = (android.widget.Switch) view;
            sw.setTextColor(onSurface);
            sw.setThumbTintList(ColorStateList.valueOf(accent));
            sw.setTrackTintList(ColorStateList.valueOf(liquidGlass
                    ? androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 100)
                    : androidx.core.graphics.ColorUtils.blendARGB(surface, accent, 0.30f)));
        } else if (view instanceof android.widget.RadioButton) {
            android.widget.RadioButton rb = (android.widget.RadioButton) view;
            rb.setTextColor(onSurface);
            rb.setButtonTintList(ColorStateList.valueOf(accent));
        } else if (view instanceof android.widget.Button) {
            android.widget.Button btn = (android.widget.Button) view;
            styleButton(btn, isDarkMode, onSurface, onSurfaceVariant, accent, surface, divider);
        } else if (view instanceof TextView) {
            TextView tv = (TextView) view;
            int id = tv.getId();
            if (id == R.id.TVGraphicsDriverVersion) {
                tv.setTextColor(accent);
            } else if (isFieldSetHeader(tv)) {
                tv.setTextColor(accent);
                tv.setTypeface(null, Typeface.BOLD);
            } else {
                tv.setTextColor(onSurface);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyThemeRecursively(group.getChildAt(i), isDarkMode, onSurface, onSurfaceVariant, accent, surface, divider);
            }
        }
    }

    public static void applyFieldSetLabelStyle(TextView textView, boolean isDarkMode) {
        if (textView == null) return;
        Context context = textView.getContext();
        int accent = getAccentColor(context);
        textView.setTextColor(accent);
        textView.setTypeface(null, Typeface.BOLD);
    }

    private static boolean isFieldSetHeader(TextView tv) {
        if (tv == null) return false;

        // 0. Check explicit tag
        if ("section_header".equals(tv.getTag())) return true;

        // 1. Check if enclosed within a FrameLayout header position
        if (tv.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) tv.getParent();
            if (parent instanceof FrameLayout) {
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View sibling = parent.getChildAt(i);
                    if (sibling != tv && sibling instanceof ViewGroup) {
                        return true;
                    }
                }
            }
        }

        // 2. Check view ID
        int id = tv.getId();
        return id == R.id.TVDesktop || id == R.id.TVDirectInput || id == R.id.TVDirectX ||
               id == R.id.TVGeneral || id == R.id.TVBox64 || id == R.id.TVFEXCore ||
               id == R.id.TVSystem || id == R.id.TVImageFs || id == R.id.TVLogs ||
               id == R.id.TVExperimental || id == R.id.TVSound || id == R.id.TVTheme ||
               id == R.id.TVShortcutSettings || id == R.id.TVCustomApiKey || id == R.id.TVXServer ||
               id == R.id.TVGeneralDisplay || id == R.id.TVGraphicsRendering || id == R.id.TVSystemAudio ||
               id == R.id.TVInputControls || id == R.id.TVLaunchArgs ||
               id == R.id.TVProcessorAffinity;
    }

    public static void styleButton(android.widget.Button btn, boolean isDarkMode, int onSurface, int onSurfaceVariant, int accent, int surface, int divider) {
        if (btn == null) return;
        boolean liquidGlass = isFullLiquidGlassEnabled(btn.getContext());
        int primaryText = liquidGlass ? accent : ThemePreset.onAccentFor(accent);
        int id = btn.getId();
        CharSequence text = btn.getText();
        String textStr = text != null ? text.toString().trim().toLowerCase(java.util.Locale.ROOT) : "";

        boolean isConfirmOrPrimary = (id == R.id.BTConfirm || id == R.id.BTRun ||
                id == R.id.BTInstallContent || id == R.id.BTInstallDriver || id == R.id.BTReInstallImagefs ||
                textStr.contains("install") || textStr.contains("confirm") || textStr.contains("run") ||
                textStr.contains("save") || textStr.contains("apply") || textStr.contains("download") ||
                "ok".equals(textStr) || textStr.contains("continue") || textStr.contains("done"));

        boolean isCancelOrSecondary = (id == R.id.BTCancel || id == R.id.BTReset ||
                textStr.contains("cancel") || textStr.contains("reset") || textStr.contains("close") ||
                textStr.contains("clear") || textStr.contains("no,") || "no".equals(textStr));

        float cornerRadius = com.winlator.cmod.core.UnitUtils.dpToPx(8);
        int strokeWidth = (int) Math.max(1, com.winlator.cmod.core.UnitUtils.dpToPx(1));

        if (isConfirmOrPrimary) {
            android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();
            android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable();
            int normalBg = liquidGlass ? 0x1AFFFFFF : accent;
            int strokeColor = liquidGlass ? 0x99FFFFFF : accent;
            int strokeWidthAccent = (int) Math.max(1, com.winlator.cmod.core.UnitUtils.dpToPx(1.5f));
            normal.setColor(normalBg);
            normal.setStroke(strokeWidthAccent, strokeColor);
            normal.setCornerRadius(cornerRadius);

            android.graphics.drawable.GradientDrawable pressed = new android.graphics.drawable.GradientDrawable();
            int pressedBg = liquidGlass ? 0x40FFFFFF
                    : androidx.core.graphics.ColorUtils.blendARGB(accent, primaryText, 0.12f);
            pressed.setColor(pressedBg);
            pressed.setStroke(strokeWidthAccent, strokeColor);
            pressed.setCornerRadius(cornerRadius);

            sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
            sld.addState(new int[]{android.R.attr.state_focused}, pressed);
            sld.addState(new int[]{}, normal);

            btn.setBackground(sld);
            btn.setBackgroundTintList(null);

            btn.setTextColor(primaryText);
            btn.setTypeface(null, Typeface.BOLD);
        } else if (isCancelOrSecondary) {
            android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();
            android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable();
            int normalBg = liquidGlass ? 0x1AFFFFFF : surface;
            int strokeColor = liquidGlass ? 0x80FFFFFF : divider;
            int strokeWidthCancel = (int) Math.max(1, com.winlator.cmod.core.UnitUtils.dpToPx(1.5f));
            normal.setColor(normalBg);
            normal.setStroke(strokeWidthCancel, strokeColor);
            normal.setCornerRadius(cornerRadius);

            android.graphics.drawable.GradientDrawable pressed = new android.graphics.drawable.GradientDrawable();
            int pressedBg = liquidGlass ? 0x40FFFFFF
                    : androidx.core.graphics.ColorUtils.blendARGB(surface, accent, 0.16f);
            pressed.setColor(pressedBg);
            pressed.setStroke(strokeWidthCancel, strokeColor);
            pressed.setCornerRadius(cornerRadius);

            sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
            sld.addState(new int[]{android.R.attr.state_focused}, pressed);
            sld.addState(new int[]{}, normal);

            btn.setBackground(sld);
            btn.setBackgroundTintList(null);

            btn.setTextColor(onSurface);
            btn.setTypeface(null, Typeface.BOLD);
        } else {
            android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();
            android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable();
            int normalBg = liquidGlass ? 0x1AFFFFFF : surface;
            int strokeColor = liquidGlass ? 0x80FFFFFF : divider;
            normal.setColor(normalBg);
            normal.setStroke(strokeWidth, strokeColor);
            normal.setCornerRadius(cornerRadius);

            android.graphics.drawable.GradientDrawable pressed = new android.graphics.drawable.GradientDrawable();
            int pressedBg = liquidGlass ? 0x40FFFFFF
                    : androidx.core.graphics.ColorUtils.blendARGB(surface, accent, 0.16f);
            pressed.setColor(pressedBg);
            pressed.setStroke(strokeWidth, strokeColor);
            pressed.setCornerRadius(cornerRadius);

            sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
            sld.addState(new int[]{android.R.attr.state_focused}, pressed);
            sld.addState(new int[]{}, normal);

            btn.setBackground(sld);
            btn.setBackgroundTintList(null);

            btn.setTextColor(onSurface);
            btn.setTypeface(null, Typeface.BOLD);
        }
    }

    public static void styleImageButton(ImageButton ib, boolean isDarkMode, int onSurface, int onSurfaceVariant, int accent, int surface, int divider) {
        if (ib == null) return;
        ib.setImageTintList(ColorStateList.valueOf(accent));
        boolean liquidGlass = isFullLiquidGlassEnabled(ib.getContext());

        int id = ib.getId();
        if (id == R.id.BTUpDir || id == R.id.BTSearch || id == R.id.BTCloseSearch || id == R.id.BTInfo) {
            return;
        }

        float cornerRadius = com.winlator.cmod.core.UnitUtils.dpToPx(8);
        int strokeWidth = (int) Math.max(1, com.winlator.cmod.core.UnitUtils.dpToPx(1.2f));

        android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable();
        int normalBg = liquidGlass
                ? androidx.core.graphics.ColorUtils.setAlphaComponent(accent, isDarkMode ? 35 : 25)
                : surface;
        int strokeColor = liquidGlass
                ? androidx.core.graphics.ColorUtils.setAlphaComponent(accent, isDarkMode ? 120 : 90)
                : divider;
        normal.setColor(normalBg);
        normal.setStroke(strokeWidth, strokeColor);
        normal.setCornerRadius(cornerRadius);

        android.graphics.drawable.GradientDrawable pressed = new android.graphics.drawable.GradientDrawable();
        int pressedBg = liquidGlass
                ? androidx.core.graphics.ColorUtils.setAlphaComponent(accent, isDarkMode ? 90 : 60)
                : androidx.core.graphics.ColorUtils.blendARGB(surface, accent, 0.16f);
        pressed.setColor(pressedBg);
        pressed.setStroke(strokeWidth, strokeColor);
        pressed.setCornerRadius(cornerRadius);

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);

        ib.setBackground(sld);
        ib.setBackgroundTintList(null);
    }

    private static int adjustColorBrightness(int color, float factor) {
        int a = android.graphics.Color.alpha(color);
        int r = Math.round(android.graphics.Color.red(color) * factor);
        int g = Math.round(android.graphics.Color.green(color) * factor);
        int b = Math.round(android.graphics.Color.blue(color) * factor);
        return android.graphics.Color.argb(a,
                Math.min(r, 255),
                Math.min(g, 255),
                Math.min(b, 255));
    }
}
