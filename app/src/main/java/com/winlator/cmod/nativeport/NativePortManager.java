package com.winlator.cmod.nativeport;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports and inspects game source archives. Imported scripts and executables are never run. */
public final class NativePortManager {
    private static final String ROOT_DIR = "native-ports";
    private static final int MAX_ENTRIES = 20000;
    private static final long MAX_EXTRACTED_BYTES = 1536L * 1024L * 1024L;
    private static final int MAX_SCAN_DEPTH = 12;
    private static final int MAX_SCAN_FILES = 30000;

    private NativePortManager() {}

    public static final class Project {
        public final File directory;
        public final Analysis analysis;
        private Project(File directory, Analysis analysis) {
            this.directory = directory;
            this.analysis = analysis;
        }
    }

    public static final class Analysis {
        public final String engine;
        public final String status;
        public final String nextStep;
        public final boolean androidGradleReady;
        public final boolean hasNativeSource;
        public final boolean hasWindowsExecutable;
        public final int filesScanned;

        private Analysis(String engine, String status, String nextStep,
                         boolean androidGradleReady, boolean hasNativeSource,
                         boolean hasWindowsExecutable, int filesScanned) {
            this.engine = engine;
            this.status = status;
            this.nextStep = nextStep;
            this.androidGradleReady = androidGradleReady;
            this.hasNativeSource = hasNativeSource;
            this.hasWindowsExecutable = hasWindowsExecutable;
            this.filesScanned = filesScanned;
        }

        public String toReport(String projectName) {
            return "# Native Port Readiness Report\n\n"
                    + "- Project: " + projectName + "\n"
                    + "- Detected type: " + engine + "\n"
                    + "- Status: " + status + "\n"
                    + "- Files inspected: " + filesScanned + "\n\n"
                    + "## Next step\n\n" + nextStep + "\n\n"
                    + "## Important limitation\n\n"
                    + "A Windows executable or game-data folder cannot be converted into native Android code by renaming, wrapping, or recompiling the binary. A native port requires legitimate source code or an official engine project, Android-compatible dependencies, and permission to build and distribute required assets.\n";
        }
    }

    public static File importArchive(Context context, Uri archiveUri) throws IOException {
        if (context == null || archiveUri == null) throw new IOException("Missing source archive");
        String slug = toSafeSlug(queryDisplayName(context.getContentResolver(), archiveUri));
        File root = new File(context.getFilesDir(), ROOT_DIR);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create native project storage");
        File destination = new File(root, slug + "-" + System.currentTimeMillis());
        if (!destination.mkdirs()) throw new IOException("Cannot create project folder");

        long totalBytes = 0L;
        int entries = 0;
        try {
            InputStream raw = context.getContentResolver().openInputStream(archiveUri);
            if (raw == null) throw new IOException("Cannot open the selected archive");
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry entry;
                byte[] buffer = new byte[32768];
                String canonicalRoot = destination.getCanonicalPath() + File.separator;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > MAX_ENTRIES) throw new IOException("Archive has too many entries");
                    String relative = validateRelativePath(entry.getName());
                    if (relative.length() == 0 || shouldSkip(relative)) {
                        zip.closeEntry();
                        continue;
                    }

                    File output = new File(destination, relative);
                    if (!output.getCanonicalPath().startsWith(canonicalRoot)) {
                        throw new IOException("Archive contains an unsafe path");
                    }
                    if (entry.isDirectory()) {
                        if (!output.isDirectory() && !output.mkdirs()) {
                            throw new IOException("Cannot create directory: " + relative);
                        }
                    } else {
                        File parent = output.getParentFile();
                        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                            throw new IOException("Cannot create directory for: " + relative);
                        }
                        try (FileOutputStream out = new FileOutputStream(output)) {
                            int read;
                            while ((read = zip.read(buffer)) != -1) {
                                totalBytes += read;
                                if (totalBytes > MAX_EXTRACTED_BYTES) {
                                    throw new IOException("Archive expands beyond the 1.5 GB safety limit");
                                }
                                out.write(buffer, 0, read);
                            }
                        }
                    }
                    zip.closeEntry();
                }
            }
            if (entries == 0) throw new IOException("The selected ZIP archive is empty");

            Analysis analysis = analyzeProject(destination);
            File report = new File(destination, "NATIVE_PORT_REPORT.md");
            try (FileOutputStream out = new FileOutputStream(report)) {
                out.write(analysis.toReport(destination.getName()).getBytes("UTF-8"));
            }
            return destination;
        } catch (IOException | RuntimeException error) {
            deleteRecursively(destination);
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Failed to import source archive", error);
        }
    }

    public static List<Project> listProjects(Context context) {
        File root = new File(context.getFilesDir(), ROOT_DIR);
        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null || directories.length == 0) return Collections.emptyList();
        List<Project> projects = new ArrayList<>();
        for (File directory : directories) projects.add(new Project(directory, analyzeProject(directory)));
        Collections.sort(projects, (left, right) ->
                Long.compare(right.directory.lastModified(), left.directory.lastModified()));
        return projects;
    }

    public static Analysis analyzeProject(File root) {
        ScanFlags flags = new ScanFlags();
        int[] count = { 0 };
        scan(root, 0, flags, count);
        boolean androidReady = flags.androidManifest && flags.gradleBuild && flags.gradleWrapper;
        String engine;
        String status;
        String next;

        if (flags.unrealProject) {
            engine = "Unreal Engine project";
            status = "Source detected; Android packaging required";
            next = "Build with a matching Unreal Engine toolchain and Android SDK/NDK. Verify the engine version, source availability, plugins, and Android packaging.";
        } else if (flags.unityProject) {
            engine = "Unity project";
            status = "Source detected; Android export required";
            next = "Open in a compatible Unity Editor, install Android Build Support, configure Android ARM64, and export an Android Gradle project. The editor is not bundled in this app.";
        } else if (flags.godotProject) {
            engine = "Godot project";
            status = "Project detected; Android export required";
            next = "Use a compatible Godot version and Android export templates, configure an Android ARM64 preset, and export an installable Android project.";
        } else if (androidReady) {
            engine = "Android Gradle project";
            status = "Ready for a native Android Gradle build";
            next = "Place the project under native-ports/<project-folder> in this repository, push the source, then run Actions > Build native Android game project. This builds an existing Android app; it does not automatically port an arbitrary PC game.";
        } else if (flags.cmake && flags.nativeSource) {
            engine = "C/C++ source with CMake";
            status = "Native source detected; Android port integration required";
            next = "Add an Android application shell, Android NDK toolchain configuration, compatible libraries, input/audio/rendering backends, and game lifecycle integration. CMakeLists.txt alone is not a complete APK.";
        } else if (flags.windowsExecutable && !flags.nativeSource) {
            engine = "Windows executable / binary";
            status = "Not eligible for native recompile from this archive";
            next = "Obtain legitimate game source code or an official Android-capable engine project. Keep using Windows Compatibility Mode for an EXE-only game.";
        } else if (flags.nativeSource) {
            engine = "C/C++ or mixed source project";
            status = "Source detected; Android build system not complete";
            next = "Identify the engine and add Android NDK build files and an Android application/launcher layer before producing an ARM64 APK.";
        } else {
            engine = "Unclassified source archive";
            status = "Insufficient build metadata";
            next = "Import the complete source project, including engine metadata, build scripts, and required submodules. A game-data folder alone is not source code.";
        }

        return new Analysis(engine, status, next, androidReady, flags.nativeSource,
                flags.windowsExecutable, count[0]);
    }

    private static final class ScanFlags {
        boolean androidManifest, gradleBuild, gradleWrapper, cmake, nativeSource;
        boolean windowsExecutable, unityProject, unrealProject, godotProject;
    }

    private static void scan(File directory, int depth, ScanFlags flags, int[] count) {
        if (directory == null || depth > MAX_SCAN_DEPTH || count[0] >= MAX_SCAN_FILES) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (count[0] >= MAX_SCAN_FILES) break;
            String lower = child.getName().toLowerCase(Locale.ROOT);
            if (child.isDirectory()) {
                if (!shouldSkip(child.getName())) scan(child, depth + 1, flags, count);
                continue;
            }
            count[0]++;
            if ("androidmanifest.xml".equals(lower)) flags.androidManifest = true;
            if ("build.gradle".equals(lower) || "build.gradle.kts".equals(lower)
                    || "settings.gradle".equals(lower) || "settings.gradle.kts".equals(lower)) flags.gradleBuild = true;
            if ("gradlew".equals(lower)) flags.gradleWrapper = true;
            if ("cmakelists.txt".equals(lower)) flags.cmake = true;
            if (lower.endsWith(".c") || lower.endsWith(".cc") || lower.endsWith(".cpp")
                    || lower.endsWith(".h") || lower.endsWith(".hpp") || lower.endsWith(".mm")) flags.nativeSource = true;
            if (lower.endsWith(".exe")) flags.windowsExecutable = true;
            if (lower.endsWith(".uproject")) flags.unrealProject = true;
            if ("project.godot".equals(lower)) flags.godotProject = true;
            if ("projectversion.txt".equals(lower) && child.getParentFile() != null
                    && "ProjectSettings".equalsIgnoreCase(child.getParentFile().getName())) flags.unityProject = true;
            if ("manifest.json".equals(lower) && child.getParentFile() != null
                    && "Packages".equalsIgnoreCase(child.getParentFile().getName())) {
                File parent = child.getParentFile().getParentFile();
                if (parent != null && new File(parent, "Assets").isDirectory()) flags.unityProject = true;
            }
        }
    }

    private static String validateRelativePath(String raw) throws IOException {
        if (raw == null) throw new IOException("Archive contains an invalid path");
        String path = raw.replace('\\', '/');
        if (path.startsWith("/") || path.indexOf('\0') >= 0 || path.matches("^[A-Za-z]:.*")) {
            throw new IOException("Archive contains an absolute path");
        }
        for (String part : path.split("/")) {
            if ("..".equals(part)) throw new IOException("Archive contains a parent-directory path");
        }
        return path;
    }

    private static boolean shouldSkip(String path) {
        String[] blocked = { ".git", ".gradle", ".idea", ".vs", "node_modules",
                "library", "temp", "intermediate", "binaries", ".ds_store" };
        for (String segment : path.toLowerCase(Locale.ROOT).split("/")) {
            for (String item : blocked) if (item.equals(segment)) return true;
        }
        return false;
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "game-source.zip";
    }

    private static String toSafeSlug(String source) {
        String name = source == null ? "game-source" : source;
        int extension = name.lastIndexOf('.');
        if (extension > 0) name = name.substring(0, extension);
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        if (slug.isEmpty()) slug = "game-source";
        return slug.length() > 36 ? slug.substring(0, 36) : slug;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
