package com.winlator.cmod.nativeport;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.cmod.R;
import com.winlator.cmod.ThemeManager;

import java.util.List;

public class NativePortFragment extends Fragment {
    private static final int REQUEST_SOURCE_ARCHIVE = 8301;
    private TextView statusView;
    private TextView projectsView;
    private Button importButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.native_port_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ThemeManager.applyThemeToView(view, requireContext());
        if (getActivity() instanceof AppCompatActivity) {
            androidx.appcompat.app.ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (bar != null) bar.setTitle("Native Port Workspace");
        }

        statusView = view.findViewById(R.id.TVNativePortStatus);
        projectsView = view.findViewById(R.id.TVNativePortProjects);
        importButton = view.findViewById(R.id.BTNativePortImport);
        importButton.setOnClickListener(v -> chooseSourceArchive());
        view.findViewById(R.id.BTNativePortRefresh).setOnClickListener(v -> refreshProjects());
        view.findViewById(R.id.BTNativePortWorkflow).setOnClickListener(v -> openBuildWorkflow());
        view.findViewById(R.id.BTNativePortGuide).setOnClickListener(v -> showNativePortGuide());
        refreshProjects();
    }

    private void chooseSourceArchive() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/zip", "application/x-zip-compressed", "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_SOURCE_ARCHIVE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SOURCE_ARCHIVE || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) return;

        Uri archiveUri = data.getData();
        Context appContext = requireContext().getApplicationContext();
        Activity activity = getActivity();
        importButton.setEnabled(false);
        statusView.setText("Importing and inspecting source archive…");

        new Thread(() -> {
            java.io.File imported = null;
            String error = null;
            try {
                imported = NativePortManager.importArchive(appContext, archiveUri);
            } catch (Exception exception) {
                error = exception.getMessage() == null ? "Unknown import error" : exception.getMessage();
            }
            java.io.File finalImported = imported;
            String finalError = error;
            if (activity != null) activity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                importButton.setEnabled(true);
                statusView.setText(finalError == null
                        ? "Imported: " + finalImported.getName()
                        : "Import failed: " + finalError);
                refreshProjects();
            });
        }, "native-port-import").start();
    }

    private void refreshProjects() {
        if (projectsView == null || statusView == null) return;
        statusView.setText("Inspecting imported projects…");
        Context appContext = requireContext().getApplicationContext();
        Activity activity = getActivity();
        new Thread(() -> {
            List<NativePortManager.Project> projects = NativePortManager.listProjects(appContext);
            StringBuilder output = new StringBuilder();
            if (projects.isEmpty()) {
                output.append("No source projects imported yet.\n\n");
                output.append("Import a ZIP containing a game engine project or source code. The archive is inspected only; scripts and binaries are not executed.");
            } else {
                for (NativePortManager.Project project : projects) {
                    output.append(project.directory.getName()).append('\n');
                    output.append("Engine: ").append(project.analysis.engine).append('\n');
                    output.append("Status: ").append(project.analysis.status).append('\n');
                    output.append("Files inspected: ").append(project.analysis.filesScanned).append('\n');
                    output.append("Next: ").append(project.analysis.nextStep).append("\n\n");
                }
            }
            if (activity != null) activity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                projectsView.setText(output.toString());
                statusView.setText(projects.isEmpty()
                        ? "Workspace ready. Native build requires a supported source project."
                        : "Inspection complete. Review each project's native build readiness below.");
            });
        }, "native-port-scan").start();
    }

    private void openBuildWorkflow() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://github.com/suksesjt12-source/WinlatorMaliPlus/actions/workflows/native-port-build.yml"));
        startActivity(intent);
    }

    private void showNativePortGuide() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Native recompile requirements")
                .setMessage("Native ARM64 builds require legitimate game source code or an official engine project, Android-compatible dependencies, an Android application layer, and the required assets. An EXE/XEX/NSP file alone cannot be recompiled into native Android code. Android Gradle projects can be built by the repository workflow after their source is placed under native-ports/<project-folder> and pushed to GitHub.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        statusView = null;
        projectsView = null;
        importButton = null;
        super.onDestroyView();
    }
}
