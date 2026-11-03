package com.winlator.cmod.perf;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class RootPerformanceManager {
    private static final String TAG = "RootPerformance";
    private static final String ORIGINAL_GOVERNORS = "root_perf_original_governors";
    private static final Pattern POLICY_NAME = Pattern.compile("policy[0-9]+");
    private static final Pattern GOVERNOR_NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final ExecutorService ROOT_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WinlatorRootPerformance");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface AccessCallback {
        void onResult(AvailableControls controls, String error);
    }

    public interface OperationCallback {
        void onResult(String error);
    }

    private static final class Policy {
        final String name;
        final Set<String> availableGovernors;
        final String currentGovernor;

        Policy(String name, Set<String> availableGovernors, String currentGovernor) {
            this.name = name;
            this.availableGovernors = availableGovernors;
            this.currentGovernor = currentGovernor;
        }
    }

    public static final class AvailableControls {
        public final List<String> cpuGovernors;
        public final List<String> gpuGovernors;
        public final List<String> ioSchedulers;

        private AvailableControls(List<String> cpuGovernors, List<String> gpuGovernors,
                                  List<String> ioSchedulers) {
            this.cpuGovernors = cpuGovernors;
            this.gpuGovernors = gpuGovernors;
            this.ioSchedulers = ioSchedulers;
        }
    }

    private RootPerformanceManager() {}

    public static boolean hasPendingRestore(Context context) {
        return preferences(context).contains(ORIGINAL_GOVERNORS);
    }

    public static void requestAccess(Context context, AccessCallback callback) {
        ROOT_EXECUTOR.execute(() -> {
            try {
                String uid = runRoot("id -u").trim();
                if (!"0".equals(uid)) throw new IOException("Root access was not granted.");
                AvailableControls controls = readAvailableControls();
                if (controls.cpuGovernors.isEmpty()
                        && controls.gpuGovernors.isEmpty()
                        && controls.ioSchedulers.isEmpty()) {
                    throw new IOException("No supported governor or I/O scheduler controls were found.");
                }
                MAIN_HANDLER.post(() -> callback.onResult(controls, null));
            } catch (Exception e) {
                Log.w(TAG, "Root performance access check failed", e);
                String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                MAIN_HANDLER.post(() -> callback.onResult(
                        new AvailableControls(new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>()), error));
            }
        });
    }

    public static void applyPerformanceGovernor(Context context, String governor,
                                                OperationCallback callback) {
        applyPerformanceProfile(context, governor, null, null, callback);
    }

    public static void applyPerformanceProfile(Context context, String cpuGovernor,
                                               String gpuGovernor, String ioScheduler,
                                               OperationCallback callback) {
        Context appContext = context.getApplicationContext();
        ROOT_EXECUTOR.execute(() -> {
            String error = null;
            try {
                restorePendingNow(appContext);
                List<TuningNode> nodes = discoverTuningNodes();
                List<TuningNode> selected = new ArrayList<>();
                addMatchingNodes(selected, nodes, "cpu", cpuGovernor);
                addMatchingNodes(selected, nodes, "gpu", gpuGovernor);
                addMatchingNodes(selected, nodes, "io", ioScheduler);
                if (selected.isEmpty()) {
                    throw new IOException("None of the selected CPU/GPU governors or I/O schedulers "
                            + "is available on this device.");
                }

                StringBuilder original = new StringBuilder();
                StringBuilder commands = new StringBuilder();
                for (TuningNode node : selected) {
                    if (original.length() > 0) original.append('\n');
                    original.append(node.type).append('|').append(node.name)
                            .append('|').append(node.currentValue);
                    commands.append("if [ -w ").append(nodePath(node.type, node.name))
                            .append(" ]; then echo '").append(node.selectedValue).append("' > ")
                            .append(nodePath(node.type, node.name))
                            .append(" || exit 1; else exit 1; fi\n");
                }
                if (!preferences(appContext).edit()
                        .putString(ORIGINAL_GOVERNORS, original.toString()).commit()) {
                    throw new IOException("Could not save current CPU/GPU tuning settings.");
                }
                runRoot(commands.toString());
                Log.i(TAG, "Applied root performance settings to " + selected.size() + " nodes");
            } catch (Exception e) {
                Log.e(TAG, "Could not apply root performance profile", e);
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            if (callback != null) {
                String result = error;
                MAIN_HANDLER.post(() -> callback.onResult(result));
            }
        });
    }

    public static void restore(Context context, OperationCallback callback) {
        Context appContext = context.getApplicationContext();
        ROOT_EXECUTOR.execute(() -> {
            String error = null;
            try {
                restorePendingNow(appContext);
            } catch (Exception e) {
                Log.e(TAG, "Could not restore CPU governor settings", e);
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            if (callback != null) {
                String result = error;
                MAIN_HANDLER.post(() -> callback.onResult(result));
            }
        });
    }

    private static void addMatchingNodes(List<TuningNode> result, List<TuningNode> nodes,
                                         String type, String value) {
        if (value == null || value.isEmpty() || "No change".equals(value)) return;
        if (!GOVERNOR_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + type + " tuning value.");
        }
        for (TuningNode node : nodes) {
            if (matchesType(node.type, type) && node.availableValues.contains(value)) {
                result.add(new TuningNode(node.type, node.name, node.currentValue,
                        node.availableValues, value));
            }
        }
    }

    private static AvailableControls readAvailableControls() throws IOException {
        List<String> cpu = new ArrayList<>();
        for (Policy policy : readPolicies()) cpu.addAll(policy.availableGovernors);
        List<String> gpu = new ArrayList<>();
        List<String> io = new ArrayList<>();
        for (TuningNode node : discoverTuningNodes()) {
            if (node.type.startsWith("gpu")) gpu.addAll(node.availableValues);
            else if (node.type.equals("io")) io.addAll(node.availableValues);
        }
        return new AvailableControls(uniqueValues(cpu), uniqueValues(gpu), uniqueValues(io));
    }

    private static List<String> uniqueValues(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static List<TuningNode> discoverTuningNodes() throws IOException {
        List<TuningNode> nodes = new ArrayList<>();
        for (Policy policy : readPolicies()) {
            nodes.add(new TuningNode("cpu", policy.name, policy.currentGovernor,
                    policy.availableGovernors, null));
        }
        String output = runRoot(
                "for d in /sys/class/devfreq/*; do "
                        + "[ -d \"$d\" ] || continue; "
                        + "n=${d##*/}; name=$(cat \"$d/name\" 2>/dev/null); "
                        + "case \"$n $name\" in *[Gg][Pp][Uu]*|*[Mm][Aa][Ll][Ii]*|"
                        + "*[Kk][Gg][Ss][Ll]*|*[Aa][Dd][Rr][Ee][Nn][Oo]*|"
                        + "*[Gg]3[Dd]*|*[Pp][Oo][Ww][Ee][Rr][Vv][Rr]*|"
                        + "*[Gg][Rr][Aa][Pp][Hh][Ii][Cc][Ss]*) ;; *) continue;; esac; "
                        + "[ -r \"$d/available_governors\" ] && [ -r \"$d/governor\" ] || continue; "
                        + "a=$(cat \"$d/available_governors\" 2>/dev/null) || continue; "
                        + "c=$(cat \"$d/governor\" 2>/dev/null) || continue; "
                        + "printf 'gpu|%s|%s|%s\\n' \"$n\" \"$a\" \"$c\"; done\n"
                        + "d=/sys/class/kgsl/kgsl-3d0/devfreq; "
                        + "if [ -r \"$d/available_governors\" ] && [ -r \"$d/governor\" ]; then "
                        + "a=$(cat \"$d/available_governors\" 2>/dev/null); "
                        + "c=$(cat \"$d/governor\" 2>/dev/null); "
                        + "printf 'gpu_kgsl|kgsl-3d0|%s|%s\\n' \"$a\" \"$c\"; fi\n"
                        + "for d in /sys/block/*/queue; do "
                        + "[ -r \"$d/scheduler\" ] || continue; "
                        + "b=${d%/queue}; b=${b##*/}; "
                        + "a=$(cat \"$d/scheduler\" 2>/dev/null) || continue; "
                        + "c=$(printf '%s\\n' \"$a\" | sed -n 's/.*\\[\\([^]]*\\)\\].*/\\1/p'); "
                        + "printf 'io|%s|%s|%s\\n' \"$b\" \"$a\" \"$c\"; done");
        for (String line : output.split("\\R")) {
            String[] fields = line.trim().split("\\|", 4);
            if (fields.length != 4 || !(fields[0].startsWith("gpu") || fields[0].equals("io"))
                    || !NODE_NAME.matcher(fields[1]).matches()) continue;
            Set<String> values = parseValues(fields[2]);
            String current = fields[3].trim();
            if (!values.isEmpty() && GOVERNOR_NAME.matcher(current).matches()) {
                nodes.add(new TuningNode(fields[0], fields[1], current, values, null));
            }
        }
        return nodes;
    }

    private static Set<String> parseValues(String rawValues) {
        Set<String> values = new LinkedHashSet<>();
        for (String raw : rawValues.trim().split("\\s+")) {
            String value = raw.replace("[", "").replace("]", "");
            if (GOVERNOR_NAME.matcher(value).matches()) values.add(value);
        }
        return values;
    }

    private static List<Policy> readPolicies() throws IOException {
        String script = "for d in /sys/devices/system/cpu/cpufreq/policy*; do "
                + "[ -d \"$d\" ] || continue; "
                + "[ -r \"$d/scaling_available_governors\" ] || continue; "
                + "g=$(cat \"$d/scaling_available_governors\" 2>/dev/null) || continue; "
                + "c=$(cat \"$d/scaling_governor\" 2>/dev/null) || continue; "
                + "printf '%s|%s|%s\\n' \"${d##*/}\" \"$g\" \"$c\"; done";
        String output = runRoot(script);
        List<Policy> policies = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String[] fields = line.trim().split("\\|", 3);
            if (fields.length != 3 || !POLICY_NAME.matcher(fields[0]).matches()) continue;
            Set<String> governors = new LinkedHashSet<>();
            for (String governor : fields[1].trim().split("\\s+")) {
                if (GOVERNOR_NAME.matcher(governor).matches()) governors.add(governor);
            }
            if (governors.isEmpty() || !GOVERNOR_NAME.matcher(fields[2]).matches()) continue;
            policies.add(new Policy(fields[0], governors, fields[2]));
        }
        return policies;
    }

    private static void restorePendingNow(Context context) throws IOException {
        SharedPreferences prefs = preferences(context);
        String saved = prefs.getString(ORIGINAL_GOVERNORS, null);
        if (saved == null || saved.isEmpty()) return;

        StringBuilder commands = new StringBuilder();
        for (String line : saved.split("\\R")) {
            String[] fields = line.split("\\|", 3);
            if (fields.length != 3 || !isValidNode(fields[0], fields[1])
                    || !GOVERNOR_NAME.matcher(fields[2]).matches()) {
                throw new IOException("Saved CPU governor state is invalid; it was kept for recovery.");
            }
            String path = nodePath(fields[0], fields[1]);
            commands.append("if [ -w ").append(path).append(" ]; then echo '")
                    .append(fields[2]).append("' > ").append(path)
                    .append(" || exit 1; fi\n");
        }
        runRoot(commands.toString());
        if (!prefs.edit().remove(ORIGINAL_GOVERNORS).commit()) {
            throw new IOException("Governor was restored, but its recovery record could not be cleared.");
        }
        Log.i(TAG, "Restored CPU governors after game session");
    }

    private static boolean isValidNode(String type, String name) {
        if (!NODE_NAME.matcher(name).matches()) return false;
        if ("cpu".equals(type)) return POLICY_NAME.matcher(name).matches();
        return type.startsWith("gpu") || "io".equals(type);
    }

    private static String nodePath(String type, String name) {
        if ("cpu".equals(type)) {
            return "'/sys/devices/system/cpu/cpufreq/" + name + "/scaling_governor'";
        }
        if ("gpu_kgsl".equals(type)) {
            return "'/sys/class/kgsl/kgsl-3d0/devfreq/governor'";
        }
        if ("gpu".equals(type)) return "'/sys/class/devfreq/" + name + "/governor'";
        return "'/sys/block/" + name + "/queue/scheduler'";
    }

    private static boolean matchesType(String nodeType, String selectedType) {
        return "gpu".equals(selectedType) ? nodeType.startsWith("gpu")
                : nodeType.equals(selectedType);
    }

    private static String runRoot(String command) throws IOException {
        java.lang.Process process;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new IOException("Could not start the root manager (su).", e);
        }
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Timed out waiting for root-manager approval.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) output.append('\n');
                output.append(line);
            }
            if (process.exitValue() != 0) {
                String detail = output.toString().trim();
                throw new IOException(detail.isEmpty()
                        ? "Root access was denied or the governor operation failed." : detail);
            }
            return output.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Root operation was interrupted.", e);
        }
    }

    private static SharedPreferences preferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static final Pattern NODE_NAME = Pattern.compile("[A-Za-z0-9_.:-]+");

    private static final class TuningNode {
        final String type;
        final String name;
        final String currentValue;
        final Set<String> availableValues;
        final String selectedValue;

        TuningNode(String type, String name, String currentValue, Set<String> availableValues,
                   String selectedValue) {
            this.type = type;
            this.name = name;
            this.currentValue = currentValue;
            this.availableValues = availableValues;
            this.selectedValue = selectedValue;
        }
    }
}
