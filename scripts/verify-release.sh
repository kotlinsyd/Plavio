#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME}"
: "${PLAVIO_EXPECTED_SIGNER_SHA256:?Set the existing owner certificate SHA256, not the debug certificate}"
apk="${1:-app/build/outputs/apk/mobile/release/app-mobile-release.apk}"
build_tools="${PLAVIO_BUILD_TOOLS:-35.0.0}"
tools="$ANDROID_HOME/build-tools/$build_tools"
[[ -f "$apk" ]] || { echo 'Missing mobile release APK' >&2; exit 1; }
certs="$($tools/apksigner verify --verbose --print-certs "$apk")"
expected="$(printf '%s' "$PLAVIO_EXPECTED_SIGNER_SHA256" | tr '[:upper:]' '[:lower:]' | tr -d ': ')"
actual="$(printf '%s\n' "$certs" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
[[ "$actual" == "$expected" ]] || { echo 'Release signer does not match owner identity' >&2; exit 1; }
if printf '%s\n' "$certs" | grep -q 'CN=Android Debug'; then
  echo 'Publishing a debug certificate is forbidden' >&2; exit 1
fi
"$tools/zipalign" -c -P 16 -v 4 "$apk"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
unzip -q "$apk" 'lib/*' -d "$tmp"
python3 scripts/check-native-alignment.py "$tmp/lib"
python3 scripts/scan-source-secrets.py
echo 'Mobile release artifact passed signer, ZIP, native alignment and source-secret gates.'
