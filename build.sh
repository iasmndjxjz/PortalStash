#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$ROOT/tools"
mkdir -p "$ROOT/build/classes" "$ROOT/build/dex" "$ROOT/dist"
# Dependencies are compile-only. Never package the Xposed API classes.
javac -encoding UTF-8 --release 8 -cp "$TOOLS/android-35.jar:$TOOLS/xposed-api-82.jar" \
  -d "$ROOT/build/classes" "$ROOT"/src/io/github/portalstash/*.java
jar cf "$ROOT/build/module.jar" -C "$ROOT/build/classes" .
java -cp "$TOOLS/r8.jar" com.android.tools.r8.D8 --min-api 28 \
  --lib "$TOOLS/android-35.jar" --classpath "$TOOLS/xposed-api-82.jar" \
  --output "$ROOT/build/dex" "$ROOT/build/module.jar"
aapt package -f -M "$ROOT/AndroidManifest.xml" -S "$ROOT/res" -A "$ROOT/assets" \
  -I "$TOOLS/android-28-res.jar" -F "$ROOT/build/unsigned.apk"
(cd "$ROOT/build/dex" && zip -q -u "$ROOT/build/unsigned.apk" classes.dex)
zipalign -f -p 4 "$ROOT/build/unsigned.apk" "$ROOT/build/aligned.apk"
if [[ ! -f "$TOOLS/experimental.keystore" ]]; then
  keytool -genkeypair -keystore "$TOOLS/experimental.keystore" -storepass portalstash-local \
    -keypass portalstash-local -alias portalstash -dname "CN=PortalStash Experimental" \
    -keyalg RSA -keysize 3072 -validity 3650
fi
apksigner sign --ks "$TOOLS/experimental.keystore" --ks-pass pass:portalstash-local \
  --key-pass pass:portalstash-local --ks-key-alias portalstash \
  --out "$ROOT/dist/PortalStash-LSP-0.4.0-os4-experimental.apk" "$ROOT/build/aligned.apk"
apksigner verify --verbose "$ROOT/dist/PortalStash-LSP-0.4.0-os4-experimental.apk"
aapt dump badging "$ROOT/dist/PortalStash-LSP-0.4.0-os4-experimental.apk"
sha256sum "$ROOT/dist/PortalStash-LSP-0.4.0-os4-experimental.apk"
