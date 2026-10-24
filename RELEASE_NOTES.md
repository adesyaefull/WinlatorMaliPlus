# Winlator Mali+ Local Integration Update

This source update brings the local Winlator Mali+ integration forward with Vulkan and shortcut-library improvements while preserving the upstream EGL/Gladio and ZeroFG paths.

## Highlights

- Optional Vulkan host renderer with Vulkan-specific display settings, native rendering support, and LSFG/Win-FG controls.
- Online Bannerlator wrapper catalog with wrapper selection and archive validation.
- Live PanVK release list in **Adrenotools GPU Drivers**. Refreshes the latest releases from three GitHub repositories, filters for supported Adrenotools ZIP packages, verifies downloads, and imports them directly.
- PanVK release entries show repository owner, version, publication date, package size, and reported device/GPU compatibility.
- Shortcut artwork and library improvements, including full-cover game cards, per-game cover handling, and direct executable shortcut creation with container selection.
- Animated PlayStation-inspired background and liquid-glass styling across app and in-game menus.

## Experimental driver notice

PanVK is experimental and may be less smooth or stable than the built-in System driver. Compatibility and performance depend on the device, Mali GPU, kernel, and game. Review the device notes for each release. If problems occur, switch back to the System driver. Import success does not guarantee that a driver will work on a particular device.

## APK packages

Upload the four APK files manually as release assets:

- Standard — `app-standard-release.apk`
- Ludashi — `app-ludashi-release.apk`
- AnTuTu — `app-antutu-release.apk`
- Futuremark — `app-futuremark-release.apk`

This repository update contains source and release text only; APK files are not committed or uploaded by this change.
