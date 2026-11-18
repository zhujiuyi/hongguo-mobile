# Repository Guidelines

## Project Structure

- In the three-directory workspace, keep the upstream checkout in `../参考/` and temporary scripts, build outputs, and logs in `../脚本/`.
- `kotlin-tv/app/` builds the current APK. Kotlin code under `src/main/kotlin/` implements Android View screens, input, and Media3 playback. `src/main/res/` and `src/main/assets/` contain Android resources and packaged assets.
- `kotlin-tv/core/` is the Kotlin/JVM module for content, search, and playback models. Reuse it for shared behavior and keep Android-specific code in `:app`.
- `kotlin-tv/decoder-ffmpeg/` provides audio compatibility for Android 5. Shared branding lives in `assets/brand/`. The root `android/`, `ios/`, `src/`, and `server/` directories contain the legacy implementation.

## Build and Run

Use the JDK and Android SDK specified in [the native build guide](kotlin-tv/README.md). Set `ANDROID_HOME` or `sdk.dir` in `kotlin-tv/local.properties`. Run the wrapper from the repository root:

```sh
./kotlin-tv/gradlew -p kotlin-tv :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The first two tasks run JVM and Android app unit tests. `lintDebug` runs Android Lint, and `assembleDebug` builds debug APKs. On Windows, use `./kotlin-tv/gradlew.bat` with the same arguments. Install the default universal debug APK on a test device with `adb install kotlin-tv/app/build/platform/outputs/apk/debug/app-universal-debug.apk`.

## Coding Style

Follow official Kotlin style with four-space indentation, `PascalCase` types, and `camelCase` functions and properties. Match neighboring files and reuse existing view, playback, and data components.

## Testing

Keep local checks focused on the affected behavior. Put `*Test.kt` files under the owning module's `src/test/kotlin/` directory and mirror the production package. Use JUnit 4 for unit tests and Robolectric for Android framework behavior. Device playback instrumentation lives in `kotlin-tv/app/src/androidTest/`. For changed input or playback behavior, record which emulator or device you exercised. Native CI checks the default mobile build.

## Commits and Pull Requests

Use short, imperative subjects with prefixes found in history, such as `fix: preserve search scroll position`. Describe the behavior change, link related issues, and list the checks run. For visible changes, include a screenshot or recording when available. Keep signing keys, local configuration, and generated APKs out of Git. See the [native build guide](kotlin-tv/README.md) for mobile signing.
