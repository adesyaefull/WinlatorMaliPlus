package com.winlator.cmod.renderer;

import com.winlator.cmod.widget.XServerView;

public interface HostRenderer {
    XServerView getXServerView();
    void setRenderingEnabled(boolean enabled);
    void requestRender();
    void forceCleanup();
    void setCursorVisible(boolean visible);
    boolean isCursorVisible();
    void setUnviewableWMClasses(String wmClasses);
    void setFilterMode(int mode);
    void setMagnifierZoom(float zoom);
    float getMagnifierZoom();
    void toggleFullscreen();
    boolean isFullscreen();
    // Fit/Stretch/Fill/Integer mode, matching ViewTransformation and the shared settings picker.
    // isFullscreen() is true only for Stretch, which uses the fullscreen scanout path.
    void setFullscreenMode(int mode);
    int getFullscreenMode();
    // Screen alignment (issue #413): Container.ALIGN_CENTER/TOP/BOTTOM — vertical placement of the
    // letterbox rect. CENTER is the historical behavior; only moves the bar, never the scale.
    void setScreenAlignment(int alignment);
    int getScreenAlignment();
    void setScreenOffsetYRelativeToCursor(boolean b);
    boolean isScreenOffsetYRelativeToCursor();
    void setFpsWindowId(int id);
    void setFrameRating(Object fr);
    int getFpsLimit();
    void setFpsLimit(int limit);
    int getSurfaceWidth();
    int getSurfaceHeight();
}
