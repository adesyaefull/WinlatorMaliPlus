package com.winlator.cmod;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.ArrayMap;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.cmod.box64.Box64EditPresetDialog;
import com.winlator.cmod.box64.Box64Preset;
import com.winlator.cmod.box64.Box64PresetManager;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.ArrayUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.LsfgNative;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.fexcore.FEXCoreEditPresetDialog;
import com.winlator.cmod.fexcore.FEXCorePreset;
import com.winlator.cmod.fexcore.FEXCorePresetManager;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.xenvironment.ImageFsInstaller;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsFragment extends Fragment {
    public static final String DEFAULT_WINE_DEBUG_CHANNELS = "warn,err,fixme";
    public static final String DEFAULT_WINLATOR_PATH = Environment.getExternalStorageDirectory().getPath() + "/Winlator";
    public static final String DEFAULT_SHORTCUT_EXPORT_PATH = DEFAULT_WINLATOR_PATH + "/Shortcuts";
    private Callback<Uri> installSoundFontCallback;
    private PreloaderDialog preloaderDialog;
    private SharedPreferences preferences;

	// Disable or enable True Mouse Control
	private CheckBox cbCursorLock;
    // Disable or enable Xinput Processing
    private CheckBox cbXinputToggle;

    private CheckBox cbEnableCustomApiKey;
    private EditText etCustomApiKey;

    boolean isDarkMode = true;

    private static final int REQUEST_CODE_WINLATOR_PATH = 1002;
    private static final int REQUEST_CODE_SHORTCUT_EXPORT_PATH = 1003;
    private static final int REQUEST_CODE_INSTALL_SOUNDFONT = 1001;
    private static final int REQUEST_CODE_IMPORT_BOX64_PRESET = 1004;
    private static final int REQUEST_CODE_IMPORT_FEXCORE_PRESET = 1005;
    private static final int REQUEST_CODE_IMPORT_LOSSLESS_DLL = 1006;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Apply dynamic styles to all labels
        applyDynamicStylesRecursively(view);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.settings);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.settings_fragment, container, false);
        final Context context = getContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Check for Dark Mode preference
        isDarkMode = ThemeManager.isDarkMode(context);
        // Apply dynamic styles
        applyDynamicStyles(view, isDarkMode);

        final com.google.android.material.tabs.TabLayout tabLayout = view.findViewById(R.id.TabLayout);
        final View llTabAppearance = view.findViewById(R.id.LLTabAppearance);
        final View llTabEmulation = view.findViewById(R.id.LLTabEmulation);
        final View llTabInputDisplay = view.findViewById(R.id.LLTabInputDisplay);
        final View llTabStorageAudio = view.findViewById(R.id.LLTabStorageAudio);
        final View llTabPerformance = view.findViewById(R.id.LLTabPerformance);
        final View llTabAdvanced = view.findViewById(R.id.LLTabAdvanced);

        if (tabLayout != null) {
            final int tabTextColor = isDarkMode ? 0xFFFFFFFF : 0xFF121212;
            final int accent = ThemeManager.getAccentColor(context);
            tabLayout.setSelectedTabIndicatorColor(accent);
            tabLayout.setTabTextColors(tabTextColor, tabTextColor);
            tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                    tabLayout.setTabTextColors(tabTextColor, tabTextColor);
                    if (llTabAppearance != null) llTabAppearance.setVisibility(View.GONE);
                    if (llTabEmulation != null) llTabEmulation.setVisibility(View.GONE);
                    if (llTabInputDisplay != null) llTabInputDisplay.setVisibility(View.GONE);
                    if (llTabStorageAudio != null) llTabStorageAudio.setVisibility(View.GONE);
                    if (llTabPerformance != null) llTabPerformance.setVisibility(View.GONE);
                    if (llTabAdvanced != null) llTabAdvanced.setVisibility(View.GONE);

                    switch (tab.getPosition()) {
                        case 0:
                            if (llTabAppearance != null) llTabAppearance.setVisibility(View.VISIBLE);
                            break;
                        case 1:
                            if (llTabEmulation != null) llTabEmulation.setVisibility(View.VISIBLE);
                            break;
                        case 2:
                            if (llTabInputDisplay != null) llTabInputDisplay.setVisibility(View.VISIBLE);
                            break;
                        case 3:
                            if (llTabStorageAudio != null) llTabStorageAudio.setVisibility(View.VISIBLE);
                            break;
                        case 4:
                            if (llTabPerformance != null) llTabPerformance.setVisibility(View.VISIBLE);
                            break;
                        case 5:
                            if (llTabAdvanced != null) llTabAdvanced.setVisibility(View.VISIBLE);
                            break;
                    }
                }

                @Override
                public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            });
        }

        TextView selectAppTheme = view.findViewById(R.id.BTSelectAppTheme);
        if (selectAppTheme != null) {
            updateSelectedThemeLabel(selectAppTheme, context);
            selectAppTheme.setOnClickListener(v ->
                    com.winlator.cmod.contentdialog.ThemeSelectionDialog.show(
                            requireActivity(), false, () -> requireActivity().recreate()));
        }

        initCustomApiKeySettings(view);

        // Initialize the cursor lock checkbox
        cbCursorLock = view.findViewById(R.id.CBCursorLock);
        cbCursorLock.setChecked(preferences.getBoolean("cursor_lock", true));
        final CheckBox cbMoveCursorToTouchpoint = view.findViewById(R.id.CBMoveCursorToTouchpoint);
        cbMoveCursorToTouchpoint.setChecked(preferences.getBoolean("move_cursor_to_touchpoint", false));
        cbMoveCursorToTouchpoint.setOnCheckedChangeListener((b, checked) -> preferences.edit().putBoolean("move_cursor_to_touchpoint", checked).apply());

        // Initialize the xinput toggle checkbox
        cbXinputToggle = view.findViewById(R.id.CBXinputToggle);
        cbXinputToggle.setChecked(preferences.getBoolean("xinput_toggle", false));

        Button btnChooseWinlatorPath = view.findViewById(R.id.BTChooseWinlatorPath);
        TextView tvWinlatorPath = view.findViewById(R.id.TVWinlatorPath);

        String savedUriString = preferences.getString("winlator_path_uri", null);
        if (savedUriString == null) {
            // No saved path, set default path
            tvWinlatorPath.setText(DEFAULT_WINLATOR_PATH);
        } else {
            // Parse and display the saved URI path
            Uri savedUri = Uri.parse(savedUriString);
            String displayPath = FileUtils.getFilePathFromUri(getContext(), savedUri);
            tvWinlatorPath.setText(displayPath != null ? displayPath : savedUriString);
        }

        btnChooseWinlatorPath.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); // Launch File Picker for directory selection
            startActivityForResult(intent, REQUEST_CODE_WINLATOR_PATH);
        });

        Button btChooseShortcutExportPath = view.findViewById(R.id.BTChooseShortcutExportPath);
        TextView tvShortcutExportPath = view.findViewById(R.id.TVShortcutExportPath);

        savedUriString = preferences.getString("shortcuts_export_path_uri", null);

        if (savedUriString != null) {
            Uri savedUri = Uri.parse(savedUriString);
            String displayPath = FileUtils.getFilePathFromUri(context, savedUri);
            tvShortcutExportPath.setText(displayPath != null ? displayPath : savedUriString);
        }
        else {
            tvShortcutExportPath.setText(DEFAULT_SHORTCUT_EXPORT_PATH);
        }

        btChooseShortcutExportPath.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, REQUEST_CODE_SHORTCUT_EXPORT_PATH);
        });

        final Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        loadBox64PresetSpinners(view, sBox64Preset);

        final Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        loadFEXCorePresetSpinners(view, sFEXCorePreset);

        final Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);

        sMIDISoundFont.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        TextView tvLosslessDllStatus = view.findViewById(R.id.TVLosslessDllStatus);
        if (LsfgNative.isDllAvailable(context)) {
            tvLosslessDllStatus.setText(R.string.lossless_dll_imported);
        }
        view.findViewById(R.id.BTImportLosslessDll).setOnClickListener(v -> openFile(REQUEST_CODE_IMPORT_LOSSLESS_DLL));

        final View btInstallSF = view.findViewById(R.id.BTInstallSF);
        final View btRemoveSF = view.findViewById(R.id.BTRemoveSF);

        MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
        btInstallSF.setOnClickListener(v -> {
            installSoundFontCallback = uri -> {
                PreloaderDialog dialog = new PreloaderDialog(requireActivity());
                dialog.showOnUiThread(R.string.installing_content);
                MidiManager.installSF2File(context, uri, new MidiManager.OnSoundFontInstalledCallback() {
                    @Override
                    public void onSuccess() {
                        dialog.closeOnUiThread();
                        requireActivity().runOnUiThread(() -> {
                            ContentDialog.alert(context, R.string.sound_font_installed_success, null);
                            MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
                        });
                    }

                    @Override
                    public void onFailed(int reason) {
                        dialog.closeOnUiThread();
                        int resId = switch (reason) {
                            case MidiManager.ERROR_BADFORMAT -> R.string.sound_font_bad_format;
                            case MidiManager.ERROR_EXIST -> R.string.sound_font_already_exist;
                            default -> R.string.sound_font_installed_failed;
                        };
                        requireActivity().runOnUiThread(() -> ContentDialog.alert(context, resId, null));
                    }
                });
            };

            // Open the file picker with the request code for SoundFont installation
            openFile(REQUEST_CODE_INSTALL_SOUNDFONT);
        });

        btRemoveSF.setOnClickListener(v -> {
            if (sMIDISoundFont.getSelectedItemPosition() != 0) {
                ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_sound_font, () -> {
                    if (MidiManager.removeSF2File(context, sMIDISoundFont.getSelectedItem().toString())) {
                        AppUtils.showToast(context, R.string.sound_font_removed_success);
                        MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
                    } else
                        AppUtils.showToast(context, R.string.sound_font_removed_failed);
                });
            } else
                AppUtils.showToast(context, R.string.cannot_remove_default_sound_font);
        });

        final CheckBox cbUseDRI3 = view.findViewById(R.id.CBUseDRI3);
        cbUseDRI3.setChecked(preferences.getBoolean("use_dri3", true));

        // Logs: everything is in the Log Manager (it saves its own switches at once).
        View openLogManager = view.findViewById(R.id.LLOpenLogManager);
        openLogManager.setOnClickListener(v -> getParentFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.slide_in_up, R.anim.slide_out_down, R.anim.slide_in_down, R.anim.slide_out_up)
            .addToBackStack(null)
            .replace(R.id.FLFragmentContainer, new LogManagerFragment())
            .commit());
        TextView tvLogSummary = view.findViewById(R.id.TVLogManagerSummary);
        boolean recording = com.winlator.cmod.logs.LogFiles.isOn(context, "enable_wine_debug") || com.winlator.cmod.logs.LogFiles.isOn(context, "enable_box64_logs");
        tvLogSummary.setText((!com.winlator.cmod.logs.LogFiles.isOn(context, com.winlator.cmod.logs.LogFiles.PREF_ENABLED) ? "Logs off" : recording ? "Recording game logs" : "Game logs off") + "  ·  " + com.winlator.cmod.logs.LogFiles.root(context).getAbsolutePath());

        final TextView tvCursorSpeed = view.findViewById(R.id.TVCursorSpeed);
        final SeekBar sbCursorSpeed = view.findViewById(R.id.SBCursorSpeed);
        sbCursorSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvCursorSpeed.setText(progress+"%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbCursorSpeed.setProgress((int)(preferences.getFloat("cursor_speed", 1.0f) * 100));

        final CheckBox cbEnableFileProvider = view.findViewById(R.id.CBEnableFileProvider);
        final View btHelpFileProvider = view.findViewById(R.id.BTHelpFileProvider);

        cbEnableFileProvider.setChecked(preferences.getBoolean("enable_file_provider", true));
        cbEnableFileProvider.setOnClickListener(v -> AppUtils.showToast(context, R.string.take_effect_next_startup));
        btHelpFileProvider.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_file_provider));

        final CheckBox cbOpenInBrowser = view.findViewById(R.id.CBOpenWithAndroidBrowser);
        cbOpenInBrowser.setChecked(preferences.getBoolean("open_with_android_browser", false));

        final CheckBox cbShareClipboard = view.findViewById(R.id.CBShareAndroidClipboard);
        cbShareClipboard.setChecked(preferences.getBoolean("share_android_clipboard", false));

        final CheckBox cbShowAdrenoTools = view.findViewById(R.id.CBShowAdrenoTools);
        cbShowAdrenoTools.setChecked(preferences.getBoolean("show_adrenotools_unsupported", true));
        cbShowAdrenoTools.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("show_adrenotools_unsupported", isChecked).apply();
            if (getActivity() != null) {
                NavigationView navView = getActivity().findViewById(R.id.NavigationView);
                if (navView != null) {
                    android.view.MenuItem item = navView.getMenu().findItem(R.id.main_menu_adrenotools_gpu_drivers);
                    if (item != null) {
                        item.setVisible(isChecked || com.winlator.cmod.core.GPUInformation.isAdrenoGPU(context));
                    }
                }
            }
        });

        final EditText etDownloadableContentsURL = view.findViewById(R.id.ETDownloadableContentsURL);
        String savedCatalogUrl = preferences.getString("downloadable_contents_url", ContentsManager.BANNERLATOR_PROFILES);
        if (savedCatalogUrl.contains("gitlab") || savedCatalogUrl.contains("Sd/")) {
            savedCatalogUrl = ContentsManager.BANNERLATOR_PROFILES;
        }
        etDownloadableContentsURL.setText(savedCatalogUrl);

        final Spinner sCatalogPreset = view.findViewById(R.id.SCatalogPreset);
        if (sCatalogPreset != null) {
            String[] presets = {
                "☁️ Bannerlator (The412Banner)",
                "☁️ WinNative (nicholasx417)",
                "☁️ StevenMXZ Contents",
                "🌐 Custom Catalog URL"
            };
            int appearanceTextColor = ThemeManager.getOnSurfaceTextColor(context);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, presets) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextColor(appearanceTextColor);
                    }
                    return v;
                }

                @Override
                public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = super.getDropDownView(position, convertView, parent);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextColor(appearanceTextColor);
                        v.setBackground(ThemeManager.createThemedSurfaceDrawable(context, 10));
                    }
                    return v;
                }
            };
            sCatalogPreset.setAdapter(adapter);
            sCatalogPreset.setPopupBackgroundResource(ThemeManager.isFullLiquidGlassEnabled(context)
                    ? R.drawable.liquid_glass_popup : R.drawable.theme_dialog_background);

            if (savedCatalogUrl.equals(ContentsManager.BANNERLATOR_PROFILES)) {
                sCatalogPreset.setSelection(0, false);
            } else if (savedCatalogUrl.equals(ContentsManager.WINNATIVE_PROFILES)) {
                sCatalogPreset.setSelection(1, false);
            } else if (savedCatalogUrl.equals(ContentsManager.STEVENMXZ_PROFILES)) {
                sCatalogPreset.setSelection(2, false);
            } else {
                sCatalogPreset.setSelection(3, false);
            }

            sCatalogPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                    switch (position) {
                        case 0 -> etDownloadableContentsURL.setText(ContentsManager.BANNERLATOR_PROFILES);
                        case 1 -> etDownloadableContentsURL.setText(ContentsManager.WINNATIVE_PROFILES);
                        case 2 -> etDownloadableContentsURL.setText(ContentsManager.STEVENMXZ_PROFILES);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        final CheckBox cbGameModeSignal = view.findViewById(R.id.CBGameModeSignal);
        final CheckBox cbThreadPriorityBoost = view.findViewById(R.id.CBThreadPriorityBoost);
        final CheckBox cbPreferBigCores = view.findViewById(R.id.CBPreferBigCores);
        final CheckBox cbHighRefreshRate = view.findViewById(R.id.CBHighRefreshRate);
        final CheckBox cbSustainedPerformance = view.findViewById(R.id.CBSustainedPerformance);
        final CheckBox cbSamsungBoost = view.findViewById(R.id.CBSamsungBoost);
        final View llSamsungBoost = view.findViewById(R.id.LLSamsungBoost);
        final Button requestRootPerformanceAccess =
                view.findViewById(R.id.BTSettingsRequestRootPerformanceAccess);
        final TextView rootPerformanceAccessStatus =
                view.findViewById(R.id.TVSettingsRootPerformanceAccessStatus);
        final View rootPerformanceOptions =
                view.findViewById(R.id.LLSettingsRootPerformanceOptions);
        final CheckBox cbRootPerformance =
                view.findViewById(R.id.CBSettingsRootPerformanceProfile);
        final Spinner rootCpuGovernor = view.findViewById(R.id.SPSettingsRootCpuGovernor);
        final TextView rootCpuGovernorLabel =
                view.findViewById(R.id.TVSettingsRootCpuGovernorLabel);
        final Spinner rootGpuGovernor = view.findViewById(R.id.SPSettingsRootGpuGovernor);
        final TextView rootGpuGovernorLabel =
                view.findViewById(R.id.TVSettingsRootGpuGovernorLabel);
        final Spinner rootIoScheduler = view.findViewById(R.id.SPSettingsRootIoScheduler);
        final TextView rootIoSchedulerLabel =
                view.findViewById(R.id.TVSettingsRootIoSchedulerLabel);
        final boolean[] rootAccessGranted = {false};
        LinearLayout deviceInfoGrid = view.findViewById(R.id.LLSettingsDeviceInfoGrid);
        View deviceInfoHeader = view.findViewById(R.id.LLSettingsDeviceInfoHeader);
        TextView deviceInfoExpand = view.findViewById(R.id.TVSettingsDeviceInfoExpand);
        populateDeviceInfo(deviceInfoGrid, context);
        deviceInfoHeader.setOnClickListener(header -> {
            boolean expanded = deviceInfoGrid.getVisibility() != View.VISIBLE;
            deviceInfoGrid.setVisibility(expanded ? View.VISIBLE : View.GONE);
            deviceInfoExpand.setText(expanded ? "⌃" : "⌄");
            deviceInfoHeader.setContentDescription(
                    expanded ? "Collapse device information" : "Expand device information");
        });

        if (cbGameModeSignal != null) cbGameModeSignal.setChecked(preferences.getBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_GAME_MODE_SIGNAL, true));
        if (cbThreadPriorityBoost != null) cbThreadPriorityBoost.setChecked(preferences.getBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_THREAD_PRIORITY_BOOST, true));
        if (cbPreferBigCores != null) cbPreferBigCores.setChecked(preferences.getBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_PREFER_BIG_CORES, false));
        if (cbHighRefreshRate != null) cbHighRefreshRate.setChecked(preferences.getBoolean("high_refresh_rate_mode", false));
        if (cbSustainedPerformance != null) cbSustainedPerformance.setChecked(preferences.getBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_SUSTAINED_PERFORMANCE, false));
        if (cbSamsungBoost != null) cbSamsungBoost.setChecked(preferences.getBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_SAMSUNG_PERF_BOOST, true));
        if (llSamsungBoost != null) {
            llSamsungBoost.setVisibility(com.winlator.cmod.perf.SamsungSPerfDriver.isSamsungDevice() ? View.VISIBLE : View.GONE);
        }
        requestRootPerformanceAccess.setOnClickListener(button -> {
            requestRootPerformanceAccess.setEnabled(false);
            requestRootPerformanceAccess.setText("Waiting for root manager...");
            com.winlator.cmod.perf.RootPerformanceManager.requestAccess(requireContext(),
                    (controls, error) -> {
                if (!isAdded() || getView() != view) return;
                requestRootPerformanceAccess.setEnabled(true);
                if (error != null) {
                    requestRootPerformanceAccess.setText("Grant root access");
                    AppUtils.showToast(getContext(),
                            "Root performance options remain locked: " + error);
                    return;
                }

                rootAccessGranted[0] = true;
                rootPerformanceOptions.setVisibility(View.VISIBLE);
                rootPerformanceAccessStatus.setText("Root access granted by the root manager.");
                rootPerformanceAccessStatus.setVisibility(View.VISIBLE);
                requestRootPerformanceAccess.setText("Root access granted");
                requestRootPerformanceAccess.setEnabled(false);

                List<String> cpuOptions = withNoChange(controls.cpuGovernors);
                List<String> gpuOptions = withNoChange(controls.gpuGovernors);
                List<String> ioOptions = withNoChange(controls.ioSchedulers);
                configureRootTuningSpinner(rootCpuGovernor, rootCpuGovernorLabel,
                        cpuOptions, preferences.getString(
                        com.winlator.cmod.perf.PerformanceManager.PREF_ROOT_CPU_GOVERNOR,
                        cpuOptions.contains("performance") ? "performance" : "No change"));
                configureRootTuningSpinner(rootGpuGovernor, rootGpuGovernorLabel,
                        gpuOptions, preferences.getString(
                        com.winlator.cmod.perf.PerformanceManager.PREF_ROOT_GPU_GOVERNOR,
                        "No change"));
                configureRootTuningSpinner(rootIoScheduler, rootIoSchedulerLabel,
                        ioOptions, preferences.getString(
                        com.winlator.cmod.perf.PerformanceManager.PREF_ROOT_IO_SCHEDULER,
                        "No change"));
                cbRootPerformance.setChecked(preferences.getBoolean(
                        com.winlator.cmod.perf.PerformanceManager.PREF_ROOT_PERFORMANCE_PROFILE,
                        false));
            });
        });

        view.findViewById(R.id.BTReInstallImagefs).setOnClickListener(v -> {
            ContentDialog.confirm(context, R.string.do_you_want_to_reinstall_imagefs, () -> ImageFsInstaller.installFromAssets((MainActivity) getActivity()));
        });

        view.findViewById(R.id.BTConfirm).setOnClickListener((v) -> {
            SharedPreferences.Editor editor = preferences.edit();

            editor.putString("box64_preset", Box64PresetManager.getSpinnerSelectedId(sBox64Preset));
            editor.putString("fexcore_preset", FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
            editor.putBoolean("use_dri3", cbUseDRI3.isChecked());
            editor.putFloat("cursor_speed", sbCursorSpeed.getProgress() / 100.0f);
            editor.putBoolean("cursor_lock", cbCursorLock.isChecked()); // Save cursor lock state
            editor.putBoolean("xinput_toggle", cbXinputToggle.isChecked()); // Save xinput toggle state
            editor.putBoolean("enable_file_provider", cbEnableFileProvider.isChecked());
            editor.putBoolean("open_with_android_browser", cbOpenInBrowser.isChecked());
            editor.putBoolean("share_android_clipboard", cbShareClipboard.isChecked());
            editor.putBoolean("show_adrenotools_unsupported", cbShowAdrenoTools.isChecked());

            if (cbGameModeSignal != null) editor.putBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_GAME_MODE_SIGNAL, cbGameModeSignal.isChecked());
            if (cbThreadPriorityBoost != null) editor.putBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_THREAD_PRIORITY_BOOST, cbThreadPriorityBoost.isChecked());
            if (cbPreferBigCores != null) editor.putBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_PREFER_BIG_CORES, cbPreferBigCores.isChecked());
            if (cbHighRefreshRate != null) editor.putBoolean("high_refresh_rate_mode", cbHighRefreshRate.isChecked());
            if (cbSustainedPerformance != null) editor.putBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_SUSTAINED_PERFORMANCE, cbSustainedPerformance.isChecked());
            if (cbSamsungBoost != null) editor.putBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_SAMSUNG_PERF_BOOST, cbSamsungBoost.isChecked());
            if (rootAccessGranted[0]) {
                editor.putBoolean(
                        com.winlator.cmod.perf.PerformanceManager.PREF_ROOT_PERFORMANCE_PROFILE,
                        cbRootPerformance.isChecked());
                saveRootSpinnerSelection(editor, rootCpuGovernor,
                        com.winlator.cmod.perf.PerformanceManager.PREF_ROOT_CPU_GOVERNOR);
                saveRootSpinnerSelection(editor, rootGpuGovernor,
                        com.winlator.cmod.perf.PerformanceManager.PREF_ROOT_GPU_GOVERNOR);
                saveRootSpinnerSelection(editor, rootIoScheduler,
                        com.winlator.cmod.perf.PerformanceManager.PREF_ROOT_IO_SCHEDULER);
            }

            editor.putString("downloadable_contents_url", etDownloadableContentsURL.getText().toString());

            saveCustomApiKeySettings(editor);

            if (editor.commit()) {
                NavigationView navigationView = getActivity().findViewById(R.id.NavigationView);
                navigationView.setCheckedItem(R.id.main_menu_containers);
                FragmentManager fragmentManager = getParentFragmentManager();
                fragmentManager.beginTransaction()
                        .replace(R.id.FLFragmentContainer, new ContainersFragment())
                        .commit();
            }
        });

        return view;
    }

    private void updateTheme(boolean isDarkMode) {
        ThemeManager.applyActivityTheme(getActivity(), false);
        getActivity().recreate();
    }

    private static void updateSelectedThemeLabel(TextView view, Context context) {
        String themeName = ThemeManager.getSelectedPreset(context).name;
        view.setText(themeName + "  ›");
    }

    private List<String> withNoChange(List<String> values) {
        ArrayList<String> options = new ArrayList<>();
        options.add("No change");
        options.addAll(values);
        return options;
    }

    private void configureRootTuningSpinner(Spinner spinner, TextView label,
                                            List<String> options, String selected) {
        if (options.size() <= 1) return;
        spinner.setAdapter(createRootTuningAdapter(requireContext(), options));
        com.winlator.cmod.widget.GlassSpinnerPopup.attach(spinner);
        spinner.setPopupBackgroundResource(isDarkMode
                ? R.drawable.content_dialog_background_dark
                : R.drawable.content_dialog_background);
        spinner.setSelection(Math.max(0, options.indexOf(selected)), false);
        spinner.setVisibility(View.VISIBLE);
        label.setVisibility(View.VISIBLE);
    }

    private ArrayAdapter<String> createRootTuningAdapter(Context context, List<String> options) {
        int textColor = ThemeManager.getOnSurfaceTextColor(context);
        return new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item,
                options) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView,
                                @NonNull ViewGroup parent) {
                View itemView = super.getView(position, convertView, parent);
                if (itemView instanceof TextView) ((TextView) itemView).setTextColor(textColor);
                return itemView;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView,
                                        @NonNull ViewGroup parent) {
                View itemView = super.getDropDownView(position, convertView, parent);
                if (itemView instanceof TextView) {
                    TextView text = (TextView) itemView;
                    text.setTextColor(textColor);
                    text.setSingleLine(true);
                    text.setPaddingRelative(dp(context, 18), dp(context, 12),
                            dp(context, 18), dp(context, 12));
                }
                itemView.setBackgroundColor(ThemeManager.isFullLiquidGlassEnabled(context)
                        ? Color.TRANSPARENT : ThemeManager.getSurfaceColor(context));
                return itemView;
            }
        };
    }

    private void populateDeviceInfo(LinearLayout grid, Context context) {
        String chip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? (Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL).trim()
                : Build.HARDWARE;
        if (chip.isEmpty()) chip = Build.HARDWARE;
        String[] abis = Build.SUPPORTED_ABIS;
        String abi = abis.length == 0 ? "Unknown" : abis[0];
        String version = "Unknown";
        try {
            android.content.pm.PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            version = packageInfo.versionName == null ? "Unknown" : packageInfo.versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            android.util.Log.w("SettingsFragment", "Could not read Winlator version", e);
        }
        String selinuxStatus = readSelinuxStatus();
        String deviceName = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        String androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        String fingerprint = Build.FINGERPRINT == null || Build.FINGERPRINT.isEmpty()
                ? "Unknown" : Build.FINGERPRINT;
        String kernelVersion = System.getProperty("os.version", "Unknown");
        String[][] items = {
                {"Kernel version", kernelVersion},
                {"Device name", deviceName},
                {"Platform chip", chip},
                {"Winlator version", version},
                {"Build fingerprint", fingerprint},
                {"SELinux status", selinuxStatus},
                {"Android ABI", abi},
                {"Android version", androidVersion}
        };
        grid.removeAllViews();
        for (int i = 0; i < items.length; i += 2) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rowParams.topMargin = dp(context, 8);
            grid.addView(row, rowParams);
            addDeviceInfoCell(row, context, items[i][0], items[i][1], true);
            addDeviceInfoCell(row, context, items[i + 1][0], items[i + 1][1], false);
        }
    }

    private String readSelinuxStatus() {
        File enforceFile = new File("/sys/fs/selinux/enforce");
        if (!enforceFile.canRead()) return "Unavailable";
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(enforceFile))) {
            String value = reader.readLine();
            if ("1".equals(value)) return "Enforcing";
            if ("0".equals(value)) return "Permissive";
        } catch (IOException e) {
            android.util.Log.w("SettingsFragment", "Could not read SELinux status", e);
        }
        return "Unavailable";
    }

    private void addDeviceInfoCell(LinearLayout row, Context context, String label, String value,
                                   boolean first) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(context, 14), dp(context, 14),
                dp(context, 14), dp(context, 14));
        cell.setBackground(ThemeManager.createThemedSurfaceDrawable(context, 22));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
                ThemeManager.getOnSurfaceTextColor(context), 185));
        labelView.setTextSize(12);
        cell.addView(labelView);

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextColor(ThemeManager.getOnSurfaceTextColor(context));
        valueView.setTextSize(14);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        valueView.setMaxLines(2);
        valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dp(context, 4);
        cell.addView(valueView, valueParams);

        LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        if (first) cellParams.rightMargin = dp(context, 8);
        row.addView(cell, cellParams);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void saveRootSpinnerSelection(SharedPreferences.Editor editor, Spinner spinner,
                                                 String preferenceKey) {
        Object selected = spinner.getSelectedItem();
        if (selected != null) {
            String value = selected.toString();
            editor.putString(preferenceKey, "No change".equals(value) ? "" : value);
        }
    }


    private void applyDynamicStyles(View view, boolean isDarkMode) {

        Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        sBox64Preset.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        if (sFEXCorePreset != null) sFEXCorePreset.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        com.winlator.cmod.ThemeManager.applyThemeToView(view, getContext());
        applyLiquidGlassSettingsSurfaces(view);
    }

    private void applyLiquidGlassSettingsSurfaces(View view) {
        Context context = getContext();
        if (context == null) return;
        applyLiquidGlassSettingsSurfacesRecursively(view, context);
        com.google.android.material.tabs.TabLayout tabLayout = view.findViewById(R.id.TabLayout);
        if (tabLayout != null) {
            tabLayout.setBackground(ThemeManager.createThemedSurfaceDrawable(context, 14));
        }
        TextView themeButton = view.findViewById(R.id.BTSelectAppTheme);
        if (themeButton != null) {
            themeButton.setBackground(ThemeManager.createThemedSurfaceDrawable(context, 14));
        }
    }

    private void applyLiquidGlassSettingsSurfacesRecursively(View view, Context context) {
        if (view == null) return;
        if ("settingsSurface".equals(view.getTag())) {
            view.setBackground(ThemeManager.createThemedSurfaceDrawable(context, 14));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyLiquidGlassSettingsSurfacesRecursively(group.getChildAt(i), context);
            }
        }
    }

    private void applyDynamicStylesRecursively(View view) {
        TextView box64Label = view.findViewById(R.id.TVBox64);
        applyFieldSetLabelStyle(box64Label, isDarkMode);

        TextView fexcoreLabel = view.findViewById(R.id.TVFEXCore);
        applyFieldSetLabelStyle(fexcoreLabel, isDarkMode);

        TextView soundLabel = view.findViewById(R.id.TVSound);
        applyFieldSetLabelStyle(soundLabel, isDarkMode);

        TextView themeLabel = view.findViewById(R.id.TVTheme);
        applyFieldSetLabelStyle(themeLabel, isDarkMode);

        TextView shortcutSettingsLabel = view.findViewById(R.id.TVShortcutSettings);
        applyFieldSetLabelStyle(shortcutSettingsLabel, isDarkMode);

        TextView tvCustomApiKey = view.findViewById(R.id.TVCustomApiKey);
        applyFieldSetLabelStyle(tvCustomApiKey, isDarkMode);

        // Inputs tab labels
        TextView xServerLabel = view.findViewById(R.id.TVXServer);
        applyFieldSetLabelStyle(xServerLabel, isDarkMode);

        // Advanced tab labels
        TextView logsLabel = view.findViewById(R.id.TVLogs);
        applyFieldSetLabelStyle(logsLabel, isDarkMode);

        TextView experimentalLabel = view.findViewById(R.id.TVExperimental);
        applyFieldSetLabelStyle(experimentalLabel, isDarkMode);

        TextView systemLabel = view.findViewById(R.id.TVSystem);
        applyFieldSetLabelStyle(systemLabel, isDarkMode);

        TextView ImageFsLabel = view.findViewById(R.id.TVImageFs);
        applyFieldSetLabelStyle(ImageFsLabel, isDarkMode);

    }

    private void applyFieldSetLabelStyle(TextView textView, boolean isDarkMode) {
        ThemeManager.applyFieldSetLabelStyle(textView, isDarkMode);
    }

    private void initCustomApiKeySettings(View view) {
        cbEnableCustomApiKey = view.findViewById(R.id.CBEnableCustomApiKey);
        etCustomApiKey = view.findViewById(R.id.ETCustomApiKey);

        // Load saved preferences
        boolean isCustomApiKeyEnabled = preferences.getBoolean("enable_custom_api_key", false);
        String customApiKey = preferences.getString("custom_api_key", "");

        cbEnableCustomApiKey.setChecked(isCustomApiKeyEnabled);
        etCustomApiKey.setText(customApiKey);

        // Show/hide the EditText based on checkbox state
        etCustomApiKey.setVisibility(isCustomApiKeyEnabled ? View.VISIBLE : View.GONE);

        cbEnableCustomApiKey.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etCustomApiKey.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Help button listener to open API documentation
        view.findViewById(R.id.BTHelpApiKey).setOnClickListener(v -> {
            String url = "https://www.steamgriddb.com/profile/preferences/api";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
    }

    private void saveCustomApiKeySettings(SharedPreferences.Editor editor) {
        // Save custom API key preferences
        boolean isCustomApiKeyEnabled = cbEnableCustomApiKey.isChecked();
        editor.putBoolean("enable_custom_api_key", isCustomApiKeyEnabled);

        if (isCustomApiKeyEnabled) {
            String customApiKey = etCustomApiKey.getText().toString().trim();
            editor.putString("custom_api_key", customApiKey);
        } else {
            editor.remove("custom_api_key");
        }
    }

    private void loadBox64PresetSpinners(View view, final Spinner sBox64Preset) {
        final ArrayMap<String, Spinner> spinners = new ArrayMap<String, Spinner>() {{
            put("box64", sBox64Preset);
        }};
        final Context context = getContext();

        Callback<String> updateSpinner = (prefix) -> {
            Box64PresetManager.loadSpinner(prefix, spinners.get(prefix), preferences.getString(prefix+"_preset", Box64Preset.COMPATIBILITY));
        };

        Callback<String> onAddPreset = (prefix) -> {
            Box64EditPresetDialog dialog = new Box64EditPresetDialog(context, prefix, null);
            dialog.setOnConfirmCallback(() -> updateSpinner.call(prefix));
            dialog.show();
        };

        Callback<String> onEditPreset = (prefix) -> {
            Box64EditPresetDialog dialog = new Box64EditPresetDialog(context, prefix, Box64PresetManager.getSpinnerSelectedId(spinners.get(prefix)));
            dialog.setOnConfirmCallback(() -> updateSpinner.call(prefix));
            dialog.show();
        };

        Callback<String> onDuplicatePreset = (prefix) -> ContentDialog.confirm(context, R.string.do_you_want_to_duplicate_this_preset, () -> {
            Spinner spinner = spinners.get(prefix);
            Box64PresetManager.duplicatePreset(prefix, context, Box64PresetManager.getSpinnerSelectedId(spinner));
            updateSpinner.call(prefix);
            spinner.setSelection(spinner.getCount()-1);
        });

        Callback<String> onRemovePreset = (prefix) -> {
            final String presetId = Box64PresetManager.getSpinnerSelectedId(spinners.get(prefix));
            if (!presetId.startsWith(Box64Preset.CUSTOM)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_preset);
                return;
            }
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset, () -> {
                Box64PresetManager.removePreset(prefix, context, presetId);
                updateSpinner.call(prefix);
            });
        };

        Callback<String> onExportPreset = (String prefix) -> {
            final String presetId = Box64PresetManager.getSpinnerSelectedId(spinners.get(prefix));
            if (!presetId.startsWith(Box64Preset.CUSTOM)) {
                AppUtils.showToast(context, "Cannot export this preset");
                return;
            }
            getActivity().runOnUiThread(() ->  {
                Box64PresetManager.exportPreset(prefix, context, presetId);
            });
        };

        Callback<String> onImportPreset = (String prefix) -> {
          openFile(REQUEST_CODE_IMPORT_BOX64_PRESET);
        };

        updateSpinner.call("box64");

        view.findViewById(R.id.BTAddBox64Preset).setOnClickListener((v) -> onAddPreset.call("box64"));
        view.findViewById(R.id.BTEditBox64Preset).setOnClickListener((v) -> onEditPreset.call("box64"));
        view.findViewById(R.id.BTDuplicateBox64Preset).setOnClickListener((v) -> onDuplicatePreset.call("box64"));
        view.findViewById(R.id.BTRemoveBox64Preset).setOnClickListener((v) -> onRemovePreset.call("box64"));
        view.findViewById(R.id.BTExportBox64Preset).setOnClickListener((v) -> onExportPreset.call("box64"));
        view.findViewById(R.id.BTImportBox64Preset).setOnClickListener((v) -> onImportPreset.call("box64"));
    }

    private void loadFEXCorePresetSpinners(View view, final Spinner sFEXCorePreset) {
        final Context context = getContext();

        Callback<String> updateSpinner = (String prefix) -> {
            FEXCorePresetManager.loadSpinner(sFEXCorePreset, preferences.getString(prefix + "_preset", FEXCorePreset.COMPATIBILITY));
        };

        Callback<String> onAddPreset = (String prefix) -> {
            FEXCoreEditPresetDialog dialog = new FEXCoreEditPresetDialog(context, null);
            dialog.setOnConfirmCallback(() -> updateSpinner.call(prefix));
            dialog.show();
        };

        Callback<String> onEditPreset = (prefix) -> {
            FEXCoreEditPresetDialog dialog = new FEXCoreEditPresetDialog(context, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
            dialog.setOnConfirmCallback(() -> updateSpinner.call(prefix));
            dialog.show();
        };

        Callback<String> onDuplicatePreset = (String prefix) -> ContentDialog.confirm(context, R.string.do_you_want_to_duplicate_this_preset, () -> {
            FEXCorePresetManager.duplicatePreset(context, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
            updateSpinner.call(prefix);
            sFEXCorePreset.setSelection(sFEXCorePreset.getCount()-1);
        });

        Callback<String> onRemovePreset = (prefix) -> {
            final String presetId = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
            if (!presetId.startsWith(FEXCorePreset.CUSTOM)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_preset);
                return;
            }
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset, () -> {
                FEXCorePresetManager.removePreset(context, presetId);
                updateSpinner.call(prefix);
            });
        };

        Callback<String> onExportPreset = (String prefix) -> {
            final String presetId = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
            if (!presetId.startsWith(FEXCorePreset.CUSTOM)) {
                AppUtils.showToast(context, "Cannot export this preset");
                return;
            }
            getActivity().runOnUiThread(() ->  {
                FEXCorePresetManager.exportPreset(context, presetId);
            });
        };

        Callback<String> onImportPreset = (String prefix) -> {
            openFile(REQUEST_CODE_IMPORT_FEXCORE_PRESET);
        };

        updateSpinner.call("fexcore");

        view.findViewById(R.id.BTAddFEXCorePreset).setOnClickListener((v) -> onAddPreset.call("fexcore"));
        view.findViewById(R.id.BTEditFEXCorePreset).setOnClickListener((v) -> onEditPreset.call("fexcore"));
        view.findViewById(R.id.BTDuplicateFEXCorePreset).setOnClickListener((v) -> onDuplicatePreset.call("fexcore"));
        view.findViewById(R.id.BTRemoveFEXCorePreset).setOnClickListener((v) -> onRemovePreset.call("fexcore"));
        view.findViewById(R.id.BTExportFEXCorePreset).setOnClickListener((v) -> onExportPreset.call("fexcore"));
        view.findViewById(R.id.BTImportFEXCorePreset).setOnClickListener((v) -> onImportPreset.call("fexcore"));
    }


    private void openFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        // Start activity for result based on the provided request code
        getActivity().startActivityFromFragment(this, intent, requestCode);
    }


    public static void resetEmulatorsVersion(AppCompatActivity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("current_box64_version");
        editor.remove("current_wowbox64_version");
        editor.remove("current_fexcore_version");
        editor.apply();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();

            if (uri != null) {

                SharedPreferences.Editor editor = preferences.edit();

                switch (requestCode) {

                    case REQUEST_CODE_WINLATOR_PATH:
                        // Save the selected URI as a string in SharedPreferences
                        editor.putString("winlator_path_uri", uri.toString());
                        editor.apply();

                        // Take persistable URI permission
                        try {
                            // Take persistable URI permission with explicit flags
                            requireContext().getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                            AppUtils.showToast(getContext(), "Unable to take persistable permissions: " + e.getMessage());
                        }

                        // Convert the URI to an absolute path and display it
                        String fullPath = FileUtils.getFilePathFromUri(getContext(), uri);

                        // Update the TextView with the absolute path or URI string if conversion fails
                        TextView tvWinlatorPath = getView().findViewById(R.id.TVWinlatorPath);
                        tvWinlatorPath.setText(fullPath != null ? fullPath : uri.toString());
                        break;

                    case REQUEST_CODE_SHORTCUT_EXPORT_PATH:
                        editor.putString("shortcuts_export_path_uri", uri.toString());
                        editor.apply();

                        // Take persistable URI permission
                        try {
                            // Take persistable URI permission with explicit flags
                            requireContext().getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                            AppUtils.showToast(getContext(), "Unable to take persistable permissions: " + e.getMessage());
                        }

                        // Convert the URI to an absolute path and display it
                        String path = FileUtils.getFilePathFromUri(getContext(), uri);

                        // Update the TextView with the absolute path or URI string if conversion fails
                        TextView tvShortcutExportPath = getView().findViewById(R.id.TVShortcutExportPath);
                        tvShortcutExportPath.setText(path != null ? path : uri.toString());
                        break;

                    // Case for installing a SoundFont
                    case REQUEST_CODE_INSTALL_SOUNDFONT:
                        if (installSoundFontCallback != null) {
                            try {
                                installSoundFontCallback.call(uri);
                            } catch (Exception e) {
                                AppUtils.showToast(getContext(), R.string.unable_to_install_soundfont);
                            } finally {
                                installSoundFontCallback = null;
                            }
                        }
                        break;

                    case REQUEST_CODE_IMPORT_LOSSLESS_DLL:
                        importLosslessDll(uri);
                        break;

                    case REQUEST_CODE_IMPORT_BOX64_PRESET:
                        try {
                            Spinner sBox64Preset = getView().findViewById(R.id.SBox64Preset);
                            InputStream is = getActivity().getContentResolver().openInputStream(uri);
                            Box64PresetManager.importPreset("box64", getContext(), is);
                            Box64PresetManager.loadSpinner("box64", sBox64Preset, preferences.getString("box64_preset", Box64Preset.COMPATIBILITY));
                        } catch (FileNotFoundException e) {
                        }
                        break;
                    case REQUEST_CODE_IMPORT_FEXCORE_PRESET:
                        try {
                            Spinner sFEXCorePreset = getView().findViewById(R.id.SFEXCorePreset);
                            InputStream is = getActivity().getContentResolver().openInputStream(uri);
                            FEXCorePresetManager.importPreset( getContext(), is);
                            FEXCorePresetManager.loadSpinner(sFEXCorePreset, preferences.getString("fexcore_preset", FEXCorePreset.INTERMEDIATE));
                        } catch (FileNotFoundException e) {
                        }
                        break;
                        // Add future cases here for other request codes...
                    default:
                        break;
                }
            }
        }
    }

    private void importLosslessDll(Uri uri) {
        Activity activity = getActivity();
        Context context = getContext();
        if (activity == null || context == null) return;

        PreloaderDialog dialog = new PreloaderDialog(activity);
        dialog.showOnUiThread(R.string.installing_content);
        new Thread(() -> {
            File dll = LsfgNative.dllFile(context);
            File parent = dll.getParentFile();
            File temp = new File(parent, "Lossless.dll.import");
            int status;
            try {
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Unable to create LSFG storage directory");
                }
                InputStream source = context.getContentResolver().openInputStream(uri);
                if (source == null) throw new IOException("Unable to open the selected file");
                try (InputStream input = source; FileOutputStream output = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }

                status = LsfgNative.validateDll(temp);
                if (status == LsfgNative.STATUS_OK) {
                    Files.move(temp.toPath(), dll.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    status = LsfgNative.ensureCache(context, false);
                }
            } catch (IOException e) {
                android.util.Log.e("SettingsFragment", "Failed to import Lossless.dll", e);
                status = LsfgNative.STATUS_CACHE_UNUSABLE;
            } finally {
                if (temp.exists() && !temp.delete()) {
                    android.util.Log.w("SettingsFragment", "Unable to remove temporary Lossless.dll import");
                }
            }

            int result = status;
            activity.runOnUiThread(() -> {
                dialog.closeOnUiThread();
                View currentView = getView();
                if (currentView != null && LsfgNative.isDllAvailable(context)) {
                    TextView statusView = currentView.findViewById(R.id.TVLosslessDllStatus);
                    statusView.setText(R.string.lossless_dll_imported);
                }
                if (result == LsfgNative.STATUS_OK) {
                    AppUtils.showToast(context, "Lossless.dll validated and LSFG cache generated");
                } else {
                    AppUtils.showToast(context, "Lossless.dll import/cache failed: " + LsfgNative.explain(result));
                }
            });
        }, "lsfg-dll-import").start();
    }

    private void moveFiles(File sourceDir, File targetDir) throws IOException {
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                File targetFile = new File(targetDir, file.getName());
                if (file.isDirectory()) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs();
                    }
                    moveFiles(file, targetFile); // Recursively move directory contents
                } else {
                    if (!file.renameTo(targetFile)) {
                        throw new IOException("Failed to move file: " + file.getAbsolutePath());
                    }
                }
            }
        }
        // Clear the temporary directory after moving
        FileUtils.clear(sourceDir);
    }
}
