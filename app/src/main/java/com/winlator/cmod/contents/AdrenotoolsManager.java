package com.winlator.cmod.contents;

import android.content.res.AssetManager;
import android.net.Uri;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.xenvironment.ImageFs;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONException;
import org.json.JSONObject;

public class AdrenotoolsManager {
    private static final long MAX_DRIVER_ARCHIVE_SIZE = 512L * 1024 * 1024;
    private static final int MAX_DRIVER_ARCHIVE_ENTRIES = 10000;
    
    private File adrenotoolsContentDir;
    private Context mContext;
    
    public AdrenotoolsManager(Context context) {
        this.mContext = context;
        this.adrenotoolsContentDir = new File(mContext.getFilesDir(), "contents/adrenotools");
        if (!adrenotoolsContentDir.exists())
            adrenotoolsContentDir.mkdirs();
    }
        
    public String getLibraryName(String adrenoToolsDriverId) {
        String libraryName = "";
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        try {
            File metaProfile = new File(driverPath, "meta.json");
            JSONObject jsonObject = new JSONObject(FileUtils.readString(metaProfile));
            libraryName = jsonObject.getString("libraryName");
        }
        catch (JSONException e) {
        }
        return libraryName;
    }
    
    public String getDriverName(String adrenoToolsDriverId) {
        String driverName = "";
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        try {
            File metaProfile = new File(driverPath, "meta.json");
            JSONObject jsonObject = new JSONObject(FileUtils.readString(metaProfile));
            driverName = jsonObject.getString("name");
        }
        catch (JSONException e) {
        }
        return driverName;
    }

    public String getDriverVersion(String adrenoToolsDriverId) {
        String driverVersion = "";
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        try {
            File metaProfile = new File(driverPath, "meta.json");
            JSONObject jsonObject = new JSONObject(FileUtils.readString(metaProfile));
            driverVersion = jsonObject.getString("driverVersion");
        }
        catch (JSONException e) {
        }
        return driverVersion;
    }

    public String getDriverPath(String adrenotoolsDriverId) {
        return adrenotoolsContentDir.getAbsolutePath() + "/" + adrenotoolsDriverId + "/";
    }

    private void reloadContainers(String adrenoToolsDriverId) {
        ContainerManager containerManager = new ContainerManager(mContext);
        for (Container container : containerManager.getContainers()) {
            HashMap<String, String> config = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(container.getGraphicsDriverConfig());
            Log.d("AdrenotoolsManager", "Checking if container driver version " + config.get("version") + " matches " + getDriverName(adrenoToolsDriverId));
            if (config.get("version").contains(getDriverName(adrenoToolsDriverId))) {
                Log.d("AdrenotoolsManager", "Found a match for container " + container.getName());
                config.put("version", GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, mContext) ? DefaultVersion.WRAPPER_ADRENO : DefaultVersion.WRAPPER);
                container.setGraphicsDriverConfig(GraphicsDriverConfigDialog.toGraphicsDriverConfig(config));
                container.saveData();
            }     
        }
        for (Shortcut shortcut : containerManager.loadShortcuts()) {
            HashMap<String, String> config = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(shortcut.getExtra("graphicsDriverConfig", shortcut.container.getGraphicsDriverConfig()));
            Log.d("AdrenotoolsManager", "Checking if shortcut driver version " + config.get("version") + " matches " + getDriverName(adrenoToolsDriverId));
            if (config.get("version").contains(getDriverName(adrenoToolsDriverId))) {
                Log.d("AdrenotoolsManager", "Found a match for shortcut " + shortcut.name);
                config.put("version", GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, mContext) ? DefaultVersion.WRAPPER_ADRENO : DefaultVersion.WRAPPER);
                shortcut.putExtra("graphicsDriverConfig", GraphicsDriverConfigDialog.toGraphicsDriverConfig(config));
                shortcut.saveData();
            }
        }
    }
    
    public void removeDriver(String adrenoToolsDriverId) {
        Log.d("AdrenotoolsManager", "Removing driver " + adrenoToolsDriverId);
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        reloadContainers(adrenoToolsDriverId);
        FileUtils.delete(driverPath);
    }

    public ArrayList<String> enumarateInstalledDrivers() {
        ArrayList<String> driversList = new ArrayList<>();
        
        for (File f : adrenotoolsContentDir.listFiles()) {
            boolean fromResources = isFromResources(f.getName());
            if (!fromResources && new File(f, "meta.json").exists())
                driversList.add(f.getName());
        }
        return driversList;
    }
    
    public boolean isFromResources(String adrenotoolsDriverId) {
        String driver = "graphics_driver/adrenotools-" + adrenotoolsDriverId + ".tzst";
        AssetManager am = mContext.getResources().getAssets();
        InputStream is = null;
        boolean isFromResources = true;
        
        try {
            is = am.open(driver);
            is.close();
        }
        catch (IOException e) {
            isFromResources = false;
        }
        
        return isFromResources;
    }
        
    public boolean extractDriverFromResources(String adrenotoolsDriverId) {
        String src = "graphics_driver/adrenotools-" + adrenotoolsDriverId + ".tzst";
        boolean hasExtracted;

        File dst = new File(adrenotoolsContentDir, adrenotoolsDriverId);
        if (dst.exists())
            return true;

        dst.mkdirs();
        Log.d("AdrenotoolsManager", "Extracting " + src + " to " + dst.getAbsolutePath());
        hasExtracted = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, mContext, src, dst);

        if (!hasExtracted)
            dst.delete();

        return hasExtracted;
    }
    
    public String installDriver(Uri driverUri) {
        File tmpDir = new File(adrenotoolsContentDir, "tmp");
        try (InputStream input = mContext.getContentResolver().openInputStream(driverUri)) {
            return installDriver(input);
        } catch (IOException e) {
            Log.e("AdrenotoolsManager", "Unable to read driver archive", e);
            FileUtils.delete(tmpDir);
            return "";
        }
    }

    public String installDriver(InputStream input) {
        if (input == null) return "";
        File tmpDir = new File(adrenotoolsContentDir, "tmp");
        FileUtils.delete(tmpDir);
        if (!tmpDir.mkdirs()) {
            Log.e("AdrenotoolsManager", "Unable to create temporary driver directory");
            return "";
        }
        String name = "";
        try (ZipInputStream zis = new ZipInputStream(input)) {
            File canonicalRoot = tmpDir.getCanonicalFile();
            String rootPath = canonicalRoot.getPath() + File.separator;
            long extractedBytes = 0;
            int entryCount = 0;
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > MAX_DRIVER_ARCHIVE_ENTRIES) {
                    throw new IOException("Driver archive contains too many files");
                }
                String entryName = entry.getName().replace('\\', '/');
                if (entryName.isEmpty() || entryName.startsWith("/") || entryName.matches("^[A-Za-z]:.*")) {
                    throw new IOException("Driver archive contains an unsafe path");
                }
                File dstFile = new File(tmpDir, entryName).getCanonicalFile();
                if (dstFile.equals(canonicalRoot) && entry.isDirectory()) continue;
                if (!dstFile.getPath().startsWith(rootPath)) {
                    throw new IOException("Driver archive contains a path outside its root");
                }
                if (entry.isDirectory()) {
                    if (!dstFile.isDirectory() && !dstFile.mkdirs()) {
                        throw new IOException("Unable to create driver directory");
                    }
                    continue;
                }
                File parent = dstFile.getParentFile();
                if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                    throw new IOException("Unable to create driver directory");
                }
                try (OutputStream output = new FileOutputStream(dstFile)) {
                    int count;
                    while ((count = zis.read(buffer)) != -1) {
                        extractedBytes += count;
                        if (extractedBytes > MAX_DRIVER_ARCHIVE_SIZE) {
                            throw new IOException("Driver archive exceeds the extraction limit");
                        }
                        output.write(buffer, 0, count);
                    }
                }
                zis.closeEntry();
            }

            File metaFile = new File(tmpDir, "meta.json");
            if (!metaFile.isFile()) throw new IOException("Driver ZIP must contain root meta.json");
            JSONObject metadata = new JSONObject(FileUtils.readString(metaFile));
            name = metadata.optString("name", "").trim();
            String libraryName = metadata.optString("libraryName", "").trim();
            String driverVersion = metadata.optString("driverVersion", "").trim();
            if (!name.matches("[A-Za-z0-9][A-Za-z0-9._ -]{0,99}")
                    || libraryName.isEmpty() || driverVersion.isEmpty()
                    || libraryName.contains("/") || libraryName.contains("\\")
                    || !new File(tmpDir, libraryName).isFile()) {
                throw new IOException("Driver ZIP is missing valid Adrenotools metadata or its library");
            }

            File destination = new File(adrenotoolsContentDir, name);
            if (destination.exists()) {
                throw new IOException("A driver named " + name + " is already installed");
            }
            if (!tmpDir.renameTo(destination)) {
                throw new IOException("Unable to move the imported driver into place");
            }
            return name;
        } catch (IOException | JSONException e) {
            Log.e("AdrenotoolsManager", "Failed to install driver archive", e);
            return "";
        } finally {
            if (tmpDir.exists()) FileUtils.delete(tmpDir);
        }
    }
    
    public void setDriverById(EnvVars envVars, ImageFs imagefs, String adrenotoolsDriverId) {
        boolean isFromResources = isFromResources(adrenotoolsDriverId);

        if (isFromResources || enumarateInstalledDrivers().contains(adrenotoolsDriverId)) {
            String driverPath = getDriverPath(adrenotoolsDriverId);

            if (!getLibraryName(adrenotoolsDriverId).equals("")) {
                envVars.put("ADRENOTOOLS_DRIVER_PATH", driverPath);
                envVars.put("ADRENOTOOLS_HOOKS_PATH", imagefs.getLibDir());
                envVars.put("ADRENOTOOLS_DRIVER_NAME", getLibraryName(adrenotoolsDriverId));

                File winlatorDir = new File(SettingsFragment.DEFAULT_WINLATOR_PATH);
                File qglConfig = new File(winlatorDir, "qgl_config.txt");
                if (qglConfig.exists())
                    envVars.put("ADRENOTOOLS_REDIRECT_DIR", winlatorDir.getAbsolutePath() + "/");
            }
        }
    }
 }
