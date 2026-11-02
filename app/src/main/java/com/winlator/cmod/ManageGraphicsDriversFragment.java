package com.winlator.cmod;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.StreamUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.contents.WrapperCatalogManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ManageGraphicsDriversFragment extends Fragment {
    private static final int IMPORT_WRAPPER_REQUEST_CODE =
            MainActivity.OPEN_FILE_REQUEST_CODE + 1;
    private String selectedDriverFile;
    private LinearLayout driverListView;
    private LinearLayout wrapperCatalogList;
    private TextView wrapperCatalogStatus;
    private Button wrapperCatalogToggle;
    private Button importWrapperButton;
    private boolean wrapperCatalogExpanded;
    private boolean wrapperCatalogStatusShown;
    private final List<DriverInfo> driverList = new ArrayList<>();
    private final List<WrapperCatalogManager.Entry> wrapperEntries = new ArrayList<>();
    private final Set<String> downloadingWrapperIds = new HashSet<>();

    private static class DriverInfo {
        final String fileName;
        String version = "Unknown";
        String notes = "";
        boolean hasInfo = false;
        boolean isUpdated = false;

        public DriverInfo(String fileName) {
            this.fileName = fileName;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        MenuItem catalogItem = menu.add(Menu.NONE, 2, Menu.NONE, R.string.refresh_wrapper_catalog);
        catalogItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        MenuItem resetAllItem = menu.add(Menu.NONE, 1, Menu.NONE, R.string.reset_all);
        resetAllItem.setIcon(R.drawable.ic_reset_all);
        resetAllItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 2) {
            refreshWrapperCatalog();
            return true;
        }
        if (item.getItemId() == 1) {
            ContentDialog.confirm(getContext(), R.string.reset_all_drivers_confirm, () -> {
                File internalDriversDir = new File(getContext().getFilesDir(), "graphics_driver");
                if (internalDriversDir.exists()) {
                    FileUtils.delete(internalDriversDir);
                    AppUtils.showToast(getContext(), R.string.driver_reset_success);
                    loadDrivers();
                }
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.manage_graphics_drivers);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.manage_graphics_drivers_fragment, container, false);

        TextView tvDescription = view.findViewById(R.id.TVDescription);
        if (tvDescription != null) {
            tvDescription.setText(Html.fromHtml(
                    getString(R.string.manage_graphics_drivers_description), Html.FROM_HTML_MODE_LEGACY));
        }

        driverListView = view.findViewById(R.id.DriverList);
        wrapperCatalogList = view.findViewById(R.id.WrapperCatalogList);
        wrapperCatalogStatus = view.findViewById(R.id.TVWrapperCatalogStatus);
        wrapperCatalogToggle = view.findViewById(R.id.BTWrapperCatalogToggle);
        importWrapperButton = view.findViewById(R.id.BTImportWrapper);
        wrapperCatalogToggle.setText(R.string.wrapper_catalog_title_collapsed);
        wrapperCatalogToggle.setOnClickListener(v -> {
            wrapperCatalogExpanded = !wrapperCatalogExpanded;
            updateWrapperCatalogVisibility();
        });
        importWrapperButton.setOnClickListener(v -> openWrapperPicker());

        ThemeManager.applyThemeToView(view, getContext());
        updateWrapperCatalogVisibility();

        loadDrivers();
        refreshWrapperCatalog();

        return view;
    }

    private void loadDrivers() {
        final Activity activity = getActivity();
        if (activity == null) return;

        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        preloaderDialog.showOnUiThread(R.string.loading);

        new Thread(() -> {
            driverList.clear();
            for (String wrapper : com.winlator.cmod.container.Container.WRAPPER_DRIVERS)
                driverList.add(new DriverInfo(wrapper + ".tzst"));
            driverList.add(new DriverInfo("leegao_bcn.tzst"));
            driverList.add(new DriverInfo("leegao_bcn_legacy.tzst"));
            driverList.add(new DriverInfo("gladio-1.1.tzst"));
            driverList.add(new DriverInfo("extra_libs.tzst"));

            android.content.Context context = getContext();
            if (context != null) {
                Set<String> knownDrivers = new HashSet<>();
                for (DriverInfo info : driverList) {
                    knownDrivers.add(info.fileName.toLowerCase(java.util.Locale.ROOT));
                }
                for (WrapperCatalogManager.Entry wrapper
                        : WrapperCatalogManager.getInstalledWrappers(context)) {
                    File importedFile = new File(context.getFilesDir(),
                            "graphics_driver/" + wrapper.id + ".tzst");
                    if (importedFile.isFile()
                            && knownDrivers.add(importedFile.getName().toLowerCase(
                            java.util.Locale.ROOT))) {
                        driverList.add(new DriverInfo(importedFile.getName()));
                    }
                }
                for (DriverInfo info : driverList) {
                    fetchDriverInfo(context, info);
                }
            }

            activity.runOnUiThread(() -> {
                preloaderDialog.closeOnUiThread();
                if (isAdded()) {
                    driverListView.removeAllViews();
                    DriverAdapter adapter = new DriverAdapter(driverList);
                    for (int i = 0; i < adapter.getItemCount(); i++) {
                        DriverAdapter.ViewHolder holder = adapter.onCreateViewHolder(driverListView, 0);
                        adapter.onBindViewHolder(holder, i);
                        driverListView.addView(holder.itemView);
                    }
                }
            });
        }).start();
    }

    private void fetchDriverInfo(android.content.Context context, DriverInfo info) {
        File internalFile = new File(context.getFilesDir(), "graphics_driver/" + info.fileName);
        info.isUpdated = internalFile.exists();
        
        String content;
        if (info.isUpdated) {
            content = TarCompressorUtils.readTextFile(TarCompressorUtils.Type.ZSTD, internalFile, "version.txt");
        } else {
            content = TarCompressorUtils.readTextFile(TarCompressorUtils.Type.ZSTD, context, "graphics_driver/" + info.fileName, "version.txt");
        }

        if (content != null) {
            info.hasInfo = true;
            String[] lines = content.split("\n");
            boolean parsingNotes = false;
            StringBuilder notesBuilder = new StringBuilder();

            for (String line : lines) {
                String trimmedLine = line.trim();
                String lowerLine = trimmedLine.toLowerCase();
                if (lowerLine.startsWith("version:")) {
                    info.version = trimmedLine.substring(8).trim();
                } else if (lowerLine.startsWith("notes:")) {
                    parsingNotes = true;
                    notesBuilder.append(trimmedLine.substring(6).trim());
                } else if (parsingNotes) {
                    if (notesBuilder.length() > 0) notesBuilder.append("\n");
                    notesBuilder.append(trimmedLine);
                }
            }
            info.notes = notesBuilder.toString().trim();
        } else {
            // The Vulkan wrappers restored from the 1.1 release carry no version.txt.
            switch (info.fileName) {
                case "wrapper-1.1.tzst":
                    info.version = "1.1 release";
                    info.notes = "The main Vulkan wrapper shipped with Winlator Mali 1.1.";
                    break;
                case "wrapper-v2.tzst":
                    info.version = "v2 (1.1 release)";
                    info.notes = "The v2 build of the Vulkan wrapper from Winlator Mali 1.1.";
                    break;
                case "wrapper-leegao.tzst":
                    info.version = "leegao (1.1 release)";
                    info.notes = "leegao's Vulkan wrapper, as shipped with Winlator Mali 1.1.";
                    break;
                case "wrapper-gamenative.tzst":
                    info.version = "GameNative (1.1 release)";
                    info.notes = "GameNative's lightweight Vulkan wrapper, as shipped with Winlator Mali 1.1.";
                    break;
            }
        }
    }

    private class DriverAdapter extends RecyclerView.Adapter<DriverAdapter.ViewHolder> {
        private final List<DriverInfo> data;

        public DriverAdapter(List<DriverInfo> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.manage_graphics_drivers_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DriverInfo info = data.get(position);
            holder.tvName.setText("wrapper.tzst".equals(info.fileName)
                    ? "Apex-Wrapper" : info.fileName.replace(".tzst", ""));
            
            android.content.Context ctx = holder.itemView.getContext();
            ThemeManager.applyThemeToView(holder.itemView, ctx);

            String statusStr;
            if (info.isUpdated) {
                int accentColor = ThemeManager.getAccentColor(ctx);
                statusStr = "<font color='" + String.format("#%06X", (0xFFFFFF & accentColor)) + "'><b>" + getString(R.string.updated) + "</b></font>";
            } else {
                statusStr = getString(R.string.bundled);
            }
            holder.tvVersion.setText(Html.fromHtml(getString(R.string.version) + ": " + info.version + " (" + statusStr + ")", Html.FROM_HTML_MODE_LEGACY));

            holder.btInfo.setOnClickListener(v -> {
                String details = "<b>File:</b> " + info.fileName + "<br/>" +
                                 "<b>Version:</b> " + info.version + "<br/><br/>" +
                                 "<b>Notes:</b><br/>" + (info.notes.isEmpty() ? getString(R.string.no_notes_available) : info.notes.replace("\n", "<br/>"));
                
                ContentDialog dialog = new ContentDialog(getContext());
                dialog.setTitle(R.string.driver_details);
                dialog.setIcon(R.drawable.ic_driver_info);
                dialog.setMessage(details);
                dialog.getContentView().setMinimumWidth(AppUtils.getPreferredDialogWidth(getContext()));
                dialog.findViewById(R.id.BTCancel).setVisibility(View.GONE);
                dialog.show();
            });

            holder.btUpdate.setOnClickListener(v -> {
                String message = getString(R.string.update_driver_initial_confirm, info.fileName);
                ContentDialog.confirm(getContext(), message, () -> {
                    selectedDriverFile = info.fileName;
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    getActivity().startActivityFromFragment(ManageGraphicsDriversFragment.this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);
                });
            });

            holder.btRemove.setVisibility(info.isUpdated ? View.VISIBLE : View.GONE);
            holder.btRemove.setOnClickListener(v -> {
                ContentDialog.confirm(getContext(), R.string.reset_driver_confirm, () -> {
                    File file = new File(getContext().getFilesDir(), "graphics_driver/" + info.fileName);
                    if (file.exists() && file.delete()) {
                        AppUtils.showToast(getContext(), R.string.driver_reset_success);
                        loadDrivers();
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName, tvVersion;
            final ImageButton btInfo, btUpdate, btRemove;
            final ImageView ivIcon;

            ViewHolder(View view) {
                super(view);
                tvName = view.findViewById(R.id.TVName);
                tvVersion = view.findViewById(R.id.TVVersion);
                btInfo = view.findViewById(R.id.BTInfo);
                btUpdate = view.findViewById(R.id.BTUpdate);
                btRemove = view.findViewById(R.id.BTRemove);
                ivIcon = view.findViewById(R.id.IVIcon);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMPORT_WRAPPER_REQUEST_CODE && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String fileName = FileUtils.getUriFileName(getContext(), uri);
            if (fileName == null) {
                showWrapperImportFailure("The selected filename could not be determined.");
            } else {
                importWrapper(uri, fileName);
            }
            return;
        }
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            String fileName = FileUtils.getUriFileName(getContext(), uri);
            if (fileName != null && fileName.equals(selectedDriverFile)) {
                String message = getString(R.string.update_driver_confirm, selectedDriverFile);
                ContentDialog.confirm(getContext(), message, () -> updateDriver(uri));
            } else {
                String errorMsg = fileName != null ? getString(R.string.driver_file_mismatch, fileName, selectedDriverFile) : getString(R.string.unable_to_determine_filename);
                AppUtils.showToast(getContext(), errorMsg);
            }
        }
    }

    private void updateWrapperCatalogVisibility() {
        if (wrapperCatalogToggle == null) return;
        wrapperCatalogToggle.setText(wrapperCatalogExpanded
                ? R.string.wrapper_catalog_title_expanded
                : R.string.wrapper_catalog_title_collapsed);
        int visibility = wrapperCatalogExpanded ? View.VISIBLE : View.GONE;
        wrapperCatalogStatus.setVisibility(wrapperCatalogExpanded && wrapperCatalogStatusShown
                ? View.VISIBLE : View.GONE);
        wrapperCatalogList.setVisibility(visibility);
    }

    private void setWrapperCatalogStatusShown(boolean shown) {
        wrapperCatalogStatusShown = shown;
        wrapperCatalogStatus.setVisibility(shown && wrapperCatalogExpanded
                ? View.VISIBLE : View.GONE);
    }

    private void openWrapperPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_WRAPPER_REQUEST_CODE);
    }

    private void importWrapper(Uri uri, String fileName) {
        Activity activity = getActivity();
        if (activity == null) return;
        final PreloaderDialog progress = new PreloaderDialog(activity);
        progress.showOnUiThread(R.string.updating_system_files);
        new Thread(() -> {
            Exception failure = null;
            try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                WrapperCatalogManager.importWrapper(activity, input, fileName);
            } catch (Exception e) {
                failure = e;
                android.util.Log.e("ManageGraphicsDrivers",
                        "Could not import graphics wrapper " + fileName, e);
            }
            Exception error = failure;
            activity.runOnUiThread(() -> {
                progress.closeOnUiThread();
                if (!isAdded()) return;
                if (error == null) {
                    wrapperCatalogStatus.setText(R.string.wrapper_import_success);
                    setWrapperCatalogStatusShown(true);
                    AppUtils.showToast(activity, R.string.wrapper_import_success);
                    loadDrivers();
                } else {
                    showWrapperImportFailure(error.getMessage() == null
                            ? error.getClass().getSimpleName() : error.getMessage());
                }
            });
        }, "ImportGraphicsWrapper").start();
    }

    private void showWrapperImportFailure(String reason) {
        Activity activity = getActivity();
        if (activity == null) return;
        ContentDialog.alert(activity, getString(R.string.wrapper_import_failed, reason), null);
    }

    private void updateDriver(Uri uri) {
        final PreloaderDialog preloaderDialog = new PreloaderDialog(getActivity());
        preloaderDialog.showOnUiThread(R.string.updating_system_files);

        new Thread(() -> {
            try {
                File internalDriversDir = new File(getContext().getFilesDir(), "graphics_driver");
                if (!internalDriversDir.exists()) internalDriversDir.mkdirs();

                File finalFile = new File(internalDriversDir, selectedDriverFile);
                try (InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                     FileOutputStream outputStream = new FileOutputStream(finalFile)) {
                    byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                }

                getActivity().runOnUiThread(() -> {
                    preloaderDialog.closeOnUiThread();
                    AppUtils.showToast(getContext(), R.string.driver_updated_success);
                    loadDrivers();
                });
            } catch (Exception e) {
                getActivity().runOnUiThread(() -> {
                    preloaderDialog.closeOnUiThread();
                    AppUtils.showToast(getContext(), R.string.driver_update_failed);
                });
            }
        }).start();
    }

    private void refreshWrapperCatalog() {
        Activity activity = getActivity();
        if (activity == null) return;
        List<WrapperCatalogManager.Entry> cached = WrapperCatalogManager.getCachedCatalog(activity);
        if (!cached.isEmpty()) {
            showWrapperCatalogEntries(cached);
            wrapperCatalogStatus.setText(R.string.wrapper_catalog_refreshing);
            setWrapperCatalogStatusShown(true);
        } else {
            wrapperCatalogStatus.setText(R.string.wrapper_catalog_loading);
            setWrapperCatalogStatusShown(true);
        }
        new Thread(() -> {
            List<WrapperCatalogManager.Entry> entries;
            String failure = null;
            try {
                entries = WrapperCatalogManager.fetchCatalog(activity);
            } catch (Exception e) {
                android.util.Log.e("ManageGraphicsDrivers", "Unable to fetch Bannerlator wrapper catalog", e);
                entries = WrapperCatalogManager.getCachedCatalog(activity);
                failure = e.getMessage();
            }
            List<WrapperCatalogManager.Entry> result = entries;
            String error = failure;
            activity.runOnUiThread(() -> {
                if (!isAdded()) return;
                showWrapperCatalogEntries(result);
                if (result.isEmpty()) {
                    wrapperCatalogStatus.setText(getString(R.string.wrapper_catalog_unavailable,
                            error == null ? "No cached catalog is available." : error));
                    setWrapperCatalogStatusShown(true);
                    return;
                }
                if (error != null) {
                    wrapperCatalogStatus.setText(getString(R.string.wrapper_catalog_cached,
                            error));
                }
                setWrapperCatalogStatusShown(error != null);
            });
        }).start();
    }

    private void showWrapperCatalogEntries(List<WrapperCatalogManager.Entry> entries) {
        wrapperEntries.clear();
        wrapperEntries.addAll(entries);
        renderWrapperCatalogEntries();
    }

    private void renderWrapperCatalogEntries() {
        wrapperCatalogList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(wrapperCatalogList.getContext());
        for (WrapperCatalogManager.Entry entry : wrapperEntries) {
            View item = inflater.inflate(R.layout.wrapper_catalog_list_item, wrapperCatalogList, false);
            TextView name = item.findViewById(R.id.TVWrapperName);
            TextView descriptionView = item.findViewById(R.id.TVWrapperDescription);
            Button download = item.findViewById(R.id.BTWrapperDownload);
            boolean installed = WrapperCatalogManager.getInstalledWrapperFile(
                    wrapperCatalogList.getContext(), entry.id) != null;
            name.setText(entry.name);
            String description = entry.description;
            if (!entry.author.isEmpty()) {
                description += (description.isEmpty() ? "" : "\n")
                        + getString(R.string.wrapper_catalog_author, entry.author);
            }
            descriptionView.setText(description);
            download.setText(downloadingWrapperIds.contains(entry.id)
                    ? R.string.downloading_wrapper
                    : installed ? R.string.update_wrapper : R.string.download_wrapper);
            download.setEnabled(!downloadingWrapperIds.contains(entry.id));
            download.setOnClickListener(v -> {
                Activity activity = getActivity();
                if (activity != null) downloadWrapper(activity, entry);
            });
            ThemeManager.applyThemeToView(item, wrapperCatalogList.getContext());
            wrapperCatalogList.addView(item);
        }
    }

    private void downloadWrapper(Activity activity, WrapperCatalogManager.Entry entry) {
        if (!downloadingWrapperIds.add(entry.id)) return;
        renderWrapperCatalogEntries();
        PreloaderDialog progress = new PreloaderDialog(activity);
        progress.showOnUiThread(R.string.downloading_wrapper);
        new Thread(() -> {
            Exception failure = null;
            try {
                WrapperCatalogManager.downloadWrapper(activity, entry);
            } catch (Exception e) {
                failure = e;
                android.util.Log.e("ManageGraphicsDrivers", "Wrapper download failed: " + entry.id, e);
            }
            Exception error = failure;
            activity.runOnUiThread(() -> {
                progress.closeOnUiThread();
                if (!isAdded()) return;
                downloadingWrapperIds.remove(entry.id);
                renderWrapperCatalogEntries();
                if (error == null) {
                    AppUtils.showToast(activity, getString(R.string.wrapper_download_success, entry.name));
                    wrapperCatalogStatus.setText(R.string.wrapper_catalog_downloaded_selectable);
                    setWrapperCatalogStatusShown(true);
                } else {
                    ContentDialog.alert(activity, getString(R.string.wrapper_download_failed,
                            error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()), null);
                }
            });
        }).start();
    }

}
