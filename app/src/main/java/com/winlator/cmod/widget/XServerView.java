package com.winlator.cmod.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.renderer.vulkan.VulkanRenderer;
import com.winlator.cmod.xserver.XServer;

@SuppressLint("ViewConstructor")
public class XServerView extends FrameLayout {
    private final GLSurfaceView glSurfaceView;
    private final GLRenderer renderer;
    private final XServer xServer;
    private SurfaceView vulkanSurfaceView;
    private VulkanRenderer vulkanRenderer;

    public XServerView(Context context, XServer xServer) {
        super(context);
        this.xServer = xServer;
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        glSurfaceView = new GLSurfaceView(context);
        glSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        glSurfaceView.setEGLContextClientVersion(3);
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        glSurfaceView.setPreserveEGLContextOnPause(true);
        renderer = new GLRenderer(this, xServer);
        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        addView(glSurfaceView);
    }

    public void initVulkanRenderer(boolean nativeMode) {
        if (vulkanRenderer != null) {
            throw new IllegalStateException("Vulkan renderer is already initialized");
        }

        vulkanSurfaceView = new SurfaceView(getContext());
        vulkanSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        vulkanSurfaceView.setZOrderMediaOverlay(true);
        try {
            vulkanRenderer = new VulkanRenderer(this, xServer);
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.e("XServerView", "Unable to load Vulkan renderer", e);
            Toast.makeText(getContext(), "Vulkan renderer could not be loaded; using OpenGL", Toast.LENGTH_LONG).show();
            removeView(vulkanSurfaceView);
            vulkanSurfaceView = null;
            return;
        }
        vulkanRenderer.setInitialNativeMode(nativeMode);
        vulkanSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                vulkanRenderer.onSurfaceCreated(holder.getSurface());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                vulkanRenderer.onSurfaceChanged(width, height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                vulkanRenderer.onSurfaceDestroyed();
            }
        });
        addView(vulkanSurfaceView);
        WinlatorHUD hud = xServer.getWinlatorHUD();
        if (hud != null) hud.setFrameGenerationRenderer(vulkanRenderer);
    }

    public void setApexMode(boolean active) {
        glSurfaceView.setRenderMode(active ? GLSurfaceView.RENDERMODE_CONTINUOUSLY : GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        if (active) {
            renderer.startChoreographer();
        } else {
            renderer.stopChoreographer();
        }
    }

    public GLRenderer getRenderer() {
        return renderer;
    }

    public VulkanRenderer getVulkanRenderer() {
        return vulkanRenderer;
    }

    public Object getSurfaceControl() {
        if (android.os.Build.VERSION.SDK_INT < 29) return null;
        if (vulkanSurfaceView != null) return vulkanSurfaceView.getSurfaceControl();
        return glSurfaceView.getSurfaceControl();
    }

    public void onVulkanInitializationFailed(String reason) {
        post(() -> {
            if (vulkanRenderer == null) return;
            WinlatorHUD hud = xServer.getWinlatorHUD();
            if (hud != null) hud.setFrameGenerationRenderer(null);
            vulkanRenderer.dispose();
            if (vulkanSurfaceView != null) removeView(vulkanSurfaceView);
            vulkanRenderer = null;
            vulkanSurfaceView = null;
            xServer.setDisplayDriver("opengl");
            if (xServer.getWinlatorHUD() != null) {
                xServer.getWinlatorHUD().setDisplayDriver("opengl");
            }
            Toast.makeText(getContext(), "Vulkan renderer failed: " + reason + ". Using OpenGL.", Toast.LENGTH_LONG).show();
            requestRender();
        });
    }

    public void onDestroy() {
        WinlatorHUD hud = xServer.getWinlatorHUD();
        if (hud != null) hud.setFrameGenerationRenderer(null);
        if (vulkanRenderer != null) {
            vulkanRenderer.dispose();
            vulkanRenderer = null;
        }
    }

    public void prepareForGuestShutdown() {
        if (vulkanRenderer != null) vulkanRenderer.prepareForGuestShutdown();
    }

    public boolean isVulkanRendererActive() {
        return vulkanRenderer != null;
    }

    public void requestRender() {
        glSurfaceView.requestRender();
        if (vulkanRenderer != null) vulkanRenderer.requestRender();
    }

    public void queueEvent(Runnable runnable) {
        glSurfaceView.queueEvent(runnable);
    }

    public void onPause() {
        glSurfaceView.onPause();
    }

    public void onResume() {
        glSurfaceView.onResume();
    }

    public void setCursorVisible(boolean visible) {
        renderer.setCursorVisible(visible);
        if (vulkanRenderer != null) vulkanRenderer.setCursorVisible(visible);
    }

    public void setUnviewableWMClasses(String... classes) {
        renderer.setUnviewableWMClasses(classes);
        if (vulkanRenderer != null) vulkanRenderer.setUnviewableWMClasses(String.join(",", classes));
    }

    public void setFrameRating(Object frameRating) {
        if (frameRating instanceof WinlatorHUD) renderer.setWinlatorHUD((WinlatorHUD) frameRating);
        if (frameRating instanceof WinlatorHUD)
            ((WinlatorHUD) frameRating).setFrameGenerationRenderer(vulkanRenderer);
        if (vulkanRenderer != null) {
            vulkanRenderer.setFrameRating(frameRating);
            if (frameRating instanceof WinlatorHUD) {
                WinlatorHUD hud = (WinlatorHUD) frameRating;
                vulkanRenderer.setHudFrameTick(windowId -> hud.onFrame());
            }
        }
    }

    public void toggleFullscreen() {
        renderer.toggleFullscreen();
        if (vulkanRenderer != null) vulkanRenderer.toggleFullscreen();
    }

    public boolean isFullscreen() {
        return vulkanRenderer != null ? vulkanRenderer.isFullscreen() : renderer.isFullscreen();
    }

    public void setFullscreenMode(int mode) {
        if (vulkanRenderer != null) vulkanRenderer.setFullscreenMode(mode);
        boolean shouldBeFullscreen = mode != Container.FULLSCREEN_OFF;
        if (renderer.isFullscreen() != shouldBeFullscreen) renderer.toggleFullscreen();
    }

    public void setScreenOffsetYRelativeToCursor(boolean enabled) {
        renderer.setScreenOffsetYRelativeToCursor(enabled);
        if (vulkanRenderer != null) vulkanRenderer.setScreenOffsetYRelativeToCursor(enabled);
    }

    public void setMagnifierZoom(float zoom) {
        renderer.setMagnifierZoom(zoom);
        if (vulkanRenderer != null) vulkanRenderer.setMagnifierZoom(zoom);
    }

    public float getMagnifierZoom() {
        return vulkanRenderer != null ? vulkanRenderer.getMagnifierZoom() : renderer.getMagnifierZoom();
    }

    public void setFpsLimit(int fpsLimit) {
        renderer.setFpsLimit(fpsLimit);
        if (vulkanRenderer != null) vulkanRenderer.setFpsLimit(fpsLimit);
    }
}
