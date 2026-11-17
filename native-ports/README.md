# Native port workspace

Place an existing Android Gradle game project in this directory, for example:

`native-ports/my-game/`

The project must contain its own `gradlew`, one or more Android Gradle build files, and an AndroidManifest.xml. Then run **Actions → Build native Android game project** and set `project_dir` to `native-ports/my-game`.

This workflow builds an existing Android project. It does not turn a Windows executable or closed-source game data into native Android code. C/C++ source that is not already packaged as an Android app still needs an Android application shell, NDK integration, compatible engine dependencies, input/audio/rendering integration, and game lifecycle handling.

Imported projects are not automatically uploaded from the phone to GitHub. Add the source to this repository and push it before triggering the workflow.
