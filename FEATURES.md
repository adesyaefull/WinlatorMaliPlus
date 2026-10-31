# Winlator Mali+ Feature and Upstream Port Guide

This document inventories the Winlator Mali+ changes layered onto upstream Winlator Mali and gives a future agent a checklist for rebasing them onto a newer upstream source. It is not a substitute for inspecting the current implementation: use it to locate the relevant code, then verify behavior and APIs in both revisions before porting.

## Integration baseline

- The local integration branch is `bionic-mali-1.2`. Its current committed baseline is merge commit `acff21f20794e085ce3d0f3f27876650e9c01677`, with local parent `4ef849eb6282db26cbd4c476f93fc32652fb7ccb` and upstream Winlator Mali 1.2.1 parent `428bd945be92c2063fe01daf770fa7310126e1b2`.
- The original Mali+ integration was based on upstream Winlator Mali 1.2 commit `e34f1e87f875fa143bf5bca6bd37fc2f781528d9` and integration commit `ceb730481bf3f7f146c19947bd161445a4a721ad`; these are historical references, not the current rebase base.
- The current working tree may also contain uncommitted follow-up changes. Check `git status` and review the complete diff before using the committed merge as the only source of truth.
- Keep the upstream native EGL/Gladio renderer and ZeroFG path. Vulkan host rendering is an additional choice, not a replacement for those defaults.
- The original Mali+ source branch is `bionic-mali-1.0`; use it as a historical reference when a change is not documented here. Do not merge old files wholesale over newer upstream files: the renderer and UI APIs have changed.
- No GitHub push is implied by this guide. Keep future integration local until explicitly asked otherwise.

## Mali+ additions and adaptations

### Optional Vulkan host renderer

- Adds Vulkan as an optional host/display renderer, selectable in container settings and shortcut settings. OpenGL remains the safe/default path.
- Persists renderer choice and related settings across container and shortcut configuration, with shortcut values falling back to their container.
- Adapts the Vulkan Java renderer to upstream XServer, `Drawable`, and `GPUImage` APIs, including AHardwareBuffer access and renderer lifecycle/event routing.
- Provides Vulkan-specific configuration, including color order, present mode, driver selection, post-processing controls, scaling, and display sizing/alignment where supported.
- Keeps the HUD renderer label consistent with the selected backend and avoids reporting EGL/GL values as Vulkan. Vulkan initialization failure reports the failure and falls back to OpenGL.
- Main implementation: `app/src/main/java/com/winlator/cmod/renderer/vulkan/`, `XServerDisplayActivity.java`, `widget/XServerView.java`, `container/Container.java`, and the container/shortcut settings dialogs.
- Native build integration: `app/src/main/cpp/CMakeLists.txt` and `app/src/main/cpp/winlator/`.

### Vulkan Native Rendering and direct scanout

- Adds a Vulkan Native Rendering option backed by Android SurfaceControl/AHardwareBuffer scanout.
- Selects a suitable large game drawable for direct scanout while continuing to composite other windows through Vulkan.
- Avoids switching the Vulkan compositor to its scanout black-frame path until a game buffer has actually been delivered; otherwise it keeps rendering through the compositor. This is a safeguard against a black screen when scanout has no game frame yet.
- Relevant code: `renderer/vulkan/VulkanRenderer.java`, `cpp/winlator/VulkanRendererContext.cpp`, and `cpp/scanout/ScanoutContext.cpp`.
- Runtime behavior depends on Android SurfaceControl support, the device, and the game/driver. It must be tested on-device after every renderer port.

### Vulkan frame generation, while preserving ZeroFG

- Adds Vulkan-native LSFG and Win-FG engine selection, enable/multiplier/tuning settings, and runtime application through Java/JNI/native renderer paths.
- Stores frame-generation configuration with container/shortcut settings. LSFG availability is gated by the required imported Lossless DLL/cache; Win-FG remains an available native option where supported.
- Routes Vulkan generation statistics to the HUD and prevents EGL renderer detection from mislabeling a Vulkan session.
- Keeps upstream ZeroFG and its EGL path intact. Do not remove ZeroFG or the upstream `zerofg` native target when porting Vulkan code.
- Frame generation has native processing/generation paths, but an enabled UI setting is not proof that generation is active on a device. Capability checks, engine initialization, composition, and scanout mode can prevent frames from being generated. Confirm with runtime telemetry/logs, not the checkbox alone.
- Relevant code: `contentdialog/GraphicsEnhancementsDialog.java`, `widget/WinlatorHUD.java`, `XServerDisplayActivity.java`, `renderer/vulkan/VulkanRenderer.java`, `cpp/winlator/VulkanRendererContext.cpp`, and `cpp/winlator/vulkan_jni.cpp`.

### Bannerlator wrapper catalog and selection

- Adds an online Bannerlator wrapper catalog, installed-wrapper discovery, download/update, checksum and archive validation, and local cache under the app's private `files/graphics_driver/` directory.
- Filters the known unusable BCn downloads (`bannerlator-bcn-leegao`, `bannerlator-compat-bcn`, and `wmali-bcn-fcharan`) from catalog results. This only hides them from the downloadable catalog; it does not remove files users already installed.
- The built-in wrapper is displayed as **Apex-Wrapper** while retaining the runtime identifier expected by the built-in wrapper.
- Installed catalog wrappers appear in the main Graphics Driver dropdown and shortcut settings. Their IDs are persisted so reopening settings does not silently revert the selection.
- At launch, the selected installed archive is extracted into the container; missing selections use the explicit built-in fallback rather than an invalid archive path.
- Catalog metadata is sourced from `The412Banner/winlator-contents`; binary wrapper archives are downloaded on demand instead of bundled into the APK.
- Relevant code: `contents/WrapperCatalogManager.java`, `ManageGraphicsDriversFragment.java`, `ContainerDetailFragment.java`, `contentdialog/GraphicsDriverConfigDialog.java`, `contentdialog/ShortcutSettingsDialog.java`, `container/Container.java`, and `XServerDisplayActivity.java`.

### Live PanVK release catalog and Adrenotools import

- Adds a **Browse / refresh PanVK driver releases** action to the Adrenotools GPU Drivers page. Each refresh paginates through the GitHub Releases API until all releases are checked, so older catalog entries do not hide newer or previously missed release packages. It does not silently poll in the background.
- Sources: [`zenithblue-oss/panvk-kbase-android`](https://github.com/zenithblue-oss/panvk-kbase-android/releases), [`GunaCharanTeja/panvk-kbase-android`](https://github.com/GunaCharanTeja/panvk-kbase-android/releases), [`FristOneRR/FristOneRR-Panvk-Driver`](https://github.com/FristOneRR/FristOneRR-Panvk-Driver/releases), and [`Noysz/panvk-g99-jm`](https://github.com/Noysz/panvk-g99-jm/releases).
- Offers only repo-specific ZIP candidates: `*-adrenotools.zip` for the first two repositories, `PanVK-*.zip` (including current `PanVK-FristOneRR-V.*` packages) for FristOneRR, and `FourFectVK-G57-*.zip` for Noysz. README/checksum files, X11/emulator/app-specific builds, raw `.so`, and unrelated ZIPs are excluded; the existing Adrenotools metadata/library checks still decide whether a candidate can import.
- Groups the chooser by repository owner and shows the exact release package, a short release-note description, tag/type/date, ZIP size, repository, and author-reported device/GPU compatibility. Compatibility notes are not a guarantee for all devices.
- Download is user-initiated and imports directly into the existing Adrenotools driver store. The download checks HTTPS/GitHub host, expected byte size, configured maximum size, and the release SHA-256 when GitHub supplies a digest.
- Adrenotools ZIP import requires root `meta.json`, valid `name`, `libraryName`, and `driverVersion` values, and the referenced library file. Extraction rejects unsafe paths, caps expanded size/file count, and refuses to overwrite an already installed driver name.
- The screen and confirmation warn that PanVK is experimental and may be less smooth/stable than the built-in System driver. Return to System if the custom driver causes issues. Driver choice and actual compatibility must be tested on the target Mali GPU/kernel.
- The latest ZIP from each listed repository was checked during implementation for the Adrenotools metadata/library layout. That is a package-format check, not an on-device compatibility test.
- Relevant code: `contents/PanvkReleaseManager.java`, `contents/AdrenotoolsManager.java`, `AdrenotoolsFragment.java`, `res/layout/adrenotools_fragment.xml`, `res/layout/panvk_release_list_item.xml`, and `res/values/strings.xml`.

### Game library, artwork, and executable shortcuts

- Requests cloud cover-art discovery for shortcuts that do not already have primary artwork, using the existing SteamGridDB/artwork path and preserving local/custom art as the preferred source.
- Adds a direct **Add game (.exe)** picker action to the Shortcuts screen. The user selects a Windows executable and target container; the app copies the executable under the container's `Program Files`, creates a `.desktop` entry, requests icon extraction, and refreshes the library.
- Adds safeguards for shortcut cover art: local picker completion updates only the chosen shortcut rather than reloading the library; local selection is persisted while the picker is open; writes use a same-directory temporary file and atomic replacement where supported; a shared per-art-file version rejects stale background decodes and older asynchronous downloads; current bitmap state is synchronized to matching live adapter items; and default/cloud artwork uses a guarded save path. Gallery images are decoded with a 1600px maximum dimension. **User testing previously reproduced unresolved cover-art bugs; these additional race fixes are not yet device-verified.** Do not describe cover art as reliable until repeated-change and refresh scenarios pass on-device.
- Shortcut cards load persisted artwork files through Glide with a signature incorporating path, timestamp, file size, and shared cover-art version; after a local cover change, all visible cards are rebound without reconstructing the shortcut library. Preserve disk-backed reload behavior so artwork recovers when in-memory bitmaps are evicted.
- Preserve the explicit cover-art search/selection flow and do not replace custom/local art with a default download.
- **Cover-art verification required:** test repeated local and downloaded selections, picker cancellation/failure, delayed default downloads, simultaneous `Shortcut` instances, list refresh/rebind, duplicate shortcut names, and corrupt/unreadable files. Confirm that stale loads/downloads cannot replace the newest selection and that one shortcut's update does not clear another card.
- Uses full-bleed, center-cropped artwork as the background of both portrait and landscape game cards. Game name, container/build subtitle, and version sit over a darker bottom gradient overlay that follows the card's rounded outline; do not reintroduce a separate square metadata panel or change the established card dimensions.
- Improves shortcut carousel layout and selection behavior across portrait/landscape, applies eased carousel blur and subtle card elevation, avoids a RecyclerView item-animation recycling crash, and centers the Shortcut Details heading.
- Shows selected-game configuration details (DX wrapper, renderer/frame generation, Vulkan wrapper, FEXCore/Box64 versions and presets where populated). Keeps game launch bound to the explicit play action; selecting/browsing a card should not launch it.
- Removes the obsolete “Share with Community” shortcut-popup action.
- Refreshes the container manager when returning to the Shortcuts screen so a container created moments earlier is immediately offered by Add Game and container-selection flows.
- Relevant code: `ShortcutsFragment.java`, `container/Shortcut.java`, `FileManagerFragment.java`, `core/CoverArtManager.java`, `res/layout/shortcuts_fragment.xml`, `res/layout/shortcut_list_item.xml`, and `res/menu/shortcuts_menu.xml`.

### UI, navigation, and visibility

- Adds a continuously animated PlayStation-inspired wave-and-particle background, drawn frame-by-frame by a custom Android `View`; this is actual motion/shape animation, not a static image translation.
- Places the animated view behind the main navigation content so transparent fragment roots can reveal it.
- Applies the liquid-glass visual treatment across shared surfaces and controls, including toolbar/drawer, buttons, dialogs, cards, and the in-game navigation drawer. Keep shared drawables/styles and runtime theme application consistent; a change limited to the launcher screen is incomplete.
- The appearance setting is labeled **Enhanced Liquid Glass**; keep the existing `full_liquid_glass` preference key when porting so saved user settings are not lost. The setting controls the enhanced treatment and the animated `GlassBackdrop` RenderEffect.
- The Container Detail editor temporarily blurs the animated backdrop even when Enhanced Liquid Glass is off; on pause/exit it restores the normal preference-controlled backdrop. Keep this scoped to the editor so disabling the option does not blur the animation on other screens.
- Enhanced Liquid Glass defaults to enabled for a fresh install and is applied before the first screen/dialog is created. App-owned first-run dialogs use the shared glass alert-dialog theme; Android system permission sheets remain OS-owned and cannot be themed by the app.
- Dialogs and the custom glass Spinner popup use Android window blur-behind on Android 12+ independently of the Enhanced Liquid Glass setting. Disabling the setting must not blur the animated background, but should not remove this dialog/dropdown compositor blur. Below Android 12, retain the translucent drawable fallback.
- Distinguish true compositor blur from tinted glass surfaces: dialogs and the custom Spinner popup request window blur; inline cards and theme-styled/native popup menus use drawable styling and are not guaranteed live backdrop blur.
- Preserve the separately tuned popup treatments: dropdown (`liquid_glass_popup.xml`), card action menu (`liquid_glass_menu.xml`), and editor/dialog (`liquid_glass_dialog.xml` / `liquid_glass_editor_background.xml`). Do not homogenize their opacity; the three-dot card menu is intentionally stronger than edit dialogs.
- Uses transparent layered glass surfaces so the animated background remains visible through menus, while preserving readable text and touch feedback.
- The Containers screen retains the glass toolbar/title. Each card uses one rounded outer glass surface; do not add a second inset glass background behind its text, which appeared as a long purple strip.
- The File Manager's secondary toolbar (drive selector, path, and action buttons) uses the shared glass toolbar drawable instead of an opaque `windowBackground`.
- Shows controller-navigation hints only while a physical game controller is connected and centers the hint row; preserve the upstream input/control stack and this layout adjustment when porting.
- Virtual gamepad detection is based on whether any touchscreen-control binding maps to a gamepad button, including mixed keyboard/gamepad profiles; derive this from loaded bindings so edits take effect immediately.
- The fake evdev controller redirects `/dev/input` directory enumeration and access checks to its backing directory, reports event files as character devices, and sizes variable-length capability/name ioctls to the caller's request. These compatibility details help games that filter devices before opening them; this preload-based evdev path still cannot guarantee support for games that bypass it or require the legacy `/dev/input/js*` interface.
- Keeps Adrenotools GPU Drivers visible in the main navigation regardless of GPU brand.
- Removes the redundant standalone in-game Frame Generation menu entry while retaining the frame-generation controls inside Graphics Enhancements.
- Container discovery and shortcut loading share mutable state. `ContainerManager.getContainers()` returns a synchronized snapshot and `loadContainers()` is synchronized; keep callers iterating snapshots to avoid `ConcurrentModificationException` during background shortcut loading.
- Relevant code: `widget/PlayStationWaveBackgroundView.java`, `widget/GlassWindowEffects.java`, `widget/GlassSpinnerPopup.java`, `MainActivity.java`, `ThemeManager.java`, `ContainerManager.java`, `ContainersFragment.java`, `ContainerDetailFragment.java`, `res/layout/main_activity.xml`, `res/layout/file_manager_fragment.xml`, `res/layout/container_list_item.xml`, `res/layout/settings_fragment.xml`, `res/drawable/navigation_drawer_glass.xml`, and `res/menu/xserver_menu.xml`.

### Branding, credits, and build variants

- Uses **Winlator Mali+** as the app label and adds/updates project and Bannerlator credit links in the About/release documentation.
- The configured product flavors keep the same visible app name and use these package IDs:
  - `standard`: `com.winlator.mali.plus`
  - `ludashi`: `com.ludasi.benchmark`
  - `antutu`: `com.antutu.ABenchMark`
  - `futuremark`: `com.futuremark.dmandroid.application`
- Flavor definitions are in `app/build.gradle`. Build and verify every requested flavor separately; a successful Standard Debug build does not certify all package variants or device installation.

## Upstream capabilities to preserve

These are part of upstream Winlator Mali 1.2 rather than unique Mali+ port additions, but they are easy to lose during a rebase and are required compatibility targets:

- Native EGL/Gladio rendering and its normal fallback path.
- ZeroFG frame generation and the upstream `zerofg` native build target.
- Upstream graphics enhancements, controller/input system, HUD, themes, SteamGridDB artwork/save management, Adrenotools driver management, and container configuration behavior.
- Existing built-in Vulkan wrapper variants and Mali-oriented BCn/cache options.

Do not describe these as new features of this port unless the newer upstream revision has dropped them and the port actually restores them.

## Rebase checklist for a newer upstream

1. Start from a clean checkout of the requested upstream release and record its commit. Keep the current integration branch/source available for comparison.
2. Port features by behavior and contracts, not by copying old classes. Inspect changes to `XServerView`, `XServer`, `Drawable`, `GPUImage`, renderer lifecycle, JNI signatures, and CMake targets.
3. Preserve both renderer paths: upstream EGL/Gladio + ZeroFG, and optional Vulkan + LSFG/Win-FG. Verify defaults remain safe and old container/shortcut configs still load.
4. Preserve settings migration/fallback behavior for renderer IDs, wrapper IDs, frame-generation values, and shortcut-to-container inheritance.
5. Port wrapper catalogue validation and extraction together with both Graphics Driver selectors. Test the full downloaded-wrapper selection → save → reopen → launch path.
6. Port the PanVK GitHub release catalog with live API fetch, repo-specific Adrenotools ZIP allowlists, progress/integrity checks, and validated import. Check that GitHub asset naming and Adrenotools `meta.json` contracts still match; do not broaden the list to arbitrary ZIPs or raw driver libraries. Test refresh after a new release, download, import, duplicate handling, and subsequent driver selection.
7. Port the direct `.exe` picker together with desktop-file creation, container path mapping, collision handling, icon extraction, and library refresh.
8. Recheck asynchronous cover-art ownership, not only file writes: test repeated changes, picker cancellation, slow cloud responses, simultaneous shortcut instances, refresh/rebind, Glide disk signatures, and corrupt/unreadable files. Ensure stale loads cannot replace current in-memory art or delete a newer file.
9. Exercise container creation and shortcut loading concurrently; verify refresh/rebind from the Container Detail editor and Containers screen does not race background readers, and newly created containers are immediately selectable.
10. Check that transparent fragment roots do not cover the animated background; validate the animated backdrop setting, editor-only forced blur, Android 12+ dialog/dropdown blur with the setting both on and off, fallback appearance below Android 12, all menu surfaces, and the in-game drawer separately.
11. Build `:app:assembleStandardDebug`, run `git diff --check`, and build the other requested flavors. Then test on a real device: EGL/ZeroFG, Vulkan composition, native scanout fallback, LSFG/Win-FG status, wrapper extraction, PanVK selection/fallback, artwork, and `.exe` shortcut launch.
12. Record which items were compile-tested versus device-tested. Do not claim native rendering, frame generation, or experimental PanVK compatibility is operational from a successful build/package-format check alone.

## Open review notes

- A read-only review of `VulkanRendererContext.cpp/.h` raised items for targeted verification, not confirmed defects or completed fixes: AHardwareBuffer format setup and cache ownership; device fallback; recovery after acquire/submit failures; frame-generation/swapchain image-count and synchronization assumptions; swapchain format selection; mapped-memory cleanup; and cross-thread access. Inspect the current upstream implementation and reproduce on-device before changing or declaring these resolved.
- Inline glass cards and theme-styled menus do not have true backdrop sampling merely because they use a glass drawable. Keep their documentation and acceptance tests separate from window blur-behind.

## Current validation caveat

The merged source has previously built successfully with `:app:assembleStandardDebug`, and the latest PanVK ZIP from each listed repository was checked for the expected Adrenotools metadata/library structure. These checks verify compilation and package layout, not runtime behavior on every GPU, downloaded-wrapper archive, product flavor, or Android version. Rebuild after porting and after incorporating current uncommitted follow-up changes. On-device Vulkan/native scanout, frame-generation output, PanVK compatibility/performance, popup appearance across Android versions, `.exe` picker flow, and repeated/cloud cover-art changes still require explicit runtime verification.
