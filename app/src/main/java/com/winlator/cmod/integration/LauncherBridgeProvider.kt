package com.winlator.cmod.integration

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.container.ContainerManager
import java.io.File

/**
 * Read-only container discovery and validated direct-launch bridge for PC Game Launcher.
 * Game files must be staged in Download/PCGameLauncher through the launcher's SAF import flow.
 */
class LauncherBridgeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun verifyCaller() {
        if (callingPackage != LAUNCHER_PACKAGE) {
            throw SecurityException("Caller is not the paired PC Game Launcher")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        verifyCaller()
        if (uri.lastPathSegment != "containers") return null
        val ctx = context ?: return null
        val cursor = MatrixCursor(arrayOf("_id", "container_id", "name"))
        ContainerManager(ctx).getContainers().forEach { container ->
            cursor.addRow(arrayOf(container.id, container.id, container.getName()))
        }
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        verifyCaller()
        if (method != METHOD_LAUNCH_EXE) return result(false, "Unknown bridge method")

        val ctx = context ?: return result(false, "Winlator context is unavailable")
        val containerId = extras?.getInt(KEY_CONTAINER_ID, 0) ?: 0
        val requestedPath = extras?.getString(KEY_EXE_PATH).orEmpty()
        if (containerId <= 0) return result(false, "Select a valid container")
        if (requestedPath.isBlank()) return result(false, "EXE path is empty")

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val allowedDirectory = try {
            File(downloads, "PCGameLauncher").canonicalFile
        } catch (_: Exception) {
            return result(false, "Cannot resolve the game directory")
        }
        val exe = try {
            File(requestedPath).canonicalFile
        } catch (_: Exception) {
            return result(false, "Invalid EXE path")
        }
        if (!exe.path.startsWith(allowedDirectory.path + File.separator)) {
            return result(false, "Game must be stored in Download/PCGameLauncher")
        }
        if (!exe.isFile || !exe.canRead() || !exe.name.endsWith(".exe", ignoreCase = true)) {
            return result(false, "The selected EXE does not exist or cannot be read")
        }

        val containerExists = try {
            ContainerManager(ctx).getContainers().any { it.id == containerId }
        } catch (_: Exception) {
            false
        }
        if (!containerExists) return result(false, "Container not found. Refresh the container list.")

        return try {
            val launch = Intent(ctx, XServerDisplayActivity::class.java).apply {
                putExtra("container_id", containerId)
                putExtra("shortcut_path", exe.absolutePath)
                putExtra("shortcut_name", exe.nameWithoutExtension)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(launch)
            result(true, "Launching ${exe.name} in container $containerId")
        } catch (e: Exception) {
            result(false, e.message ?: "Winlator could not start the game")
        }
    }

    private fun result(success: Boolean, message: String) = Bundle().apply {
        putBoolean(KEY_SUCCESS, success)
        putString(KEY_MESSAGE, message)
    }

    override fun getType(uri: Uri): String? =
        if (uri.lastPathSegment == "containers") "vnd.android.cursor.dir/vnd.winlator.container" else null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val METHOD_LAUNCH_EXE = "launch_exe"
        const val KEY_CONTAINER_ID = "container_id"
        const val KEY_EXE_PATH = "exe_path"
        const val KEY_SUCCESS = "success"
        const val KEY_MESSAGE = "message"
        private const val LAUNCHER_PACKAGE = "com.suksesjt.pcgamelauncherplus"
    }
}
