package com.winlator.cmod;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import com.winlator.cmod.R;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ArrayAdapter;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.Downloader;
import com.winlator.cmod.contents.PanvkReleaseManager;
import com.winlator.cmod.contents.PanvkReleaseManager.ReleaseAsset;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdrenotoolsFragment extends Fragment {
    
    private AdrenotoolsManager adrenotoolsManager;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private Button btDownloadPanVK;
    private ProgressDialog panvkProgressDialog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService panvkExecutor = Executors.newSingleThreadExecutor();

    private void updateEmptyState() {
        if (tvEmpty == null || recyclerView.getAdapter() == null) return;
        tvEmpty.setVisibility(recyclerView.getAdapter().getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
    
    @Override 
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.adrenotoolsManager = new AdrenotoolsManager(getActivity());
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup layout = (ViewGroup)inflater.inflate(R.layout.adrenotools_fragment, container, false);
        recyclerView = layout.findViewById(R.id.RecyclerView);
        tvEmpty = layout.findViewById(R.id.TVEmpty);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(new DriversAdapter(adrenotoolsManager.enumarateInstalledDrivers()));
        updateEmptyState();
        View btInstallDriver = layout.findViewById(R.id.BTInstallDriver);
        btInstallDriver.setOnClickListener((v) -> {
            ContentDialog.confirm(getContext(), getString(R.string.install_drivers_message) + " " + getString(R.string.install_drivers_warning), () -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);               
            });
        });
        btDownloadPanVK = layout.findViewById(R.id.BTDownloadPanVK);
        btDownloadPanVK.setOnClickListener(v -> fetchPanvkReleases());
        return layout;
    }
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ThemeManager.applyThemeToView(view, getContext());
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.adrenotools_gpu_drivers);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String driver = adrenotoolsManager.installDriver(uri);
            if (!driver.isEmpty())
                ((DriversAdapter)recyclerView.getAdapter()).addItem(driver);
        }
     }       

    private void fetchPanvkReleases() {
        if (getContext() == null || btDownloadPanVK == null) return;
        btDownloadPanVK.setEnabled(false);
        ProgressDialog progress = new ProgressDialog(requireContext());
        progress.setMessage(getString(R.string.panvk_fetching_releases));
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        panvkProgressDialog = progress;
        panvkExecutor.execute(() -> {
            List<ReleaseAsset> releases = null;
            IOException failure = null;
            try {
                releases = PanvkReleaseManager.fetchReleases();
            } catch (IOException e) {
                failure = e;
            }
            List<ReleaseAsset> result = releases;
            IOException error = failure;
            mainHandler.post(() -> {
                if (progress.isShowing()) progress.dismiss();
                if (panvkProgressDialog == progress) panvkProgressDialog = null;
                if (btDownloadPanVK != null) btDownloadPanVK.setEnabled(true);
                if (!isAdded() || getContext() == null) return;
                if (error != null) {
                    ContentDialog.alert(requireContext(),
                            "Unable to refresh PanVK releases from GitHub: " + error.getMessage(), null);
                } else if (result == null || result.isEmpty()) {
                    ContentDialog.alert(requireContext(), R.string.panvk_no_releases, null);
                } else {
                    showPanvkReleases(result);
                }
            });
        });
    }

    private void showPanvkReleases(List<ReleaseAsset> releases) {
        android.content.Context context = requireContext();
        ArrayAdapter<ReleaseAsset> adapter = new ArrayAdapter<ReleaseAsset>(
                context, R.layout.panvk_release_list_item, R.id.TVReleaseName, releases) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                ReleaseAsset release = getItem(position);
                if (release != null) {
                    TextView title = row.findViewById(R.id.TVReleaseName);
                    TextView details = row.findViewById(R.id.TVReleaseDetails);
                    String name = release.releaseName.isEmpty() ? release.tag : release.releaseName;
                    title.setText(name);
                    String published = release.publishedAt.length() >= 10
                            ? release.publishedAt.substring(0, 10) : release.publishedAt;
                    String releaseType = release.prerelease ? "Pre-release" : "Release";
                    details.setText(String.format(Locale.ROOT,
                            "@%1$s/%2$s · %3$s · %4$s · %5$s\nZIP: %6$s · Tested: %7$s",
                            release.owner, release.repository, release.tag, releaseType, published,
                            Downloader.formatFileSize(release.size), release.deviceSupport));
                }
                return row;
            }
        };
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.panvk_release_list_title)
                .setAdapter(adapter, (dialogInterface, position) ->
                        confirmPanvkDownload(releases.get(position)))
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
    }

    private void confirmPanvkDownload(ReleaseAsset release) {
        String published = release.publishedAt.length() >= 10
                ? release.publishedAt.substring(0, 10) : release.publishedAt;
        String message = getString(R.string.panvk_download_confirm, release.owner,
                release.repository, release.fileName, published, release.deviceSupport);
        ContentDialog.confirm(requireContext(), message, () -> downloadAndImportPanvk(release));
    }

    private void downloadAndImportPanvk(ReleaseAsset release) {
        if (btDownloadPanVK != null) btDownloadPanVK.setEnabled(false);
        android.content.Context appContext = requireContext().getApplicationContext();
        ProgressDialog progress = new ProgressDialog(requireContext());
        progress.setMessage(getString(R.string.panvk_downloading));
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        panvkProgressDialog = progress;
        panvkExecutor.execute(() -> {
            String installedDriver = "";
            IOException failure = null;
            File downloaded = null;
            try {
                downloaded = PanvkReleaseManager.download(appContext, release,
                        (current, total) -> mainHandler.post(() -> {
                            if (!progress.isShowing()) return;
                            if (total > 0) {
                                progress.setIndeterminate(false);
                                progress.setMax(100);
                                progress.setProgress((int) (current * 100 / total));
                            }
                        }));
                try (FileInputStream input = new FileInputStream(downloaded)) {
                    installedDriver = adrenotoolsManager.installDriver(input);
                }
                if (installedDriver.isEmpty()) {
                    failure = new IOException("The downloaded ZIP is not a valid Adrenotools driver");
                }
            } catch (IOException e) {
                failure = e;
            } finally {
                if (downloaded != null && downloaded.exists() && !downloaded.delete()) {
                    android.util.Log.w("AdrenotoolsFragment", "Unable to remove downloaded PanVK ZIP");
                }
            }
            String driver = installedDriver;
            IOException error = failure;
            mainHandler.post(() -> {
                if (progress.isShowing()) progress.dismiss();
                if (panvkProgressDialog == progress) panvkProgressDialog = null;
                if (btDownloadPanVK != null) btDownloadPanVK.setEnabled(true);
                if (!isAdded() || getContext() == null) return;
                if (error != null) {
                    ContentDialog.alert(requireContext(),
                            getString(R.string.panvk_download_failed) + "\n\n" + error.getMessage(), null);
                } else {
                    if (recyclerView != null && recyclerView.getAdapter() instanceof DriversAdapter) {
                        ((DriversAdapter) recyclerView.getAdapter()).addItem(driver);
                    }
                    ContentDialog.alert(requireContext(),
                            getString(R.string.panvk_import_complete, adrenotoolsManager.getDriverName(driver)),
                            null);
                }
            });
        });
    }

    @Override
    public void onDestroy() {
        if (panvkProgressDialog != null && panvkProgressDialog.isShowing()) {
            panvkProgressDialog.dismiss();
        }
        panvkExecutor.shutdownNow();
        super.onDestroy();
    }
    
    private class DriversAdapter extends RecyclerView.Adapter<DriversAdapter.ViewHolder> {
        private ArrayList<String> driversList;

        public class ViewHolder extends RecyclerView.ViewHolder {
            private TextView tvName;
            private TextView tvVersion;
            private ImageButton btMenu;

            public ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.TVName);
                tvVersion = v.findViewById(R.id.TVVersion);
                btMenu = v.findViewById(R.id.BTMenu);
            }
        }
        
        public DriversAdapter(ArrayList<String> driversList) {
            this.driversList = driversList;
        }
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.adrenotools_list_item, viewGroup, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, final int position) {
            String driver = driversList.get(position);
            String name = adrenotoolsManager.getDriverName(driver);
            viewHolder.tvName.setText(name);
            viewHolder.tvVersion.setText(adrenotoolsManager.getDriverVersion(driver));
            viewHolder.btMenu.setOnClickListener((v) -> {
                // Looked up at click time: the position captured at bind time is stale after an earlier removal.
                int current = viewHolder.getBindingAdapterPosition();
                if (current == RecyclerView.NO_POSITION) return;
                ContentDialog.confirm(getContext(), "Remove the driver \"" + name + "\"?", () -> removeAtIndex(current));
            });
        }

        public void addItem(String item) {
            driversList.add(item);
            notifyItemInserted(getItemCount() - 1);
            updateEmptyState();
        }

        public void removeAtIndex(int index) {
            if (index < 0 || index >= driversList.size()) return;
            String deletedDriver = driversList.remove(index);
            adrenotoolsManager.removeDriver(deletedDriver);
            notifyItemRemoved(index);
            notifyItemRangeChanged(index, getItemCount());
            updateEmptyState();
        }
        
        @Override
        public int getItemCount() {
            return driversList.size();
        }
    }
}
