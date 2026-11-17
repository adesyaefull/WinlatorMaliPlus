package com.winlator.cmod.integration;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;

import java.io.File;
import java.util.List;

public final class LauncherBridgeProvider extends ContentProvider {
    public static final String METHOD_LAUNCH_EXE = "launch_exe";
    public static final String KEY_CONTAINER_ID = "container_id";
    public static final String KEY_EXE_PATH = "exe_path";
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_MESSAGE = "message";

    private static final String LAUNCHER_PACKAGE = "com.suksesjt.pcgamelauncherplus";

    @Override
    public boolean onCreate() {
        return true;
    }

    private void verifyCaller() {
        String caller = getCallingPackage();
        if (!LAUNCHER_PACKAGE.equals(caller)) {
            throw new SecurityException("Caller is not the paired PC Game Launcher");
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        verifyCaller();
        if (uri == null || !"containers".equals(uri.getLastPathSegment())) return null;
        if (getContext() == null) return null;

        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "container_id", "name"});
        List<Container> containers = new ContainerManager(getContext()).getContainers();
        for (Container container : containers) {
            cursor.addRow(new Object[]{container.id, container.id, container.getName()});
        }
        return cursor;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        verifyCaller();
        if (!METHOD_LAUNCH_EXE.equals(method)) {
            return result(false, "Unknown bridge method");
        }
        if (getContext() == null) return result(false, "Winlator context is unavailable");

        int containerId = extras == null ? 0 : extras.getInt(KEY_CONTAINER_ID, 0);
        String requestedPath = extras == null ? null : extras.getString(KEY_EXE_PATH);
        if (containerId <= 0) return result(false, "Select a valid container");
        if (requestedPath == null || requestedPath.trim().isEmpty()) {
            return result(false, "EXE path is empty");
        }

        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File allowedDirectory;
        File exe;
        try {
            allowedDirectory = new File(downloads, "PCGameLauncher").getCanonicalFile();
            exe = new File(requestedPath).getCanonicalFile();
        } catch (Exception e) {
            return result(false, "Invalid EXE path");
        }

        String allowedPrefix = allowedDirectory.getPath() + File.separator;
        if (!exe.getPath().startsWith(allowedPrefix)) {
            return result(false, "Game must be stored in Download/PCGameLauncher");
        }
        if (!exe.isFile() || !exe.canRead() || !exe.getName().toLowerCase().endsWith(".exe")) {
            return result(false, "The selected EXE does not exist or cannot be read");
        }

        boolean containerExists = false;
        try {
            for (Container container : new ContainerManager(getContext()).getContainers()) {
                if (container.id == containerId) {
                    containerExists = true;
                    break;
                }
            }
        } catch (Exception ignored) {
            return result(false, "Could not read Winlator containers");
        }
        if (!containerExists) return result(false, "Container not found. Refresh the container list.");

        try {
            Intent launch = new Intent(getContext(), XServerDisplayActivity.class);
            launch.putExtra("container_id", containerId);
            launch.putExtra("shortcut_path", exe.getAbsolutePath());
            launch.putExtra("shortcut_name", exe.getName().substring(0, exe.getName().length() - 4));
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(launch);
            return result(true, "Launching " + exe.getName() + " in container " + containerId);
        } catch (Exception e) {
            String message = e.getMessage();
            return result(false, message == null ? "Winlator could not start the game" : message);
        }
    }

    private Bundle result(boolean success, String message) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_SUCCESS, success);
        bundle.putString(KEY_MESSAGE, message);
        return bundle;
    }

    @Override
    public String getType(Uri uri) {
        return uri != null && "containers".equals(uri.getLastPathSegment())
                ? "vnd.android.cursor.dir/vnd.winlator.container" : null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
