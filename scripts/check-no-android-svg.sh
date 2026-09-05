#!/usr/bin/env bash
# Regression guard for the Android startup crash:
#   java.lang.IllegalStateException: Android platform doesn't support SVG format.
#
# Neither Jetpack Compose nor Compose Multiplatform resources can render SVG
# drawables on Android. A single .svg file under composeResources/drawable (or
# android res/drawable) crashes the app as soon as the first screen that uses
# it is composed (fresh installs die on the Auth screen via ic_google).
#
# Rule: use Android VectorDrawable XML (.xml) for every vector drawable.
# (iOS .svg files under iosApp/ asset catalogs are intentionally NOT checked.)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fail=0

while IFS= read -r -d '' f; do
    echo "ERROR: SVG drawable not allowed on Android: ${f#$ROOT/}" >&2
    fail=1
done < <(find "$ROOT/composeApp/src" "$ROOT/androidApp/src" -type f -iname "*.svg" -print0 2>/dev/null)

if [ "$fail" -ne 0 ]; then
    echo "" >&2
    echo "Convert the file(s) above to Android VectorDrawable XML (.xml)." >&2
    echo "See: https://developer.android.com/develop/ui/views/graphics/vector-drawable-resources" >&2
    exit 1
fi

echo "SVG guard passed: no .svg drawables under composeApp/src or androidApp/src."
