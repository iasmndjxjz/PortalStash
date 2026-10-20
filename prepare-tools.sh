#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$ROOT/tools"
curl -fL --retry 2 https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-35/android.jar -o "$ROOT/tools/android-35.jar"
curl -fL --retry 2 https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-28/android.jar -o "$ROOT/tools/android-28-res.jar"
curl -fL --retry 2 https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar -o "$ROOT/tools/xposed-api-82.jar"
curl -fL --retry 2 https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.7.18/r8-8.7.18.jar -o "$ROOT/tools/r8.jar"
(cd "$ROOT" && sha256sum -c tools.sha256)
