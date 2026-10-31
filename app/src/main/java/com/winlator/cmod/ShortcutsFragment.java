package com.winlator.cmod;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.util.TypedValue;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.cmod.contentdialog.ShortcutSettingsDialog;
import com.winlator.cmod.box64.Box64Preset;
import com.winlator.cmod.box64.Box64PresetManager;
import com.winlator.cmod.contents.WrapperCatalogManager;
import com.winlator.cmod.fexcore.FEXCorePreset;
import com.winlator.cmod.fexcore.FEXCorePresetManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.CommunityConfigManager;
import com.winlator.cmod.core.CommunityConfigUtils;
import com.winlator.cmod.core.CoverArtManager;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.ImageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.winlator.cmod.core.steamgrid.SteamGridDBApi;
import com.winlator.cmod.core.steamgrid.SteamGridGameDetailsResponse;
import com.winlator.cmod.core.steamgrid.SteamGridSearchResponse;

public class ShortcutsFragment extends Fragment {
    private ContainerManager manager;
    private RecyclerView recyclerView;
    private TextView dxvkDetails;
    private TextView rendererDetails;
    private TextView frameGenDetails;
    private TextView wrapperDetails;
    private TextView fexDetails;
    private TextView box64Details;
    private Shortcut currentShortcut;
    private long currentCoverArtSelectionVersion = -1;
    private Shortcut selectedShortcut;
    private ShortcutsAdapter adapter;
    private final Set<String> coverArtRequests = new HashSet<>();
    private final Set<String> coverArtPromptsShown = new HashSet<>();
    private final Map<String, Integer> coverArtRetryCounts = new HashMap<>();
    private final Set<String> pendingShortcutImageUpdates = new HashSet<>();
    private int shortcutCardWidthPx;
    private int shortcutCoverHeightPx;
    private boolean isLaunchingShortcut;
    private static long lastShortcutLaunchTime;
    private LinearSnapHelper snapHelper;
    private RecyclerView.OnScrollListener snapScrollListener;
    private View carouselArea;
    private View.OnLayoutChangeListener carouselLayoutChangeListener;
    private int lastCarouselWidth;
    private int lastCarouselHeight;
    private Drawable previousToolbarBackground;
    private static final int REQUEST_CODE_CUSTOM_COVER_ART = 1;
    private static final int REQUEST_CODE_CUSTOM_HERO_ART = 6;
    private static final int REQUEST_CODE_IMPORT_SHORTCUT = 1002;
    private static final int REQUEST_CODE_ADD_GAME_EXE = 1003;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (getActivity() instanceof MainActivity) {
            manager = ((MainActivity) getActivity()).getContainerManager();
        } else {
            manager = new ContainerManager(getContext());
        }
    }

    private void refreshShortcutCoverArtCards() {
        if (adapter != null && adapter.data != null) {
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.shortcuts_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ThemeManager.applyThemeToView(view, getContext());
        androidx.appcompat.app.ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.shortcut_library_title);
        }
        Toolbar toolbar = view.findViewById(R.id.Toolbar);
        if (toolbar == null && getActivity() != null) toolbar = getActivity().findViewById(R.id.Toolbar);
        if (toolbar != null) {
            previousToolbarBackground = toolbar.getBackground();
            toolbar.setBackgroundResource(R.drawable.shortcut_toolbar_glass);
            toolbar.setTitleTextColor(Color.WHITE);
        }

        recyclerView = view.findViewById(R.id.RecyclerView);
        recyclerView.setItemAnimator(null);
        carouselArea = view.findViewById(R.id.CarouselArea);
        carouselLayoutChangeListener = (changedView, left, top, right, bottom,
                                       oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            int height = bottom - top;
            if ((width != lastCarouselWidth || height != lastCarouselHeight)
                    && width > 0 && height > 0) {
                lastCarouselWidth = width;
                lastCarouselHeight = height;
                changedView.post(this::refreshCarouselLayout);
            }
        };
        carouselArea.addOnLayoutChangeListener(carouselLayoutChangeListener);
        dxvkDetails = view.findViewById(R.id.TVDxvkDetails);
        rendererDetails = view.findViewById(R.id.TVRendererDetails);
        frameGenDetails = view.findViewById(R.id.TVFrameGenDetails);
        wrapperDetails = view.findViewById(R.id.TVWrapperDetails);
        fexDetails = view.findViewById(R.id.TVFEXDetails);
        box64Details = view.findViewById(R.id.TVBox64Details);
        updateGridLayout();
        recyclerView.post(this::updateGridLayout);

        loadShortcutsList();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.shortcuts_menu, menu);

        if (getContext() != null) {
            int accent = ThemeManager.getAccentColor(getContext());
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item.getIcon() != null) {
                    Drawable icon = item.getIcon().mutate();
                    icon.setTint(accent);
                    item.setIcon(icon);
                }
            }
        }

        MenuItem searchItem = menu.findItem(R.id.shortcuts_menu_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.search));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        if (adapter != null) adapter.filter(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (adapter != null) adapter.filter(newText);
                        return true;
                    }
                });
            }
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    if (adapter != null) adapter.filter("");
                    return true;
                }
            });
        }
        MenuItem landscapeItem = menu.findItem(R.id.shortcuts_menu_toggle_landscape);
        if (landscapeItem != null && getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            landscapeItem.setTitle("Portrait mode");
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.shortcuts_menu_import) {
            importShortcutWorkflow();
            return true;
        } else if (id == R.id.shortcuts_menu_sort) {
            showSortDialog();
            return true;
        } else if (id == R.id.shortcuts_menu_toggle_landscape) {
            Activity activity = getActivity();
            if (activity != null) {
                activity.setRequestedOrientation(getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE
                        ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
            return true;
        } else if (id == R.id.shortcuts_menu_refresh) {
            loadShortcutsList();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortDialog() {
        Context context = getContext();
        if (context == null) return;
        String[] sortOptions = {
            "Name (A to Z)",
            "Name (Z to A)",
            "Most Played",
            "Longest Playtime",
            "Container Name"
        };
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        ContentDialog.showSingleChoiceList(context, "Sort Shortcuts", sortOptions, (index) -> {
            prefs.edit().putInt("shortcuts_sort_order", index).apply();
            if (adapter != null) {
                adapter.setSortOrder(index);
            }
        });
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (recyclerView != null) {
            recyclerView.postOnAnimation(() ->
                    recyclerView.postOnAnimation(this::refreshCarouselLayout));
        }
    }

    private void refreshCarouselLayout() {
        if (recyclerView == null || carouselArea == null || carouselArea.getWidth() == 0) return;
        updateGridLayout();
        if (adapter != null) adapter.notifyDataSetChanged();
        recyclerView.post(this::centerSelectedShortcut);
    }

    @Override
    public void onDestroyView() {
        if (getActivity() instanceof AppCompatActivity) {
            androidx.appcompat.app.ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) actionBar.setTitle("");
            Toolbar toolbar = getActivity().findViewById(R.id.Toolbar);
            if (toolbar != null && previousToolbarBackground != null) {
                toolbar.setBackground(previousToolbarBackground);
            }
        }
        if (snapHelper != null) {
            snapHelper.attachToRecyclerView(null);
            snapHelper = null;
        }
        if (recyclerView != null && snapScrollListener != null) {
            recyclerView.removeOnScrollListener(snapScrollListener);
        }
        snapScrollListener = null;
        if (carouselArea != null && carouselLayoutChangeListener != null) {
            carouselArea.removeOnLayoutChangeListener(carouselLayoutChangeListener);
        }
        carouselLayoutChangeListener = null;
        carouselArea = null;
        dxvkDetails = null;
        rendererDetails = null;
        frameGenDetails = null;
        wrapperDetails = null;
        fexDetails = null;
        box64Details = null;
        recyclerView = null;
        previousToolbarBackground = null;
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        isLaunchingShortcut = false;
        if (manager != null) manager.loadContainers();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
            loadShortcutsList();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        com.winlator.cmod.container.Shortcut.setOnShortcutLoadedListener(null);
    }

    private void updateGridLayout() {
        if (recyclerView == null) return;
        Configuration config = getResources().getConfiguration();
        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        float maxCardDp = landscape ? 190f : 180f;
        int contentHeight = carouselArea != null ? carouselArea.getHeight() : 0;
        int contentWidth = carouselArea != null && carouselArea.getWidth() > 0
                ? carouselArea.getWidth() : screenWidth;
        float availableWidthDp = contentWidth / density;
        if (landscape) {
            maxCardDp = Math.min(200f, Math.max(170f, availableWidthDp * 0.23f));
        } else {
            maxCardDp = Math.min(maxCardDp, availableWidthDp * 0.62f);
        }
        float availableHeightDp = contentHeight > 0
                ? contentHeight / density
                : Math.max(160f, screenHeight / density - 112f);
        float maxCardHeightDp = Math.max(100f, availableHeightDp - 96f);
        float cardWidthDp = landscape ? maxCardDp : Math.min(maxCardDp, maxCardHeightDp);
        int sideInsetPx = Math.round(16f * density);
        int cardWidth = Math.max(1, Math.min(Math.round(cardWidthDp * density), contentWidth - sideInsetPx * 2));
        shortcutCardWidthPx = cardWidth;
        float coverHeightDp = landscape
                ? Math.min(cardWidthDp * 0.95f, Math.max(80f, availableHeightDp - 112f))
                : Math.min(cardWidthDp, Math.max(120f, availableHeightDp - 170f));
        coverHeightDp += landscape ? 60f : 83f;
        shortcutCoverHeightPx = Math.round(coverHeightDp * density);
        int cardSlotWidth = cardWidth + Math.round(16f * density);
        int recyclerWidth = landscape
                ? Math.min(contentWidth, cardSlotWidth * 3)
                : contentWidth;
        ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
        params.width = recyclerWidth;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        recyclerView.setLayoutParams(params);
        recyclerView.setTranslationY(0f);
        int centerPadding = Math.max(0, (recyclerWidth - cardSlotWidth) / 2);
        recyclerView.setPadding(centerPadding, Math.round(16f * density),
                centerPadding, Math.round(16f * density));
        recyclerView.setClipToPadding(false);
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm instanceof LinearLayoutManager
                && ((LinearLayoutManager) lm).getOrientation() == LinearLayoutManager.HORIZONTAL) {
            ((LinearLayoutManager) lm).setOrientation(LinearLayoutManager.HORIZONTAL);
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        }
        if (snapHelper == null) {
            snapHelper = new LinearSnapHelper();
            snapHelper.attachToRecyclerView(recyclerView);
            snapScrollListener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView view, int newState) {
                    if (newState != RecyclerView.SCROLL_STATE_IDLE || snapHelper == null) return;
                    View snappedView = snapHelper.findSnapView(view.getLayoutManager());
                    if (snappedView == null || adapter == null) return;
                    int position = view.getChildAdapterPosition(snappedView);
                    if (position >= 0 && position < adapter.data.size()) {
                        adapter.selectShortcut(adapter.data.get(position));
                    }
                }

                @Override
                public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                    applyCarouselEffects();
                }
            };
            recyclerView.addOnScrollListener(snapScrollListener);
        }
        recyclerView.invalidateItemDecorations();
        centerSelectedShortcut();
    }

    private void centerSelectedShortcut() {
        if (recyclerView == null || adapter == null || selectedShortcut == null) return;
        int position = adapter.data.indexOf(selectedShortcut);
        if (position >= 0 && recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(
                    position, 0);
            recyclerView.post(() -> {
                if (adapter != null) adapter.centerShortcutIfNeeded(position);
                applyCarouselEffects();
                updateSelectedShortcutDetails();
            });
        }
    }

    private void applyCarouselEffects() {
        if (recyclerView == null) return;
        float center = (recyclerView.getPaddingLeft()
                + recyclerView.getWidth() - recyclerView.getPaddingRight()) / 2f;
        float maxDistance = Math.max(1, shortcutCardWidthPx + recyclerView.getPaddingLeft());
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            float childCenter = (child.getLeft() + child.getRight()) / 2f;
            float distance = Math.min(1f, Math.abs(childCenter - center) / maxDistance);
            float easedDistance = distance * distance * (3f - 2f * distance);
            float blurRadius = easedDistance * 6.6f * density;
            float scale = 1f - distance * 0.12f;
            child.setScaleX(scale);
            child.setScaleY(scale);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                View blurTarget = holder instanceof ShortcutsAdapter.ViewHolder
                        ? ((ShortcutsAdapter.ViewHolder) holder).innerArea : child;
                blurTarget.setRenderEffect(blurRadius > 0.05f
                        ? RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP) : null);
            }
            child.setAlpha(1f - distance * 0.4f);
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
            if (holder instanceof ShortcutsAdapter.AddGameViewHolder) {
                ShortcutsAdapter.AddGameViewHolder addHolder =
                        (ShortcutsAdapter.AddGameViewHolder) holder;
                addHolder.card.setEnabled(true);
                addHolder.card.setClickable(true);
                addHolder.card.setFocusable(true);
            }
        }
    }

    private void updateSelectedShortcutDetails() {
        if (dxvkDetails == null || rendererDetails == null || frameGenDetails == null
                || wrapperDetails == null || fexDetails == null || box64Details == null) return;
        if (selectedShortcut == null || selectedShortcut.container == null) {
            dxvkDetails.setText("");
            rendererDetails.setText("");
            frameGenDetails.setText("");
            wrapperDetails.setText("");
            fexDetails.setText("");
            box64Details.setText("");
            return;
        }

        Shortcut shortcut = selectedShortcut;
        Container container = shortcut.container;
        Context context = getContext();
        if (context == null) return;

        String dxWrapper = shortcut.getExtra("dxwrapper", container.getDXWrapper());
        String dxConfig = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());
        String dxVersion = getConfigValue(dxConfig, "version");
        String dxLabel = dxWrapper.replace('+', ' ').trim();
        if (!dxVersion.isEmpty()) dxLabel += " " + dxVersion;
        dxvkDetails.setText(getString(R.string.shortcut_dxvk_details,
                dxLabel.isEmpty() ? "Default" : dxLabel));

        String renderer = shortcut.getExtra("renderer", container.getRenderer());
        String rendererDriver = shortcut.getExtra("rendererDriverId",
                container.getExtra("rendererDriverId", ""));
        String rendererLabel = "vulkan".equalsIgnoreCase(renderer) ? "Vulkan" : "OpenGL";
        if (container.isRendererNative()) rendererLabel += " · Native";
        if (!rendererDriver.isEmpty()) rendererLabel += " · " + rendererDriver;
        String colorOrder = shortcut.getExtra("rendererColorOrder",
                container.getExtra("rendererColorOrder", ""));
        String presentMode = shortcut.getExtra("rendererPresentMode",
                container.getExtra("rendererPresentMode", ""));
        if (!colorOrder.isEmpty()) rendererLabel += " · " + colorOrder.toUpperCase(java.util.Locale.ROOT);
        if (!presentMode.isEmpty()) rendererLabel += " · " + presentMode;
        rendererDetails.setText(getString(R.string.shortcut_renderer_details, rendererLabel));

        boolean frameGenEnabled = "1".equals(shortcut.getExtra("frameGenEnabled",
                container.getExtra("frameGenEnabled", "0")));
        String engine = shortcut.getExtra("frameGenEngine",
                container.getExtra("frameGenEngine", "lsfg"));
        String multiplier = shortcut.getExtra("frameGenMultiplier",
                container.getExtra("frameGenMultiplier", "2"));
        String frameGenLabel = frameGenEnabled
                ? ("winfg".equalsIgnoreCase(engine) ? "Win-FG" : "LSFG") + " ×" + multiplier
                : getString(R.string.disabled);
        frameGenDetails.setText(getString(R.string.shortcut_frame_gen_details, frameGenLabel));

        String wrapperId = shortcut.getExtra("graphicsDriver", container.getGraphicsDriver());
        String wrapperName = WrapperCatalogManager.getInstalledWrapperName(context, wrapperId);
        wrapperDetails.setText(getString(R.string.shortcut_wrapper_details, wrapperName));

        String fexVersion = shortcut.getExtra("fexcoreVersion", container.getFEXCoreVersion());
        String fexPresetId = shortcut.getExtra("fexcorePreset", container.getFEXCorePreset());
        FEXCorePreset fexPreset = fexPresetId == null || fexPresetId.isEmpty()
                ? null : FEXCorePresetManager.getPreset(context, fexPresetId);
        String fexPresetName = fexPreset != null ? fexPreset.name : displayValue(fexPresetId);
        fexDetails.setText(getString(R.string.shortcut_fex_details,
                displayValue(fexVersion), fexPresetName));

        String box64Version = shortcut.getExtra("box64Version", container.getBox64Version());
        String box64PresetId = shortcut.getExtra("box64Preset", container.getBox64Preset());
        Box64Preset box64Preset = box64PresetId == null || box64PresetId.isEmpty()
                ? null : Box64PresetManager.getPreset("box64", context, box64PresetId);
        String box64PresetName = box64Preset != null
                ? box64Preset.name : displayValue(box64PresetId);
        box64Details.setText(getString(R.string.shortcut_box64_details,
                displayValue(box64Version), box64PresetName));
    }

    private String displayValue(String value) {
        return value == null || value.trim().isEmpty() ? "Default" : value;
    }

    private void requestCloudCoverArt(Shortcut shortcut) {
        if (shortcut == null || shortcut.container == null
                || shortcut.hasPrimaryCoverArt()
                || !shortcut.getExtra("coverArtSelectionPending").isEmpty()
                || getContext() == null) return;
        String requestKey = shortcut.file.getAbsolutePath();
        synchronized (coverArtRequests) {
            if (!coverArtRequests.add(requestKey)) return;
        }

        CoverArtManager.fetchDefaultCoverArt(getContext(), shortcut, new CoverArtManager.DownloadCallback() {
            @Override
            public void onCompleted(android.graphics.Bitmap bitmap) {
                synchronized (coverArtRequests) {
                    coverArtRequests.remove(requestKey);
                    coverArtRetryCounts.remove(requestKey);
                }
                Activity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded() || adapter == null) return;
                    int position = adapter.data.indexOf(shortcut);
                    if (position >= 0) adapter.notifyItemChanged(position);
                    if (selectedShortcut == shortcut) updateSelectedShortcutDetails();
                });
            }

            @Override
            public void onFailed(CoverArtManager.ErrorReason reason) {
                boolean retry;
                synchronized (coverArtRequests) {
                    coverArtRequests.remove(requestKey);
                    int attempts = coverArtRetryCounts.getOrDefault(requestKey, 0);
                    retry = (reason == CoverArtManager.ErrorReason.NETWORK_UNAVAILABLE
                            || reason == CoverArtManager.ErrorReason.UNKNOWN) && attempts < 2;
                    if (retry) coverArtRetryCounts.put(requestKey, attempts + 1);
                }
                android.util.Log.w("ShortcutsFragment",
                        "Unable to fetch cloud cover art for " + shortcut.name + ": " + reason);
                if (retry && recyclerView != null) {
                    recyclerView.postDelayed(() -> {
                        if (isAdded() && !shortcut.hasPrimaryCoverArt()
                                && shortcut.getExtra("coverArtSelectionPending").isEmpty()) {
                            requestCloudCoverArt(shortcut);
                        }
                    }, 3000L);
                }
            }
        });
    }

    private String getConfigValue(String config, String key) {
        if (config == null || config.isEmpty()) return "";
        for (String entry : config.split(",")) {
            String[] pair = entry.trim().split("=", 2);
            if (pair.length == 2 && key.equalsIgnoreCase(pair[0].trim())) return pair[1].trim();
        }
        return "";
    }

    private final java.util.concurrent.ExecutorService shortcutLoaderExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    public void loadShortcutsList() {
        com.winlator.cmod.container.Shortcut.setOnShortcutLoadedListener(shortcut -> {
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    notifyShortcutImageUpdated(shortcut);
                });
            }
        });

        shortcutLoaderExecutor.execute(() -> {
            ArrayList<Shortcut> shortcuts = manager.loadShortcuts();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (recyclerView != null) {
                        adapter = new ShortcutsAdapter(shortcuts);
                        recyclerView.setAdapter(adapter);
                        flushPendingShortcutImageUpdates();
                        updateGridLayout();
                        centerSelectedShortcut();
                        updateSelectedShortcutDetails();
                        adapter.updateEmptyState();
                        for (Shortcut shortcut : adapter.data) {
                            String coverArtPending = shortcut.getExtra("coverArtSelectionPending");
                            if (("1".equals(coverArtPending) || "local-selection".equals(coverArtPending))
                                    && coverArtPromptsShown.add(shortcut.file.getAbsolutePath())) {
                                shortcut.putExtra("coverArtSelectionPending", "prompted");
                                shortcut.saveData();
                                showSearchPrompt(shortcut, shortcut.name, false);
                            } else {
                                requestCloudCoverArt(shortcut);
                            }
                        }
                    }
                });
            }
        });
    }

    private void notifyShortcutImageUpdated(Shortcut shortcut) {
        if (shortcut == null || shortcut.file == null) return;
        String path = shortcut.file.getAbsolutePath();
        if (adapter != null && adapter.data != null) {
            for (int i = 0; i < adapter.data.size(); i++) {
                Shortcut current = adapter.data.get(i);
                if (current != null && current.file != null
                        && path.equals(current.file.getAbsolutePath())) {
                    current.copyCoverArtFrom(shortcut);
                    adapter.notifyItemChanged(i);
                    return;
                }
            }
        }
        synchronized (pendingShortcutImageUpdates) {
            pendingShortcutImageUpdates.add(path);
        }
    }

    private void flushPendingShortcutImageUpdates() {
        if (adapter == null || adapter.data == null) return;
        synchronized (pendingShortcutImageUpdates) {
            if (pendingShortcutImageUpdates.isEmpty()) return;
            for (int i = 0; i < adapter.data.size(); i++) {
                Shortcut shortcut = adapter.data.get(i);
                if (shortcut != null && shortcut.file != null
                        && pendingShortcutImageUpdates.remove(shortcut.file.getAbsolutePath())) {
                    adapter.notifyItemChanged(i);
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CUSTOM_COVER_ART && currentShortcut != null) {
            Shortcut shortcut = currentShortcut;
            long selectionVersion = currentCoverArtSelectionVersion;
            try {
                if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
                    if (shortcut.cancelCoverArtSelection(selectionVersion)) {
                        requestCloudCoverArt(shortcut);
                    }
                    return;
                }
                Uri selectedImage = data.getData();
                Bitmap bitmap = ImageUtils.getBitmapFromUri(requireContext(), selectedImage, 1600);
                if (bitmap == null || !shortcut.saveCustomCoverArt(bitmap, selectionVersion)) {
                    boolean selectionStillCurrent =
                            shortcut.getCoverArtVersion() == selectionVersion;
                    boolean cleared = shortcut.cancelCoverArtSelection(selectionVersion);
                    if (!selectionStillCurrent) return;
                    android.util.Log.e("ShortcutsFragment",
                            "Unable to save selected cover art for " + shortcut.name);
                    if (cleared) requestCloudCoverArt(shortcut);
                    Toast.makeText(getContext(), "Failed to update cover art.", Toast.LENGTH_SHORT).show();
                } else {
                    notifyShortcutImageUpdated(shortcut);
                    refreshShortcutCoverArtCards();
                    if (selectedShortcut != null && selectedShortcut.file.equals(shortcut.file)) {
                        updateSelectedShortcutDetails();
                    }
                    Toast.makeText(getContext(), "Cover art updated.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                android.util.Log.e("ShortcutsFragment", "Failed to load selected cover art.", e);
                boolean cleared = shortcut.cancelCoverArtSelection(selectionVersion);
                if (cleared) {
                    requestCloudCoverArt(shortcut);
                    Toast.makeText(getContext(), "Failed to update cover art.", Toast.LENGTH_SHORT).show();
                }
            } finally {
                currentShortcut = null;
                currentCoverArtSelectionVersion = -1;
            }
        } else if (requestCode == REQUEST_CODE_CUSTOM_HERO_ART && resultCode == Activity.RESULT_OK && data != null && currentShortcut != null) {
            Uri selectedImage = data.getData();
            try {
                android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), selectedImage);
                currentShortcut.saveCustomHeroArt(bitmap);
                loadShortcutsList();
                Toast.makeText(getContext(), "Loading screen art updated.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getContext(), "Failed to update loading screen art.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_IMPORT_SHORTCUT && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                handleImportedFile(uri);
            }
        } else if (requestCode == REQUEST_CODE_ADD_GAME_EXE
                && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) createGameShortcut(uri);
        }
    }

    private void browseForGameExecutable() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, REQUEST_CODE_ADD_GAME_EXE);
        } catch (Exception e) {
            Toast.makeText(getContext(), "No file browser found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void createGameShortcut(Uri uri) {
        Context context = getContext();
        if (context == null || manager == null) return;
        String selectedName = getDisplayName(uri);
        if (selectedName == null || !selectedName.toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) {
            Toast.makeText(context, "Select a Windows .exe file.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<Container> containers = new ArrayList<>(manager.getContainers());
        if (containers.isEmpty()) {
            ContentDialog.alert(context, "No containers available. Please create a container first.", null);
            return;
        }
        String[] names = new String[containers.size()];
        for (int i = 0; i < containers.size(); i++) names[i] = containers.get(i).getName();
        ContentDialog.showSingleChoiceList(context, "Target Container", names,
                index -> {
                    Container container = containers.get(index);
                    File source = resolveSelectedExecutable(uri);
                    if (source != null) {
                        createShortcutForExecutable(source, container);
                    } else {
                        android.util.Log.w("ShortcutsFragment",
                                "Selected executable URI has no direct filesystem path; copying into the container");
                        copyGameIntoContainer(uri, selectedName, container);
                    }
                });
    }

    @Nullable
    private File resolveSelectedExecutable(Uri uri) {
        Context context = getContext();
        if (context == null) return null;
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            File file = new File(uri.getPath());
            return file.isFile() ? file : null;
        }

        String path = com.winlator.cmod.core.FileUtils.getFilePathFromUriUsingSAF(context, uri);
        if (path == null || path.isEmpty()) return null;
        File file = new File(path);
        return file.isFile() ? file : null;
    }

    private void createShortcutForExecutable(File executable, Container container) {
        Context context = getContext();
        if (context == null) return;
        if (!executable.isFile()) {
            Toast.makeText(context, "The selected executable is no longer available.", Toast.LENGTH_LONG).show();
            return;
        }
        String displayName = executable.getName();
        if (displayName.toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) {
            displayName = displayName.substring(0, displayName.length() - 4);
        }
        displayName = displayName.trim();
        if (displayName.isEmpty()) displayName = "Game";

        File desktopDir = container.getDesktopDir();
        if (!desktopDir.exists() && !desktopDir.mkdirs()) {
            Toast.makeText(context, "Could not create the container desktop folder.", Toast.LENGTH_LONG).show();
            return;
        }
        String uniqueName = displayName;
        for (int suffix = 2; new File(desktopDir, uniqueName + ".desktop").exists(); suffix++) {
            uniqueName = displayName + " (" + suffix + ")";
        }
        String windowsPath = executableToWindowsPath(executable, container);
        String workingDirectory = executableWorkingDirectory(executable, container);
        File desktopFile = new File(desktopDir, uniqueName + ".desktop");
        String winePrefix = getContainerWineHome(container) + "/.wine";
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(desktopFile))) {
            writer.println("[Desktop Entry]");
            writer.println("Name=" + uniqueName);
            writer.println("Exec=env WINEPREFIX=\"" + winePrefix + "\" wine "
                    + com.winlator.cmod.core.StringUtils.escapeFileDOSPath(windowsPath));
            writer.println("Type=Application");
            if (!workingDirectory.isEmpty()) writer.println("Path=" + workingDirectory);
            writer.println("Icon=" + uniqueName);
            writer.println("container_id:" + container.id);
            writer.println();
            writer.println("[Extra Data]");
            writer.println("coverArtSelectionPending=1");
        } catch (java.io.IOException e) {
            android.util.Log.e("ShortcutsFragment", "Unable to create direct game shortcut", e);
            Toast.makeText(context, "Failed to create the game shortcut.", Toast.LENGTH_LONG).show();
            return;
        }

        File iconDir = container.getIconsDir(64);
        if (!iconDir.exists() && !iconDir.mkdirs()) {
            android.util.Log.w("ShortcutsFragment", "Unable to create icon directory: " + iconDir);
        } else {
            com.winlator.cmod.core.ExeIconExtractor.extractAsync(executable,
                    new File(iconDir, uniqueName + ".png"), false, null);
        }
        Toast.makeText(context, "Game shortcut added using the executable's original folder.",
                Toast.LENGTH_LONG).show();
        loadShortcutsList();
    }

    private String executableToWindowsPath(File executable, Container container) {
        String path = executable.getAbsolutePath();
        File driveC = new File(container.getRootDir(), ".wine/drive_c");
        String driveCPath = driveC.getAbsolutePath();
        if (isSameOrChildPath(path, driveCPath)) {
            return "C:\\" + path.substring(driveCPath.length())
                    .replace(File.separatorChar, '\\').replaceFirst("^\\\\", "");
        }
        for (String[] drive : container.drivesIterator()) {
            if (drive == null || drive.length < 2 || drive[0] == null || drive[1] == null) continue;
            String driveRoot = new File(drive[1]).getAbsolutePath();
            if (isSameOrChildPath(path, driveRoot)) {
                return drive[0].replace(":", "").toUpperCase(java.util.Locale.ROOT) + ":\\"
                        + path.substring(driveRoot.length())
                        .replace(File.separatorChar, '\\').replaceFirst("^\\\\", "");
            }
        }
        return "Z:" + path.replace(File.separatorChar, '\\');
    }

    private String executableWorkingDirectory(File executable, Container container) {
        File parent = executable.getParentFile();
        if (parent == null) return "";
        String path = parent.getAbsolutePath();
        for (String[] drive : container.drivesIterator()) {
            if (drive == null || drive.length < 2 || drive[0] == null || drive[1] == null) continue;
            String driveRoot = new File(drive[1]).getAbsolutePath();
            if (isSameOrChildPath(path, driveRoot)) {
                String relativePath = path.substring(driveRoot.length())
                        .replace(File.separatorChar, '/').replaceFirst("^/", "");
                return getContainerWineHome(container) + "/.wine/dosdevices/"
                        + drive[0].replace(":", "").toLowerCase(java.util.Locale.ROOT) + ":/"
                        + relativePath;
            }
        }
        return path;
    }

    private boolean isSameOrChildPath(String path, String root) {
        return path.equals(root) || path.startsWith(root.endsWith(File.separator)
                ? root : root + File.separator);
    }

    private String getContainerWineHome(Container container) {
        Context context = getContext();
        if (context == null) return "/home/" + com.winlator.cmod.xenvironment.ImageFs.USER;
        String rootPath = container.getRootDir().getAbsolutePath();
        String imageFsPath = new File(context.getFilesDir(), "imagefs").getAbsolutePath();
        return rootPath.startsWith(imageFsPath)
                ? rootPath.substring(imageFsPath.length())
                : "/home/" + com.winlator.cmod.xenvironment.ImageFs.USER;
    }

    private String getDisplayName(Uri uri) {
        Context context = getContext();
        if (context == null) return null;
        try (android.database.Cursor cursor = context.getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception e) {
            android.util.Log.e("ShortcutsFragment", "Unable to read selected executable name", e);
        }
        return null;
    }

    private void copyGameIntoContainer(Uri uri, String selectedName, Container container) {
        Context context = getContext();
        if (context == null) return;
        String fileName = new File(selectedName).getName()
                .replaceAll("[<>:\"/\\\\|?*]", "_").trim();
        String baseName = fileName.substring(0, fileName.length() - 4).trim();
        if (baseName.isEmpty()) baseName = "Game";

        File programFiles = new File(container.getRootDir(), ".wine/drive_c/Program Files");
        File gameDir = new File(programFiles, baseName);
        for (int suffix = 2; gameDir.exists()
                || new File(container.getDesktopDir(), gameDir.getName() + ".desktop").exists(); suffix++) {
            gameDir = new File(programFiles, baseName + " (" + suffix + ")");
        }
        if (!gameDir.mkdirs()) {
            Toast.makeText(context, "Could not create the game's program folder.", Toast.LENGTH_LONG).show();
            return;
        }
        File executable = new File(gameDir, fileName);
        if (!com.winlator.cmod.core.FileUtils.copy(context, uri, executable)) {
            executable.delete();
            gameDir.delete();
            Toast.makeText(context, "Failed to copy the selected executable.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!createDesktopShortcut(executable, container)) {
            executable.delete();
            gameDir.delete();
            return;
        }
        Toast.makeText(context, "Game added to " + container.getName() + " shortcuts.", Toast.LENGTH_LONG).show();
        loadShortcutsList();
    }

    private boolean createDesktopShortcut(File executable, Container container) {
        Context context = getContext();
        if (context == null) return false;
        String displayName = executable.getParentFile().getName();
        File desktopDir = container.getDesktopDir();
        if (!desktopDir.exists() && !desktopDir.mkdirs()) {
            Toast.makeText(context, "Could not create the container desktop folder.", Toast.LENGTH_LONG).show();
            return false;
        }
        File desktopFile = new File(desktopDir, displayName + ".desktop");
        if (desktopFile.exists()) {
            Toast.makeText(context, "A shortcut named '" + displayName + "' already exists.", Toast.LENGTH_LONG).show();
            return false;
        }

        File driveC = new File(container.getRootDir(), ".wine/drive_c");
        String relativePath = executable.getAbsolutePath().substring(driveC.getAbsolutePath().length())
                .replace('/', '\\');
        if (relativePath.startsWith("\\")) relativePath = relativePath.substring(1);
        String windowsPath = "C:\\" + relativePath;
        String imageFsPath = new File(context.getFilesDir(), "imagefs").getAbsolutePath();
        String containerHome = container.getRootDir().getAbsolutePath().startsWith(imageFsPath)
                ? container.getRootDir().getAbsolutePath().substring(imageFsPath.length())
                : "/home/" + com.winlator.cmod.xenvironment.ImageFs.USER;
        String winePrefix = containerHome + "/.wine";
        String workingDirectory = winePrefix + "/dosdevices/c:/"
                + relativePath.substring(0, relativePath.lastIndexOf('\\')).replace('\\', '/');

        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(desktopFile))) {
            writer.println("[Desktop Entry]");
            writer.println("Name=" + displayName);
            writer.println("Exec=env WINEPREFIX=\"" + winePrefix + "\" wine "
                    + com.winlator.cmod.core.StringUtils.escapeFileDOSPath(windowsPath));
            writer.println("Type=Application");
            writer.println("Path=" + workingDirectory);
            writer.println("Icon=" + displayName);
            writer.println("container_id:" + container.id);
            writer.println();
            writer.println("[Extra Data]");
            writer.println("coverArtSelectionPending=1");
        } catch (java.io.IOException e) {
            android.util.Log.e("ShortcutsFragment", "Unable to create game desktop shortcut", e);
            Toast.makeText(context, "Failed to create the game shortcut.", Toast.LENGTH_LONG).show();
            return false;
        }

        File iconDir = container.getIconsDir(64);
        if (!iconDir.exists() && !iconDir.mkdirs()) {
            android.util.Log.w("ShortcutsFragment", "Unable to create icon directory: " + iconDir);
        } else {
            com.winlator.cmod.core.ExeIconExtractor.extractAsync(executable,
                    new File(iconDir, displayName + ".png"), false, null);
        }
        return true;
    }

    private void importShortcutWorkflow() {
        final Context context = getContext();
        if (context == null) return;

        final ArrayList<Container> containers = new ArrayList<>(manager.getContainers());
        if (containers.isEmpty()) {
            ContentDialog.alert(context, "No containers available. Please create a container first.", null);
            return;
        }

        File exportDir = new File(com.winlator.cmod.SettingsFragment.DEFAULT_SHORTCUT_EXPORT_PATH);
        final File[] files = exportDir.exists() ? exportDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".desktop")) : null;

        if (files != null && files.length > 0) {
            String[] options = new String[files.length + 1];
            for (int i = 0; i < files.length; i++) {
                options[i] = files[i].getName().replace(".desktop", "");
            }
            options[files.length] = "📁 Browse from Storage...";

            ContentDialog.showSingleChoiceList(context, "Select Shortcut to Import", options, index -> {
                if (index < files.length) {
                    copyDesktopFileToContainer(files[index], containers);
                } else {
                    browseAndImportShortcut();
                }
            });
        } else {
            browseAndImportShortcut();
        }
    }

    private void browseAndImportShortcut() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select .desktop shortcut file"), REQUEST_CODE_IMPORT_SHORTCUT);
        } catch (Exception e) {
            Toast.makeText(getContext(), "No file browser found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleImportedFile(Uri uri) {
        final Context context = getContext();
        if (context == null || uri == null) return;
        final ArrayList<Container> containers = new ArrayList<>(manager.getContainers());
        if (containers.isEmpty()) return;

        try {
            String fileName = "imported_shortcut.desktop";
            android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
                cursor.close();
            }
            if (!fileName.endsWith(".desktop")) fileName = fileName + ".desktop";

            File tempFile = new File(context.getCacheDir(), fileName);
            java.io.InputStream in = context.getContentResolver().openInputStream(uri);
            java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();

            copyDesktopFileToContainer(tempFile, containers);
        } catch (Exception e) {
            Toast.makeText(context, "Failed to read shortcut file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyDesktopFileToContainer(File sourceFile, ArrayList<Container> containers) {
        final Context context = getContext();
        if (context == null || sourceFile == null || !sourceFile.exists()) return;

        if (containers.size() == 1) {
            doCopyDesktop(sourceFile, containers.get(0));
        } else {
            String[] containerNames = new String[containers.size()];
            for (int i = 0; i < containers.size(); i++) {
                containerNames[i] = containers.get(i).getName();
            }
            ContentDialog.showSingleChoiceList(context, "Target Container", containerNames, index -> {
                doCopyDesktop(sourceFile, containers.get(index));
            });
        }
    }

    private void doCopyDesktop(File sourceFile, Container targetContainer) {
        final Context context = getContext();
        if (context == null || targetContainer == null) return;

        File targetDir = targetContainer.getDesktopDir();
        if (!targetDir.exists()) targetDir.mkdirs();

        File targetFile = new File(targetDir, sourceFile.getName());
        if (FileUtils.copy(sourceFile, targetFile)) {
            Toast.makeText(context, "Shortcut '" + sourceFile.getName().replace(".desktop", "") + "' imported into " + targetContainer.getName(), Toast.LENGTH_LONG).show();
            loadShortcutsList();
        } else {
            Toast.makeText(context, "Failed to import shortcut into container.", Toast.LENGTH_SHORT).show();
        }
    }

    private class ShortcutsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_SHORTCUT = 0;
        private static final int VIEW_TYPE_ADD_GAME = 1;
        final List<Shortcut> originalData;
        final List<Shortcut> data;
        private String currentQuery = "";
        private int currentSortOrder = 0;

        public ShortcutsAdapter(List<Shortcut> shortcuts) {
            this.originalData = new ArrayList<>(shortcuts);
            this.data = new ArrayList<>();
            Context ctx = getContext();
            if (ctx != null) {
                this.currentSortOrder = PreferenceManager.getDefaultSharedPreferences(ctx).getInt("shortcuts_sort_order", 0);
            }
            applyFilterAndSort();
        }

        public void filter(String query) {
            this.currentQuery = query != null ? query.trim().toLowerCase() : "";
            applyFilterAndSort();
            notifyDataSetChanged();
            updateGridLayout();
            updateEmptyState();
        }

        public void setSortOrder(int sortOrder) {
            this.currentSortOrder = sortOrder;
            applyFilterAndSort();
            notifyDataSetChanged();
            centerSelectedShortcut();
        }

        private void applyFilterAndSort() {
            data.clear();
            for (Shortcut s : originalData) {
                if (s == null) continue;
                if (currentQuery.isEmpty()) {
                    data.add(s);
                } else {
                    String name = s.name != null ? s.name.toLowerCase() : "";
                    String containerName = (s.container != null && s.container.getName() != null) ? s.container.getName().toLowerCase() : "";
                    String exe = s.getExecutable() != null ? s.getExecutable().toLowerCase() : "";
                    if (name.contains(currentQuery) || containerName.contains(currentQuery) || exe.contains(currentQuery)) {
                        data.add(s);
                    }
                }
            }

            Context ctx = getContext();
            SharedPreferences pt = ctx != null ? ctx.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE) : null;

            switch (currentSortOrder) {
                case 1: // Z to A
                    data.sort((a, b) -> b.name.compareToIgnoreCase(a.name));
                    break;
                case 2: // Most Played
                    if (pt != null) {
                        data.sort((a, b) -> {
                            int countA = pt.getInt(a.name + "_play_count", 0);
                            int countB = pt.getInt(b.name + "_play_count", 0);
                            if (countB != countA) return Integer.compare(countB, countA);
                            return a.name.compareToIgnoreCase(b.name);
                        });
                    } else {
                        data.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                    }
                    break;
                case 3: // Longest Playtime
                    if (pt != null) {
                        data.sort((a, b) -> {
                            long timeA = pt.getLong(a.name + "_playtime", 0L);
                            long timeB = pt.getLong(b.name + "_playtime", 0L);
                            if (timeB != timeA) return Long.compare(timeB, timeA);
                            return a.name.compareToIgnoreCase(b.name);
                        });
                    } else {
                        data.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                    }
                    break;
                case 4: // By Container
                    data.sort((a, b) -> {
                        String cA = a.container != null ? a.container.getName() : "";
                        String cB = b.container != null ? b.container.getName() : "";
                        int cmp = cA.compareToIgnoreCase(cB);
                        if (cmp != 0) return cmp;
                        return a.name.compareToIgnoreCase(b.name);
                    });
                    break;
                case 0: // A to Z (Default)
                default:
                    data.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                    break;
            }
            if (selectedShortcut == null || !data.contains(selectedShortcut)) {
                selectedShortcut = data.isEmpty() ? null : data.get(0);
            }
            updateSelectedShortcutDetails();
        }

        private void selectShortcut(Shortcut shortcut) {
            int previousPosition = data.indexOf(selectedShortcut);
            selectedShortcut = shortcut;
            int selectedPosition = data.indexOf(shortcut);
            if (previousPosition >= 0) notifyItemChanged(previousPosition);
            if (selectedPosition >= 0 && selectedPosition != previousPosition) notifyItemChanged(selectedPosition);
            updateSelectedShortcutDetails();
            applyCarouselEffects();
            centerShortcutIfNeeded(selectedPosition);
        }

        private void centerShortcutIfNeeded(int position) {
            if (recyclerView == null || recyclerView.isComputingLayout() || position < 0) return;
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            View card = layoutManager != null ? layoutManager.findViewByPosition(position) : null;
            if (card == null) return;
            float cardCenter = (card.getLeft() + card.getRight()) / 2f;
            float viewportCenter = recyclerView.getWidth() / 2f;
            if (Math.abs(cardCenter - viewportCenter) > Math.round(4f * getResources().getDisplayMetrics().density)) {
                recyclerView.smoothScrollToPosition(position);
            }
        }

        public void updateEmptyState() {
            View view = getView();
            if (view == null) return;
            View emptyView = view.findViewById(R.id.TVEmptyText);
            if (emptyView instanceof TextView) {
                emptyView.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position == data.size() ? VIEW_TYPE_ADD_GAME : VIEW_TYPE_SHORTCUT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_TYPE_ADD_GAME) {
                return new AddGameViewHolder(inflater.inflate(R.layout.shortcut_add_game_card, parent, false));
            }
            return new ViewHolder(inflater.inflate(R.layout.shortcut_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder recyclerHolder, int position) {
            if (recyclerHolder instanceof AddGameViewHolder) {
                AddGameViewHolder holder = (AddGameViewHolder) recyclerHolder;
                ViewGroup.LayoutParams params = holder.card.getLayoutParams();
                if (params != null && shortcutCardWidthPx > 0) {
                    params.width = shortcutCardWidthPx;
                    holder.card.setLayoutParams(params);
                }
                holder.card.setOnClickListener(v -> {
                    int itemPosition = holder.getBindingAdapterPosition();
                    if (itemPosition == RecyclerView.NO_POSITION || recyclerView == null) return;
                    float cardCenter = (v.getLeft() + v.getRight()) / 2f;
                    float viewportCenter = (recyclerView.getPaddingLeft()
                            + recyclerView.getWidth() - recyclerView.getPaddingRight()) / 2f;
                    float maxDistance = Math.max(1f,
                            shortcutCardWidthPx + recyclerView.getPaddingLeft());
                    float distance = Math.min(1f,
                            Math.abs(cardCenter - viewportCenter) / maxDistance);
                    if (distance > 0.12f) {
                        recyclerView.smoothScrollToPosition(itemPosition);
                        return;
                    }
                    Context context = getContext();
                    if (context == null || manager == null) return;
                    if (manager.getContainers().isEmpty()) {
                        ContentDialog.alert(context,
                                "Create a container before adding a game shortcut.", null);
                    } else {
                        browseForGameExecutable();
                    }
                });
                holder.card.setClickable(true);
                holder.card.setFocusable(true);
                holder.card.setContentDescription("Add game shortcut");
                if (recyclerView != null) {
                    recyclerView.postOnAnimation(ShortcutsFragment.this::applyCarouselEffects);
                }
                return;
            }
            ViewHolder holder = (ViewHolder) recyclerHolder;
            Shortcut item = data.get(position);
            holder.title.setText(item.name);
            requestCloudCoverArt(item);

            boolean landscape = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            float titleSize = landscape ? 14.0f : 15.0f;

            if (holder.title != null) {
                holder.title.setMaxLines(landscape ? 2 : 1);
                holder.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSize);
            }

            ViewGroup.LayoutParams itemParams = holder.cardRoot.getLayoutParams();
            if (itemParams != null && shortcutCardWidthPx > 0 && itemParams.width != shortcutCardWidthPx) {
                itemParams.width = shortcutCardWidthPx;
                holder.cardRoot.setLayoutParams(itemParams);
            }
            ViewGroup.LayoutParams coverParams = holder.coverFrame.getLayoutParams();
            if (coverParams != null && shortcutCoverHeightPx > 0 && coverParams.height != shortcutCoverHeightPx) {
                coverParams.height = shortcutCoverHeightPx;
                holder.coverFrame.setLayoutParams(coverParams);
            }

            String rawWine = item.getExtra("wineVersion", item.container.getWineVersion());
            String displayWine = formatWineVersionForDisplay(rawWine);
            if (displayWine.isEmpty()) displayWine = item.container.getName();
            if (holder.subtitle != null) {
                adjustTextSizeToFit(holder.subtitle, displayWine, 10.5f, 6.0f);
            }

            String gameVersion = item.getGameVersion();
            if (gameVersion != null && !gameVersion.isEmpty()) {
                String formattedVer = gameVersion.startsWith("v") || gameVersion.startsWith("V") ? gameVersion : "v" + gameVersion;
                if (holder.version != null) {
                    adjustTextSizeToFit(holder.version, formattedVer, 10.5f, 6.0f);
                    holder.version.setVisibility(View.VISIBLE);
                }
            } else if (holder.version != null) {
                holder.version.setVisibility(View.GONE);
            }

            File coverArtFile = item.getCoverArtFile();
            com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> coverRequest =
                    Glide.with(holder.coverArt).load(
                            coverArtFile != null ? coverArtFile : item.getCoverArt());
            if (coverArtFile != null) {
                coverRequest.signature(new ObjectKey(coverArtFile.getAbsolutePath()
                        + ":" + coverArtFile.lastModified() + ":" + coverArtFile.length()
                        + ":" + item.getCoverArtVersion()));
            }
            coverRequest
                    .placeholder(R.drawable.cover_art_placeholder)
                    .error(R.drawable.cover_art_placeholder)
                    .fallback(R.drawable.cover_art_placeholder)
                    .centerCrop()
                    .into(holder.coverArt);
            holder.coverArt.setImageTintList(null);
            holder.coverArt.clearColorFilter();

            if (getContext() != null) {
                int accent = ThemeManager.getAccentColor(getContext());
                holder.menuButton.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
                boolean selected = item == selectedShortcut;
                float density = getResources().getDisplayMetrics().density;
                holder.cardRoot.setCardBackgroundColor(selected
                        ? Color.argb(110, 255, 255, 255)
                        : Color.argb(60, 255, 255, 255));
                holder.cardRoot.setCardElevation((selected ? 11f : 7f) * density);
                holder.cardRoot.setMaxCardElevation(14f * density);
                holder.playButton.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            }
            holder.cardRoot.setAlpha(1f);
            holder.cardRoot.setEnabled(true);
            holder.cardRoot.setClickable(true);
            holder.innerArea.setEnabled(true);
            holder.innerArea.setClickable(true);

            holder.menuButton.setOnClickListener((v) -> showListItemMenu(v, item));
            holder.playButton.setContentDescription(getString(R.string.start_game_named, item.name));
            holder.playButton.setEnabled(!isLaunchingShortcut);
            holder.playButton.setOnClickListener(v -> launchShortcut(item));
            holder.playButton.setVisibility(landscape ? View.GONE : View.VISIBLE);
            holder.landscapePlayButton.setContentDescription(getString(R.string.start_game_named, item.name));
            holder.landscapePlayButton.setEnabled(!isLaunchingShortcut);
            holder.landscapePlayButton.setVisibility(landscape ? View.VISIBLE : View.GONE);
            holder.landscapePlayButton.setOnClickListener(v -> launchShortcut(item));
            holder.cardRoot.setOnClickListener((v) -> selectShortcut(item));
            holder.cardRoot.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == android.view.KeyEvent.ACTION_UP
                        && (keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER)) {
                    selectShortcut(item);
                    return true;
                }
                return false;
            });
            holder.innerArea.setOnClickListener((v) -> selectShortcut(item));
            holder.cardRoot.setContentDescription(item.name);
            holder.innerArea.setOnLongClickListener((v) -> {
                showListItemMenu(holder.menuButton, item);
                return true;
            });
            if (recyclerView != null) {
                recyclerView.postOnAnimation(ShortcutsFragment.this::applyCarouselEffects);
            }
        }

        private void launchShortcut(Shortcut shortcut) {
            long now = SystemClock.elapsedRealtime();
            if (isLaunchingShortcut
                    || (lastShortcutLaunchTime > 0 && now - lastShortcutLaunchTime < 1500L)) return;
            isLaunchingShortcut = true;
            lastShortcutLaunchTime = now;
            if (adapter != null) adapter.notifyDataSetChanged();
            runFromShortcut(shortcut);
        }

        private void adjustTextSizeToFit(TextView textView, String text, float maxSp, float minSp) {
            if (textView == null) return;
            textView.setText(text != null ? text : "");
            if (text == null || text.isEmpty()) return;

            int width = textView.getWidth();
            if (width > 0) {
                applyFittedTextSize(textView, text, maxSp, minSp, width);
            }

            textView.post(() -> {
                int w = textView.getWidth();
                if (w > 0) {
                    applyFittedTextSize(textView, text, maxSp, minSp, w);
                }
            });
        }

        private void applyFittedTextSize(TextView textView, String text, float maxSp, float minSp, int widthPx) {
            float padding = textView.getCompoundPaddingLeft() + textView.getCompoundPaddingRight();
            float availableWidth = widthPx - padding;
            if (availableWidth <= 0) return;

            android.text.TextPaint paint = new android.text.TextPaint(textView.getPaint());
            float density = textView.getResources().getDisplayMetrics().scaledDensity;

            float currentSp = maxSp;
            while (currentSp > minSp) {
                paint.setTextSize(currentSp * density);
                if (paint.measureText(text) <= availableWidth) {
                    break;
                }
                currentSp -= 0.25f;
            }
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSp);
        }

        @Override
        public int getItemCount() {
            return data.size() + 1;
        }

        private void showListItemMenu(View anchorView, final Shortcut shortcut) {
            final Context context = getContext();
            PopupMenu listItemMenu = new PopupMenu(context, anchorView);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

            listItemMenu.inflate(R.menu.shortcut_popup_menu);

            Menu menu = listItemMenu.getMenu();
            int accent = ThemeManager.getAccentColor(context);
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item.getIcon() != null) {
                    Drawable icon = item.getIcon().mutate();
                    icon.setTint(accent);
                    item.setIcon(icon);
                }
            }

            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.shortcut_settings) {
                    (new ShortcutSettingsDialog(ShortcutsFragment.this, shortcut)).show();
                }
                else if (itemId == R.id.shortcut_remove) {
                    ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_shortcut, () -> {
                        if (shortcut.file.delete()) {
                            File lnkFile = new File(shortcut.file.getPath().substring(0, shortcut.file.getPath().lastIndexOf(".")) + ".lnk");
                            if (lnkFile.exists()) lnkFile.delete();
                            disableShortcutOnScreen(requireContext(), shortcut);
                            loadShortcutsList();
                            Toast.makeText(context, "Shortcut removed successfully.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Failed to remove the shortcut.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                else if (itemId == R.id.shortcut_clone_to_container) {
                    showContainerSelectionDialog(new ArrayList<>(manager.getContainers()), (selectedContainer) -> {
                        if (shortcut.cloneToContainer(selectedContainer)) {
                            Toast.makeText(context, "Shortcut cloned successfully.", Toast.LENGTH_SHORT).show();
                            loadShortcutsList();
                        } else {
                            Toast.makeText(context, "Failed to clone shortcut.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                else if (itemId == R.id.shortcut_view_logs) {
                    // As in Bannerlator: straight to this game's logs in the Log Manager.
                    getParentFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.slide_in_up, R.anim.slide_out_down, R.anim.slide_in_down, R.anim.slide_out_up)
                        .addToBackStack(null)
                        .replace(R.id.FLFragmentContainer, LogManagerFragment.forGame(shortcut.name))
                        .commit();
                }
                else if (itemId == R.id.shortcut_save_manager) {
                    new com.winlator.cmod.saves.SaveManagerDialog(getActivity(), shortcut).show();
                }
                else if (itemId == R.id.shortcut_add_to_home_screen) {
                    shortcut.genUUID();
                    addShortcutToScreen(shortcut);
                }
                else if (itemId == R.id.shortcut_export) {
                    exportShortcut(shortcut);
                }
                else if (itemId == R.id.shortcut_properties) {
                    showShortcutProperties(shortcut);
                }
                else if (itemId == R.id.shortcut_manage_cover_art) {
                    String[] options = {getString(R.string.search_cover_art), getString(R.string.change_cover_art), getString(R.string.reset_cover_art)};
                    ContentDialog.showSingleChoiceList(context, R.string.manage_cover_art, options, (index) -> {
                        if (index == 0) {
                            showArtSelectionDialog(shortcut, false);
                        }
                        else if (index == 1) {
                            currentShortcut = shortcut;
                            currentCoverArtSelectionVersion = shortcut.beginCoverArtSelection();
                            Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            try {
                                startActivityForResult(intent, REQUEST_CODE_CUSTOM_COVER_ART);
                            } catch (Exception e) {
                                long selectionVersion = currentCoverArtSelectionVersion;
                                boolean cleared = shortcut.cancelCoverArtSelection(selectionVersion);
                                currentShortcut = null;
                                currentCoverArtSelectionVersion = -1;
                                android.util.Log.e("ShortcutsFragment", "Unable to open image picker.", e);
                                if (cleared) {
                                    requestCloudCoverArt(shortcut);
                                    Toast.makeText(context, "Unable to open image picker.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                        else if (index == 2) {
                            shortcut.removeCustomCoverArt();
                            shortcut.putExtra("coverArtSelectionPending", "prompted");
                            shortcut.saveData();
                            coverArtPromptsShown.add(shortcut.file.getAbsolutePath());
                            loadShortcutsList();
                            showSearchPrompt(shortcut, shortcut.name, false);
                        }
                    });
                }
                else if (itemId == R.id.shortcut_manage_hero_art) {
                    String[] options = {getString(R.string.search_hero_art), getString(R.string.change_hero_art), getString(R.string.reset_hero_art)};
                    ContentDialog.showSingleChoiceList(context, R.string.manage_hero_art, options, (index) -> {
                        if (index == 0) {
                            showArtSelectionDialog(shortcut, true);
                        }
                        else if (index == 1) {
                            currentShortcut = shortcut;
                            Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            startActivityForResult(intent, REQUEST_CODE_CUSTOM_HERO_ART);
                        }
                        else if (index == 2) {
                            shortcut.removeCustomHeroArt();
                            loadShortcutsList();
                            Toast.makeText(context, "Loading screen art reset.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                return true;
            });

            listItemMenu.show();
        }

        private void showContainerSelectionDialog(ArrayList<Container> containers, OnContainerSelectedListener listener) {
            String[] containerNames = new String[containers.size()];
            for (int i = 0; i < containers.size(); i++) containerNames[i] = containers.get(i).getName();
            ContentDialog.showSingleChoiceList(getContext(), R.string.containers, containerNames, (index) -> {
                listener.onContainerSelected(containers.get(index));
            });
        }

        private void runFromShortcut(Shortcut shortcut) {
            Intent intent = new Intent(getContext(), XServerDisplayActivity.class);
            intent.putExtra("container_id", shortcut.container.id);
            intent.putExtra("shortcut_path", shortcut.file.getPath());
            intent.putExtra("shortcut_name", shortcut.name);
            getContext().startActivity(intent);
        }

        private void exportShortcut(Shortcut shortcut) {
            File exportDir = new File(com.winlator.cmod.SettingsFragment.DEFAULT_SHORTCUT_EXPORT_PATH);
            if (!exportDir.exists()) exportDir.mkdirs();
            File exportFile = new File(exportDir, shortcut.file.getName());
            if (FileUtils.copy(shortcut.file, exportFile)) {
                Toast.makeText(getContext(), "Shortcut exported to " + exportFile.getPath(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), "Failed to export shortcut.", Toast.LENGTH_SHORT).show();
            }
        }

        private void shareShortcutToCommunity(Shortcut shortcut) {
            final Context context = getContext();
            if (context == null) return;

            if (!AppUtils.isNetworkAvailable(context)) {
                ContentDialog.alert(context, R.string.no_internet_connection, null);
                return;
            }

            final String currentName = shortcut.name;
            final String exeName = shortcut.getExecutable();
            final String steamId = shortcut.getExtra("steam_id");
            final String communityImage = shortcut.getExtra("community_image");

            // Step 1: If it's already tagged with a Steam ID, ask to confirm
            if (!steamId.isEmpty()) {
                ContentDialog dialog = new ContentDialog(context);
                dialog.setTitle("Confirm Game Info");
                dialog.setMessage("This configuration is tagged as:\n\n<b>" + currentName + "</b>\n\nIs this correct for the community?");
                ((TextView)dialog.findViewById(R.id.BTConfirm)).setText("YES");
                ((TextView)dialog.findViewById(R.id.BTCancel)).setText("NO, SEARCH");
                dialog.setOnConfirmCallback(() -> performCommunityUpload(shortcut, currentName, steamId, communityImage));
                dialog.setOnCancelCallback(() -> {
                    showCommunitySearchPrompt(shortcut, currentName);
                });
                dialog.show();
                return;
            }

            // Step 2: Try to find info automatically via PCGamingWiki (Searching by EXE)
            android.app.Dialog loadingDialog = new android.app.Dialog(context);
            loadingDialog.setContentView(new android.widget.ProgressBar(context));
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            loadingDialog.setCancelable(false);
            loadingDialog.show();

            com.winlator.cmod.core.pcgw.PCGamingWikiAPI pcgwApi = com.winlator.cmod.core.CoverArtManager.getPCGWRetrofit().create(com.winlator.cmod.core.pcgw.PCGamingWikiAPI.class);
            String where = "Executable.File LIKE \"%" + exeName + "%\" OR Executable.File LIKE \"%" + exeName.toLowerCase() + "%\"";
            
            pcgwApi.searchByExecutable("cargoquery", "Executable", "Executable._pageName=GameTitle", where, "json").enqueue(new retrofit2.Callback<com.winlator.cmod.core.pcgw.PCGWResponse>() {
                @Override
                public void onResponse(retrofit2.Call<com.winlator.cmod.core.pcgw.PCGWResponse> call, retrofit2.Response<com.winlator.cmod.core.pcgw.PCGWResponse> response) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (response.isSuccessful() && response.body() != null && response.body().cargoquery != null && !response.body().cargoquery.isEmpty()) {
                            if (loadingDialog.isShowing()) loadingDialog.dismiss();
                            
                            // Get best name from PCGW
                            String pcgwName = response.body().cargoquery.get(0).title.gameTitle;
                            int bestScore = Integer.MAX_VALUE;
                            for (com.winlator.cmod.core.pcgw.PCGWResponse.CargoItem item : response.body().cargoquery) {
                                int score = calculateMatchScore(currentName, item.title.gameTitle);
                                if (score < bestScore) {
                                    bestScore = score;
                                    pcgwName = item.title.gameTitle;
                                }
                            }
                            final String finalSuggestedName = cleanGameName(currentName, pcgwName);
                            
                            ContentDialog dialog = new ContentDialog(context);
                            dialog.setTitle("Confirm Game Info");
                            dialog.setMessage("Identified via EXE:\n\n<b>" + finalSuggestedName + "</b>\n\nIs this correct?");
                            ((TextView)dialog.findViewById(R.id.BTConfirm)).setText("YES");
                            ((TextView)dialog.findViewById(R.id.BTCancel)).setText("NO, SEARCH");
                            dialog.setOnConfirmCallback(() -> searchGameForSharing(shortcut, finalSuggestedName, true));
                            dialog.setOnCancelCallback(() -> {
                                showCommunitySearchPrompt(shortcut, currentName);
                            });
                            dialog.show();
                        } else {
                            // Fallback to Steam Search if PCGW fails
                            trySteamSearchForSharing(shortcut, currentName, exeName, loadingDialog);
                        }
                    });
                }

                @Override
                public void onFailure(retrofit2.Call<com.winlator.cmod.core.pcgw.PCGWResponse> call, Throwable t) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> trySteamSearchForSharing(shortcut, currentName, exeName, loadingDialog));
                }
            });
        }

        private void trySteamSearchForSharing(Shortcut shortcut, String currentName, String exeName, android.app.Dialog loadingDialog) {
            Context context = getContext();
            if (context == null) return;

            com.winlator.cmod.core.steam.SteamStoreAPI steamApi = com.winlator.cmod.core.CoverArtManager.getSteamRetrofit().create(com.winlator.cmod.core.steam.SteamStoreAPI.class);
            String folderName = shortcut.getParentFolderName();
            String cleanedExe = exeName.toLowerCase().replace(".exe", "")
                    .replaceAll("(?i)(_)?(x64|x86|win64|win32|shipping|launcher|setup|installer)$", "")
                    .replaceAll("[^a-z0-9]", " ").trim();
            
            String searchTerm = currentName;
            if (!folderName.isEmpty() && folderName.length() > 2) searchTerm = folderName;
            else if (cleanedExe.length() > 3) searchTerm = cleanedExe;

            final String finalSearchTerm = searchTerm;
            steamApi.search(finalSearchTerm, "english", "US").enqueue(new retrofit2.Callback<com.winlator.cmod.core.steam.SteamSearchResponse>() {
                @Override
                public void onResponse(retrofit2.Call<com.winlator.cmod.core.steam.SteamSearchResponse> call, retrofit2.Response<com.winlator.cmod.core.steam.SteamSearchResponse> response) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (loadingDialog.isShowing()) loadingDialog.dismiss();
                        String suggestedName = currentName;
                        if (response.isSuccessful() && response.body() != null && response.body().items != null && !response.body().items.isEmpty()) {
                            suggestedName = findBestSteamMatch(finalSearchTerm, response.body().items);
                        }
                        final String finalSuggestedName = suggestedName;

                        ContentDialog dialog = new ContentDialog(context);
                        dialog.setTitle("Confirm Game Info");
                        dialog.setMessage("Is this configuration for:\n\n<b>" + finalSuggestedName + "</b>?");
                        ((TextView)dialog.findViewById(R.id.BTConfirm)).setText("YES");
                        ((TextView)dialog.findViewById(R.id.BTCancel)).setText("NO, SEARCH");
                        dialog.setOnConfirmCallback(() -> searchGameForSharing(shortcut, finalSuggestedName, true));
                        dialog.setOnCancelCallback(() -> {
                            showCommunitySearchPrompt(shortcut, currentName);
                        });
                        dialog.show();
                    });
                }

                @Override
                public void onFailure(retrofit2.Call<com.winlator.cmod.core.steam.SteamSearchResponse> call, Throwable t) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (loadingDialog.isShowing()) loadingDialog.dismiss();
                        showCommunitySearchPrompt(shortcut, currentName);
                    });
                }
            });
        }

        private void showCommunitySearchPrompt(Shortcut shortcut, String initialValue) {
            Context context = getContext();
            if (context == null) return;

            ContentDialog dialog = new ContentDialog(context);
            dialog.setTitle(R.string.search_game_info);
            dialog.setMessage(getString(R.string.community_search_instruction));
            
            final android.widget.EditText editText = dialog.findViewById(R.id.EditText);
            editText.setVisibility(View.VISIBLE);
            
            // Apply independent styling to ensure visibility in both light and dark themes
            boolean isDarkMode = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean("dark_mode", true);
            if (isDarkMode) {
                editText.setTextColor(android.graphics.Color.WHITE);
                editText.setHintTextColor(android.graphics.Color.GRAY);
                editText.setBackgroundResource(R.drawable.edit_text_dark);
            } else {
                editText.setTextColor(android.graphics.Color.BLACK);
                editText.setHintTextColor(android.graphics.Color.GRAY);
                editText.setBackgroundResource(R.drawable.edit_text);
            }

            if (initialValue != null) editText.setText(initialValue);
            
            dialog.setOnConfirmCallback(() -> {
                String query = editText.getText().toString().trim();
                if (!query.isEmpty()) {
                    searchGameForSharing(shortcut, query, false);
                } else {
                    Toast.makeText(context, "Please enter a game name", Toast.LENGTH_SHORT).show();
                }
            });
            dialog.show();
        }

        private void searchGameForSharing(Shortcut shortcut, String query, boolean autoSelect) {
            Context context = getContext();
            if (context == null) return;

            if (!AppUtils.isNetworkAvailable(context)) {
                ContentDialog.alert(context, R.string.no_internet_connection, null);
                return;
            }

            final String apiKey = "0324c52513634547a7b32d6d323635d0"; // Reusing Winlator default SGDB key
            SteamGridDBApi api = com.winlator.cmod.core.CoverArtManager.getRetrofit().create(SteamGridDBApi.class);

            api.searchGame("Bearer " + apiKey, query).enqueue(new retrofit2.Callback<SteamGridSearchResponse>() {
                @Override
                public void onResponse(retrofit2.Call<SteamGridSearchResponse> call, retrofit2.Response<SteamGridSearchResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                        List<SteamGridSearchResponse.GameData> games = response.body().data;

                        // If autoSelect is enabled and we have a match, skip the list dialog
                        if (autoSelect && !games.isEmpty()) {
                            fetchSgdbDetailsAndUpload(shortcut, games.get(0), api, apiKey);
                            return;
                        }

                        String[] names = new String[games.size()];
                        for (int i = 0; i < games.size(); i++) names[i] = games.get(i).name;

                        Activity activity = getActivity();
                        if (activity != null) activity.runOnUiThread(() -> {
                            ContentDialog.showSingleChoiceList(context, "Select Game", names, index -> {
                                fetchSgdbDetailsAndUpload(shortcut, games.get(index), api, apiKey);
                            });
                        });
                    } else {
                        Activity activity = getActivity();
                        if (activity != null) activity.runOnUiThread(() -> {
                            Toast.makeText(context, "No games found on SteamGridDB. Uploading with current info.", Toast.LENGTH_SHORT).show();
                            performCommunityUpload(shortcut, shortcut.name, "", "");
                        });
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<SteamGridSearchResponse> call, Throwable t) {
                    Activity activity = getActivity();
                    if (activity != null) activity.runOnUiThread(() -> performCommunityUpload(shortcut, shortcut.name, "", ""));
                }
            });
        }

        private void fetchSgdbDetailsAndUpload(Shortcut shortcut, SteamGridSearchResponse.GameData selected, SteamGridDBApi api, String apiKey) {
            api.getGameDetails("Bearer " + apiKey, selected.id).enqueue(new retrofit2.Callback<SteamGridGameDetailsResponse>() {
                @Override
                public void onResponse(retrofit2.Call<SteamGridGameDetailsResponse> call, retrofit2.Response<SteamGridGameDetailsResponse> response) {
                    String foundSteamId = "";
                    if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                        if (response.body().data.externalIds != null && response.body().data.externalIds.steam != null) {
                            foundSteamId = response.body().data.externalIds.steam.id;
                        }
                    }

                    final String finalSteamId = foundSteamId;
                    if (finalSteamId.isEmpty()) {
                        api.getGridsByGameId("Bearer " + apiKey, selected.id, "no_logo", null, "static").enqueue(new retrofit2.Callback<com.winlator.cmod.core.steamgrid.SteamGridGridsResponse>() {
                            @Override
                            public void onResponse(retrofit2.Call<com.winlator.cmod.core.steamgrid.SteamGridGridsResponse> call, retrofit2.Response<com.winlator.cmod.core.steamgrid.SteamGridGridsResponse> response) {
                                String fallbackImage = "";
                                if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                                    fallbackImage = response.body().data.get(0).url;
                                }
                                final String finalFallback = fallbackImage;
                                Activity activity2 = getActivity();
                                if (activity2 != null) activity2.runOnUiThread(() -> performCommunityUpload(shortcut, selected.name, "", finalFallback));
                            }

                            @Override public void onFailure(retrofit2.Call<com.winlator.cmod.core.steamgrid.SteamGridGridsResponse> call, Throwable t) {
                                Activity activity2 = getActivity();
                                if (activity2 != null) activity2.runOnUiThread(() -> performCommunityUpload(shortcut, selected.name, "", ""));
                            }
                        });
                    } else {
                        Activity activity2 = getActivity();
                        if (activity2 != null) activity2.runOnUiThread(() -> performCommunityUpload(shortcut, selected.name, finalSteamId, ""));
                    }
                }

                @Override public void onFailure(retrofit2.Call<SteamGridGameDetailsResponse> call, Throwable t) {
                    Activity activity2 = getActivity();
                    if (activity2 != null) activity2.runOnUiThread(() -> performCommunityUpload(shortcut, selected.name, "", ""));
                }
            });
        }

        private void performCommunityUpload(Shortcut shortcut, String gameName, String steamId, String communityImage) {
            final Context context = getContext();
            if (context == null) return;

            ContentDialog uploadDialog = new ContentDialog(context, R.layout.community_upload_dialog);
            uploadDialog.setTitle("Community Upload");
            
            final android.widget.EditText etTitle = uploadDialog.findViewById(R.id.ETConfigTitle);
            final android.widget.EditText etNotes = uploadDialog.findViewById(R.id.ETConfigNotes);
            
            boolean isDarkMode = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean("dark_mode", true);
            int textColor = isDarkMode ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;
            int hintColor = android.graphics.Color.GRAY;
            int bgRes = isDarkMode ? R.drawable.edit_text_dark : R.drawable.edit_text;

            etTitle.setTextColor(textColor); etTitle.setHintTextColor(hintColor); etTitle.setBackgroundResource(bgRes);
            etNotes.setTextColor(textColor); etNotes.setHintTextColor(hintColor); etNotes.setBackgroundResource(bgRes);

            uploadDialog.setOnConfirmCallback(() -> {
                String title = etTitle.getText().toString().trim();
                String notes = etNotes.getText().toString().trim();
                if (title.isEmpty()) {
                    Toast.makeText(context, "Config title is required", Toast.LENGTH_SHORT).show();
                    return;
                }
                confirmAndUpload(shortcut, gameName, steamId, communityImage, notes, title);
            });
            uploadDialog.show();
        }

        private void confirmAndUpload(Shortcut shortcut, String gameName, String steamId, String communityImage, String notes, String configTitle) {
            final Context context = getContext();
            ContentDialog.confirm(context, "Share configuration for '" + gameName + "'?", () -> {
                org.json.JSONObject config = CommunityConfigUtils.exportConfig(context, shortcut, gameName, steamId, communityImage, notes, configTitle);
                if (config != null) {
                    final MainActivity activity = (MainActivity) getActivity();
                    if (activity != null) activity.preloaderDialog.show("Uploading Configuration...");
                    CommunityConfigManager.uploadConfig(config, error -> {
                        if (activity == null) return;
                        activity.runOnUiThread(() -> {
                            activity.preloaderDialog.close();
                            if (error == null) {
                                Toast.makeText(context, "Uploaded successfully!", Toast.LENGTH_LONG).show();
                            } else {
                                ContentDialog.alert(context, "Upload failed:\n\n" + error, null);
                            }
                        });
                    });
                } else {
                    ContentDialog.alert(context, "Please configure and select a valid Wine runtime before sharing.", null);
                }
            });
        }

        private void showShortcutProperties(Shortcut shortcut) {
            Context context = getContext();
            if (context == null) return;

            ContentDialog dialog = new ContentDialog(context, R.layout.shortcut_properties_dialog);
            dialog.setTitle("Properties");
            dialog.setIcon(R.drawable.ic_nav_info);

            View cancelBtn = dialog.findViewById(R.id.BTCancel);
            if (cancelBtn != null) cancelBtn.setVisibility(View.GONE);

            android.content.SharedPreferences playtimePrefs = context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE);
            String playtimeKey = shortcut.name + "_playtime";
            String playCountKey = shortcut.name + "_play_count";

            TextView tvPlayCount = dialog.findViewById(R.id.play_count);
            TextView tvPlaytime = dialog.findViewById(R.id.playtime);
            TextView tvDetails = dialog.findViewById(R.id.shortcut_details);
            android.widget.Button btnReset = dialog.findViewById(R.id.reset_properties);

            long totalMs = playtimePrefs.getLong(playtimeKey, 0L);
            int playCount = playtimePrefs.getInt(playCountKey, 0);

            long seconds = (totalMs / 1000) % 60;
            long minutes = (totalMs / (1000 * 60)) % 60;
            long hours = (totalMs / (1000 * 60 * 60)) % 24;
            long days = (totalMs / (1000 * 60 * 60 * 24));
            String formattedTime = String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);

            if (tvPlayCount != null) tvPlayCount.setText("Number of times played: " + playCount);
            if (tvPlaytime != null) tvPlaytime.setText("Playtime: " + formattedTime);

            StringBuilder sb = new StringBuilder();
            sb.append("<b>Name:</b> ").append(shortcut.name).append("<br/>");
            sb.append("<b>Container:</b> ").append(shortcut.container.getName()).append("<br/>");

            String rawWine = shortcut.getExtra("wineVersion", shortcut.container.getWineVersion());
            String displayWine = formatWineVersionForDisplay(rawWine);
            if (displayWine.isEmpty()) displayWine = shortcut.container.getName();
            sb.append("<b>Wine:</b> ").append(displayWine).append("<br/>");

            String gv = shortcut.getGameVersion();
            if (gv != null && !gv.isEmpty()) {
                sb.append("<b>Game Version:</b> ").append(gv).append("<br/>");
            }

            com.winlator.cmod.win32.PEParser.FileVersionInfo fi = shortcut.getFileVersionInfo();
            if (fi != null) {
                if (fi.ProductName != null && !fi.ProductName.isEmpty()) {
                    sb.append("<b>Product:</b> ").append(fi.ProductName).append("<br/>");
                }
                if (fi.CompanyName != null && !fi.CompanyName.isEmpty()) {
                    sb.append("<b>Company:</b> ").append(fi.CompanyName).append("<br/>");
                }
                if (fi.FileDescription != null && !fi.FileDescription.isEmpty()) {
                    sb.append("<b>Description:</b> ").append(fi.FileDescription).append("<br/>");
                }
                if (fi.LegalCopyright != null && !fi.LegalCopyright.isEmpty()) {
                    sb.append("<b>Copyright:</b> ").append(fi.LegalCopyright).append("<br/>");
                }
            }

            sb.append("<b>Path:</b> ").append(shortcut.path).append("<br/>");
            sb.append("<b>File:</b> ").append(shortcut.file != null ? shortcut.file.getPath() : "");

            if (tvDetails != null) {
                tvDetails.setText(android.text.Html.fromHtml(sb.toString(), android.text.Html.FROM_HTML_MODE_LEGACY));
            }

            if (btnReset != null) {
                btnReset.setOnClickListener(v -> {
                    playtimePrefs.edit().remove(playtimeKey).remove(playCountKey).apply();
                    if (tvPlayCount != null) tvPlayCount.setText("Number of times played: 0");
                    if (tvPlaytime != null) tvPlaytime.setText("Playtime: 0d 00h 00m 00s");
                    Toast.makeText(context, "Properties reset", Toast.LENGTH_SHORT).show();
                });
            }

            dialog.show();
        }

        public static String formatWineVersionForDisplay(String raw) {
            if (raw == null || raw.trim().isEmpty()) return "";
            String s = raw.trim();

            // Strip common archive and package extensions
            for (String ext : new String[]{".tzst", ".tar.xz", ".tar.zst", ".tar.gz", ".wcp", ".zip"}) {
                if (s.toLowerCase().endsWith(ext)) {
                    s = s.substring(0, s.length() - ext.length()).trim();
                }
            }

            // Strip trailing versionCode (e.g. "-1" in "Proton-11.0-1-arm64ec-1")
            if (s.matches(".*-[0-9]+$")) {
                int lastDash = s.lastIndexOf('-');
                if (lastDash > 0) {
                    String beforeDash = s.substring(0, lastDash);
                    String lowerBefore = beforeDash.toLowerCase();
                    if (lowerBefore.contains("arm64") || lowerBefore.contains("x86") || lowerBefore.contains("wine") || lowerBefore.contains("proton")) {
                        s = beforeDash;
                    }
                }
            }

            // Detect architecture
            String arch = "";
            String lower = s.toLowerCase();
            if (lower.contains("arm64ec")) {
                arch = " arm64ec";
            } else if (lower.contains("x86_64") || lower.contains("x86-64") || lower.contains("x64")) {
                arch = " x86_64";
            } else if (lower.contains("x86") || lower.contains("i386")) {
                arch = " x86";
            }

            // Strip architecture tokens from core version string
            String core = s;
            core = core.replaceAll("(?i)[-_\\s]+arm64ec", "");
            core = core.replaceAll("(?i)[-_\\s]+x86_64", "");
            core = core.replaceAll("(?i)[-_\\s]+x86-64", "");
            core = core.replaceAll("(?i)[-_\\s]+x86", "");
            core = core.replaceAll("(?i)[-_\\s]+i386", "");

            // Format prefix cleanly
            if (core.toLowerCase().startsWith("ge-proton")) {
                String rest = core.substring(9);
                if (rest.startsWith("-") || rest.startsWith(" ")) rest = rest.substring(1).trim();
                return "GE-Proton " + rest + arch;
            } else if (core.toLowerCase().startsWith("proton")) {
                String rest = core.substring(6);
                if (rest.startsWith("-") || rest.startsWith(" ")) rest = rest.substring(1).trim();
                return "Proton " + rest + arch;
            } else if (core.toLowerCase().startsWith("wine")) {
                String rest = core.substring(4);
                if (rest.startsWith("-") || rest.startsWith(" ")) rest = rest.substring(1).trim();
                return "Wine " + rest + arch;
            }

            return (core + arch).trim();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final CardView cardRoot;
            private final FrameLayout coverFrame;
            private final ImageView coverArt;
            private final TextView title;
            private final TextView subtitle;
            private final TextView version;
            private final ImageButton menuButton;
            private final ImageButton playButton;
            private final ImageButton landscapePlayButton;
            private final View innerArea;

            public ViewHolder(View view) {
                super(view);
                cardRoot = view.findViewById(R.id.CardRoot);
                coverFrame = view.findViewById(R.id.FLShortcutCover);
                coverArt = view.findViewById(R.id.ImageView);
                title = view.findViewById(R.id.TVTitle);
                subtitle = view.findViewById(R.id.TVSubtitle);
                version = view.findViewById(R.id.TVVersion);
                menuButton = view.findViewById(R.id.BTMenu);
                playButton = view.findViewById(R.id.BTPlay);
                landscapePlayButton = view.findViewById(R.id.BTPlayLandscape);
                innerArea = view.findViewById(R.id.LLInnerArea);
            }
        }

        class AddGameViewHolder extends RecyclerView.ViewHolder {
            private final CardView card;

            AddGameViewHolder(View view) {
                super(view);
                card = (CardView) view;
            }
        }
    }

    private interface OnContainerSelectedListener {
        void onContainerSelected(Container container);
    }

    public static void addShortcutToScreen(Shortcut shortcut) {
        Context context = shortcut.container.getManager().getContext();
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
            Bitmap launcherIcon = shortcut.getLauncherIcon();
            Icon icon = launcherIcon != null ? Icon.createWithBitmap(launcherIcon) : Icon.createWithResource(context, R.drawable.icon_shortcut);
            ShortcutInfo shortcutInfo = buildScreenShortCut(context, shortcut.name, shortcut.name, shortcut.container.id, shortcut.file.getPath(), icon, shortcut.name);
            shortcutManager.requestPinShortcut(shortcutInfo, null);
        }
    }

    public static void disableShortcutOnScreen(Context context, Shortcut shortcut) {
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager != null) {
            ArrayList<String> ids = new ArrayList<>();
            ids.add(shortcut.name);
            shortcutManager.disableShortcuts(ids);
        }
    }

    public void updateShortcutOnScreen(String id, String label, int containerId, String shortcutPath, Icon icon, String shortcutName) {
        Context context = getContext();
        if (context == null) return;
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager != null) {
            ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();
            shortcuts.add(buildScreenShortCut(context, id, label, containerId, shortcutPath, icon, shortcutName));
            shortcutManager.updateShortcuts(shortcuts);
        }
    }

    public static ShortcutInfo buildScreenShortCut(Context context, String id, String label, int containerId, String shortcutPath, Icon icon, String shortcutName) {
        Intent intent = new Intent(context, XServerDisplayActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("container_id", containerId);
        intent.putExtra("shortcut_path", shortcutPath);
        intent.putExtra("shortcut_name", shortcutName);

        return new ShortcutInfo.Builder(context, id)
                .setShortLabel(label)
                .setIcon(icon)
                .setIntent(intent)
                .build();
    }

    // The same PCGamingWiki -> Steam -> SteamGridDB name-matching chain finds the right search term for either art
    // type; forHero only decides which SteamGridDB endpoint the final search hits (see performArtSearch).
    private void showArtSelectionDialog(Shortcut shortcut, boolean forHero) {
        Context context = getContext();
        if (context == null) return;

        String defaultName = shortcut.name.replaceAll("\\(.*?\\)", "").replaceAll("\\[.*?\\]", "").trim();
        String exeName = shortcut.getExecutable();
        String folderName = shortcut.getParentFolderName();

        if (AppUtils.isNetworkAvailable(context)) {
            android.app.Dialog loadingDialog = new android.app.Dialog(context);
            loadingDialog.setContentView(new android.widget.ProgressBar(context));
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            loadingDialog.setCancelable(false);
            loadingDialog.show();

            com.winlator.cmod.core.pcgw.PCGamingWikiAPI pcgwApi = com.winlator.cmod.core.CoverArtManager.getPCGWRetrofit().create(com.winlator.cmod.core.pcgw.PCGamingWikiAPI.class);
            String where = "Executable.File LIKE \"%" + exeName + "%\" OR Executable.File LIKE \"%" + exeName.toLowerCase() + "%\"";

            pcgwApi.searchByExecutable("cargoquery", "Executable", "Executable._pageName=GameTitle", where, "json").enqueue(new retrofit2.Callback<com.winlator.cmod.core.pcgw.PCGWResponse>() {
                @Override
                public void onResponse(retrofit2.Call<com.winlator.cmod.core.pcgw.PCGWResponse> call, retrofit2.Response<com.winlator.cmod.core.pcgw.PCGWResponse> response) {
                    if (!isAdded() || getActivity() == null || getActivity().isFinishing()) return;
                    if (response.isSuccessful() && response.body() != null && response.body().cargoquery != null && !response.body().cargoquery.isEmpty()) {
                        if (loadingDialog.isShowing()) loadingDialog.dismiss();

                        String bestName = response.body().cargoquery.get(0).title.gameTitle;
                        int bestScore = Integer.MAX_VALUE;

                        for (com.winlator.cmod.core.pcgw.PCGWResponse.CargoItem item : response.body().cargoquery) {
                            int score = calculateMatchScore(defaultName, item.title.gameTitle);
                            if (score < bestScore) {
                                bestScore = score;
                                bestName = item.title.gameTitle;
                            }
                        }

                        String suggestedName = cleanGameName(defaultName, bestName);
                        Toast.makeText(context, "Matched via PCGamingWiki: " + suggestedName, Toast.LENGTH_SHORT).show();
                        showSearchPrompt(shortcut, suggestedName, forHero);
                    } else {
                        trySteamSearch(shortcut, defaultName, folderName, loadingDialog, forHero);
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<com.winlator.cmod.core.pcgw.PCGWResponse> call, Throwable t) {
                    if (!isAdded() || getActivity() == null || getActivity().isFinishing()) return;
                    trySteamSearch(shortcut, defaultName, folderName, loadingDialog, forHero);
                }
            });
        } else {
            showSearchPrompt(shortcut, defaultName, forHero);
        }
    }

    private void trySteamSearch(Shortcut shortcut, String defaultName, String folderName, android.app.Dialog loadingDialog, boolean forHero) {
        Context context = getContext();
        if (context == null) return;
        
        com.winlator.cmod.core.steam.SteamStoreAPI steamApi = com.winlator.cmod.core.CoverArtManager.getSteamRetrofit().create(com.winlator.cmod.core.steam.SteamStoreAPI.class);
        
        // Improve fallback: try folder name first, then cleaned exe name, finally the shortcut name
        String exeName = shortcut.getExecutable();
        String cleanedExe = exeName.toLowerCase().replace(".exe", "")
                .replaceAll("(?i)(_)?(x64|x86|win64|win32|shipping|launcher|setup|installer)$", "")
                .replaceAll("[^a-z0-9]", " ").trim();
        
        String searchTerm = defaultName;
        if (!folderName.isEmpty() && folderName.length() > 2) {
            searchTerm = folderName;
        } else if (cleanedExe.length() > 3) {
            searchTerm = cleanedExe;
        }
        
        final String finalSearchTerm = searchTerm;
        steamApi.search(finalSearchTerm, "english", "US").enqueue(new retrofit2.Callback<com.winlator.cmod.core.steam.SteamSearchResponse>() {
            @Override
            public void onResponse(retrofit2.Call<com.winlator.cmod.core.steam.SteamSearchResponse> call, retrofit2.Response<com.winlator.cmod.core.steam.SteamSearchResponse> response) {
                if (!isAdded() || getActivity() == null || getActivity().isFinishing()) return;
                if (loadingDialog.isShowing()) loadingDialog.dismiss();
                String suggestedName = defaultName;
                if (response.isSuccessful() && response.body() != null && response.body().items != null && !response.body().items.isEmpty()) {
                    suggestedName = findBestSteamMatch(finalSearchTerm, response.body().items);
                    Toast.makeText(context, "Matched via Steam: " + suggestedName, Toast.LENGTH_SHORT).show();
                } else if (!folderName.isEmpty() && folderName.length() > 2) {
                    suggestedName = folderName;
                } else if (cleanedExe.length() > 3) {
                    suggestedName = cleanedExe;
                }
                showSearchPrompt(shortcut, suggestedName, forHero);
            }

            @Override
            public void onFailure(retrofit2.Call<com.winlator.cmod.core.steam.SteamSearchResponse> call, Throwable t) {
                if (!isAdded() || getActivity() == null || getActivity().isFinishing()) return;
                if (loadingDialog.isShowing()) loadingDialog.dismiss();
                String suggestedName = !folderName.isEmpty() && folderName.length() > 2 ? folderName : defaultName;
                showSearchPrompt(shortcut, suggestedName, forHero);
            }
        });
    }

    private int calculateMatchScore(String query, String target) {
        String q = query.toLowerCase().replaceAll("[^a-z0-9]", "");
        String t = target.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        // Base score is the difference in length
        int score = Math.abs(q.length() - t.length());
        
        // Heavy penalty for "Remastered" if query doesn't have it
        if (t.contains("remaster") && !q.contains("remaster")) score += 30;
        
        // Penalty for "Edition" or "Pack" if query is basic
        if ((t.contains("edition") || t.contains("pack") || t.contains("bundle")) && 
            !(q.contains("edition") || q.contains("pack") || q.contains("bundle"))) {
            score += 15;
        }

        // Penalty for very long names (usually subtitles/DLCs)
        if (t.length() > q.length() + 10) score += 10;
        
        // Bonus for containing the exact query
        if (t.contains(q)) score -= 5;
        
        return score;
    }

    private String cleanGameName(String query, String foundName) {
        String resultName = foundName;
        boolean queryIsBasic = !query.toLowerCase().contains("remaster") && !query.toLowerCase().contains("edition");
        
        if (queryIsBasic) {
            String lowercaseName = resultName.toLowerCase();
            // Order is important: more specific first
            if (lowercaseName.contains("remastered")) {
                resultName = resultName.replaceAll("(?i)\\s*[:-]?\\s*remastered\\s*", " ").trim();
            } else if (lowercaseName.contains("remaster")) {
                resultName = resultName.replaceAll("(?i)\\s*[:-]?\\s*remaster\\s*", " ").trim();
            }
            
            if (!query.toLowerCase().contains("edition")) {
                resultName = resultName.replaceAll("(?i)\\s*[:-]?\\s*(standard|gold|ultimate|complete|deluxe|game of the year|goty|director's cut)\\s+edition\\s*", " ").trim();
                resultName = resultName.replaceAll("(?i)\\s*[:-]?\\s*edition\\s*", " ").trim();
            }
        }
        return resultName.replaceAll("\\s+", " ").trim();
    }

    private String findBestSteamMatch(String query, List<com.winlator.cmod.core.steam.SteamSearchResponse.SteamItem> items) {
        com.winlator.cmod.core.steam.SteamSearchResponse.SteamItem bestMatch = items.get(0);
        int bestScore = Integer.MAX_VALUE;

        for (com.winlator.cmod.core.steam.SteamSearchResponse.SteamItem item : items) {
            int score = calculateMatchScore(query, item.name);
            if (score < bestScore) {
                bestScore = score;
                bestMatch = item;
            }
        }
        
        return cleanGameName(query, bestMatch.name);
    }

    private void showSearchPrompt(Shortcut shortcut, String initialValue, boolean forHero) {
        ContentDialog.prompt(getContext(), forHero ? R.string.search_hero_art : R.string.search_cover_art, initialValue, (query) -> {
            performArtSearch(shortcut, query, forHero);
        });
    }

    // Shared by both art types: forHero only switches which SteamGridDB list is fetched (fetchCoverArtOptions /
    // fetchHeroArtOptions), which save path the picked item downloads into (downloadSelectedCoverArt /
    // downloadSelectedHeroArt), and which item layout the grid uses -- hero_art_selection_item is a wide (280x90)
    // box so the picker previews the actual banner shape instead of cropping it into a portrait square.
    private void performArtSearch(Shortcut shortcut, String query, boolean forHero) {
        Context context = getContext();
        if (context == null) return;

        android.app.Dialog loadingDialog = new android.app.Dialog(context);
        loadingDialog.setContentView(new android.widget.ProgressBar(context));
        loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        loadingDialog.setCancelable(false);
        loadingDialog.show();

        int titleRes = forHero ? R.string.search_hero_art : R.string.search_cover_art;

        com.winlator.cmod.core.CoverArtManager.GridOptionsCallback gridCallback = new com.winlator.cmod.core.CoverArtManager.GridOptionsCallback() {
            @Override
            public void onOptionsAvailable(java.util.List<com.winlator.cmod.core.steamgrid.SteamGridGridsResponse.GridData> options) {
                java.util.List<ArtOption> wrapped = new java.util.ArrayList<>();
                for (com.winlator.cmod.core.steamgrid.SteamGridGridsResponse.GridData d : options)
                    wrapped.add(new ArtOption() { public String thumb() { return d.thumb; } public String url() { return d.url; } });
                showArtOptions(context, loadingDialog, titleRes, wrapped, shortcut, forHero, query);
            }

            @Override
            public void onFailed(com.winlator.cmod.core.CoverArtManager.ErrorReason reason) {
                showArtSearchError(context, loadingDialog, reason, query, shortcut, forHero);
            }
        };

        com.winlator.cmod.core.CoverArtManager.HeroOptionsCallback heroCallback = new com.winlator.cmod.core.CoverArtManager.HeroOptionsCallback() {
            @Override
            public void onOptionsAvailable(java.util.List<com.winlator.cmod.core.steamgrid.SteamGridHeroesResponse.HeroData> options) {
                java.util.List<ArtOption> wrapped = new java.util.ArrayList<>();
                for (com.winlator.cmod.core.steamgrid.SteamGridHeroesResponse.HeroData d : options)
                    wrapped.add(new ArtOption() { public String thumb() { return d.thumb; } public String url() { return d.url; } });
                showArtOptions(context, loadingDialog, titleRes, wrapped, shortcut, forHero, query);
            }

            @Override
            public void onFailed(com.winlator.cmod.core.CoverArtManager.ErrorReason reason) {
                showArtSearchError(context, loadingDialog, reason, query, shortcut, forHero);
            }
        };

        if (forHero) com.winlator.cmod.core.CoverArtManager.fetchHeroArtOptions(context, shortcut, query, heroCallback);
        else com.winlator.cmod.core.CoverArtManager.fetchCoverArtOptions(context, shortcut, query, gridCallback);
    }

    private interface ArtOption { String thumb(); String url(); }

    private void showArtOptions(Context context, android.app.Dialog loadingDialog, int titleRes,
                                java.util.List<ArtOption> options, Shortcut shortcut, boolean forHero, String query) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || !isAdded()) return;
        activity.runOnUiThread(() -> {
            if (loadingDialog.isShowing()) loadingDialog.dismiss();
            ContentDialog dialog = new ContentDialog(context, R.layout.cover_art_selection_dialog);
            dialog.setTitle(titleRes);
            dialog.findViewById(R.id.BTConfirm).setVisibility(View.GONE);

            RecyclerView recyclerView = dialog.findViewById(R.id.RecyclerView);
            recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
            recyclerView.setVisibility(View.VISIBLE);
            dialog.findViewById(R.id.ProgressBar).setVisibility(View.GONE);

            recyclerView.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
                    int itemLayout = forHero ? R.layout.hero_art_selection_item : R.layout.cover_art_selection_item;
                    View view = LayoutInflater.from(context).inflate(itemLayout, parent, false);
                    return new RecyclerView.ViewHolder(view) {};
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    ArtOption data = options.get(position);
                    ImageView imageView = holder.itemView.findViewById(R.id.ImageView);
                    com.bumptech.glide.request.RequestOptions options2 = new com.bumptech.glide.request.RequestOptions();
                    // Hero thumbnails show the whole banner uncropped (it's a preview, not a fill target) --
                    // cover art keeps cropping to fill its portrait box, matching the shortcut grid it previews for.
                    if (forHero) options2 = options2.fitCenter(); else options2 = options2.centerCrop();
                    Glide.with(context)
                            .load(data.thumb() != null ? data.thumb() : data.url())
                            .placeholder(R.drawable.cover_art_placeholder)
                            .thumbnail(0.1f)
                            .apply(options2)
                            .into(imageView);

                    holder.itemView.setOnClickListener(v -> {
                        dialog.dismiss();
                        Toast.makeText(context, forHero ? "Applying loading screen art..." : "Applying cover art...", Toast.LENGTH_SHORT).show();
                        long coverArtVersion = forHero ? -1 : shortcut.beginCoverArtSelection();
                        com.winlator.cmod.core.CoverArtManager.DownloadCallback cb = new com.winlator.cmod.core.CoverArtManager.DownloadCallback() {
                            @Override
                            public void onCompleted(android.graphics.Bitmap bitmap) {
                                Activity a = getActivity();
                                if (a != null && !a.isFinishing() && isAdded()) a.runOnUiThread(() -> {
                                    if (forHero) loadShortcutsList();
                                    else {
                                        notifyShortcutImageUpdated(shortcut);
                                        refreshShortcutCoverArtCards();
                                    }
                                    Toast.makeText(context, forHero ? "Loading screen art updated." : "Cover art updated.", Toast.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void onFailed(com.winlator.cmod.core.CoverArtManager.ErrorReason reason) {
                                if (!forHero) {
                                    if (shortcut.getCoverArtVersion() != coverArtVersion
                                            || !shortcut.cancelCoverArtSelection(coverArtVersion)) return;
                                }
                                Activity a = getActivity();
                                if (a != null && !a.isFinishing() && isAdded()) a.runOnUiThread(() -> {
                                    if (!forHero) requestCloudCoverArt(shortcut);
                                    Toast.makeText(context, forHero ? "Failed to download loading screen art." : "Failed to download cover art.", Toast.LENGTH_SHORT).show();
                                });
                            }
                        };
                        if (forHero) com.winlator.cmod.core.CoverArtManager.downloadSelectedHeroArt(data.url(), shortcut, cb);
                        else com.winlator.cmod.core.CoverArtManager.downloadSelectedCoverArt(
                                data.url(), shortcut, coverArtVersion, cb);
                    });
                }

                @Override
                public int getItemCount() {
                    return options.size();
                }
            });
            if (!activity.isFinishing()) dialog.show();
        });
    }

    private void showArtSearchError(Context context, android.app.Dialog loadingDialog, com.winlator.cmod.core.CoverArtManager.ErrorReason reason,
                                    String query, Shortcut shortcut, boolean forHero) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || !isAdded()) return;
        activity.runOnUiThread(() -> {
            if (loadingDialog.isShowing()) loadingDialog.dismiss();
            String message;
            if (reason == com.winlator.cmod.core.CoverArtManager.ErrorReason.NETWORK_UNAVAILABLE) {
                message = "Connection Error\n\nPlease check your internet and try again. Searching requires an active connection to SteamGridDB.";
            } else if (reason == com.winlator.cmod.core.CoverArtManager.ErrorReason.NOT_FOUND) {
                message = "Game Not Found\n\nCould not find \"" + query + "\".\n\n" +
                          "Try these tips:\n" +
                          "• Check for typos\n" +
                          "• Use the full title (e.g., 'Grand Theft Auto V')\n" +
                          "• Try adding the year: 'God of War (2018)'\n" +
                          "• Click 'RETRY' to try a different name.";
            } else if (reason == com.winlator.cmod.core.CoverArtManager.ErrorReason.UNAUTHORIZED) {
                message = "Invalid API Key\n\nThe custom SteamGridDB API key in Settings is invalid.";
            } else if (reason == com.winlator.cmod.core.CoverArtManager.ErrorReason.RATE_LIMITED) {
                message = "Too Many Requests\n\nPlease wait a few seconds before trying again.";
            } else {
                message = "Service Unavailable\n\nSteamGridDB is currently busy. Please try again later.";
            }

            ContentDialog errorDialog = new ContentDialog(context);
            errorDialog.setTitle(forHero ? R.string.search_hero_art : R.string.search_cover_art);
            errorDialog.setMessage(message);
            if (reason == com.winlator.cmod.core.CoverArtManager.ErrorReason.NOT_FOUND || reason == com.winlator.cmod.core.CoverArtManager.ErrorReason.NETWORK_UNAVAILABLE) {
                errorDialog.setOnConfirmCallback(() -> showArtSelectionDialog(shortcut, forHero));
                ((TextView)errorDialog.findViewById(R.id.BTConfirm)).setText("RETRY");
            } else {
                errorDialog.findViewById(R.id.BTCancel).setVisibility(View.GONE);
            }
            errorDialog.show();
        });
    }
}
