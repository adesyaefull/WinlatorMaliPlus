package com.winlator.cmod.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Cursor;
import com.winlator.cmod.xserver.CursorManager;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Property;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowAttributes;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XServer;
import com.winlator.cmod.renderer.vulkan.VulkanRenderer;

import java.util.HashSet;

import dalvik.annotation.optimization.FastNative;

/** Surface for the native EGL renderer: composites X windows and applies the post-processing chain. */
@SuppressLint("ViewConstructor")
public class XServerView extends SurfaceView implements SurfaceHolder.Callback, WindowManager.OnWindowModificationListener, Pointer.OnPointerMotionListener, CursorManager.OnCursorModificationListener {
    private static final long MIN_POINTER_MOVE_INTERVAL_NS = 8_000_000L; // ~125 Hz max pointer update rate
    private final XServer xServer;
    private final HandlerThread vulkanEventThread;
    private final Handler vulkanEventHandler;
    private VulkanRenderer vulkanRenderer;
    private boolean nativeInitialized;
    private boolean fullscreen = false;
    private String unviewableWMClass = null;
    private float magnifierZoom = 1.0f;
    private boolean cursorVisible = true;
    private String postProcessing = "";
    private final HashSet<Long> registeredDirectContents = new HashSet<>();
    private long lastPointerMoveTimeNs = 0;

    public XServerView(Context context, XServer xServer) {
        this(context, xServer, false);
    }

    public XServerView(Context context, XServer xServer, boolean useVulkan) {
        super(context);
        this.xServer = xServer;
        if (useVulkan) {
            vulkanEventThread = new HandlerThread("VulkanRendererEvents");
            vulkanEventThread.start();
            vulkanEventHandler = new Handler(vulkanEventThread.getLooper());
        } else {
            vulkanEventThread = null;
            vulkanEventHandler = null;
        }
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getHolder().addCallback(this);
        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
        xServer.cursorManager.addOnCursorModificationListener(this);
        if (!useVulkan) {
            nativeInit(context, xServer);
            nativeInitialized = true;
        }
    }

    public void initVulkanRenderer(boolean nativeMode) {
        try {
            vulkanRenderer = new VulkanRenderer(this, xServer);
            vulkanRenderer.setInitialNativeMode(nativeMode);
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.e("XServerView", "Unable to load Vulkan renderer", e);
            android.widget.Toast.makeText(getContext(),
                    "Vulkan renderer could not be loaded; using OpenGL", android.widget.Toast.LENGTH_LONG).show();
            nativeInit(getContext(), xServer);
            nativeInitialized = true;
        }
    }

    public VulkanRenderer getVulkanRenderer() {
        return vulkanRenderer;
    }

    public boolean isVulkanRendererActive() {
        return vulkanRenderer != null;
    }

    public android.view.SurfaceControl getSurfaceControl() {
        return android.os.Build.VERSION.SDK_INT >= 29 ? super.getSurfaceControl() : null;
    }

    public void onVulkanInitializationFailed(String reason) {
        post(() -> {
            if (vulkanRenderer == null) return;
            vulkanRenderer.dispose();
            vulkanRenderer = null;
            nativeInit(getContext(), xServer);
            nativeInitialized = true;
            Surface surface = getHolder().getSurface();
            if (surface != null && surface.isValid()) {
                nativeCreateSurface(surface);
                nativeChangeSurface(getWidth(), getHeight());
            }
            android.widget.Toast.makeText(getContext(),
                    "Vulkan renderer failed: " + reason + ". Using OpenGL.",
                    android.widget.Toast.LENGTH_LONG).show();
        });
    }

    public void queueEvent(Runnable runnable) {
        if (vulkanEventHandler != null) vulkanEventHandler.post(runnable);
        else post(runnable);
    }

    public void addDirectContent(Window window, Drawable drawable) {
        if (vulkanRenderer != null) return;
        if (window == null || drawable == null) return;
        long key = directContentKey(window.id, drawable.id);
        synchronized (registeredDirectContents) {
            if (!registeredDirectContents.add(key)) return;
        }
        nativeAddDirectContent(window.id, drawable);
    }

    public void removeDirectContent(Window window, int drawableId) {
        if (vulkanRenderer != null) return;
        if (window == null) return;
        synchronized (registeredDirectContents) {
            if (!registeredDirectContents.remove(directContentKey(window.id, drawableId))) return;
        }
        nativeRemoveDirectContent(window.id, drawableId);
    }

    private static long directContentKey(int windowId, int drawableId) {
        return ((long) windowId << 32) | (drawableId & 0xffffffffL);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (vulkanRenderer != null) vulkanRenderer.onSurfaceCreated(holder.getSurface());
        else nativeCreateSurface(holder.getSurface());
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (vulkanRenderer != null) vulkanRenderer.onSurfaceDestroyed();
        else if (nativeInitialized) nativeDestroySurface();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (vulkanRenderer != null) vulkanRenderer.onSurfaceChanged(width, height);
        else nativeChangeSurface(width, height);
    }

    public void onDestroy() {
        xServer.windowManager.removeOnWindowModificationListener(this);
        xServer.pointer.removeOnPointerMotionListener(this);
        xServer.cursorManager.removeOnCursorModificationListener(this);
        if (vulkanRenderer != null) {
            vulkanRenderer.dispose();
            vulkanRenderer = null;
        } else if (nativeInitialized) {
            nativeStop();
        }
        if (vulkanEventThread != null) vulkanEventThread.quitSafely();
    }

    public void onPause() {
        if (vulkanRenderer != null) xServer.setRenderingEnabled(false);
        else nativePause();
    }

    public void onResume() {
        if (vulkanRenderer != null) xServer.setRenderingEnabled(true);
        else nativeResume();
    }

    public void requestRender() {
        if (vulkanRenderer != null) vulkanRenderer.requestRender();
        else nativeRequestRender();
    }

    public void toggleFullscreen() {
        fullscreen = !fullscreen;
        if (vulkanRenderer != null) vulkanRenderer.toggleFullscreen();
        else nativeToggleFullscreen();
    }

    public boolean isFullscreen() {
        return vulkanRenderer != null ? vulkanRenderer.isFullscreen() : fullscreen;
    }

    public void setCursorVisible(boolean cursorVisible) {
        this.cursorVisible = cursorVisible;
        if (vulkanRenderer != null) vulkanRenderer.setCursorVisible(cursorVisible);
        else nativeSetCursorVisible(cursorVisible);
    }

    public void setScreenOffsetYRelativeToCursor(boolean screenOffsetYRelativeToCursor) {
        if (vulkanRenderer != null) vulkanRenderer.setScreenOffsetYRelativeToCursor(screenOffsetYRelativeToCursor);
        else nativeSetScreenOffsetYRelativeToCursor(screenOffsetYRelativeToCursor);
    }

    public float getMagnifierZoom() {
        return magnifierZoom;
    }

    public void setMagnifierZoom(float magnifierZoom) {
        this.magnifierZoom = magnifierZoom;
        if (vulkanRenderer != null) vulkanRenderer.setMagnifierZoom(magnifierZoom);
        else nativeSetMagnifierZoom(magnifierZoom);
    }

    public void setUnviewableWMClass(String unviewableWMClass) {
        this.unviewableWMClass = unviewableWMClass;
        if (vulkanRenderer != null) vulkanRenderer.setUnviewableWMClasses(unviewableWMClass);
        else nativeSetUnviewableWMClass(unviewableWMClass);
    }

    /** Post-processing config as a KeyValueSet string, see {@link com.winlator.cmod.renderer.PostProcessing}. */
    public void setPostProcessing(String config) {
        postProcessing = config != null ? config : "";
        if (vulkanRenderer != null) vulkanRenderer.applyPostProcessing(postProcessing);
        else nativeSetPostProcessing(postProcessing);
    }

    public String getPostProcessing() {
        return postProcessing;
    }

    /**
     * Where the game window is drawn when it is fitted to the screen: out = {scale, offsetX, offsetY},
     * X screen point = game point * scale + offset. Returns false while no window is fitted.
     */
    public boolean getFitMapping(float[] out) {
        if (vulkanRenderer != null) {
            if (vulkanRenderer.surfaceWidth <= 0 || vulkanRenderer.surfaceHeight <= 0 || out.length < 3) return false;
            out[0] = vulkanRenderer.viewTransformation.aspect;
            out[1] = vulkanRenderer.viewTransformation.viewOffsetX;
            out[2] = vulkanRenderer.viewTransformation.viewOffsetY;
            return true;
        }
        return nativeGetFitMapping(out);
    }

    public void setFullscreenMode(int mode) {
        if (vulkanRenderer != null) vulkanRenderer.setFullscreenMode(mode);
    }

    public int getFullscreenMode() {
        return vulkanRenderer != null ? vulkanRenderer.getFullscreenMode() : fullscreen ? 1 : 0;
    }

    public void setScreenAlignment(int alignment) {
        if (vulkanRenderer != null) vulkanRenderer.setScreenAlignment(alignment);
    }

    @Override
    public void onCreateWindow(Window window, Window parent) {
        if (vulkanRenderer == null) nativeCreateWindow(window, parent != null ? parent.id : -1);
    }

    @Override
    public void onDestroyWindow(Window window) {
        synchronized (registeredDirectContents) {
            registeredDirectContents.removeIf(key -> (int) (key >> 32) == window.id);
        }
        if (vulkanRenderer == null) nativeDestroyWindow(window.id);
    }

    @Override
    public void onMapWindow(Window window) {
        if (unviewableWMClass != null) {
            String wmClass = window.getClassName();
            if (wmClass.contains(unviewableWMClass)) {
                if (window.attributes.isEnabled()) {
                    window.disableAllDescendants();
                }
            }
        }
        if (vulkanRenderer == null) nativeMapWindow(window.id);
    }

    @Override
    public void onUnmapWindow(Window window) {
        if (vulkanRenderer == null) nativeUnmapWindow(window.id);
    }

    @Override
    public void onChangeWindowZOrder(Window.StackMode stackMode, Window window, Window sibling) {
        if (vulkanRenderer == null)
            nativeChangeWindowZOrder(stackMode == Window.StackMode.ABOVE ? 1 : 0, window.id, (sibling != null) ? sibling.id : -1);
    }

    @Override
    public void onUpdateWindowContent(Window window) {
        if (vulkanRenderer == null) nativeUpdateWindowContent(window.id);
    }

    @Override
    public void onUpdateWindowContentDirect(Window window, Drawable drawable) {
        if (window == null || drawable == null) return;
        addDirectContent(window, drawable);
        if (vulkanRenderer == null) nativeUpdateDirectContent(window.id, drawable.id);
    }

    @Override
    public void onUpdateWindowGeometry(final Window window, boolean resized) {
        if (vulkanRenderer == null)
            nativeUpdateWindowGeometry(window.id, window.getWidth(), window.getHeight(), window.getX(), window.getY(), resized);
    }

    @Override
    public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) {
            Cursor cursor = window.attributes.getCursor();
            if (cursor != null && vulkanRenderer == null)
                nativeBindCursor(window.id, cursor.id, cursor.isVisible());
        }
    }

    @Override
    public void onModifyWindowProperty(Window window, Property property) {
        if (property.nameAsString().equals("WM_CLASS") && vulkanRenderer == null)
            nativeSetWindowClassName(window.id, property.toString());
    }

    @Override
    public void onReparentWindow(Window window, Window newParent) {
        if (newParent != null && vulkanRenderer == null)
            nativeReparentWindow(window.id, newParent.id, window.getX(), window.getY());
    }

    @Override
    public void onPointerMove(short x, short y) {
        // In relative mouse mode (games) or when the cursor is hidden, skip pointer updates so
        // high-polling-rate mice (500-1000 Hz) don't flood the JNI and render threads.
        if (vulkanRenderer != null || xServer.isRelativeMouseMovement() || !cursorVisible) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastPointerMoveTimeNs >= MIN_POINTER_MOVE_INTERVAL_NS) {
            lastPointerMoveTimeNs = now;
            nativePointerMove(x, y);
        }
    }

    @Override
    public void onCreateCursor(Cursor cursor) {
        if (vulkanRenderer == null) nativeCreateCursor(cursor);
    }

    @Override
    public void onFreeCursor(Cursor cursor) {
        if (vulkanRenderer == null) nativeFreeCursor(cursor.id);
    }

    static {
        System.loadLibrary("winlator");
    }

    @FastNative
    private native void nativeCreateSurface(Surface surface);
    @FastNative
    private native void nativeDestroySurface();
    @FastNative
    private native void nativeInit(Context context, XServer xserver);
    @FastNative
    private native void nativeChangeSurface(int width, int height);
    @FastNative
    private native void nativeCreateWindow(Window window, int parentId);
    @FastNative
    private native void nativeDestroyWindow(int id);
    @FastNative
    private native void nativeCreateCursor(Cursor cursor);
    @FastNative
    private native void nativeFreeCursor(int id);
    @FastNative
    private native void nativeBindCursor(int windowId, int cursorId, boolean visible);
    @FastNative
    private native void nativeMapWindow(int id);
    @FastNative
    private native void nativeUnmapWindow(int id);
    @FastNative
    private native void nativeChangeWindowZOrder(int stackMode, int id, int siblingId);
    @FastNative
    private native void nativeUpdateWindowGeometry(int id, int width, int height, int x, int y, boolean resized);
    @FastNative
    private native void nativePointerMove(int x, int y);
    @FastNative
    private native void nativeToggleFullscreen();
    @FastNative
    private native void nativeSetCursorVisible(boolean visible);
    @FastNative
    private native void nativeSetScreenOffsetYRelativeToCursor(boolean cond);
    @FastNative
    private native void nativeSetMagnifierZoom(float magnifierZoom);
    @FastNative
    private native void nativeSetUnviewableWMClass(String unviewableWMName);
    @FastNative
    private native void nativeSetWindowClassName(int id, String className);
    @FastNative
    private native void nativeSetPostProcessing(String config);
    @FastNative
    private native void nativeRequestRender();

    /** Frame generation: {multiplier (0 = off), game fps, shown fps, ms per generated frame}. */
    public static native float[] nativeGetFrameGenStats();

    /** Fit / Fill / Integer scaling and Center / Top / Bottom placement, see {@link com.winlator.cmod.renderer.ViewTransformation}. */
    public void setScaleMode(int mode, int alignment) {
        if (vulkanRenderer != null) {
            vulkanRenderer.setFullscreenMode(mode);
            vulkanRenderer.setScreenAlignment(alignment);
        } else {
            nativeSetScaleMode(mode, alignment);
        }
    }

    private native void nativeSetScaleMode(int mode, int alignment);

    private native boolean nativeGetFitMapping(float[] out);
    @FastNative
    private native void nativeUpdateWindowContent(int id);
    @FastNative
    private native void nativeReparentWindow(int id, int parentId, short x, short y);
    @FastNative
    private native void nativePause();
    @FastNative
    private native void nativeResume();
    @FastNative
    private native void nativeStop();
    @FastNative
    private native void nativeAddDirectContent(int windowId, Drawable drawable);
    @FastNative
    private native void nativeUpdateDirectContent(int windowId, int drawableId);
    @FastNative
    private native void nativeRemoveDirectContent(int windowId, int pixmapId);
}
