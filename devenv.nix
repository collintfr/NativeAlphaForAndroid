{ pkgs, lib, config, ... }:

# Development shell for Native Alpha.
#
#   devenv shell        enter the shell (or use direnv with the provided .envrc)
#   phone-help          list the phone/testing commands below
#
# Flavor/build type default to the "extended" (all features) debug build. Override per call
# (`phone-run standard`) or for the session (`export APP_FLAVOR=standard`).

let
  # Maps a flavor/build type to the installed package name (see app/build.gradle).
  pkgName = ''
    app_package() {
      local flavor="$1" type="$2" id="com.cylonid.nativealpha"
      [ "$flavor" = extended ] && id="$id.pro"
      [ "$type" = debug ] && id="$id.debug"
      echo "$id"
    }
  '';

  # Shared argument parsing: [flavor] [buildType], falling back to env defaults.
  args = ''
    FLAVOR="''${1:-''${APP_FLAVOR:-extended}}"
    TYPE="''${2:-''${APP_BUILD_TYPE:-debug}}"
    case "$FLAVOR" in standard|extended|extendedGithub) ;; *)
      echo "Unknown flavor '$FLAVOR' (standard | extended | extendedGithub)" >&2; exit 2;; esac
    case "$TYPE" in debug|release) ;; *)
      echo "Unknown build type '$TYPE' (debug | release)" >&2; exit 2;; esac
    cap() { echo "$(tr '[:lower:]' '[:upper:]' <<< "''${1:0:1}")''${1:1}"; }
    ${pkgName}
    PKG="$(app_package "$FLAVOR" "$TYPE")"
  '';

  requireDevice = ''
    n=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)
    if [ "$n" -eq 0 ]; then
      echo "No phone connected. Plug in via USB (with USB debugging on) or run: phone-pair" >&2
      exit 1
    elif [ "$n" -gt 1 ] && [ -z "''${ANDROID_SERIAL:-}" ]; then
      echo "Several devices connected; pick one with: export ANDROID_SERIAL=<serial>" >&2
      adb devices -l >&2
      exit 1
    fi
  '';
in
{
  android = {
    enable = true;
    platforms.version = [ "37.2" ];
    buildTools.version = [ "37.0.0" ];
    # Google only hosts some platform-tools zips; if this 404s after a nixpkgs update,
    # pick a version listed in both nixpkgs androidenv/repo.json and Google's repository.
    platformTools.version = "37.0.1";
    emulator.enable = false;
    systemImages.enable = false;
    sources.enable = false;
    ndk.enable = false;
    googleAPIs.enable = false;
    googleTVAddOns.enable = false;
    cmake.version = [ ];
  };

  languages.java = {
    jdk.package = pkgs.jdk21;
    # Gradle provides everything; skip the language server (its override breaks on newer nixpkgs).
    lsp.enable = false;
  };

  packages = [ pkgs.git pkgs.unzip ];

  env.APP_FLAVOR = lib.mkDefault "extended";
  env.APP_BUILD_TYPE = lib.mkDefault "debug";

  scripts = {
    phone-help.description = "List the phone/testing commands";
    phone-help.exec = ''
      cat <<'TXT'
      Phone testing ([flavor] = extended | standard, [type] = debug | release; defaults: extended debug)

        phone-pair [ip:port]      pair over Wi-Fi (Android 11+: Developer options → Wireless debugging)
        phone-devices             list connected devices
        phone-install [flavor] [type]   build and install the APK matching the phone's CPU
        phone-run [flavor] [type]       build, install and launch
        phone-launch [flavor] [type]    launch the installed app
        phone-logs                app, GeckoView and page-console logs (Ctrl-C to stop)
        phone-logs-all [flavor] [type]  everything from the app's processes
        phone-screenshot [file]   save a screenshot from the phone
        phone-uninstall [flavor] [type] remove the app (and its data)
        phone-apk [flavor] [type] build only; print the APK path

        app-test                  run unit tests
        app-screenshots           render Compose screens to app/build/screenshots
      TXT
    '';

    phone-devices.description = "List connected Android devices";
    phone-devices.exec = "adb devices -l";

    phone-pair.description = "Pair and connect a phone over Wi-Fi (Wireless debugging)";
    phone-pair.exec = ''
      set -euo pipefail
      echo "On the phone: Settings → Developer options → Wireless debugging → Pair device with pairing code."
      PAIR="''${1:-}"
      [ -n "$PAIR" ] || read -rp "Pairing address shown on the phone (ip:port): " PAIR
      adb pair "$PAIR"
      echo
      echo "Now use the address shown under 'IP address & Port' on the Wireless debugging screen"
      echo "(it uses a different port than the pairing address)."
      read -rp "Connect address (ip:port): " CONN
      adb connect "$CONN"
      adb devices -l
    '';

    phone-apk.description = "Build the APK for the connected phone's CPU and print its path";
    phone-apk.exec = ''
      set -euo pipefail
      ${args}
      ABI="''${APP_ABI:-}"
      if [ -z "$ABI" ]; then
        if adb get-state >/dev/null 2>&1; then
          ABI=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')
        else
          ABI=arm64-v8a
        fi
      fi
      cd "$DEVENV_ROOT"
      [ -f local.properties ] && sed -i '/^ndk\.dir=/d' local.properties
      echo "Building $FLAVOR $TYPE for $ABI…" >&2
      ./gradlew --console=plain -q "assemble$(cap "$FLAVOR")$(cap "$TYPE")" >&2
      APK=$(ls app/build/outputs/apk/"$FLAVOR"/"$TYPE"/*-"$ABI"-*.apk 2>/dev/null | head -1 || true)
      if [ -z "$APK" ]; then
        echo "No APK for ABI '$ABI' (built: arm64-v8a, armeabi-v7a, x86_64)" >&2; exit 1
      fi
      if [ "$TYPE" = release ] && [[ "$APK" == *unsigned* ]]; then
        echo "Release APK is unsigned and can't be installed; use the debug build or configure signing." >&2; exit 1
      fi
      echo "$APK"
    '';

    phone-install.description = "Build and install the app on the connected phone";
    phone-install.exec = ''
      set -euo pipefail
      ${args}
      ${requireDevice}
      APK=$(phone-apk "$@")
      echo "Installing $(basename "$APK") ($(du -h "$APK" | cut -f1))…"
      adb install -r -t "$APK"
    '';

    phone-launch.description = "Launch the installed app";
    phone-launch.exec = ''
      set -euo pipefail
      ${requireDevice}
      ${args}
      adb shell am start -n "$PKG/com.cylonid.nativealpha.MainActivity" >/dev/null
      echo "Started $PKG"
    '';

    phone-run.description = "Build, install and launch the app";
    phone-run.exec = ''
      set -euo pipefail
      phone-install "$@"
      phone-launch "$@"
    '';

    phone-logs.description = "Show app, browser-engine and page console logs";
    phone-logs.exec = ''
      set -euo pipefail
      ${requireDevice}
      echo "Native Alpha + GeckoView logs (page JS errors appear as GeckoConsole); Ctrl-C to stop"
      # Gecko renders pages in separate :tab processes, so filter by tag rather than by PID.
      adb logcat -v color \
        'NA-Browser:V' 'GeckoRuntimeHolder:V' 'ShortcutHelper:V' 'IconFetcher:V' 'DownloadHelper:V' \
        'GeckoConsole:V' 'GeckoViewActivity:V' 'Gecko:I' 'GeckoView:I' 'GeckoSession:I' 'GeckoWebExtension:I' \
        'AndroidRuntime:E' 'ActivityTaskManager:W' '*:S'
    '';

    phone-logs-all.description = "Unfiltered logs from all of the app's processes";
    phone-logs-all.exec = ''
      set -euo pipefail
      ${requireDevice}
      ${args}
      PIDS=$(adb shell ps -A -o PID,NAME | tr -d '\r' | awk -v p="$PKG" '$2==p || index($2, p":")==1 {print $1}' | paste -sd'|')
      if [ -z "$PIDS" ]; then echo "$PKG isn't running; start it with: phone-launch" >&2; exit 1; fi
      echo "PIDs: $PIDS; Ctrl-C to stop"
      adb logcat -v threadtime | grep --line-buffered -E "^[0-9-]+ [0-9:.]+ +($PIDS) "
    '';

    phone-screenshot.description = "Save a screenshot from the phone";
    phone-screenshot.exec = ''
      set -euo pipefail
      ${requireDevice}
      OUT="''${1:-phone-$(date +%Y%m%d-%H%M%S).png}"
      adb exec-out screencap -p > "$OUT"
      echo "Saved $OUT"
    '';

    phone-uninstall.description = "Uninstall the app (removes its data)";
    phone-uninstall.exec = ''
      set -euo pipefail
      ${requireDevice}
      ${args}
      adb uninstall "$PKG"
    '';

    app-test.description = "Run unit tests";
    app-test.exec = ''
      cd "$DEVENV_ROOT"
      ./gradlew --console=plain testStandardDebugUnitTest "$@"
    '';

    app-screenshots.description = "Render Compose screens to app/build/screenshots";
    app-screenshots.exec = ''
      cd "$DEVENV_ROOT"
      ./gradlew --console=plain testStandardDebugUnitTest --tests '*ScreenshotTest*' && ls app/build/screenshots
    '';
  };

  # The android module writes ndk.dir pointing at an NDK we don't install; this app has no
  # native code, so drop it to silence AGP's CXX1102 warnings.
  enterShell = lib.mkAfter ''
    if [ -f "$DEVENV_ROOT/local.properties" ]; then
      sed -i '/^ndk\.dir=/d' "$DEVENV_ROOT/local.properties"
    fi

    echo "Native Alpha dev shell: JDK $(java -version 2>&1 | grep -m1 version | cut -d'"' -f2), Android SDK at $ANDROID_HOME"
    echo "Run 'phone-help' for phone testing commands."
  '';
}
