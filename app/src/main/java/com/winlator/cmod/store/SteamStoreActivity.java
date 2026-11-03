package com.winlator.cmod.store;

import android.app.AlertDialog;
import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.winlator.cmod.R;
import com.winlator.cmod.ThemeManager;
import com.winlator.cmod.ThemePreset;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.core.ExeIconExtractor;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.widget.GlassWindowEffects;
import com.winlator.cmod.widget.PlayStationWaveBackgroundView;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

public final class SteamStoreActivity extends AppCompatActivity
        implements SteamRepository.Listener, SteamAuthManager.Listener,
    SteamQrAuthManager.Listener, SteamStorefront.Listener, SteamBadgeService.Listener,
        SteamNewsService.Listener {
    private static final String PREFERENCE_SELECTED_TAB = "steam_selected_tab";
    private static final String PREFERENCE_LOGIN_PROMPT_DISMISSED =
            "steam_login_prompt_dismissed";
    private static final int REQUEST_CHAT_NOTIFICATION_PERMISSION = 2401;
    private static final String STEAM_CLIENT_CONTAINER_PREFIX = "steam_client_container_";
    private static final String STEAM_PENDING_OFFICIAL_INSTALL_PREFIX =
            "steam_pending_official_install_";
    private static final String STEAM_SETUP_URL =
            "https://cdn.akamai.steamstatic.com/client/installer/SteamSetup.exe";
    private static final long MAX_STEAM_SETUP_BYTES = 32L * 1024L * 1024L;

    private final SteamRepository repository = SteamRepository.getInstance();
    private final ExecutorService steamClientInstallerExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SteamClientSetup");
                thread.setDaemon(true);
                return thread;
            });
    private final SteamAuthManager authManager = new SteamAuthManager();
    private final SteamQrAuthManager qrAuthManager = new SteamQrAuthManager();
    private SteamStorefront storefront;
    private final List<SteamGame> games = new ArrayList<>();
    private final List<SteamGame> filteredGames = new ArrayList<>();
    private final java.util.LinkedHashMap<String, List<SteamStorefront.StoreItem>> storeSections =
            new java.util.LinkedHashMap<>();
    private final java.util.Set<Integer> freeGameRequests = new java.util.HashSet<>();
    private TextView statusView;
    private TextView accountView;
    private LinearLayout loginPanel;
    private LinearLayout accountPanel;
    private EditText usernameInput;
    private EditText passwordInput;
    private Button loginButton;
    private Button qrLoginButton;
    private ProgressBar progressBar;
    private RecyclerView gameList;
    private GameAdapter adapter;
    private RecyclerView friendList;
    private FriendAdapter friendAdapter;
    private TextView friendNotificationStatus;
    private final List<SteamFriend> friends = new ArrayList<>();
    private RecyclerView downloadList;
    private DownloadAdapter downloadAdapter;
    private final List<SteamDownload> downloads = new ArrayList<>();
    private String libraryFilter = "All";
    private TextView storeMessage;
    private ProgressBar storeProgress;
    private EditText storeSearchInput;
    private Button storeHomeButton;
    private LinearLayout storeSectionsContainer;
    private ScrollView storePage;
    private String currentSearchQuery;
    private int storeRequestsRemaining;
    private int storeRequestGeneration;
    private boolean storeLoaded;
    private boolean steamInitializationFailed;
    private boolean officialInstallScanRunning;
    private int initialSelectedTab;
    private LinearLayout steamNavBar;
    private final List<LinearLayout> steamNavItems = new ArrayList<>();
    private int selectedSteamTab;
    private FrameLayout pageContainer;
    private View libraryPage;
    private View friendsPage;
    private View profilePage;
    private View downloadsPage;
    private View newsPage;
    private View achievementsPage;
    private LinearLayout achievementGameChoices;
    private TextView achievementsSummary;
    private RecyclerView achievementList;
    private AchievementAdapter achievementAdapter;
    private final List<SteamAchievement> achievementItems = new ArrayList<>();
    private final List<SteamBadge> badges = new ArrayList<>();
    private final SteamBadgeService badgeService = new SteamBadgeService();
    private final SteamNewsService newsService = new SteamNewsService();
    private boolean badgesLoaded;
    private boolean badgesLoading;
    private String accountLevel = "";
    private String accountXp = "";
    private RecyclerView badgeList;
    private BadgeAdapter badgeAdapter;
    private TextView badgesSummary;
    private RecyclerView newsList;
    private NewsAdapter newsAdapter;
    private TextView newsStatus;
    private LinearLayout newsArticlePage;
    private ImageView newsArticleImage;
    private TextView newsArticleTitle;
    private TextView newsArticleDate;
    private TextView newsArticleContent;
    private final List<SteamNewsItem> newsItems = new ArrayList<>();
    private boolean newsLoaded;
    private int selectedAchievementAppId;
    private TextView profileName;
    private TextView profileId;
    private TextView profileStatus;
    private TextView profileAvatarFallback;
    private ImageView profileAvatar;
    private TextView profileLevel;
    private LinearLayout profileBadgeContainer;
    private LinearLayout profileStatsRow;
    private long activeChatSteamId;
    private TextView activeChatTranscript;
    private final java.util.Map<Long, StringBuilder> chatHistory = new java.util.HashMap<>();
    private AlertDialog qrDialog;
    private ImageView qrImage;
    private TextView qrStatus;
    private FrameLayout activityRoot;
    private LinearLayout headerPanel;
    private PlayStationWaveBackgroundView playstationBackdrop;
    private String profileAvatarRequestUrl;
    private AlertDialog loginDialog;
    private AlertDialog reconnectDialog;
    private boolean loginPromptDismissed;
    private boolean activityResumed;
    private final android.os.Handler promotionHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final android.os.Handler reconnectHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable reconnectVerification;
    private RecyclerView promotionCarousel;
    private Runnable promotionAutoSlide;
    private RecyclerView.OnScrollListener promotionScrollListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this, false);
        super.onCreate(savedInstanceState);
        int restoredTab = savedInstanceState == null
                ? PreferenceManager.getDefaultSharedPreferences(this)
                .getInt(PREFERENCE_SELECTED_TAB, 0)
                : savedInstanceState.getInt(PREFERENCE_SELECTED_TAB, 0);
        initialSelectedTab = restoredTab >= 3 && restoredTab <= 4
                ? restoredTab - 1 : restoredTab >= 6 ? 0 : restoredTab;
        loginPromptDismissed = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREFERENCE_LOGIN_PROMPT_DISMISSED, false);
        String setupError = null;
        try {
            storefront = new SteamStorefront(this);
            SteamConnectionService.start(this);
            repository.initialize(this);
        } catch (RuntimeException e) {
            Log.e("SteamStoreActivity", "Could not initialize Steam", e);
            steamInitializationFailed = true;
            setupError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }

        repository.addListener(this);
        buildLayout();
        statusView.setOnClickListener(view -> {
            if (!repository.isLoggedIn() && repository.isConnected()) {
                loginPromptDismissed = false;
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .remove(PREFERENCE_LOGIN_PROMPT_DISMISSED).apply();
                showLoginDialog();
            }
        });
        refreshState();
        if (setupError != null) {
            setBusy(false, "Steam setup failed: " + setupError);
        } else if (!repository.isConnected()) {
            showReconnectDialog();
        } else if (!repository.isLoggedIn()) {
            showLoginDialog();
        }
    }

    private void startRequestedSteamClientInstall() {
        int containerId = getIntent().getIntExtra("install_official_steam_container_id", 0);
        if (containerId <= 0) return;
        getIntent().removeExtra("install_official_steam_container_id");
        Container container = new ContainerManager(this).getContainerById(containerId);
        if (container == null) {
            setBusy(false, "Could not find the selected container to install Steam.");
            return;
        }
        int appId = getIntent().getIntExtra("install_official_steam_app_id", 0);
        boolean bridgeOnly = getIntent().getBooleanExtra("install_official_steam_bridge", false);
        getIntent().removeExtra("install_official_steam_bridge");
        if (appId > 0 && !bridgeOnly) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putInt(STEAM_CLIENT_CONTAINER_PREFIX + appId, containerId)
                    .putBoolean(STEAM_PENDING_OFFICIAL_INSTALL_PREFIX + appId, true)
                    .apply();
        }
        setBusy(false, bridgeOnly
                ? "Downloading Steam for this container. Finish setup, then relaunch the shortcut."
                : "Downloading the official Steam installer. You will need to finish "
                + "installing and sign in to Steam once.");
        downloadAndLaunchSteamSetup(container);
    }

    @Override
    protected void onDestroy() {
        repository.removeListener(this);
        authManager.cancel();
        qrAuthManager.cancel();
        badgeService.cancel();
        newsService.cancel();
        steamClientInstallerExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        ThemeManager.applyGlassBackdropEffect(this);
        if (playstationBackdrop != null) playstationBackdrop.setAnimationEnabled(true);
        if (!steamInitializationFailed) {
            showReconnectDialog();
            SteamConnectionService.start(this);
            if (repository.isConnected()) {
                if (reconnectVerification != null) {
                    reconnectHandler.removeCallbacks(reconnectVerification);
                }
                reconnectVerification = () -> {
                    reconnectVerification = null;
                    if (!activityResumed || !repository.isConnected()) return;
                    dismissReconnectDialog();
                    if (!repository.isLoggedIn()) showLoginDialog();
                };
                reconnectHandler.postDelayed(reconnectVerification, 650L);
            }
        } else if (!repository.isConnected()) {
            showReconnectDialog();
        } else if (!repository.isLoggedIn()) {
            showLoginDialog();
        }
        schedulePromotionAutoSlide();
    }

    private void showLoginDialog() {
        if (isFinishing() || loginPromptDismissed || repository.isLoggedIn()
                || !repository.isConnected()) return;
        if (reconnectDialog != null && reconnectDialog.isShowing()) reconnectDialog.dismiss();
        if (loginDialog != null) {
            if (loginDialog.isShowing()) return;
            loginDialog = null;
        }
        android.view.ViewParent loginPanelParent = loginPanel.getParent();
        if (loginPanelParent instanceof ViewGroup) {
            ((ViewGroup) loginPanelParent).removeView(loginPanel);
        }
        AlertDialog loginAlertDialog = new AlertDialog.Builder(this)
                .setTitle("Sign in to Steam")
                .setView(loginPanel)
                .setNegativeButton("Close", (dialog, which) -> {
                    loginPromptDismissed = true;
                    PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .putBoolean(PREFERENCE_LOGIN_PROMPT_DISMISSED, true).apply();
                    statusView.setText("Tap here to sign in to Steam");
                })
                .create();
        loginDialog = loginAlertDialog;
        loginAlertDialog.setOnDismissListener(ignored -> {
            android.view.ViewParent parent = loginPanel.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(loginPanel);
            if (loginDialog == loginAlertDialog) loginDialog = null;
        });
        loginAlertDialog.setOnShowListener(ignored -> {
            styleFullBlurDialog(loginAlertDialog);
            if (loginAlertDialog.getWindow() != null) {
                loginAlertDialog.getWindow().setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                loginAlertDialog.getWindow().setLayout(
                        getResources().getDisplayMetrics().widthPixels - dp(36),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        loginAlertDialog.show();
    }

    private void showReconnectDialog() {
        if (isFinishing()) return;
        if (reconnectDialog != null && reconnectDialog.isShowing()) return;
        if (loginDialog != null && loginDialog.isShowing()) loginDialog.dismiss();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(4), dp(8), dp(8));
        LinearLayout connectionRow = new LinearLayout(this);
        connectionRow.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        connectionRow.addView(spinner, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView message = new TextView(this);
        message.setText(repository.isConnected()
                ? "Verifying your Steam connection…" : "Connecting to Steam…");
        message.setTextSize(15);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        messageParams.leftMargin = dp(14);
        connectionRow.addView(message, messageParams);
        content.addView(connectionRow);
        TextView networkHint = new TextView(this);
        networkHint.setText("If this gets stuck, toggle Wi-Fi or mobile data off and on, "
                + "then tap Retry connection.");
        networkHint.setTextSize(12);
        networkHint.setTextColor(0xFFCCD5E2);
        networkHint.setPadding(dp(4), dp(10), dp(4), dp(2));
        content.addView(networkHint);
        reconnectDialog = new AlertDialog.Builder(this)
                .setTitle("Reconnecting to Steam")
                .setView(content)
                .setPositiveButton("Retry connection", null)
                .setCancelable(false)
                .create();
        reconnectDialog.setOnShowListener(dialog -> {
            styleFullBlurDialog(reconnectDialog);
            reconnectDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                message.setText("Retrying connection to Steam…");
                SteamConnectionService.start(this);
            });
            if (reconnectDialog.getWindow() != null) {
                reconnectDialog.getWindow().setLayout(
                        getResources().getDisplayMetrics().widthPixels - dp(36),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        reconnectDialog.show();
    }

    private void dismissReconnectDialog() {
        if (reconnectVerification != null) {
            reconnectHandler.removeCallbacks(reconnectVerification);
            reconnectVerification = null;
        }
        if (reconnectDialog != null && reconnectDialog.isShowing()) reconnectDialog.dismiss();
    }

    private void dismissLoginDialog() {
        if (loginDialog != null && loginDialog.isShowing()) loginDialog.dismiss();
    }

    private void continuePendingOfficialSteamInstall() {
        android.content.SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(this);
        Map<String, ?> savedPreferences = preferences.getAll();
        for (String key : savedPreferences.keySet()) {
            if (!key.startsWith(STEAM_PENDING_OFFICIAL_INSTALL_PREFIX)) continue;
            int appId;
            try {
                appId = Integer.parseInt(key.substring(
                        STEAM_PENDING_OFFICIAL_INSTALL_PREFIX.length()));
            } catch (NumberFormatException e) {
                Log.w("SteamStoreActivity", "Ignoring invalid pending Steam install key: " + key, e);
                preferences.edit().remove(key).apply();
                continue;
            }
            int containerId = preferences.getInt(STEAM_CLIENT_CONTAINER_PREFIX + appId, -1);
            Container container = new ContainerManager(this).getContainerById(containerId);
            if (container == null) {
                Log.w("SteamStoreActivity", "Pending Steam install has no valid container: " + appId);
                preferences.edit().remove(key).apply();
                continue;
            }
            try {
                File steamExe = findOfficialSteamClient(container);
                if (steamExe == null) continue;
                preferences.edit().remove(key).apply();
                launchSteamCommand(container, steamExe, "steam://install/" + appId,
                        "Install Steam game " + appId);
                setBusy(false, "Steam opened to continue the game installation. Sign in if asked.");
            } catch (IOException e) {
                Log.e("SteamStoreActivity", "Could not continue pending official Steam install", e);
                setBusy(false, "Could not continue the Steam install: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        if (promotionAutoSlide != null) {
            promotionHandler.removeCallbacks(promotionAutoSlide);
        }
        if (playstationBackdrop != null) playstationBackdrop.setAnimationEnabled(false);
        super.onPause();
    }

    private void buildLayout() {
        boolean landscape = isLandscapeLayout();
        activityRoot = new FrameLayout(this);
        playstationBackdrop = new PlayStationWaveBackgroundView(this);
        playstationBackdrop.setId(R.id.GlassBackdrop);
        activityRoot.addView(playstationBackdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(landscape ? 6 : 12);
        root.setPadding(padding, padding, padding, dp(landscape ? 3 : 6));

        headerPanel = new LinearLayout(this);
        headerPanel.setOrientation(LinearLayout.VERTICAL);
        headerPanel.setPadding(dp(landscape ? 10 : 16), dp(landscape ? 5 : 12),
                dp(landscape ? 10 : 16), dp(landscape ? 3 : 8));
        TextView heading = new TextView(this);
        heading.setText("Steam");
        heading.setTextSize(landscape ? 18 : 24);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        headerPanel.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setText("Connecting to Steam…");
        statusView.setPadding(0, dp(5), 0, dp(5));
        headerPanel.addView(statusView);

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        headerPanel.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));
        root.addView(headerPanel, glassPanelParams(0));

        loginPanel = new LinearLayout(this);
        loginPanel.setOrientation(LinearLayout.VERTICAL);
        loginPanel.setPadding(dp(4), dp(2), dp(4), dp(2));
        TextView loginHint = new TextView(this);
        loginHint.setText("Sign in to load your Steam library");
        loginHint.setTextSize(14);
        loginHint.setPadding(dp(2), 0, dp(2), dp(10));
        loginPanel.addView(loginHint);
        usernameInput = new EditText(this);
        usernameInput.setSingleLine(true);
        usernameInput.setHint("Steam account name");
        usernameInput.setTextColor(ThemeManager.getOnSurfaceTextColor(this));
        usernameInput.setHintTextColor(ThemeManager.getSelectedPreset(this).onSurfaceVariant);
        usernameInput.setTextSize(16);
        usernameInput.setMinHeight(dp(50));
        usernameInput.setMinimumHeight(dp(50));
        usernameInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        loginPanel.addView(usernameInput);

        passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setHint("Password");
        passwordInput.setTextColor(ThemeManager.getOnSurfaceTextColor(this));
        passwordInput.setHintTextColor(ThemeManager.getSelectedPreset(this).onSurfaceVariant);
        passwordInput.setTextSize(16);
        passwordInput.setMinHeight(dp(50));
        passwordInput.setMinimumHeight(dp(50));
        passwordInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams passwordParams = glassPanelParams(dp(8));
        loginPanel.addView(passwordInput, passwordParams);

        loginButton = new Button(this);
        loginButton.setText("Sign in to Steam");
        loginButton.setAllCaps(false);
        loginButton.setMinHeight(dp(48));
        loginButton.setMinimumHeight(dp(48));
        loginButton.setBackground(glassSurface(12));
        loginButton.setOnClickListener(view -> submitLogin());
        LinearLayout.LayoutParams loginButtonParams = glassPanelParams(dp(10));
        loginPanel.addView(loginButton, loginButtonParams);
        qrLoginButton = new Button(this);
        qrLoginButton.setText("Sign in with QR code");
        qrLoginButton.setAllCaps(false);
        qrLoginButton.setMinHeight(dp(48));
        qrLoginButton.setMinimumHeight(dp(48));
        qrLoginButton.setBackground(glassSurface(12));
        qrLoginButton.setOnClickListener(view -> startQrLogin());
        loginPanel.addView(qrLoginButton, glassPanelParams(dp(6)));

        accountPanel = new LinearLayout(this);
        accountPanel.setOrientation(LinearLayout.VERTICAL);
        accountPanel.setPadding(dp(14), dp(10), dp(14), dp(8));
        accountView = new TextView(this);
        accountPanel.addView(accountView);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.END);
        Button logoutButton = new Button(this);
        logoutButton.setText("Sign out");
        logoutButton.setAllCaps(false);
        logoutButton.setTextSize(12);
        logoutButton.setMinHeight(dp(42));
        logoutButton.setMinimumHeight(dp(42));
        logoutButton.setBackground(rectangularButton(0x332D394D, 0x553D4C64));
        logoutButton.setOnClickListener(view -> confirmLogout());
        LinearLayout.LayoutParams logoutButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        controls.addView(logoutButton, logoutButtonParams);
        accountPanel.addView(controls);
        pageContainer = new FrameLayout(this);
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        pageParams.topMargin = dp(landscape ? 2 : 5);
        root.addView(pageContainer, pageParams);

        libraryPage = new LinearLayout(this);
        ((LinearLayout) libraryPage).setOrientation(LinearLayout.VERTICAL);
        TextView disclaimer = new TextView(this);
        disclaimer.setText("Downloaded games are launched directly in the selected Winlator " +
                "container. Steam account ownership and game DRM requirements still apply.");
        disclaimer.setTextSize(12);
        disclaimer.setPadding(dp(12), dp(8), dp(12), dp(8));
        ((LinearLayout) libraryPage).addView(disclaimer, glassPanelParams(0));
        disclaimer.setBackground(glassSurface(16));
        if (landscape) disclaimer.setVisibility(View.GONE);

        LinearLayout libraryTools = new LinearLayout(this);
        libraryTools.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        String[] filters = {"All games", "Installed", "Not installed"};
        for (String filter : filters) {
            Button filterButton = new Button(this);
            filterButton.setAllCaps(false);
            filterButton.setText(filter);
            filterButton.setTextSize(11);
            filterButton.setMinHeight(dp(42));
            filterButton.setMinimumHeight(dp(42));
            filterButton.setPadding(dp(4), 0, dp(4), 0);
            filterButton.setOnClickListener(view -> {
                libraryFilter = filter;
                updateLibraryFilterButtons(filterRow, filters);
                applyLibraryFilters();
            });
            LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(dp(112), dp(42));
            filterParams.setMargins(dp(3), dp(4), dp(3), dp(4));
            filterRow.addView(filterButton, filterParams);
        }
        filterScroll.addView(filterRow);
        libraryTools.addView(filterScroll);
        ((LinearLayout) libraryPage).addView(libraryTools, glassPanelParams(dp(3)));

        gameList = new RecyclerView(this);
        gameList.setClipToPadding(false);
        gameList.setPadding(dp(3), dp(2), dp(3), dp(8));
        gameList.setLayoutManager(new GridLayoutManager(this, gameColumnCount()));
        gameList.addItemDecoration(new GameCardSpacing(dp(7)));
        adapter = new GameAdapter();
        gameList.setAdapter(adapter);
        updateLibraryFilterButtons(filterRow, filters);
        ((LinearLayout) libraryPage).addView(gameList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        storePage = createStorePage();
        profilePage = createProfilePage();
        downloadsPage = createDownloadsPage();
        pageContainer.addView(storePage, matchParentParams());
        pageContainer.addView(libraryPage, matchParentParams());
        pageContainer.addView(profilePage, matchParentParams());
        pageContainer.addView(downloadsPage, matchParentParams());
        friendsPage = createFriendsPage();
        pageContainer.addView(friendsPage, matchParentParams());
        newsPage = createNewsPage();
        pageContainer.addView(newsPage, matchParentParams());
        root.addView(createSteamBottomNavigation(), glassPanelParams(dp(landscape ? 2 : 5)));

        activityRoot.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        activityRoot.setBackgroundColor(ThemeManager.getBackgroundColor(this));
        setContentView(activityRoot);
        ThemeManager.applyThemeToView(activityRoot, this);
        applySteamShadowPolicy(activityRoot);
        headerPanel.setBackground(glassSurface(18));
        loginPanel.setBackground(null);
        accountPanel.setBackground(glassSurface(18));
        int editTextBackground = ThemeManager.isDarkMode(this)
                ? R.drawable.edit_text_dark : R.drawable.edit_text;
        usernameInput.setBackgroundResource(editTextBackground);
        passwordInput.setBackgroundResource(editTextBackground);
        int selectedTab = initialSelectedTab;
        if (selectedTab < 0 || selectedTab >= steamNavItems.size()) selectedTab = 0;
        if (!repository.isLoggedIn() && selectedTab > 1 && selectedTab != 5) selectedTab = 0;
        updateSteamNavigation(selectedTab);
        showSteamTab(selectedTab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (steamNavBar != null) {
            outState.putInt(PREFERENCE_SELECTED_TAB, selectedSteamTab);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (newsArticlePage != null && newsArticlePage.getVisibility() == View.VISIBLE) {
            showNewsList();
            return;
        }
        super.onBackPressed();
    }

    private View createSteamBottomNavigation() {
        boolean landscape = isLandscapeLayout();
        steamNavBar = new LinearLayout(this);
        steamNavBar.setGravity(Gravity.CENTER);
        steamNavBar.setPadding(dp(4), dp(landscape ? 2 : 5), dp(4), dp(landscape ? 2 : 5));
        steamNavBar.setBackground(glassSurface(18));
        int[] icons = {
                android.R.drawable.ic_menu_view,
                android.R.drawable.ic_menu_agenda,
                android.R.drawable.ic_menu_myplaces,
                android.R.drawable.ic_menu_save,
                android.R.drawable.ic_menu_send,
                android.R.drawable.ic_menu_info_details
        };
        String[] labels = {"Store", "Library", "Profile", "Downloads", "Friends", "News"};
        for (int i = 0; i < labels.length; i++) {
            final int position = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(1), dp(2), dp(1), dp(2));
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            item.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(9);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(2);
            item.addView(label, labelParams);
            item.setOnClickListener(view -> selectSteamTab(position));
            steamNavBar.addView(item, new LinearLayout.LayoutParams(0, dp(landscape ? 40 : 48), 1));
            steamNavItems.add(item);
        }
        return steamNavBar;
    }

    private void selectSteamTab(int position) {
        if (position < 0 || position >= steamNavItems.size()) return;
        selectedSteamTab = position;
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putInt(PREFERENCE_SELECTED_TAB, position).apply();
        updateSteamNavigation(position);
        showSteamTab(position);
    }

    private void updateSteamNavigation(int position) {
        selectedSteamTab = position;
        int accent = ThemeManager.getAccentColor(this);
        for (int i = 0; i < steamNavItems.size(); i++) {
            LinearLayout item = steamNavItems.get(i);
            int color = i == position ? accent : ThemeManager.getSelectedPreset(this).onSurfaceVariant;
            item.setAlpha(i == position ? 1f : 0.78f);
            ImageView icon = (ImageView) item.getChildAt(0);
            TextView label = (TextView) item.getChildAt(1);
            icon.setColorFilter(color);
            label.setTextColor(color);
        }
    }

    private ScrollView createStorePage() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(2), dp(2), dp(2), dp(12));

        LinearLayout searchControls = new LinearLayout(this);
        searchControls.setOrientation(LinearLayout.VERTICAL);
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(0, 0, 0, 0);
        storeSearchInput = new EditText(this);
        storeSearchInput.setSingleLine(true);
        storeSearchInput.setHint("Search Steam Store");
        storeSearchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        storeSearchInput.setBackgroundResource(ThemeManager.isDarkMode(this)
                ? R.drawable.edit_text_dark : R.drawable.edit_text);
        searchRow.addView(storeSearchInput, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button searchButton = new Button(this);
        searchButton.setAllCaps(false);
        searchButton.setText("Search");
        searchButton.setMinWidth(dp(86));
        searchButton.setMinimumWidth(dp(86));
        searchButton.setOnClickListener(view -> searchStore());
        LinearLayout.LayoutParams searchButtonParams = new LinearLayout.LayoutParams(
                dp(92), dp(48));
        searchButtonParams.leftMargin = dp(6);
        searchRow.addView(searchButton, searchButtonParams);
        searchControls.addView(searchRow);
        storeHomeButton = new Button(this);
        storeHomeButton.setText("Back to store home");
        storeHomeButton.setAllCaps(false);
        storeHomeButton.setVisibility(View.GONE);
        storeHomeButton.setBackground(rectangularButton(0x332D394D, 0x553D4C64));
        storeHomeButton.setOnClickListener(view -> loadStoreHome());
        LinearLayout.LayoutParams homeButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        homeButtonParams.topMargin = dp(6);
        searchControls.addView(storeHomeButton, homeButtonParams);
        content.addView(searchControls, glassPanelParams(0));
        storeSearchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchStore();
                return true;
            }
            return false;
        });

        storeProgress = new ProgressBar(this);
        storeProgress.setIndeterminate(true);
        storeProgress.setVisibility(View.GONE);
        content.addView(storeProgress, glassPanelParams(dp(6)));
        storeMessage = new TextView(this);
        storeMessage.setTextSize(12);
        storeMessage.setPadding(dp(10), dp(6), dp(10), dp(6));
        storeMessage.setVisibility(View.GONE);
        content.addView(storeMessage, glassPanelParams(dp(4)));
        storeSectionsContainer = new LinearLayout(this);
        storeSectionsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionsParams = glassPanelParams(0);
        content.addView(storeSectionsContainer, sectionsParams);

        scrollView.addView(content);
        return scrollView;
    }

    private View createFriendsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        LinearLayout notificationPanel = new LinearLayout(this);
        notificationPanel.setOrientation(LinearLayout.VERTICAL);
        notificationPanel.setPadding(dp(12), dp(8), dp(12), dp(8));
        notificationPanel.setBackground(glassSurface(16));
        friendNotificationStatus = new TextView(this);
        friendNotificationStatus.setTextSize(12);
        notificationPanel.addView(friendNotificationStatus);
        Button notificationSettings = new Button(this);
        notificationSettings.setText("Enable / configure Android chat alerts");
        notificationSettings.setAllCaps(false);
        notificationSettings.setOnClickListener(view -> openChatNotificationSettings());
        notificationPanel.addView(notificationSettings, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        page.addView(notificationPanel);
        friendList = new RecyclerView(this);
        friendList.setLayoutManager(new LinearLayoutManager(this));
        friendList.setClipToPadding(false);
        friendList.setPadding(0, dp(4), 0, dp(8));
        friendAdapter = new FriendAdapter();
        friendList.setAdapter(friendAdapter);
        page.addView(friendList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        updateFriendNotificationStatus();
        return page;
    }

    private void updateFriendNotificationStatus() {
        if (friendNotificationStatus == null) return;
        boolean enabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager manager =
                    getSystemService(android.app.NotificationManager.class);
            android.app.NotificationChannel channel = manager == null ? null
                    : manager.getNotificationChannel(SteamConnectionService.CHAT_CHANNEL_ID);
            enabled &= channel == null
                    || channel.getImportance() != android.app.NotificationManager.IMPORTANCE_NONE;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            enabled &= ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        friendNotificationStatus.setText(enabled
                ? "Chat is ready: tap Chat beside a friend. Android notifications and in-game alerts are enabled."
                : "Tap Chat beside a friend to message. Android alerts are currently blocked; enable them below.");
    }

    private void openChatNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_CHAT_NOTIFICATION_PERMISSION);
            return;
        }

        Intent settingsIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settingsIntent = new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,
                            SteamConnectionService.CHAT_CHANNEL_ID);
        } else {
            settingsIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName()));
        }
        startActivity(settingsIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CHAT_NOTIFICATION_PERMISSION) {
            updateFriendNotificationStatus();
            if (grantResults.length > 0
                    && grantResults[0] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Allow notifications in Android app settings to receive chat alerts.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private View createProfilePage() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(2), dp(2), dp(2), dp(14));

        FrameLayout profileCard = new FrameLayout(this);
        profileCard.setBackground(glassSurface(22));
        profileCard.setClipToOutline(true);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setPadding(dp(112), dp(4), dp(14), dp(4));
        FrameLayout.LayoutParams identityParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(106), Gravity.TOP);
        identityParams.topMargin = dp(10);
        profileCard.addView(identity, identityParams);
        profileName = new TextView(this);
        profileName.setTextSize(22);
        profileName.setTextColor(ThemeManager.getOnSurfaceTextColor(this));
        profileName.setTypeface(null, android.graphics.Typeface.BOLD);
        profileName.setMaxLines(1);
        profileName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(profileName);
        profileStatus = new TextView(this);
        profileStatus.setTextColor(ThemeManager.getSelectedPreset(this).onSurfaceVariant);
        profileStatus.setTextSize(13);
        identity.addView(profileStatus);

        LinearLayout idPill = new LinearLayout(this);
        idPill.setGravity(Gravity.CENTER_VERTICAL);
        idPill.setPadding(dp(9), dp(3), dp(5), dp(3));
        idPill.setBackground(glassSurface(10));
        profileId = new TextView(this);
        profileId.setTextSize(11);
        profileId.setTextColor(ThemeManager.getOnSurfaceTextColor(this));
        profileId.setSingleLine(true);
        profileId.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        idPill.addView(profileId, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView copyId = new TextView(this);
        copyId.setText("Copy");
        copyId.setTextColor(ThemeManager.getAccentColor(this));
        copyId.setTypeface(null, android.graphics.Typeface.BOLD);
        copyId.setPadding(dp(8), dp(5), dp(4), dp(5));
        copyId.setOnClickListener(view -> copySteamId());
        idPill.addView(copyId);
        LinearLayout.LayoutParams idParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        idParams.topMargin = dp(5);
        identity.addView(idPill, idParams);

        FrameLayout avatarWrap = new FrameLayout(this);
        GradientDrawable avatarFrame = new GradientDrawable();
        avatarFrame.setShape(GradientDrawable.OVAL);
        avatarFrame.setColor(ThemeManager.getPrimaryColor(this));
        avatarFrame.setStroke(dp(3), ThemeManager.getDividerColor(this));
        avatarWrap.setBackground(avatarFrame);
        profileAvatar = new ImageView(this);
        profileAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        profileAvatar.setClipToOutline(true);
        avatarWrap.addView(profileAvatar, new FrameLayout.LayoutParams(dp(88), dp(88)));
        profileAvatarFallback = new TextView(this);
        profileAvatarFallback.setGravity(Gravity.CENTER);
        profileAvatarFallback.setTextColor(ThemeManager.getOnSurfaceTextColor(this));
        profileAvatarFallback.setTextSize(28);
        profileAvatarFallback.setTypeface(null, android.graphics.Typeface.BOLD);
        avatarWrap.addView(profileAvatarFallback, new FrameLayout.LayoutParams(dp(88), dp(88)));
        FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(
                dp(94), dp(94), Gravity.TOP | Gravity.START);
        avatarParams.setMargins(dp(12), dp(16), 0, 0);
        profileCard.addView(avatarWrap, avatarParams);

        Button changeAccount = new Button(this);
        changeAccount.setAllCaps(false);
        changeAccount.setText("Change account");
        changeAccount.setTextColor(ThemeManager.getAccentColor(this));
        changeAccount.setBackground(rectangularButton(0x331A66FF, 0x553F86FF));
        changeAccount.setOnClickListener(view -> confirmLogout());
        FrameLayout.LayoutParams changeAccountParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(48), Gravity.BOTTOM);
        changeAccountParams.setMargins(dp(12), 0, dp(12), dp(10));
        profileCard.addView(changeAccount, changeAccountParams);
        content.addView(profileCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(176)));

        profileStatsRow = new LinearLayout(this);
        profileStatsRow.setGravity(Gravity.CENTER);
        String[] statLabels = {"GAMES"};
        for (String label : statLabels) {
            LinearLayout stat = new LinearLayout(this);
            stat.setOrientation(LinearLayout.VERTICAL);
            stat.setGravity(Gravity.CENTER);
            stat.setPadding(dp(3), dp(9), dp(3), dp(9));
            stat.setBackground(glassSurface(15));
            TextView value = new TextView(this);
            value.setText("—");
            value.setTextSize(17);
            value.setTypeface(null, android.graphics.Typeface.BOLD);
            value.setGravity(Gravity.CENTER);
            stat.addView(value);
            TextView caption = new TextView(this);
            caption.setText(label);
            caption.setTextSize(9);
            caption.setTextColor(ThemeManager.getSelectedPreset(this).onSurfaceVariant);
            caption.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            captionParams.topMargin = dp(5);
            stat.addView(caption, captionParams);
            LinearLayout.LayoutParams statParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(62));
            statParams.setMargins(dp(3), 0, dp(3), 0);
            profileStatsRow.addView(stat, statParams);
        }
        content.addView(profileStatsRow, glassPanelParams(dp(5)));

        LinearLayout accountBadges = new LinearLayout(this);
        accountBadges.setOrientation(LinearLayout.VERTICAL);
        accountBadges.setPadding(dp(12), dp(10), dp(12), dp(10));
        accountBadges.setBackground(glassSurface(16));
        TextView accountBadgesTitle = new TextView(this);
        accountBadgesTitle.setText("Steam account level");
        accountBadgesTitle.setTextSize(14);
        accountBadgesTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        accountBadges.addView(accountBadgesTitle);
        profileLevel = new TextView(this);
        profileLevel.setText("Level — · — XP");
        profileLevel.setTextSize(12);
        profileLevel.setTextColor(ThemeManager.getSelectedPreset(this).onSurfaceVariant);
        profileLevel.setPadding(0, dp(4), 0, dp(8));
        accountBadges.addView(profileLevel);
        profileBadgeContainer = new LinearLayout(this);
        profileBadgeContainer.setOrientation(LinearLayout.VERTICAL);
        accountBadges.addView(profileBadgeContainer);
        content.addView(accountBadges, glassPanelParams(dp(5)));
        scrollView.addView(content);
        return scrollView;
    }

    private View createAchievementsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(sectionHeading("Steam achievements & badges"));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(10), dp(8), dp(10), dp(8));
        controls.setBackground(glassSurface(18));
        HorizontalScrollView gameChoicesScroll = new HorizontalScrollView(this);
        gameChoicesScroll.setHorizontalScrollBarEnabled(false);
        achievementGameChoices = new LinearLayout(this);
        achievementGameChoices.setGravity(Gravity.CENTER_VERTICAL);
        gameChoicesScroll.addView(achievementGameChoices, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        controls.addView(gameChoicesScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        achievementsSummary = new TextView(this);
        achievementsSummary.setText("Select a game to load its achievements.");
        achievementsSummary.setTextSize(12);
        achievementsSummary.setPadding(dp(4), dp(8), dp(4), dp(4));
        controls.addView(achievementsSummary);
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setGravity(Gravity.CENTER);
        Button achievementsButton = new Button(this);
        achievementsButton.setText("Achievements");
        achievementsButton.setAllCaps(false);
        achievementsButton.setOnClickListener(view -> showAchievementMode(false));
        Button badgesButton = new Button(this);
        badgesButton.setText("Badges");
        badgesButton.setAllCaps(false);
        badgesButton.setOnClickListener(view -> showAchievementMode(true));
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        modeParams.setMargins(0, 0, dp(4), 0);
        modeRow.addView(achievementsButton, modeParams);
        LinearLayout.LayoutParams badgeModeParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        badgeModeParams.setMargins(dp(4), 0, 0, 0);
        modeRow.addView(badgesButton, badgeModeParams);
        controls.addView(modeRow);
        page.addView(controls, glassPanelParams(dp(6)));

        badgesSummary = new TextView(this);
        badgesSummary.setText("Your public Steam badges will appear here.");
        badgesSummary.setTextSize(12);
        badgesSummary.setPadding(dp(10), dp(6), dp(10), dp(6));
        badgesSummary.setVisibility(View.GONE);
        page.addView(badgesSummary);

        achievementList = new RecyclerView(this);
        achievementList.setLayoutManager(new LinearLayoutManager(this));
        achievementList.setClipToPadding(false);
        achievementList.setPadding(dp(2), dp(2), dp(2), dp(8));
        achievementAdapter = new AchievementAdapter();
        achievementList.setAdapter(achievementAdapter);
        badgeList = new RecyclerView(this);
        badgeList.setLayoutManager(new LinearLayoutManager(this));
        badgeList.setClipToPadding(false);
        badgeList.setPadding(dp(2), dp(2), dp(2), dp(8));
        badgeAdapter = new BadgeAdapter();
        badgeList.setAdapter(badgeAdapter);
        badgeList.setVisibility(View.GONE);
        page.addView(achievementList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        page.addView(badgeList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return page;
    }

    private View createDownloadsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(sectionHeading("Game downloads"));
        downloadList = new RecyclerView(this);
        downloadList.setLayoutManager(new LinearLayoutManager(this));
        downloadList.setClipToPadding(false);
        downloadList.setPadding(0, dp(4), 0, dp(8));
        downloadAdapter = new DownloadAdapter();
        downloadList.setAdapter(downloadAdapter);
        page.addView(downloadList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return page;
    }

    private View createNewsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(sectionHeading("Steam News"));
        newsStatus = new TextView(this);
        newsStatus.setText("News from Steam, displayed natively.");
        newsStatus.setTextSize(12);
        newsStatus.setPadding(dp(10), dp(7), dp(10), dp(7));
        page.addView(newsStatus);
        newsList = new RecyclerView(this);
        newsList.setLayoutManager(new LinearLayoutManager(this));
        newsList.setClipToPadding(false);
        newsList.setPadding(dp(2), dp(2), dp(2), dp(8));
        newsAdapter = new NewsAdapter();
        newsList.setAdapter(newsAdapter);
        FrameLayout content = new FrameLayout(this);
        content.addView(newsList, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        newsArticlePage = new LinearLayout(this);
        newsArticlePage.setOrientation(LinearLayout.VERTICAL);
        newsArticlePage.setVisibility(View.GONE);
        Button backToNews = new Button(this);
        backToNews.setText("‹  All news");
        backToNews.setAllCaps(false);
        backToNews.setOnClickListener(view -> showNewsList());
        newsArticlePage.addView(backToNews, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        ScrollView articleScroll = new ScrollView(this);
        articleScroll.setFillViewport(false);
        LinearLayout articleContent = new LinearLayout(this);
        articleContent.setOrientation(LinearLayout.VERTICAL);
        articleContent.setPadding(dp(12), dp(8), dp(12), dp(18));
        newsArticleImage = new ImageView(this);
        newsArticleImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        newsArticleImage.setVisibility(View.GONE);
        articleContent.addView(newsArticleImage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));
        newsArticleTitle = new TextView(this);
        newsArticleTitle.setTextSize(20);
        newsArticleTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        newsArticleTitle.setPadding(0, dp(10), 0, dp(5));
        articleContent.addView(newsArticleTitle);
        newsArticleDate = new TextView(this);
        newsArticleDate.setTextSize(12);
        newsArticleDate.setTextColor(ThemeManager.getSelectedPreset(this).onSurfaceVariant);
        articleContent.addView(newsArticleDate);
        newsArticleContent = new TextView(this);
        newsArticleContent.setTextSize(14);
        newsArticleContent.setTextIsSelectable(true);
        newsArticleContent.setLineSpacing(dp(2), 1f);
        newsArticleContent.setPadding(0, dp(12), 0, 0);
        articleContent.addView(newsArticleContent);
        articleScroll.addView(articleContent);
        newsArticlePage.addView(articleScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        content.addView(newsArticlePage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        page.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return page;
    }

    private void showNewsArticle(SteamNewsItem item) {
        newsList.setVisibility(View.GONE);
        newsArticlePage.setVisibility(View.VISIBLE);
        newsArticleTitle.setText(item.title);
        newsArticleDate.setText(item.articles.size() == 1
                ? item.published : item.articles.size() + " updates · Latest " + item.published);
        if (item.articles.size() == 1) {
            newsArticleContent.setText(item.content.isEmpty() ? item.summary : item.content);
        } else {
            StringBuilder details = new StringBuilder();
            for (SteamNewsItem.Article article : item.articles) {
                if (details.length() > 0) details.append("\n\n");
                details.append(article.published).append("\n\n")
                        .append(article.content.isEmpty() ? item.summary : article.content);
            }
            newsArticleContent.setText(details);
        }
        if (item.imageUrl.isEmpty()) {
            Glide.with(this).clear(newsArticleImage);
            newsArticleImage.setVisibility(View.GONE);
        } else {
            newsArticleImage.setVisibility(View.VISIBLE);
            Glide.with(this).load(item.imageUrl)
                    .placeholder(R.drawable.cover_art_placeholder)
                    .error(R.drawable.cover_art_placeholder)
                    .into(newsArticleImage);
        }
    }

    private void showNewsList() {
        newsArticlePage.setVisibility(View.GONE);
        newsList.setVisibility(View.VISIBLE);
    }

    private TextView sectionHeading(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextSize(16);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(dp(10), dp(8), dp(10), dp(8));
        heading.setBackground(glassSurface(16));
        return heading;
    }

    private FrameLayout.LayoutParams matchParentParams() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private void showSteamTab(int position) {
        if (!repository.isLoggedIn() && position > 1 && position != 5) {
            Toast.makeText(this, "Sign in to use Steam " + steamTabName(position) + ".",
                    Toast.LENGTH_SHORT).show();
            updateSteamNavigation(0);
            return;
        }
        View[] pages = {storePage, libraryPage, profilePage, downloadsPage, friendsPage, newsPage};
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] != null) pages[i].setVisibility(i == position ? View.VISIBLE : View.GONE);
        }
        applySteamShadowPolicy(activityRoot);
        schedulePromotionAutoSlide();
        if (position == 0 && !storeLoaded) {
            loadStoreHome();
        } else if (position == 2) {
            refreshProfile();
        } else if (position == 3) {
            refreshDownloads();
        } else if (position == 4) {
            refreshFriends();
            updateFriendNotificationStatus();
        } else if (position == 5 && !newsLoaded) {
            newsStatus.setText("Loading Steam News…");
            newsService.load(this);
        }
    }

    private String steamTabName(int position) {
        String[] names = {"Store", "Library", "Profile", "Downloads", "Friends", "News"};
        return position >= 0 && position < names.length ? names[position] : "feature";
    }

    private void updateLibraryFilterButtons(LinearLayout row, String[] filters) {
        for (int i = 0; i < row.getChildCount() && i < filters.length; i++) {
            Button button = (Button) row.getChildAt(i);
            boolean selected = filters[i].equals(libraryFilter);
            int accent = ThemeManager.getAccentColor(this);
            button.setBackground(rectangularButton(
                    selected ? accent : 0x332D394D,
                    selected ? accent : 0x553D4C64));
            button.setTextColor(selected ? ThemePreset.onAccentFor(accent)
                    : ThemeManager.getOnSurfaceTextColor(this));
            button.setAlpha(selected ? 1f : 0.9f);
        }
    }

    private void applyLibraryFilters() {
        filteredGames.clear();
        for (SteamGame game : games) {
            boolean include;
            boolean isGame = "game".equalsIgnoreCase(game.type);
            switch (libraryFilter) {
                case "Installed":
                    include = isGame && game.installed;
                    break;
                case "Not installed":
                    include = isGame && !game.installed;
                    break;
                default:
                    include = isGame;
                    break;
            }
            if (include) filteredGames.add(game);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void loadStoreHome() {
        if (storefront == null || storeSectionsContainer == null) return;
        storeLoaded = true;
        currentSearchQuery = null;
        if (storeHomeButton != null) storeHomeButton.setVisibility(View.GONE);
        if (storeSearchInput != null) storeSearchInput.setText("");
        storeRequestGeneration++;
        storeRequestsRemaining = 2;
        storeSections.clear();
        storeSectionsContainer.removeAllViews();
        storeMessage.setText("Loading the latest Steam releases and promotions…");
        storeMessage.setVisibility(View.VISIBLE);
        storeProgress.setVisibility(View.VISIBLE);
        if (storePage != null) storePage.post(() -> storePage.fullScroll(View.FOCUS_UP));
        storefront.loadLandingPage(storeRequestGeneration);
    }

    private void searchStore() {
        String query = storeSearchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter a game title to search the Steam Store.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        currentSearchQuery = query;
        storeHomeButton.setVisibility(View.VISIBLE);
        storeRequestGeneration++;
        storeSections.clear();
        storeSectionsContainer.removeAllViews();
        storeMessage.setText("Searching Steam Store…");
        storeMessage.setVisibility(View.VISIBLE);
        storeProgress.setVisibility(View.VISIBLE);
        if (storePage != null) storePage.post(() -> storePage.fullScroll(View.FOCUS_UP));
        storefront.search(query, storeRequestGeneration);
    }

    @Override
    public void onSectionLoaded(int requestId, String section,
                                List<SteamStorefront.StoreItem> items) {
        if (requestId != storeRequestGeneration) return;
        storeRequestsRemaining = Math.max(0, storeRequestsRemaining - 1);
        storeSections.put(section, items);
        for (SteamStorefront.StoreItem item : items) {
            item.owned = isStoreItemOwned(item.appId);
        }
        renderStoreSections();
        if (storeRequestsRemaining == 0) {
            storeProgress.setVisibility(View.GONE);
            storeMessage.setText("");
            storeMessage.setVisibility(View.GONE);
        }
    }

    @Override
    public void onSearchLoaded(int requestId, String query,
                               List<SteamStorefront.StoreItem> items) {
        if (requestId != storeRequestGeneration || !query.equals(currentSearchQuery)) return;
        storeSections.put("Search results", items);
        for (SteamStorefront.StoreItem item : items) {
            item.owned = isStoreItemOwned(item.appId);
        }
        storeProgress.setVisibility(View.GONE);
        storeMessage.setText(items.isEmpty() ? "No games found." :
                "Search results for “" + query + "”");
        storeMessage.setVisibility(View.VISIBLE);
        renderStoreSections();
    }

    @Override
    public void onSearchItemUpdated(int requestId, String query, SteamStorefront.StoreItem item) {
        if (requestId != storeRequestGeneration || !query.equals(currentSearchQuery)) return;
        item.owned = isStoreItemOwned(item.appId);
        renderStoreSections();
    }

    @Override
    public void onStoreError(int requestId, String section, String message) {
        if (requestId != storeRequestGeneration) return;
        storeProgress.setVisibility(View.GONE);
        storeMessage.setVisibility(View.VISIBLE);
        if ("store".equals(section) || "free".equals(section)) {
            storeRequestsRemaining = Math.max(0, storeRequestsRemaining - 1);
            storeMessage.setText("Steam Store partially unavailable: " + message);
        } else if ("search".equals(section)) {
            List<SteamStorefront.StoreItem> results = storeSections.get("Search results");
            if (results != null) {
                for (SteamStorefront.StoreItem item : results) {
                    if ("Loading price…".equals(item.price)) item.price = "Price unavailable";
                }
                renderStoreSections();
            }
            storeMessage.setText("Some Steam Store details could not be loaded: " + message);
        } else {
            storeMessage.setText("Steam Store request failed: " + message);
        }
        if (storeRequestsRemaining == 0) storeProgress.setVisibility(View.GONE);
    }

    private boolean isStoreItemOwned(int appId) {
        return repository.isInitialized() && repository.getDatabase().isOwned(appId);
    }

    private void renderStoreSections() {
        if (storeSectionsContainer == null) return;
        stopPromotionAutoSlide();
        storeSectionsContainer.removeAllViews();
        List<SteamStorefront.StoreItem> promotions = storeSections.get("Hot promotions");
        if (promotions != null && !promotions.isEmpty()) addPromotionsCarousel(promotions);
        for (java.util.Map.Entry<String, List<SteamStorefront.StoreItem>> section
                : storeSections.entrySet()) {
            if ("Hot promotions".equals(section.getKey())) continue;
            TextView heading = sectionHeading(section.getKey());
            LinearLayout.LayoutParams headingParams = glassPanelParams(dp(3));
            storeSectionsContainer.addView(heading, headingParams);
            List<SteamStorefront.StoreItem> items = section.getValue();
            if (items.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("No games are available in this section right now.");
                empty.setTextSize(12);
                empty.setPadding(dp(12), dp(10), dp(12), dp(10));
                storeSectionsContainer.addView(empty);
                continue;
            }
            HorizontalScrollView horizontalScroll = new HorizontalScrollView(this);
            horizontalScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout cards = new LinearLayout(this);
            cards.setOrientation(LinearLayout.HORIZONTAL);
            for (SteamStorefront.StoreItem item : items) {
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        dp(160), dp(244));
                cardParams.setMargins(dp(5), 0, dp(5), 0);
                cards.addView(createStoreCard(item), cardParams);
            }
            horizontalScroll.addView(cards);
            LinearLayout.LayoutParams scrollParams = glassPanelParams(dp(5));
            storeSectionsContainer.addView(horizontalScroll, scrollParams);
        }
    }

    private void addPromotionsCarousel(List<SteamStorefront.StoreItem> items) {
        List<SteamStorefront.StoreItem> slides = new ArrayList<>(
                items.subList(0, Math.min(3, items.size())));
        storeSectionsContainer.addView(sectionHeading("Hot promotions"),
                glassPanelParams(dp(2)));

        RecyclerView carousel = new RecyclerView(this);
        carousel.setClipToPadding(false);
        carousel.setPadding(0, dp(2), 0, dp(2));
        carousel.setOverScrollMode(View.OVER_SCROLL_NEVER);
        carousel.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(carousel);
        carousel.setAdapter(new PromotionAdapter(slides));
        promotionCarousel = carousel;
        promotionScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    promotionHandler.removeCallbacks(promotionAutoSlide);
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    schedulePromotionAutoSlide();
                }
            }
        };
        carousel.addOnScrollListener(promotionScrollListener);
        carousel.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                if (promotionAutoSlide != null) {
                    promotionHandler.removeCallbacks(promotionAutoSlide);
                }
            } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                schedulePromotionAutoSlide();
            }
            return false;
        });
        promotionAutoSlide = () -> {
            if (!activityResumed || selectedSteamTab != 0 || promotionCarousel != carousel
                    || carousel.getVisibility() != View.VISIBLE || slides.size() < 2) return;
            RecyclerView.LayoutManager layoutManager = carousel.getLayoutManager();
            if (!(layoutManager instanceof LinearLayoutManager)) return;
            int current = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
            int next = (Math.max(0, current) + 1) % slides.size();
            carousel.smoothScrollToPosition(next);
        };
        schedulePromotionAutoSlide();
        LinearLayout.LayoutParams carouselParams = glassPanelParams(0);
        carouselParams.height = dp(172);
        storeSectionsContainer.addView(carousel, carouselParams);

        LinearLayout indicatorRow = new LinearLayout(this);
        indicatorRow.setGravity(Gravity.CENTER);
        indicatorRow.setPadding(0, dp(3), 0, 0);
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        List<View> dotViews = new ArrayList<>();
        for (int i = 0; i < slides.size(); i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
            dotParams.setMargins(dp(3), 0, dp(3), 0);
            dots.addView(dot, dotParams);
            dotViews.add(dot);
        }
        indicatorRow.addView(dots, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(14)));
        storeSectionsContainer.addView(indicatorRow, glassPanelParams(0));

        Runnable updateIndicator = () -> {
            View snapped = snapHelper.findSnapView(carousel.getLayoutManager());
            int position = snapped == null ? 0
                    : carousel.getLayoutManager().getPosition(snapped);
            position = Math.max(0, Math.min(position, slides.size() - 1));
            for (int i = 0; i < dotViews.size(); i++) {
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(i == position
                        ? ThemeManager.getAccentColor(this) : 0x887B8797);
                dotViews.get(i).setBackground(dot);
                dotViews.get(i).setScaleX(i == position ? 1.2f : 1f);
            }
        };
        carousel.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) updateIndicator.run();
            }
        });
        carousel.post(updateIndicator);
    }

    private int promotionCardWidth() {
        return Math.max(dp(280),
                getResources().getDisplayMetrics().widthPixels - dp(36));
    }

    private void schedulePromotionAutoSlide() {
        if (promotionAutoSlide != null) {
            promotionHandler.removeCallbacks(promotionAutoSlide);
        }
        if (promotionAutoSlide == null || promotionCarousel == null || !activityResumed
                || selectedSteamTab != 0 || promotionCarousel.getVisibility() != View.VISIBLE
                || promotionCarousel.getAdapter() == null
                || promotionCarousel.getAdapter().getItemCount() < 2) {
            return;
        }
        promotionHandler.postDelayed(promotionAutoSlide, 5_000L);
    }

    private void stopPromotionAutoSlide() {
        if (promotionAutoSlide != null) {
            promotionHandler.removeCallbacks(promotionAutoSlide);
        }
        if (promotionCarousel != null && promotionScrollListener != null) {
            promotionCarousel.removeOnScrollListener(promotionScrollListener);
        }
        promotionCarousel = null;
        promotionScrollListener = null;
        promotionAutoSlide = null;
    }

    private final class PromotionAdapter extends RecyclerView.Adapter<PromotionAdapter.Holder> {
        private final List<SteamStorefront.StoreItem> items;

        PromotionAdapter(List<SteamStorefront.StoreItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout banner = new FrameLayout(SteamStoreActivity.this);
            banner.setBackground(glassSurface(18));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    promotionCardWidth(), RecyclerView.LayoutParams.MATCH_PARENT);
            params.setMargins(dp(4), dp(2), dp(4), dp(2));
            banner.setLayoutParams(params);

            ImageView artwork = new ImageView(SteamStoreActivity.this);
            artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
            banner.addView(artwork, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            View shade = new View(SteamStoreActivity.this);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000, 0x55000000, 0xE6000000}));
            banner.addView(shade, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout details = new LinearLayout(SteamStoreActivity.this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(14), dp(10), dp(14), dp(12));
            FrameLayout.LayoutParams detailsParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            banner.addView(details, detailsParams);

            TextView title = new TextView(SteamStoreActivity.this);
            title.setTextColor(Color.WHITE);
            title.setTextSize(18);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(title);

            TextView price = new TextView(SteamStoreActivity.this);
            price.setTextColor(0xFFE6EDF7);
            price.setTextSize(12);
            LinearLayout.LayoutParams priceParams = glassPanelParams(dp(3));
            details.addView(price, priceParams);

            TextView offer = new TextView(SteamStoreActivity.this);
            offer.setTextColor(Color.WHITE);
            offer.setTextSize(10);
            offer.setTypeface(null, android.graphics.Typeface.BOLD);
            offer.setGravity(Gravity.CENTER);
            offer.setPadding(dp(8), dp(5), dp(8), dp(5));
            offer.setBackground(glassSurface(12));
            FrameLayout.LayoutParams offerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            offerParams.setMargins(0, dp(10), dp(10), 0);
            banner.addView(offer, offerParams);
            return new Holder(banner, artwork, title, price, offer);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            SteamStorefront.StoreItem item = items.get(position);
            String imageUrl = item.imageUrl == null || item.imageUrl.isEmpty()
                    ? "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/"
                    + item.appId + "/header.jpg" : item.imageUrl;
            Glide.with(SteamStoreActivity.this).load(imageUrl)
                    .placeholder(R.drawable.cover_art_placeholder)
                    .error(R.drawable.cover_art_placeholder)
                    .centerCrop().into(holder.artwork);
            holder.title.setText(item.name);
            String priceText = item.price == null || item.price.isEmpty()
                    ? "Price unavailable" : item.price;
            if (item.originalPrice != null && !item.originalPrice.isEmpty()
                    && !item.originalPrice.equals(item.price)) {
                priceText = item.originalPrice + " → " + priceText;
            }
            holder.price.setText(item.owned ? "In your library · " + priceText : priceText);
            holder.offer.setText(item.discountPercent > 0
                    ? "-" + item.discountPercent + "%"
                    : item.free ? "FREE" : "FEATURED");
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView artwork;
            final TextView title;
            final TextView price;
            final TextView offer;

            Holder(View itemView, ImageView artwork, TextView title, TextView price, TextView offer) {
                super(itemView);
                this.artwork = artwork;
                this.title = title;
                this.price = price;
                this.offer = offer;
            }
        }
    }

    private View createStoreCard(SteamStorefront.StoreItem item) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(glassSurface(16));
        card.setClipToOutline(true);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(7), dp(7), dp(7), dp(7));
        content.setBackgroundColor(Color.TRANSPARENT);
        card.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView artwork = new ImageView(this);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackgroundColor(Color.TRANSPARENT);
        String imageUrl = item.imageUrl == null || item.imageUrl.isEmpty()
                ? "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/"
                + item.appId + "/header.jpg" : item.imageUrl;
        Glide.with(this).load(imageUrl)
                .placeholder(R.drawable.cover_art_placeholder)
                .error(R.drawable.cover_art_placeholder)
                .centerCrop().into(artwork);
        content.addView(artwork, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(96)));

        TextView title = new TextView(this);
        title.setText(item.name);
        title.setTextSize(13);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36));
        titleParams.topMargin = dp(5);
        content.addView(title, titleParams);

        TextView price = new TextView(this);
        String priceText = item.price == null || item.price.isEmpty()
                ? "Price unavailable" : item.price;
        if (item.discountPercent > 0) {
            priceText = item.discountPercent + "% off · " + priceText;
        } else if (!item.originalPrice.isEmpty() && !item.originalPrice.equals(item.price)) {
            priceText = item.originalPrice + " → " + priceText;
        }
        price.setText(priceText);
        price.setTextSize(11);
        price.setMaxLines(2);
        price.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(price, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(31)));

        Button action = new Button(this);
        action.setAllCaps(false);
        action.setTextSize(11);
        action.setMinHeight(dp(34));
        action.setMinimumHeight(dp(34));
        action.setPadding(dp(4), 0, dp(4), 0);
        action.setTextColor(ThemeManager.getOnSurfaceTextColor(this));
        action.setBackground(glassSurface(12));
        action.setElevation(glassElevation(2));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34));
        actionParams.topMargin = dp(4);
        content.addView(action, actionParams);
        boolean pending = freeGameRequests.contains(item.appId);
        if (item.owned) {
            action.setText("In Library");
            action.setEnabled(false);
        } else if (pending) {
            action.setText("Adding…");
            action.setEnabled(false);
        } else if (item.free) {
            action.setText("Add to library");
            action.setOnClickListener(view -> {
                if (!repository.isLoggedIn()) {
                    Toast.makeText(this, "Sign in to Steam to add free games.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                freeGameRequests.add(item.appId);
                renderStoreSections();
                try {
                    repository.requestFreeLicense(item.appId);
                } catch (RuntimeException e) {
                    freeGameRequests.remove(item.appId);
                    renderStoreSections();
                    Toast.makeText(this, "Could not add game: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        } else {
            action.setText("Not owned");
            action.setEnabled(false);
        }
        return card;
    }

    private void refreshProfile() {
        if (profileName == null) return;
        String name = repository.getPersonaName();
        profileName.setText(name.isEmpty() ? "Steam profile" : name);
        profileId.setText("SteamID64: " + repository.getSteamId64());
        profileStatus.setText("Status: " + repository.getPersonaStatus());
        String avatarUrl = steamAvatarUrl(repository.getPersonaAvatar());
        profileAvatarFallback.setText(name.isEmpty() ? "S"
                : name.substring(0, 1).toUpperCase(Locale.ROOT));
        if (avatarUrl == null) {
            profileAvatar.setVisibility(View.GONE);
            profileAvatarFallback.setVisibility(View.VISIBLE);
        }
        if (!java.util.Objects.equals(profileAvatarRequestUrl, avatarUrl)) {
            profileAvatarRequestUrl = avatarUrl;
            if (avatarUrl == null) {
                Glide.with(this).clear(profileAvatar);
            } else {
                String fallbackUrl = avatarUrl.replace(
                        "avatars.fastly.steamstatic.com/",
                        "avatars.steamstatic.com/");
                String legacyFallbackUrl = avatarUrl.replace(
                        "avatars.fastly.steamstatic.com/",
                        "avatars.akamai.steamstatic.com/");
                String finalFallbackUrl = avatarUrl.replace(
                        "avatars.fastly.steamstatic.com/",
                        "avatars.cloudflare.steamstatic.com/");
                profileAvatar.setVisibility(View.VISIBLE);
                profileAvatarFallback.setVisibility(View.VISIBLE);
                com.bumptech.glide.request.RequestListener<Drawable> avatarListener =
                        new com.bumptech.glide.request.RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(
                                    com.bumptech.glide.load.engine.GlideException e,
                                    Object model,
                                    com.bumptech.glide.request.target.Target<Drawable> target,
                                    boolean isFirstResource) {
                                Log.w("SteamStoreActivity", "Failed to load Steam profile avatar", e);
                                profileAvatar.setVisibility(View.GONE);
                                profileAvatarFallback.setVisibility(View.VISIBLE);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(
                                    Drawable resource,
                                    Object model,
                                    com.bumptech.glide.request.target.Target<Drawable> target,
                                    com.bumptech.glide.load.DataSource dataSource,
                                    boolean isFirstResource) {
                                profileAvatar.setVisibility(View.VISIBLE);
                                profileAvatarFallback.setVisibility(View.GONE);
                                return false;
                            }
                        };
                com.bumptech.glide.RequestBuilder<Drawable> lastFallback =
                        Glide.with(this).load(finalFallbackUrl)
                                .listener(avatarListener)
                                .error(R.drawable.cover_art_placeholder);
                com.bumptech.glide.RequestBuilder<Drawable> fallback =
                        Glide.with(this).load(legacyFallbackUrl)
                                .listener(avatarListener)
                                .error(lastFallback);
                com.bumptech.glide.RequestBuilder<Drawable> standardFallback =
                        Glide.with(this).load(fallbackUrl)
                                .listener(avatarListener)
                                .error(fallback);
                Glide.with(this).load(avatarUrl)
                        .listener(avatarListener)
                        .error(standardFallback)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                        .priority(com.bumptech.glide.Priority.IMMEDIATE)
                        .override(dp(88), dp(88))
                        .placeholder(R.drawable.cover_art_placeholder)
                        .circleCrop()
                        .into(profileAvatar);
            }
        }
        refreshProfileStatistics();
        profileLevel.setText(accountLevel.isEmpty()
                ? "Level — · — XP"
                : "Level " + accountLevel + (accountXp.isEmpty() ? "" : " · " + accountXp + " XP"));
        refreshProfileBadges();
        if (!badgesLoaded) loadSteamBadges();
    }

    private void refreshProfileStatistics() {
        if (profileStatsRow == null) return;
        List<SteamGame> ownedGames = repository.isInitialized()
                ? repository.getDatabase().getOwnedGames() : new ArrayList<>();
        String[] values = {String.valueOf(ownedGames.size())};
        for (int i = 0; i < profileStatsRow.getChildCount() && i < values.length; i++) {
            LinearLayout stat = (LinearLayout) profileStatsRow.getChildAt(i);
            ((TextView) stat.getChildAt(0)).setText(values[i]);
        }
    }

    private void copySteamId() {
        String steamId = repository.getSteamId64();
        if (steamId.isEmpty()) {
            Toast.makeText(this, "SteamID64 is not available yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Could not access clipboard.", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SteamID64", steamId));
        Toast.makeText(this, "SteamID64 copied.", Toast.LENGTH_SHORT).show();
    }

    private static String steamAvatarUrl(byte[] avatarHash) {
        if (avatarHash == null || avatarHash.length == 0) return null;
        boolean hasNonZeroByte = false;
        StringBuilder hash = new StringBuilder(avatarHash.length * 2);
        for (byte value : avatarHash) {
            hasNonZeroByte |= value != 0;
            hash.append(String.format(Locale.ROOT, "%02x", value & 0xFF));
        }
        return hasNonZeroByte
            ? "https://avatars.fastly.steamstatic.com/" + hash + "_full.jpg" : null;
    }

    private void refreshProfileBadges() {
        if (profileBadgeContainer == null) return;
        profileBadgeContainer.removeAllViews();
        if (badges.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(badgesLoaded
                    ? "No public badges are available."
                    : "Loading public Steam badges…");
            empty.setTextSize(11);
            empty.setTextColor(ThemeManager.getSelectedPreset(this).onSurfaceVariant);
            profileBadgeContainer.addView(empty);
            return;
        }
        int displayed = Math.min(badges.size(), 5);
        for (int i = 0; i < displayed; i++) {
            SteamBadge badge = badges.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(5), 0, dp(5));
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(image, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(9), 0, 0, 0);
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView title = new TextView(this);
            title.setText(badge.name);
            title.setTextSize(12);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(title);
            TextView detail = new TextView(this);
            detail.setText(badge.xp.isEmpty() ? "Steam badge" : badge.xp + " XP");
            detail.setTextSize(10);
            detail.setTextColor(ThemeManager.getSelectedPreset(this).onSurfaceVariant);
            text.addView(detail);
            profileBadgeContainer.addView(row);
            Glide.with(this).load(badge.imageUrl)
                    .placeholder(R.drawable.cover_art_placeholder)
                    .error(R.drawable.cover_art_placeholder)
                    .into(image);
        }
        if (badges.size() > displayed) {
            TextView more = new TextView(this);
            more.setText("+" + (badges.size() - displayed)
                    + " more badges · See all in Achievements & Badges");
            more.setTextSize(10);
            more.setTextColor(ThemeManager.getAccentColor(this));
            more.setPadding(0, dp(4), 0, 0);
            profileBadgeContainer.addView(more);
        }
    }

    private void refreshAchievementGameChoices() {
        if (achievementGameChoices == null) return;
        achievementGameChoices.removeAllViews();
        if (games.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Sync your Steam library to choose a game.");
            empty.setTextSize(12);
            achievementGameChoices.addView(empty);
            return;
        }
        for (SteamGame game : games) {
            Button choice = new Button(this);
            choice.setAllCaps(false);
            choice.setText(game.name);
            choice.setSingleLine(true);
            choice.setMaxWidth(dp(190));
            choice.setEllipsize(android.text.TextUtils.TruncateAt.END);
            boolean selected = game.appId == selectedAchievementAppId;
            int accent = ThemeManager.getAccentColor(this);
            choice.setBackground(rectangularButton(
                    selected ? accent : 0x332D394D, selected ? accent : 0x553D4C64));
            choice.setTextColor(selected ? ThemePreset.onAccentFor(accent)
                    : ThemeManager.getOnSurfaceTextColor(this));
            choice.setOnClickListener(view -> selectAchievementGame(game));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            params.setMargins(0, 0, dp(6), 0);
            achievementGameChoices.addView(choice, params);
        }
    }

    private void selectAchievementGame(SteamGame game) {
        selectedAchievementAppId = game.appId;
        refreshAchievementGameChoices();
        achievementItems.clear();
        achievementAdapter.notifyDataSetChanged();
        achievementsSummary.setText("Loading achievements for " + game.name + "…");
        try {
            repository.requestAchievements(game.appId);
        } catch (RuntimeException e) {
            achievementsSummary.setText("Could not load achievements: " + e.getMessage());
        }
    }

    private void refreshAchievements() {
        achievementItems.clear();
        achievementItems.addAll(repository.getAchievements(selectedAchievementAppId));
        int unlocked = 0;
        for (SteamAchievement item : achievementItems) {
            if (item.unlocked) unlocked++;
        }
        achievementsSummary.setText(achievementItems.isEmpty()
                ? "No achievement data is available for this game."
                : unlocked + " of " + achievementItems.size() + " achievements unlocked");
        achievementAdapter.notifyDataSetChanged();
    }

    private void showAchievementMode(boolean badgesMode) {
        if (achievementGameChoices != null) {
            achievementGameChoices.setVisibility(badgesMode ? View.GONE : View.VISIBLE);
        }
        if (achievementsSummary != null) {
            achievementsSummary.setVisibility(badgesMode ? View.GONE : View.VISIBLE);
        }
        if (achievementList != null) {
            achievementList.setVisibility(badgesMode ? View.GONE : View.VISIBLE);
        }
        if (badgesSummary != null) {
            badgesSummary.setVisibility(badgesMode ? View.VISIBLE : View.GONE);
        }
        if (badgeList != null) {
            badgeList.setVisibility(badgesMode ? View.VISIBLE : View.GONE);
        }
        if (badgesMode) loadSteamBadges();
    }

    private void loadSteamBadges() {
        if (badgesLoaded || badgesLoading) return;
        String steamId = repository.getSteamId64();
        if (steamId.isEmpty()) return;
        badgesLoading = true;
        badgeService.load(steamId, this);
    }

    private void updateBadgesSummary(String text) {
        TextView summary = badgesSummary;
        if (summary == null || summary.getWindowToken() == null) return;
        summary.setText(text);
    }

    @Override
    public void onBadgesLoaded(List<SteamBadge> result, String level, String totalXp) {
        badges.clear();
        badges.addAll(result);
        accountLevel = level == null ? "" : level;
        accountXp = totalXp == null ? "" : totalXp.replace(",", "");
        badgesLoaded = true;
        badgesLoading = false;
        updateBadgesSummary(badges.size() + " badges loaded from your public Steam profile.");
        if (badgeAdapter != null) badgeAdapter.notifyDataSetChanged();
        refreshProfileBadges();
        if (profileLevel != null) {
            profileLevel.setText(accountLevel.isEmpty()
                    ? "Level — · — XP"
                    : "Level " + accountLevel
                    + (accountXp.isEmpty() ? "" : " · " + accountXp + " XP"));
        }
    }

    @Override
    public void onBadgesFailed(String reason) {
        badges.clear();
        badgesLoaded = false;
        badgesLoading = false;
        if (badgeAdapter != null) badgeAdapter.notifyDataSetChanged();
        refreshProfileBadges();
        updateBadgesSummary("Could not load badges: " + reason);
    }

    @Override
    public void onNewsLoaded(List<SteamNewsItem> result) {
        newsItems.clear();
        newsItems.addAll(result);
        newsLoaded = true;
        newsStatus.setText(result.size() + " articles from the Steam News feed.");
        newsStatus.setOnClickListener(null);
        newsAdapter.notifyDataSetChanged();
    }

    @Override
    public void onNewsFailed(String message) {
        newsLoaded = false;
        newsStatus.setText("Could not load Steam News: " + message + " · Tap to retry.");
        newsStatus.setOnClickListener(view -> {
            newsStatus.setText("Loading Steam News…");
            newsService.load(this);
        });
    }

    private void refreshFriends() {
        if (friendAdapter == null) return;
        friends.clear();
        friends.addAll(repository.getFriends());
        friendAdapter.notifyDataSetChanged();
    }

    private void refreshDownloads() {
        if (downloadAdapter == null) return;
        downloads.clear();
        downloads.addAll(SteamDepotDownloadManager.getDownloads());
        downloadAdapter.notifyDataSetChanged();
    }

    private void submitLogin() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter your Steam account name and password.", Toast.LENGTH_SHORT).show();
            return;
        }
        passwordInput.setText("");
        setBusy(true, "Starting secure Steam sign-in…");
        authManager.login(username, password, this);
    }

    private void startQrLogin() {
        setBusy(true, "Preparing Steam QR sign-in…");
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(8));
        qrImage = new ImageView(this);
        qrImage.setAdjustViewBounds(true);
        content.addView(qrImage, new LinearLayout.LayoutParams(dp(260), dp(260)));
        qrStatus = new TextView(this);
        qrStatus.setText("Waiting for Steam…");
        qrStatus.setGravity(Gravity.CENTER);
        content.addView(qrStatus);
        qrDialog = new AlertDialog.Builder(this)
                .setTitle("Steam QR sign-in")
                .setView(content)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> qrAuthManager.cancel())
                .create();
        qrDialog.setOnCancelListener(dialog -> qrAuthManager.cancel());
        qrDialog.show();
        styleGlassDialog(qrDialog);
        qrAuthManager.login(this);
    }

    private void confirmLogout() {
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("Sign out of Steam?")
                .setMessage(SteamDepotDownloadManager.hasActiveDownloads()
                        ? "The encrypted session will be removed and active downloads will be cancelled."
                        : "The saved encrypted session will be removed from this device.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Sign out", (dialog, which) -> {
                    repository.logout();
                    SteamConnectionService.stop(this);
                    loginPromptDismissed = false;
                    PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .remove(PREFERENCE_LOGIN_PROMPT_DISMISSED).apply();
                    refreshState();
                })
                .create();
        alertDialog.show();
        styleGlassDialog(alertDialog);
    }

    private void refreshState() {
        boolean loggedIn = repository.isLoggedIn();
        accountPanel.setVisibility(View.GONE);
        String headerStatus = statusView.getText().toString();
        boolean loginStatus = headerStatus.startsWith("Signed in")
                || "Library updated".equals(headerStatus);
        statusView.setVisibility(loggedIn && loginStatus ? View.GONE : View.VISIBLE);
        if (loggedIn) {
            dismissLoginDialog();
            dismissReconnectDialog();
            String username = repository.getUsername();
            accountView.setText(username.isEmpty() ? "Signed in to Steam" : "Signed in as " + username);
            games.clear();
            games.addAll(repository.getDatabase().getOwnedGames());
            applyLibraryFilters();
            refreshAchievementGameChoices();
            refreshStoreOwnership();
            refreshProfile();
            refreshDownloads();
        } else {
            games.clear();
            applyLibraryFilters();
            if (!repository.isConnected()) statusView.setText("Connecting to Steam…");
            else statusView.setText(loginPromptDismissed
                    ? "Tap here to sign in to Steam" : "Sign in to load your Steam library.");
            statusView.setClickable(repository.isConnected());
            if (!repository.isConnected()) showReconnectDialog();
            else if (!repository.isLoggedIn()) showLoginDialog();
            if (selectedSteamTab > 1 && selectedSteamTab != 5) {
                selectSteamTab(0);
            }
        }
    }

    private void refreshOfficialSteamInstallations() {
        if (officialInstallScanRunning || games.isEmpty() || !repository.isInitialized()
                || isFinishing()) return;
        List<Container> containers = new ContainerManager(this).getContainers();
        Map<Integer, Container> containersById = new HashMap<>();
        for (Container container : containers) containersById.put(container.id, container);
        List<SteamGame> gameSnapshot = new ArrayList<>(games);
        android.content.SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(this);
        officialInstallScanRunning = true;
        steamClientInstallerExecutor.execute(() -> {
            boolean changed = false;
            try {
                for (SteamGame game : gameSnapshot) {
                    if (Thread.currentThread().isInterrupted()) return;
                    int containerId = preferences.getInt(
                            STEAM_CLIENT_CONTAINER_PREFIX + game.appId, -1);
                    Container container = containersById.get(containerId);
                    if (container == null) continue;
                    File installDirectory = findOfficialSteamGameDirectory(container, game.appId);
                    if (installDirectory == null) continue;
                    if (!game.installed || game.installedSizeBytes <= 0
                            || !installDirectory.getAbsolutePath()
                            .equals(new File(game.installedPath).getAbsolutePath())) {
                        long actualSize = SteamDepotDownloadManager.getDirectorySizeBytes(
                                installDirectory);
                        repository.getDatabase().markInstalled(game.appId,
                                installDirectory.getAbsolutePath(), actualSize);
                        changed = true;
                    }
                }
            } catch (IOException e) {
                Log.e("SteamStoreActivity", "Could not scan official Steam game installations", e);
            } finally {
                officialInstallScanRunning = false;
            }
            if (changed && !isFinishing()) {
                runOnUiThread(() -> {
                    if (isFinishing() || !repository.isLoggedIn()) return;
                    games.clear();
                    games.addAll(repository.getDatabase().getOwnedGames());
                    applyLibraryFilters();
                    refreshAchievementGameChoices();
                    refreshProfileStatistics();
                });
            }
        });
    }

    private void refreshStoreOwnership() {
        for (List<SteamStorefront.StoreItem> section : storeSections.values()) {
            for (SteamStorefront.StoreItem item : section) {
                item.owned = item.owned || isStoreItemOwned(item.appId);
            }
        }
        renderStoreSections();
    }

    private static int parseEventAppId(String event) {
        int separator = event.indexOf(':');
        int nextSeparator = separator < 0 ? -1 : event.indexOf(':', separator + 1);
        if (nextSeparator < 0) return 0;
        try {
            return Integer.parseInt(event.substring(separator + 1, nextSeparator));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String eventDetail(String event) {
        int separator = event.indexOf(':');
        int nextSeparator = separator < 0 ? -1 : event.indexOf(':', separator + 1);
        return nextSeparator < 0 ? "Unknown error" : event.substring(nextSeparator + 1);
    }

    private static int parseEventValueAppId(String event) {
        int separator = event.indexOf(':');
        if (separator < 0) return 0;
        try {
            return Integer.parseInt(event.substring(separator + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void onSteamEvent(String event) {
        runOnUiThread(() -> {
            if ("Connected".equals(event)) {
                dismissReconnectDialog();
                statusView.setText("Connected to Steam");
                if (!repository.isLoggedIn()) showLoginDialog();
            } else if ("LoggedIn".equals(event)) {
                loginPromptDismissed = false;
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .remove(PREFERENCE_LOGIN_PROMPT_DISMISSED).apply();
                dismissLoginDialog();
                dismissReconnectDialog();
                statusView.setText("Signed in. Loading your library…");
                setBusy(true, null);
                refreshState();
            } else if (event.startsWith("LoginFailed:")) {
                setBusy(false, "Steam sign-in failed: " + event.substring(event.indexOf(':') + 1));
            } else if (event.startsWith("LibraryProgress:")) {
                setBusy(true, event.substring(event.indexOf(':') + 1));
            } else if (event.startsWith("LibrarySynced:")) {
                setBusy(false, null);
                statusView.setText("Library updated");
                refreshState();
            } else if (event.startsWith("LibraryFailed:")) {
                setBusy(false, "Library sync failed: " + event.substring(event.indexOf(':') + 1));
            } else if (event.startsWith("DownloadFailed:")) {
                setBusy(false, "Download failed: " + eventDetail(event));
                refreshState();
            } else if (event.startsWith("DownloadComplete:")) {
                setBusy(false, "Game installed. Choose an executable to add a container shortcut.");
                refreshState();
            } else if (event.startsWith("DownloadStarted:")
                    || event.startsWith("DownloadQueued:")) {
                setBusy(false, "Downloading Steam game…");
                adapter.notifyDataSetChanged();
                refreshDownloads();
            } else if (event.startsWith("DownloadProgress:")
                    || event.startsWith("DownloadStatus:")
                    || event.startsWith("DownloadPaused:")) {
                refreshDownloads();
                adapter.notifyDataSetChanged();
            } else if (event.startsWith("DownloadCancelled:")) {
                setBusy(false, "Download cancelled");
                adapter.notifyDataSetChanged();
                refreshDownloads();
            } else if (event.startsWith("FreeLicenseAdded:")) {
                int appId = parseEventAppId(event);
                freeGameRequests.remove(appId);
                for (List<SteamStorefront.StoreItem> section : storeSections.values()) {
                    for (SteamStorefront.StoreItem item : section) {
                        if (item.appId == appId) item.owned = true;
                    }
                }
                storeMessage.setText("Free game added to your Steam account.");
                storeMessage.setVisibility(View.VISIBLE);
                renderStoreSections();
                refreshState();
            } else if (event.startsWith("FreeLicenseFailed:")) {
                int appId = parseEventAppId(event);
                freeGameRequests.remove(appId);
                renderStoreSections();
                Toast.makeText(this, "Could not add free game: " + eventDetail(event),
                        Toast.LENGTH_LONG).show();
            } else if (event.startsWith("AchievementsUpdated:")) {
                int appId = parseEventValueAppId(event);
                if (appId == selectedAchievementAppId) refreshAchievements();
            } else if (event.startsWith("AchievementsFailed:")) {
                int appId = parseEventAppId(event);
                if (appId == selectedAchievementAppId) {
                    achievementsSummary.setText("Could not load achievements: " + eventDetail(event));
                    achievementAdapter.notifyDataSetChanged();
                }
            } else if ("GameUninstalled".equals(event) || event.startsWith("GameUninstalled:")) {
                refreshState();
                Toast.makeText(this, "Steam game removed from this device.",
                        Toast.LENGTH_SHORT).show();
            } else if (event.startsWith("GameUninstallFailed:")) {
                Toast.makeText(this, "Could not remove Steam game: " + eventDetail(event),
                        Toast.LENGTH_LONG).show();
            } else if ("FriendsUpdated".equals(event)) {
                refreshFriends();
                refreshProfile();
            } else if (event.startsWith("FriendMessage:")) {
                appendFriendMessage(event);
            } else if (event.startsWith("FriendMessageSendFailed:")) {
                Toast.makeText(this, "Could not send message: "
                        + event.substring(event.indexOf(':') + 1), Toast.LENGTH_LONG).show();
            } else if (event.startsWith("ConnectionError:")
                    || event.startsWith("SessionError:")) {
                if (!repository.isConnected()) {
                    statusView.setText("Steam disconnected. Reconnecting…");
                    showReconnectDialog();
                } else {
                    setBusy(false, event.substring(event.indexOf(':') + 1));
                }
            } else if ("Disconnected".equals(event)) {
                statusView.setText("Steam disconnected. Reconnecting…");
                showReconnectDialog();
            }
        });
    }

    @Override
    public void onGuardCodeRequired(String destination, boolean codeWasWrong) {
        EditText codeInput = new EditText(this);
        codeInput.setSingleLine(true);
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        codeInput.setHint("Steam Guard code");
        String prompt = codeWasWrong
                ? "That code was rejected. Enter a new code sent to " + destination + "."
                : "Enter the Steam Guard code sent to " + destination + ".";
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("Steam Guard")
                .setMessage(prompt)
                .setView(codeInput)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> authManager.cancel())
                .setPositiveButton("Continue", (dialog, which) ->
                        authManager.submitGuardCode(codeInput.getText().toString().trim()))
                .setOnCancelListener(dialog -> authManager.cancel())
                .create();
        alertDialog.show();
        styleGlassDialog(alertDialog);
    }

    @Override
    public void onDeviceApprovalRequired() {
        runOnUiThread(() -> statusView.setText(
                "Approve this sign-in in the Steam mobile app…"));
    }

    @Override
    public void onAuthenticated(String username) {
        runOnUiThread(() -> {
            setBusy(true, "Sign-in approved. Connecting your Steam account…");
            accountView.setText("Signing in as " + username);
        });
    }

    @Override
    public void onAuthenticationFailed(String reason) {
        runOnUiThread(() -> setBusy(false, "Steam sign-in failed: " + reason));
    }

    @Override
    public void onQrReady(String challengeUrl) {
        updateQr(challengeUrl);
    }

    @Override
    public void onQrRefreshed(String challengeUrl) {
        updateQr(challengeUrl);
    }

    @Override
    public void onQrAuthenticated(String username) {
        runOnUiThread(() -> {
            if (qrDialog != null) qrDialog.dismiss();
            setBusy(true, "QR approved. Connecting your Steam account…");
            accountView.setText("Signing in as " + username);
        });
    }

    @Override
    public void onQrAuthenticationFailed(String reason) {
        runOnUiThread(() -> {
            if (qrDialog != null) qrDialog.dismiss();
            setBusy(false, "Steam sign-in failed: " + reason);
        });
    }

    private void updateQr(String challengeUrl) {
        runOnUiThread(() -> {
            if (qrImage == null || qrDialog == null || !qrDialog.isShowing()) return;
            try {
                BitMatrix matrix = new MultiFormatWriter().encode(
                        challengeUrl, BarcodeFormat.QR_CODE, 512, 512);
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                        matrix.getWidth(), matrix.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                int[] pixels = new int[matrix.getWidth() * matrix.getHeight()];
                for (int y = 0; y < matrix.getHeight(); y++) {
                    for (int x = 0; x < matrix.getWidth(); x++) {
                        pixels[y * matrix.getWidth() + x] = matrix.get(x, y)
                                ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
                    }
                }
                bitmap.setPixels(pixels, 0, matrix.getWidth(), 0, 0,
                        matrix.getWidth(), matrix.getHeight());
                qrImage.setImageBitmap(bitmap);
                qrStatus.setText("Scan with the Steam mobile app. The code refreshes automatically.");
            } catch (WriterException e) {
                qrStatus.setText("Could not generate Steam's QR code: " + e.getMessage());
            }
        });
    }

    private void setBusy(boolean busy, String message) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!busy);
        qrLoginButton.setEnabled(!busy);
        if (message != null) {
            statusView.setText(message);
            statusView.setVisibility(View.VISIBLE);
        }
    }

    private void appendFriendMessage(String event) {
        String prefix = "FriendMessage:";
        int directionEnd = event.indexOf(':', prefix.length());
        int steamIdEnd = directionEnd < 0 ? -1 : event.indexOf(':', directionEnd + 1);
        if (steamIdEnd < 0) return;
        try {
            long steamId = Long.parseUnsignedLong(
                    event.substring(directionEnd + 1, steamIdEnd));
            String message = new String(android.util.Base64.decode(
                    event.substring(steamIdEnd + 1), android.util.Base64.NO_WRAP),
                    java.nio.charset.StandardCharsets.UTF_8);
            StringBuilder history = chatHistory.computeIfAbsent(steamId, ignored -> new StringBuilder());
            if (history.length() > 0) history.append('\n');
            SteamFriend friend = findFriend(steamId);
            boolean outgoing = "outgoing".equals(event.substring(prefix.length(), directionEnd));
            String sender = outgoing ? "You" : friend == null ? "Friend" : friend.name;
            history.append(sender).append(": ").append(message);
            if (steamId == activeChatSteamId && activeChatTranscript != null) {
                activeChatTranscript.setText(history.toString());
            }
        } catch (IllegalArgumentException e) {
            Log.w("SteamStoreActivity", "Ignoring malformed Steam friend message", e);
        }
    }

    private SteamFriend findFriend(long steamId) {
        for (SteamFriend friend : friends) {
            if (friend.steamId == steamId) return friend;
        }
        return null;
    }

    private void openFriendChat(SteamFriend friend) {
        activeChatSteamId = friend.steamId;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(4));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        activeChatTranscript = new TextView(this);
        activeChatTranscript.setTextSize(13);
        activeChatTranscript.setMinHeight(dp(200));
        StringBuilder history = chatHistory.computeIfAbsent(friend.steamId,
                ignored -> new StringBuilder());
        activeChatTranscript.setText(history.length() == 0
                ? "No messages in this session." : history.toString());
        scroll.addView(activeChatTranscript);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240)));
        EditText messageInput = new EditText(this);
        messageInput.setSingleLine(true);
        messageInput.setHint("Message " + friend.name);
        content.addView(messageInput);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Steam chat")
                .setView(content)
                .setNegativeButton("Close", null)
                .setPositiveButton("Send", null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            activeChatSteamId = 0;
            activeChatTranscript = null;
        });
        dialog.show();
        styleGlassDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String message = messageInput.getText().toString().trim();
            if (message.isEmpty()) return;
            try {
                repository.sendFriendMessage(friend.steamId, message);
                messageInput.setText("");
            } catch (RuntimeException e) {
                Toast.makeText(this, "Could not send message: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmRemoveGame(SteamGame game) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Remove " + game.name + "?")
                .setMessage("This deletes the downloaded game files from this device. "
                        + "The game remains in your Steam account; shortcuts to these files will stop working.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Remove game", (ignored, which) ->
                        SteamConnectionService.uninstall(this, game.appId))
                .create();
        dialog.show();
        styleGlassDialog(dialog);
    }

    private boolean isAppManagedInstall(SteamGame game) {
        if (game.installedPath == null || game.installedPath.trim().isEmpty()) return false;
        try {
            File appLibrary = new File(getFilesDir(), "imagefs/steam_games").getCanonicalFile();
            File installDirectory = new File(game.installedPath).getCanonicalFile();
            return appLibrary.equals(installDirectory.getParentFile());
        } catch (IOException e) {
            Log.w("SteamStoreActivity", "Could not verify the app-managed game path", e);
            return false;
        }
    }

    private void addShortcut(SteamGame game) {
        File gameRoot = new File(game.installedPath);
        if (!gameRoot.isDirectory()) {
            Toast.makeText(this, "The installed game folder is missing. Sync or reinstall it.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        List<File> executables = findExecutables(gameRoot);
        if (executables.isEmpty()) {
            Toast.makeText(this, "No Windows .exe files were found in this game folder.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[executables.size()];
        for (int i = 0; i < executables.size(); i++) {
            labels[i] = relativePath(gameRoot, executables.get(i));
        }
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("Choose the game executable")
                .setItems(labels, (dialog, which) -> chooseContainer(game, executables.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        alertDialog.show();
        styleGlassDialog(alertDialog);
    }

    private void installWithOfficialSteam(SteamGame game) {
        List<Container> containers = new ContainerManager(this).getContainers();
        if (containers.isEmpty()) {
            Toast.makeText(this, "Create a Winlator container before installing Steam.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[containers.size()];
        for (int i = 0; i < containers.size(); i++) names[i] = containers.get(i).getName();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Install " + game.name + " with Steam")
                .setItems(names, (ignored, which) ->
                        startOfficialSteamInstall(game, containers.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        styleGlassDialog(dialog);
    }

    private void startOfficialSteamInstall(SteamGame game, Container container) {
        android.content.SharedPreferences.Editor preferences =
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .putInt(STEAM_CLIENT_CONTAINER_PREFIX + game.appId, container.id);
        try {
            File steamExe = findOfficialSteamClient(container);
            if (steamExe == null) {
                preferences.putBoolean(
                        STEAM_PENDING_OFFICIAL_INSTALL_PREFIX + game.appId, true).apply();
                downloadAndLaunchSteamSetup(container);
            } else {
                preferences.remove(STEAM_PENDING_OFFICIAL_INSTALL_PREFIX + game.appId).apply();
                launchSteamCommand(container, steamExe, "steam://install/" + game.appId,
                        "Install " + game.name + " via Steam");
                statusView.setVisibility(View.VISIBLE);
                statusView.setText("Steam opened to install " + game.name
                        + ". Complete the install in the official client.");
            }
        } catch (IOException e) {
            Log.e("SteamStoreActivity", "Could not start official Steam install", e);
            Toast.makeText(this, "Could not start Steam installation: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void chooseInstallMethod(SteamGame game) {
        SteamConnectionService.install(this, game.appId);
    }

    private void chooseInstalledGameAction(SteamGame game) {
        addShortcut(game);
    }

    private void downloadAndLaunchSteamSetup(Container container) {
        setBusy(true, "Downloading the official Steam client installer…");
        steamClientInstallerExecutor.execute(() -> {
            try {
                File installer = downloadSteamSetup(container);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    setBusy(false, "Starting the official Steam installer…");
                    try {
                        launchSteamCommand(container, installer, "",
                                "Install the official Steam client");
                        Toast.makeText(this, "Finish installing Steam in the selected container, " +
                                        "sign in to its client, then choose Install via Steam again.",
                                Toast.LENGTH_LONG).show();
                    } catch (IOException e) {
                        Log.e("SteamStoreActivity", "Could not launch Steam installer", e);
                        setBusy(false, "Could not launch Steam installer: " + e.getMessage());
                    }
                });
            } catch (IOException e) {
                Log.e("SteamStoreActivity", "Could not download official Steam installer", e);
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        setBusy(false, "Could not download Steam installer: " + e.getMessage());
                    }
                });
            }
        });
    }

    private File downloadSteamSetup(Container container) throws IOException {
        File imageFsRoot = ImageFs.find(this).getRootDir().getCanonicalFile();
        File containerRoot = container.getRootDir().getCanonicalFile();
        String imageFsPath = imageFsRoot.getPath() + File.separator;
        if (!containerRoot.getPath().startsWith(imageFsPath)) {
            throw new IOException("Selected container is outside the Winlator image filesystem.");
        }
        File setupDirectory = new File(containerRoot, ".steam-client-setup").getCanonicalFile();
        String containerPath = containerRoot.getPath() + File.separator;
        if (!setupDirectory.getPath().startsWith(containerPath)) {
            throw new IOException("Steam installer path escaped the selected container.");
        }
        if (!setupDirectory.exists() && !setupDirectory.mkdirs()) {
            throw new IOException("Could not create the Steam installer directory.");
        }
        File destination = new File(setupDirectory, "SteamSetup.exe");
        File temporary = new File(setupDirectory, "SteamSetup.exe.part");
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Could not replace the previous partial Steam installer.");
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(STEAM_SETUP_URL).openConnection();
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "WinlatorMali Steam Client Installer");
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Steam installer server returned HTTP " + responseCode + ".");
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_STEAM_SETUP_BYTES) {
                throw new IOException("Steam installer download exceeded the allowed size.");
            }
            long received = 0L;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Steam installer download was cancelled.");
                    }
                    received += count;
                    if (received > MAX_STEAM_SETUP_BYTES) {
                        throw new IOException("Steam installer download exceeded the allowed size.");
                    }
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (received < 2L) throw new IOException("Steam installer download was incomplete.");
            try (InputStream input = new java.io.FileInputStream(temporary)) {
                if (input.read() != 'M' || input.read() != 'Z') {
                    throw new IOException("Steam installer response was not a Windows executable.");
                }
            }
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } finally {
            if (connection != null) connection.disconnect();
            if (temporary.exists() && !temporary.delete()) {
                Log.w("SteamStoreActivity", "Could not remove partial Steam installer " + temporary);
            }
        }
    }

    private void launchSteamCommand(Container container, File executable, String arguments,
                                    String shortcutName) throws IOException {
        File imageFsRoot = ImageFs.find(this).getRootDir().getCanonicalFile();
        File canonicalExecutable = executable.getCanonicalFile();
        String rootPath = imageFsRoot.getPath() + File.separator;
        if (!canonicalExecutable.getPath().startsWith(rootPath)) {
            throw new IOException("Steam executable is outside the Winlator image filesystem.");
        }
        String relative = canonicalExecutable.getPath().substring(rootPath.length())
                .replace(File.separatorChar, '/');
        String windowsPath = "Z:\\" + relative.replace('/', '\\');
        String imageFsPath = imageFsRoot.getPath();
        String containerPath = container.getRootDir().getCanonicalPath();
        String containerHome = containerPath.startsWith(imageFsPath)
                ? containerPath.substring(imageFsPath.length())
                : "/home/" + ImageFs.USER;
        String winePrefix = containerHome + "/.wine";
        String executableParent = relative.contains("/")
                ? relative.substring(0, relative.lastIndexOf('/')) : "";
        String workingDirectory = winePrefix + "/dosdevices/z:/" + executableParent;

        File launchDirectory = new File(getCacheDir(), "steam-client-shortcuts");
        if (!launchDirectory.exists() && !launchDirectory.mkdirs()) {
            throw new IOException("Could not create the temporary Steam launcher directory.");
        }
        File desktopFile = new File(launchDirectory,
                "container-" + container.id + "-" + safeFileName(shortcutName) + ".desktop");
        try (java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.FileWriter(desktopFile))) {
            writer.println("[Desktop Entry]");
            writer.println("Name=" + shortcutName);
            writer.println("Exec=env WINEPREFIX=\"" + winePrefix + "\" wine "
                    + StringUtils.escapeFileDOSPath(windowsPath)
                    + (arguments.isEmpty() ? "" : " " + arguments));
            writer.println("Type=Application");
            writer.println("Path=" + workingDirectory);
            writer.println("container_id:" + container.id);
        }
        Intent intent = new Intent(this, com.winlator.cmod.XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("shortcut_path", desktopFile.getAbsolutePath());
        intent.putExtra("shortcut_name", shortcutName);
        startActivity(intent);
    }

    private File findOfficialSteamGameDirectory(Container container, int appId)
            throws IOException {
        File steamExe = findOfficialSteamClient(container);
        if (steamExe == null) return null;
        File steamApps = new File(steamExe.getParentFile(), "steamapps");
        File manifest = new File(steamApps, "appmanifest_" + appId + ".acf");
        if (!manifest.isFile()) return null;
        String installDirectoryName = null;
        boolean installed = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(manifest))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("\"StateFlags\"")) {
                    String[] fields = trimmed.split("\"");
                    if (fields.length < 4) continue;
                    try {
                        installed = (Integer.parseInt(fields[3].trim()) & 4) != 0;
                    } catch (NumberFormatException e) {
                        throw new IOException("Steam app manifest has invalid install state.", e);
                    }
                } else if (trimmed.startsWith("\"installdir\"")) {
                    String[] fields = trimmed.split("\"");
                    if (fields.length >= 4) installDirectoryName = fields[3];
                }
            }
        }
        if (!installed || installDirectoryName == null || installDirectoryName.trim().isEmpty()) {
            return null;
        }
        File commonDirectory = new File(steamApps, "common").getCanonicalFile();
        File gameDirectory = new File(commonDirectory, installDirectoryName).getCanonicalFile();
        if (!gameDirectory.getPath().startsWith(commonDirectory.getPath() + File.separator)
                || !gameDirectory.isDirectory()) return null;
        return gameDirectory;
    }

    private void chooseContainer(SteamGame game, File executable) {
        List<Container> containers = new ContainerManager(this).getContainers();
        if (containers.isEmpty()) {
            Toast.makeText(this, "Create a Winlator container before adding a game shortcut.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[containers.size()];
        for (int i = 0; i < containers.size(); i++) names[i] = containers.get(i).getName();
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("Choose a Winlator container")
                .setItems(names, (dialog, which) ->
                        writeGameShortcut(game, executable, containers.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        alertDialog.show();
        styleGlassDialog(alertDialog);
    }

    private void writeGameShortcut(SteamGame game, File executable, Container container) {
        try {
            File imageFsRoot = ImageFs.find(this).getRootDir().getCanonicalFile();
            File canonicalExe = executable.getCanonicalFile();
            String rootPath = imageFsRoot.getPath() + File.separator;
            if (!canonicalExe.getPath().startsWith(rootPath)) {
                throw new IOException("Game executable is outside the Winlator image filesystem.");
            }
            String relative = canonicalExe.getPath().substring(rootPath.length())
                    .replace(File.separatorChar, '/');
            String windowsPath = "Z:\\" + relative.replace('/', '\\');
            File desktopDirectory = container.getDesktopDir();
            if (!desktopDirectory.exists() && !desktopDirectory.mkdirs()) {
                throw new IOException("Could not create the container desktop folder.");
            }
            String baseName = safeFileName(game.name);
            String shortcutName = baseName;
            for (int suffix = 2; new File(desktopDirectory, shortcutName + ".desktop").exists(); suffix++) {
                shortcutName = baseName + " (" + suffix + ")";
            }

            String imageFsPath = imageFsRoot.getPath();
            String containerPath = container.getRootDir().getCanonicalPath();
            String containerHome = containerPath.startsWith(imageFsPath)
                    ? containerPath.substring(imageFsPath.length())
                    : "/home/" + ImageFs.USER;
            String winePrefix = containerHome + "/.wine";
            String executableParent = relative.contains("/")
                    ? relative.substring(0, relative.lastIndexOf('/')) : "";
            String workingDirectory = winePrefix + "/dosdevices/z:/"
                    + executableParent;
            File desktopFile = new File(desktopDirectory, shortcutName + ".desktop");
            try (java.io.PrintWriter writer = new java.io.PrintWriter(
                    new java.io.FileWriter(desktopFile))) {
                writer.println("[Desktop Entry]");
                writer.println("Name=" + shortcutName);
                writer.println("Exec=env WINEPREFIX=\"" + winePrefix + "\" wine "
                        + StringUtils.escapeFileDOSPath(windowsPath));
                writer.println("Type=Application");
                writer.println("Path=" + workingDirectory);
                writer.println("Icon=" + shortcutName);
                writer.println("container_id:" + container.id);
                writer.println();
                writer.println("[Extra Data]");
                writer.println("steam_id=" + game.appId);
                writer.println("steamAppId=" + game.appId);
                writer.println("coverArtSelectionPending=1");
            }

            File iconDirectory = container.getIconsDir(64);
            if (!iconDirectory.exists() && !iconDirectory.mkdirs()) {
                Log.w("SteamStoreActivity", "Could not create container icon folder: " + iconDirectory);
            } else {
                ExeIconExtractor.extractAsync(canonicalExe,
                        new File(iconDirectory, shortcutName + ".png"), false, null);
            }
            Toast.makeText(this, "Shortcut added to " + container.getName()
                    + ". Launch it from Shortcuts to run directly in this container.",
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Could not create shortcut: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private String stageSteamGameDirectory(SteamGame game, File executable,
                                           Container container) throws IOException {
        if (game.appId <= 0) throw new IOException("Steam returned an invalid game ID.");
        File appLibrary = new File(getFilesDir(), "imagefs/steam_games").getCanonicalFile();
        String appLibraryPath = appLibrary.getPath() + File.separator;
        if (!executable.getPath().startsWith(appLibraryPath)) {
            throw new IOException("Downloaded game executable is outside the Steam library.");
        }
        String relative = executable.getPath().substring(appLibraryPath.length());
        int separator = relative.indexOf(File.separatorChar);
        if (separator <= 0 || separator == relative.length() - 1) {
            throw new IOException("Could not resolve the downloaded Steam game directory.");
        }
        String installDirectory = relative.substring(0, separator);
        String relativeExecutable = relative.substring(separator + 1);
        File gameDirectory = new File(appLibrary, installDirectory).getCanonicalFile();
        if (!gameDirectory.getPath().startsWith(appLibraryPath) || !gameDirectory.isDirectory()) {
            throw new IOException("Downloaded Steam game directory is missing.");
        }

        File driveC = new File(container.getRootDir(), ".wine/drive_c").getCanonicalFile();
        File commonDirectory = new File(driveC,
                "Program Files (x86)/Steam/steamapps/common").getCanonicalFile();
        if (!commonDirectory.getPath().startsWith(driveC.getPath() + File.separator)) {
            throw new IOException("Steam game library resolves outside the container.");
        }
        if (!commonDirectory.exists() && !commonDirectory.mkdirs()) {
            throw new IOException("Could not create the Steam game library directory.");
        }
        File link = new File(commonDirectory, "WinlatorApp-" + game.appId);
        if (Files.isSymbolicLink(link.toPath())) {
            if (!link.getCanonicalFile().equals(gameDirectory)) Files.delete(link.toPath());
        } else if (link.exists() && !link.getCanonicalFile().equals(gameDirectory)) {
            throw new IOException("A different game already occupies the Steam install path.");
        }
        if (!link.exists() && !Files.isSymbolicLink(link.toPath())) {
            try {
                Os.symlink(gameDirectory.getAbsolutePath(), link.getAbsolutePath());
            } catch (ErrnoException e) {
                throw new IOException("Could not link the downloaded game into Steam.", e);
            }
        }
        if (!link.getCanonicalFile().equals(gameDirectory)) {
            throw new IOException("Steam game directory link did not resolve correctly.");
        }
        return "C:\\Program Files (x86)\\Steam\\steamapps\\common\\WinlatorApp-"
                + game.appId + "\\" + relativeExecutable.replace(File.separatorChar, '\\');
    }

    private File findOfficialSteamClient(Container container) throws IOException {
        File driveC = new File(container.getRootDir(), ".wine/drive_c").getCanonicalFile();
        String driveCPath = driveC.getPath() + File.separator;
        File[] candidates = {
                new File(driveC, "Program Files (x86)/Steam/steam.exe"),
                new File(driveC, "Program Files/Steam/steam.exe")
        };
        for (File candidate : candidates) {
            File canonicalCandidate = candidate.getCanonicalFile();
            if (canonicalCandidate.getPath().startsWith(driveCPath)
                    && canonicalCandidate.isFile()) return canonicalCandidate;
        }
        return null;
    }

    private List<File> findExecutables(File root) {
        List<File> result = new ArrayList<>();
        int[] inspected = {0};
        collectExecutables(root, root, 0, inspected, result);
        return result;
    }

    private void collectExecutables(File root, File directory, int depth,
                                    int[] inspected, List<File> result) {
        if (depth > 5 || inspected[0] >= 1000 || result.size() >= 80) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        java.util.Arrays.sort(children, (left, right) ->
                left.getName().compareToIgnoreCase(right.getName()));
        for (File child : children) {
            if (inspected[0] >= 1000 || result.size() >= 80) return;
            inspected[0]++;
            try {
                if (!child.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) continue;
            } catch (IOException e) {
                continue;
            }
            if (child.isDirectory()) {
                String name = child.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("redist") && !name.contains("directx")
                        && !name.contains("vcredist") && !name.contains("support")) {
                    collectExecutables(root, child, depth + 1, inspected, result);
                }
            } else if (child.isFile() && child.getName().toLowerCase(Locale.ROOT).endsWith(".exe")) {
                result.add(child);
            }
        }
    }

    private static String relativePath(File root, File file) {
        String rootPath = root.getAbsolutePath() + File.separator;
        String path = file.getAbsolutePath();
        return path.startsWith(rootPath) ? path.substring(rootPath.length()) : file.getName();
    }

    private static String safeFileName(String input) {
        String name = input.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_").trim();
        return name.isEmpty() ? "Steam Game" : name;
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1_073_741_824L) return String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576L) return String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0);
        return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
    }

    private int gameColumnCount() {
        return isLandscapeLayout() ? 4 : 2;
    }

    private boolean isLandscapeLayout() {
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private void applySteamShadowPolicy(View view) {
        if (!ThemeManager.isFullLiquidGlassEnabled(this) || view == null) return;
        view.setElevation(0f);
        view.setTranslationZ(0f);
        view.setStateListAnimator(null);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applySteamShadowPolicy(group.getChildAt(i));
            }
        }
    }

    private LinearLayout.LayoutParams glassPanelParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private android.graphics.drawable.Drawable glassSurface(float radiusDp) {
        return ThemeManager.createThemedSurfaceDrawable(this, radiusDp);
    }

    private GradientDrawable rectangularButton(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        boolean liquidGlass = ThemeManager.isFullLiquidGlassEnabled(this);
        boolean translucentFill = Color.alpha(fillColor) < 255;
        drawable.setColor(!liquidGlass && translucentFill
                ? ThemeManager.getPrimaryColor(this) : fillColor);
        drawable.setCornerRadius(dp(9));
        drawable.setStroke(dp(1), !liquidGlass && translucentFill
                ? ThemeManager.getDividerColor(this) : strokeColor);
        return drawable;
    }

    private GradientDrawable createThemedDownloadActionBackground() {
        int accent = ThemeManager.getAccentColor(this);
        int surface = ThemeManager.getSurfaceColor(this);
        boolean liquidGlass = ThemeManager.isFullLiquidGlassEnabled(this);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(9));
        drawable.setColor(liquidGlass
                ? androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 88)
                : androidx.core.graphics.ColorUtils.blendARGB(surface, accent, 0.28f));
        drawable.setStroke(dp(1), liquidGlass
                ? androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 180)
                : ThemeManager.getDividerColor(this));
        return drawable;
    }

    private void styleGlassDialog(AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        dialog.getWindow().setBackgroundDrawable(ThemeManager.createThemedSurfaceDrawable(this, 22));
        if (ThemeManager.isFullLiquidGlassEnabled(this)) {
            dialog.getWindow().setElevation(0f);
            GlassWindowEffects.applyDialogBlur(dialog.getWindow(), this);
        }
    }

    private void styleFullBlurDialog(AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        dialog.getWindow().setBackgroundDrawable(ThemeManager.createThemedSurfaceDrawable(this, 22));
        if (ThemeManager.isFullLiquidGlassEnabled(this)) {
            dialog.getWindow().setElevation(0f);
            GlassWindowEffects.applyFullDialogBlur(dialog.getWindow(), this);
        }
    }

    private float glassElevation(int elevationDp) {
        return ThemeManager.isFullLiquidGlassEnabled(this) ? 0f : dp(elevationDp);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class GameCardSpacing extends RecyclerView.ItemDecoration {
        private final int spacing;

        GameCardSpacing(int spacing) {
            this.spacing = spacing;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            outRect.set(spacing, spacing, spacing, spacing);
        }
    }

    private final class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameHolder> {
        @NonNull
        @Override
        public GameHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            CardView card = new CardView(SteamStoreActivity.this);
            card.setRadius(dp(18));
            card.setCardElevation(glassElevation(5));
            card.setUseCompatPadding(!ThemeManager.isFullLiquidGlassEnabled(SteamStoreActivity.this));
            card.setCardBackgroundColor(ThemeManager.getSurfaceColor(SteamStoreActivity.this));
            card.setClipToOutline(true);
            card.setClickable(true);
            card.setFocusable(true);
            card.setForeground(getDrawable(android.R.drawable.list_selector_background));

            FrameLayout cover = new FrameLayout(SteamStoreActivity.this);
            card.addView(cover, new CardView.LayoutParams(
                    CardView.LayoutParams.MATCH_PARENT, dp(isLandscapeLayout() ? 142 : 190)));

            ImageView artwork = new ImageView(SteamStoreActivity.this);
            artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
            artwork.setImageResource(R.drawable.cover_art_placeholder);
            cover.addView(artwork, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            TextView badge = new TextView(SteamStoreActivity.this);
            badge.setTextSize(9);
            badge.setTypeface(null, android.graphics.Typeface.BOLD);
            badge.setTextColor(Color.WHITE);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(8), dp(5), dp(8), dp(5));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            badgeParams.setMargins(0, dp(9), dp(9), 0);
            cover.addView(badge, badgeParams);

            LinearLayout info = new LinearLayout(SteamStoreActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            int cardPadding = isLandscapeLayout() ? 6 : 8;
            info.setPadding(dp(cardPadding), dp(cardPadding), dp(cardPadding), dp(cardPadding));
            info.setBackgroundResource(R.drawable.shortcut_info_blur);
            FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            cover.addView(info, infoParams);

            TextView title = new TextView(SteamStoreActivity.this);
            title.setTextColor(ThemeManager.getOnSurfaceTextColor(SteamStoreActivity.this));
            title.setTextSize(isLandscapeLayout() ? 11 : 13);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(title);

            TextView details = new TextView(SteamStoreActivity.this);
            details.setTextColor(ThemeManager.getSelectedPreset(SteamStoreActivity.this).onSurfaceVariant);
            details.setTextSize(isLandscapeLayout() ? 8 : 9);
            details.setMaxLines(1);
            details.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            detailsParams.topMargin = dp(3);
            info.addView(details, detailsParams);

            Button action = new Button(SteamStoreActivity.this);
            action.setAllCaps(false);
            action.setTextColor(ThemeManager.getOnSurfaceTextColor(SteamStoreActivity.this));
            action.setTextSize(12);
            action.setMinHeight(dp(38));
            action.setMinimumHeight(dp(38));
            action.setMinWidth(0);
            action.setMinimumWidth(0);
            action.setMaxLines(1);
            action.setEllipsize(android.text.TextUtils.TruncateAt.END);
            action.setPadding(dp(2), 0, dp(2), 0);
            action.setTextColor(ThemeManager.getOnSurfaceTextColor(SteamStoreActivity.this));
            action.setBackground(glassSurface(12));
            action.setElevation(glassElevation(3));
            Button remove = new Button(SteamStoreActivity.this);
            remove.setAllCaps(false);
            remove.setText("Remove");
            remove.setTextColor(ThemeManager.getOnSurfaceTextColor(SteamStoreActivity.this));
            remove.setTextSize(10);
            remove.setMinHeight(dp(38));
            remove.setMinimumHeight(dp(38));
            remove.setPadding(dp(3), 0, dp(3), 0);
            remove.setBackground(glassSurface(12));
            remove.setElevation(glassElevation(3));
            LinearLayout actionRow = new LinearLayout(SteamStoreActivity.this);
            actionRow.setGravity(Gravity.CENTER_VERTICAL);
            int actionHeight = isLandscapeLayout() ? 30 : 34;
            LinearLayout.LayoutParams actionRowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(actionHeight));
            actionRowParams.topMargin = dp(4);
            actionRow.addView(action, new LinearLayout.LayoutParams(0, dp(actionHeight), 1));
            LinearLayout.LayoutParams removeParams =
                    new LinearLayout.LayoutParams(dp(62), dp(actionHeight));
            removeParams.leftMargin = dp(5);
            remove.setMinWidth(0);
            remove.setMinimumWidth(0);
            remove.setMaxLines(1);
            remove.setTextSize(9);
            remove.setPadding(dp(1), 0, dp(1), 0);
            actionRow.addView(remove, removeParams);
            info.addView(actionRow, actionRowParams);

            badge.setBackground(glassSurface(14));
            return new GameHolder(card, artwork, title, details, badge, action, remove);
        }

        @Override
        public void onBindViewHolder(@NonNull GameHolder holder, int position) {
            SteamGame game = filteredGames.get(position);
            boolean downloading = SteamDepotDownloadManager.isDownloading(game.appId);
            boolean appManagedInstall = game.installed && isAppManagedInstall(game);
            holder.title.setText(game.name);
            holder.details.setText((game.installed
                    ? appManagedInstall ? "Installed" : "Installed via Steam client"
                    : "Owned by account")
                    + (game.installed && game.installedSizeBytes > 0
                    ? "  ·  " + formatSize(game.installedSizeBytes) : ""));
            holder.badge.setText(downloading ? "DOWNLOADING" : game.installed ? "INSTALLED" : "OWNED");
            boolean paused = SteamDepotDownloadManager.isPaused(game.appId);
            holder.action.setText(game.installed ? "Add shortcut"
                    : downloading ? paused ? "Resume download" : "Pause download"
                    : "Install");
            holder.action.setTextSize(game.installed ? 9 : 12);
            holder.remove.setVisibility(appManagedInstall ? View.VISIBLE : View.GONE);
            String artworkUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/"
                    + game.appId + "/library_600x900.jpg";
            Glide.with(SteamStoreActivity.this)
                    .load(artworkUrl)
                    .placeholder(R.drawable.cover_art_placeholder)
                    .error(R.drawable.cover_art_placeholder)
                    .centerCrop()
                    .into(holder.artwork);
            holder.action.setOnClickListener(view -> {
                if (game.installed) {
                    if (appManagedInstall) chooseInstalledGameAction(game);
                    else addShortcut(game);
                }
                else if (SteamDepotDownloadManager.isDownloading(game.appId)) {
                    if (SteamDepotDownloadManager.isPaused(game.appId)) {
                        SteamConnectionService.resume(SteamStoreActivity.this, game.appId);
                    } else {
                        SteamConnectionService.pause(SteamStoreActivity.this, game.appId);
                    }
                }
                else chooseInstallMethod(game);
            });
            holder.remove.setOnClickListener(view -> {
                if (isAppManagedInstall(game)) confirmRemoveGame(game);
            });
        }

        @Override
        public int getItemCount() {
            return filteredGames.size();
        }

        final class GameHolder extends RecyclerView.ViewHolder {
            final ImageView artwork;
            final TextView title;
            final TextView details;
            final TextView badge;
            final Button action;
            final Button remove;

            GameHolder(View itemView, ImageView artwork, TextView title,
                       TextView details, TextView badge, Button action, Button remove) {
                super(itemView);
                this.artwork = artwork;
                this.title = title;
                this.details = details;
                this.badge = badge;
                this.action = action;
                this.remove = remove;
            }
        }
    }

    private final class AchievementAdapter extends RecyclerView.Adapter<AchievementAdapter.AchievementHolder> {
        @Override
        public int getItemViewType(int position) {
            return achievementItems.isEmpty() ? 1 : 0;
        }

        @NonNull
        @Override
        public AchievementHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 1) {
                TextView empty = new TextView(SteamStoreActivity.this);
                empty.setText("Achievements will appear here when Steam returns game stats.");
                empty.setTextSize(12);
                empty.setPadding(dp(12), dp(12), dp(12), dp(12));
                empty.setBackground(glassSurface(16));
                return new AchievementHolder(empty, null, null, null, null);
            }
            LinearLayout card = new LinearLayout(SteamStoreActivity.this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(9), dp(10), dp(9));
            card.setBackground(glassSurface(16));
            ImageView icon = new ImageView(SteamStoreActivity.this);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
            LinearLayout text = new LinearLayout(SteamStoreActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(10), 0, 0, 0);
            card.addView(text, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView title = new TextView(SteamStoreActivity.this);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(title);
            TextView description = new TextView(SteamStoreActivity.this);
            description.setTextSize(11);
            description.setMaxLines(2);
            description.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(description);
            TextView state = new TextView(SteamStoreActivity.this);
            state.setTextSize(10);
            state.setTextColor(ThemeManager.getSelectedPreset(SteamStoreActivity.this).onSurfaceVariant);
            text.addView(state);
            return new AchievementHolder(card, icon, title, description, state);
        }

        @Override
        public void onBindViewHolder(@NonNull AchievementHolder holder, int position) {
            if (achievementItems.isEmpty()) return;
            SteamAchievement achievement = achievementItems.get(position);
            boolean revealDetails = !achievement.hidden || achievement.unlocked;
            holder.title.setText(revealDetails && !achievement.displayName.isEmpty()
                    ? achievement.displayName : "Hidden achievement");
            holder.description.setText(revealDetails && !achievement.description.isEmpty()
                    ? achievement.description : "Unlock this achievement to reveal its details.");
            holder.state.setText(achievement.unlocked
                    ? achievement.unlockedAt.isEmpty() ? "Unlocked" : "Unlocked · " + achievement.unlockedAt
                    : "Locked");
            String icon = achievement.unlocked
                    ? achievement.iconUrl : achievement.lockedIconUrl;
            if (icon == null || icon.isEmpty()) {
                Glide.with(SteamStoreActivity.this).clear(holder.icon);
                holder.icon.setImageResource(R.drawable.cover_art_placeholder);
                holder.icon.setAlpha(achievement.unlocked ? 1f : 0.55f);
            } else {
                Glide.with(SteamStoreActivity.this)
                        .load(icon)
                        .placeholder(R.drawable.cover_art_placeholder)
                        .error(R.drawable.cover_art_placeholder)
                        .into(holder.icon);
                holder.icon.setAlpha(1f);
            }
        }

        @Override
        public int getItemCount() {
            return Math.max(1, achievementItems.size());
        }

        final class AchievementHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView description;
            final TextView state;

            AchievementHolder(View itemView, ImageView icon, TextView title,
                              TextView description, TextView state) {
                super(itemView);
                this.icon = icon;
                this.title = title;
                this.description = description;
                this.state = state;
            }
        }
    }

    private final class BadgeAdapter extends RecyclerView.Adapter<BadgeAdapter.BadgeHolder> {
        @Override
        public int getItemViewType(int position) {
            return badges.isEmpty() ? 1 : 0;
        }

        @NonNull
        @Override
        public BadgeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 1) {
                TextView empty = new TextView(SteamStoreActivity.this);
                empty.setText("Public Steam badges will appear here.");
                empty.setTextSize(12);
                empty.setPadding(dp(12), dp(12), dp(12), dp(12));
                empty.setBackground(glassSurface(16));
                return new BadgeHolder(empty, null, null, null);
            }
            LinearLayout card = new LinearLayout(SteamStoreActivity.this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(9), dp(10), dp(9));
            card.setBackground(glassSurface(16));
            ImageView image = new ImageView(SteamStoreActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            card.addView(image, new LinearLayout.LayoutParams(dp(64), dp(64)));
            LinearLayout text = new LinearLayout(SteamStoreActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(10), 0, 0, 0);
            card.addView(text, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView title = new TextView(SteamStoreActivity.this);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(title);
            TextView details = new TextView(SteamStoreActivity.this);
            details.setTextSize(11);
            details.setTextColor(ThemeManager.getSelectedPreset(SteamStoreActivity.this).onSurfaceVariant);
            details.setMaxLines(2);
            details.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(details);
            return new BadgeHolder(card, image, title, details);
        }

        @Override
        public void onBindViewHolder(@NonNull BadgeHolder holder, int position) {
            if (badges.isEmpty()) return;
            SteamBadge badge = badges.get(position);
            holder.title.setText(badge.name);
            String details = badge.xp.isEmpty() ? "Steam profile badge" : badge.xp + " XP";
            if (!badge.unlockedAt.isEmpty()) details += " · " + badge.unlockedAt;
            holder.details.setText(details);
            Glide.with(SteamStoreActivity.this)
                    .load(badge.imageUrl)
                    .placeholder(R.drawable.cover_art_placeholder)
                    .error(R.drawable.cover_art_placeholder)
                    .into(holder.image);
        }

        @Override
        public int getItemCount() {
            return Math.max(1, badges.size());
        }

        final class BadgeHolder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView title;
            final TextView details;

            BadgeHolder(View itemView, ImageView image, TextView title, TextView details) {
                super(itemView);
                this.image = image;
                this.title = title;
                this.details = details;
            }
        }
    }

    private final class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsHolder> {
        @NonNull
        @Override
        public NewsHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(SteamStoreActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackground(glassSurface(16));

            ImageView image = new ImageView(SteamStoreActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setVisibility(View.GONE);
            card.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

            TextView title = new TextView(SteamStoreActivity.this);
            title.setTextSize(15);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.topMargin = dp(4);
            card.addView(title, titleParams);

            TextView date = new TextView(SteamStoreActivity.this);
            date.setTextSize(10);
            date.setTextColor(ThemeManager.getSelectedPreset(SteamStoreActivity.this).onSurfaceVariant);
            card.addView(date);

            TextView summary = new TextView(SteamStoreActivity.this);
            summary.setTextSize(12);
            summary.setMaxLines(4);
            summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            summaryParams.topMargin = dp(5);
            card.addView(summary, summaryParams);
            RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(dp(2), dp(3), dp(2), dp(5));
            card.setLayoutParams(cardParams);
            return new NewsHolder(card, image, title, date, summary);
        }

        @Override
        public void onBindViewHolder(@NonNull NewsHolder holder, int position) {
            SteamNewsItem item = newsItems.get(position);
            holder.title.setText(item.title);
            holder.date.setText(item.published);
            holder.summary.setText(item.summary);
            if (item.imageUrl.isEmpty()) {
                Glide.with(SteamStoreActivity.this).clear(holder.image);
                holder.image.setVisibility(View.GONE);
            } else {
                holder.image.setVisibility(View.VISIBLE);
                Glide.with(SteamStoreActivity.this).load(item.imageUrl)
                        .placeholder(R.drawable.cover_art_placeholder)
                        .error(R.drawable.cover_art_placeholder)
                        .into(holder.image);
            }
            holder.itemView.setOnClickListener(view -> showNewsArticle(item));
        }

        @Override
        public int getItemCount() {
            return newsItems.size();
        }

        final class NewsHolder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView title;
            final TextView date;
            final TextView summary;

            NewsHolder(View itemView, ImageView image, TextView title,
                       TextView date, TextView summary) {
                super(itemView);
                this.image = image;
                this.title = title;
                this.date = date;
                this.summary = summary;
            }
        }
    }

    private final class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.FriendHolder> {
            @NonNull
            @Override
            public FriendHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType == 1) {
                    TextView empty = sectionHeading("No Steam friends found yet.");
                    return new FriendHolder(empty, null, null, null, null);
                }
                CardView card = new CardView(SteamStoreActivity.this);
                card.setRadius(dp(16));
                card.setCardElevation(glassElevation(3));
                card.setUseCompatPadding(!ThemeManager.isFullLiquidGlassEnabled(SteamStoreActivity.this));
                card.setCardBackgroundColor(Color.TRANSPARENT);
                LinearLayout row = new LinearLayout(SteamStoreActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(8), dp(8), dp(8));
                card.addView(row);
                ImageView avatar = new ImageView(SteamStoreActivity.this);
                avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                avatar.setBackgroundResource(R.drawable.shortcut_cover_frame);
                row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
                LinearLayout labels = new LinearLayout(SteamStoreActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setPadding(dp(10), 0, dp(6), 0);
                row.addView(labels, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                TextView name = new TextView(SteamStoreActivity.this);
                name.setTypeface(null, android.graphics.Typeface.BOLD);
                labels.addView(name);
                TextView presence = new TextView(SteamStoreActivity.this);
                presence.setTextSize(11);
                labels.addView(presence);
                Button chat = new Button(SteamStoreActivity.this);
                chat.setText("Chat");
                chat.setAllCaps(false);
                row.addView(chat);
                card.setBackground(glassSurface(16));
                return new FriendHolder(card, avatar, name, presence, chat);
            }

            @Override
            public void onBindViewHolder(@NonNull FriendHolder holder, int position) {
                if (friends.isEmpty()) return;
                SteamFriend friend = friends.get(position);
                holder.name.setText(friend.name);
                holder.presence.setText(friend.game.isEmpty() ? friend.status
                        : friend.status + " · " + friend.game);
                holder.chat.setVisibility(View.GONE);
                String avatarUrl = steamAvatarUrl(friend.avatar);
                if (avatarUrl == null) {
                    Glide.with(SteamStoreActivity.this).clear(holder.avatar);
                    holder.avatar.setImageResource(R.drawable.cover_art_placeholder);
                } else {
                    Glide.with(SteamStoreActivity.this)
                            .load(avatarUrl)
                            .placeholder(R.drawable.cover_art_placeholder)
                            .error(R.drawable.cover_art_placeholder)
                            .circleCrop()
                            .into(holder.avatar);
                }
                holder.itemView.setOnClickListener(view -> openFriendChat(friend));
                holder.chat.setOnClickListener(view -> openFriendChat(friend));
            }

            @Override
            public int getItemViewType(int position) {
                return friends.isEmpty() ? 1 : 0;
            }

            @Override
            public int getItemCount() {
                return Math.max(1, friends.size());
            }

            final class FriendHolder extends RecyclerView.ViewHolder {
                final ImageView avatar;
                final TextView name;
                final TextView presence;
                final Button chat;

                FriendHolder(View itemView, ImageView avatar, TextView name,
                             TextView presence, Button chat) {
                    super(itemView);
                    this.avatar = avatar;
                    this.name = name;
                    this.presence = presence;
                    this.chat = chat;
                }
            }
        }

    private final class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.DownloadHolder> {
            @NonNull
            @Override
            public DownloadHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType == 1) {
                    TextView empty = sectionHeading("No downloads yet. Install a game from Library.");
                    return new DownloadHolder(empty, null, null, null, null, null, null, null);
                }
                LinearLayout card = new LinearLayout(SteamStoreActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(14), dp(12), dp(14), dp(12));
                card.setBackground(glassSurface(18));
                card.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView title = new TextView(SteamStoreActivity.this);
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                card.addView(title);
                TextView status = new TextView(SteamStoreActivity.this);
                status.setTextSize(12);
                LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                statusParams.topMargin = dp(3);
                card.addView(status, statusParams);
                ProgressBar progress = new ProgressBar(SteamStoreActivity.this, null,
                        android.R.attr.progressBarStyleHorizontal);
                progress.setMax(100);
                LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
                progressParams.topMargin = dp(9);
                card.addView(progress, progressParams);
                TextView size = new TextView(SteamStoreActivity.this);
                size.setTextSize(11);
                LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                sizeParams.topMargin = dp(4);
                card.addView(size, sizeParams);
                LinearLayout actions = new LinearLayout(SteamStoreActivity.this);
                actions.setGravity(Gravity.CENTER);
                Button pauseResume = new Button(SteamStoreActivity.this);
                pauseResume.setAllCaps(false);
                pauseResume.setMinHeight(dp(44));
                pauseResume.setMinimumHeight(dp(44));
                pauseResume.setBackground(createThemedDownloadActionBackground());
                pauseResume.setTextColor(ThemeManager.getOnSurfaceTextColor(SteamStoreActivity.this));
                Button cancel = new Button(SteamStoreActivity.this);
                cancel.setAllCaps(false);
                cancel.setText("Cancel");
                cancel.setMinHeight(dp(44));
                cancel.setMinimumHeight(dp(44));
                cancel.setBackground(ThemeManager.createThemedSurfaceDrawable(
                        SteamStoreActivity.this, 9));
                cancel.setTextColor(ThemeManager.getOnSurfaceTextColor(SteamStoreActivity.this));
                LinearLayout.LayoutParams actionButtonParams = new LinearLayout.LayoutParams(
                        0, dp(44), 1);
                actionButtonParams.setMargins(0, 0, dp(5), 0);
                actions.addView(pauseResume, actionButtonParams);
                LinearLayout.LayoutParams cancelButtonParams = new LinearLayout.LayoutParams(
                        0, dp(44), 1);
                cancelButtonParams.setMargins(dp(5), 0, 0, 0);
                actions.addView(cancel, cancelButtonParams);
                LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
                actionsParams.topMargin = dp(7);
                card.addView(actions, actionsParams);
                return new DownloadHolder(card, title, status, progress, size, actions,
                        pauseResume, cancel);
            }

            @Override
            public void onBindViewHolder(@NonNull DownloadHolder holder, int position) {
                if (downloads.isEmpty()) return;
                SteamDownload download = downloads.get(position);
                holder.title.setText(download.name);
                boolean complete = "Complete".equals(download.status);
                holder.status.setText(complete ? "Installed"
                        : download.paused ? "Paused" : download.status);
                boolean failed = download.status.startsWith("Failed");
                holder.status.setTextColor(failed ? 0xFFFF8B8B : 0xFFE0E7F0);
                boolean hasDepotProgress = download.currentDepotProgress >= 0;
                holder.progress.setIndeterminate(!failed && !complete && !download.paused
                        && !hasDepotProgress);
                holder.progress.setProgress(complete ? 100
                        : hasDepotProgress ? download.currentDepotProgress : 0);
                String progressLabel = complete ? "Download complete"
                        : hasDepotProgress ? "Current depot · "
                        + download.currentDepotProgress + "%" : download.status;
                holder.size.setText(progressLabel + " · Transfer from Steam "
                        + formatSize(download.downloadedBytes)
                        + " (compressed data, not installed size)");
                holder.actions.setVisibility(complete ? View.GONE : View.VISIBLE);
                holder.pauseResume.setText(failed ? "Retry"
                        : download.paused ? "Resume" : "Pause");
                holder.pauseResume.setOnClickListener(view -> {
                    if (failed) {
                        SteamConnectionService.install(SteamStoreActivity.this, download.appId);
                    } else if (download.paused) {
                        SteamConnectionService.resume(SteamStoreActivity.this, download.appId);
                    } else {
                        SteamConnectionService.pause(SteamStoreActivity.this, download.appId);
                    }
                });
                holder.cancel.setOnClickListener(view ->
                        SteamConnectionService.cancel(SteamStoreActivity.this, download.appId));
            }

            @Override
            public int getItemViewType(int position) {
                return downloads.isEmpty() ? 1 : 0;
            }

            @Override
            public int getItemCount() {
                return Math.max(1, downloads.size());
            }

            final class DownloadHolder extends RecyclerView.ViewHolder {
                final TextView title;
                final TextView status;
                final ProgressBar progress;
                final TextView size;
                final View actions;
                final Button pauseResume;
                final Button cancel;

                DownloadHolder(View itemView, TextView title, TextView status, ProgressBar progress,
                               TextView size, View actions, Button pauseResume, Button cancel) {
                    super(itemView);
                    this.title = title;
                    this.status = status;
                    this.progress = progress;
                    this.size = size;
                    this.actions = actions;
                    this.pauseResume = pauseResume;
                    this.cancel = cancel;
                }
            }
    }
}
