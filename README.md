<p align="center">
	<img src="logo.png" width="376" height="128" alt="Winlator Logo" />  
</p>

# Winlator Mali+

Winlator is an Android application that lets you to run Windows (x86_64) applications with Wine and Box86/Box64.

# Installation

1. Download and install the APK (Winlator_7.1.apk) from [GitHub Releases](https://github.com/brunodev85/winlator/releases)
2. Launch the app and wait for the installation process to finish

----

[![Play on Youtube](https://img.youtube.com/vi/8PKhmT7B3Xo/1.jpg)](https://www.youtube.com/watch?v=8PKhmT7B3Xo)
[![Play on Youtube](https://img.youtube.com/vi/9E4wnKf2OsI/2.jpg)](https://www.youtube.com/watch?v=9E4wnKf2OsI)
[![Play on Youtube](https://img.youtube.com/vi/czEn4uT3Ja8/2.jpg)](https://www.youtube.com/watch?v=czEn4uT3Ja8)
[![Play on Youtube](https://img.youtube.com/vi/eD36nxfT_Z0/2.jpg)](https://www.youtube.com/watch?v=eD36nxfT_Z0)

----

# Useful Tips

- If you are experiencing performance issues, try changing the Box86/Box64 preset in Container Settings -> Advanced Tab.
- For applications that use .NET Framework, try installing Wine Mono found in Start Menu -> System Tools.
- If some older games don't open, try adding the environment variable MESA_EXTENSION_MAX_YEAR=2003 in Container Settings -> Environment Variables.
- Try running the games using the shortcut on the Winlator home screen, there you can define individual settings for each game.
- To speed up the installers, try changing the Box86/Box64 preset to Intermediate in Container Settings -> Advanced Tab.

# Vulkan Renderer and Frame Generation

Select **Vulkan** as the Display Driver in Container Settings or a game shortcut to use the Vulkan host renderer; the HUD reports the selected backend. Vulkan-specific settings appear immediately when Vulkan is selected. The HUD also reports the live additional frame rate and total presented FPS while native Win-FG or LSFG is armed, and shows when the engine is starting, unsupported, or failed. Use **Vulkan renderer settings** for color order, present mode, driver, Native Rendering, and frame-generation defaults. The running game's **Frame generation** menu can change the engine and multiplier live, and adjust Win-FG's flow scale. LSFG Native is unavailable until a user-provided `Lossless.dll` is imported and validated and its shader cache is ready. The app extracts/translates supported shader data into an app-private cache and does not execute or redistribute the DLL. Win-FG Native does not require a DLL. Both engines are experimental and require compatible Vulkan drivers.

PanVK BC texture emulation/transcoding to ASTC is not enabled. BC format operations and signed/high-dynamic-range formats are not yet validated end-to-end, so enabling a partial conversion could produce incorrect textures or readbacks.

The app shell uses an animated Java canvas backdrop beneath its bordered glass toolbar and translucent navigation drawer. The status bar is transparent in portrait and landscape; orientation changes recalculate the shortcut carousel dimensions without changing the portrait card sizing.

The built-in Vulkan wrapper is displayed as **Apex-Wrapper**. Available wrappers are listed directly in Manage Graphics Drivers with descriptions and Download/Update actions. Once downloaded, each wrapper appears alongside Apex-Wrapper in the Graphics Driver dropdown; the selected wrapper is persisted for that container or shortcut, loaded when its game starts, and shown by name in the HUD. Archives are fetched on demand and checksum-verified before they become selectable. The list comes from the [Bannerlator wrapper catalog](https://github.com/The412Banner/winlator-contents/blob/main/wrappers.json), which identifies wrapper authors, licenses, and GPU targets.

# Winlator Mali+ additions

- A console-inspired, animated launcher UI with responsive portrait/landscape shortcut cards, game artwork, per-game settings, and compact details for graphics wrapper, FEXCore, Box64, and their presets.
- Custom and cloud-fetched game cover art, with a local fallback when a remote image is unavailable.
- Downloadable Bannerlator graphics wrappers, checksum validation, update support, and persistent per-container/per-shortcut selection.
- A Vulkan host renderer with configurable color order, present mode, Vulkan driver, and optional Native Rendering.
- Experimental native Win-FG frame generation and optional LSFG Native support after importing and validating the user's own `Lossless.dll`.
- Updated Winlator Mali+ branding and a shortcut HUD that reflects selected renderer, wrapper, and frame-generation state.

Release APK variants use the same **Winlator Mali+** app label and are built for these package IDs: `com.winlator.mali.plus`, `com.ludasi.benchmark`, `com.antutu.ABenchMark`, and `com.futuremark.dmandroid.application`.

# Credits and Third-party apps
- Ubuntu RootFs ([Focal Fossa](https://releases.ubuntu.com/focal))
- Wine ([winehq.org](https://www.winehq.org/))
- Box86/Box64 by [ptitseb](https://github.com/ptitSeb)
- PRoot ([proot-me.github.io](https://proot-me.github.io))
- Mesa (Turnip/Zink/VirGL) ([mesa3d.org](https://www.mesa3d.org))
- DXVK ([github.com/doitsujin/dxvk](https://github.com/doitsujin/dxvk))
- VKD3D ([gitlab.winehq.org/wine/vkd3d](https://gitlab.winehq.org/wine/vkd3d))
- D8VK ([github.com/AlpyneDreams/d8vk](https://github.com/AlpyneDreams/d8vk))
- CNC DDraw ([github.com/FunkyFr3sh/cnc-ddraw](https://github.com/FunkyFr3sh/cnc-ddraw))
- Vulkan host renderer and Native Rendering integration adapted from [Bannerlator](https://github.com/The412Banner/Bannerlator), licensed under GPL-3.0; see [LICENSE.GPL-3.0-Bannerlator](./LICENSE.GPL-3.0-Bannerlator).
- Optional graphics wrapper catalog and downloadable wrapper archives from [The412Banner/winlator-contents](https://github.com/The412Banner/winlator-contents); individual wrapper authors and licenses are shown in-app.
- Theme System & CI Workflow by [Noysz](https://github.com/Noysz)

Many thanks to [ptitSeb](https://github.com/ptitSeb) (Box86/Box64), [Danylo](https://blogs.igalia.com/dpiliaiev/tags/mesa/) (Turnip), [alexvorxx](https://github.com/alexvorxx) (Mods/Tips), [Noysz](https://github.com/Noysz) (Theme System/CI), and others.
Thank you to all the people who believe in this project.