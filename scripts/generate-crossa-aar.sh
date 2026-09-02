#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
android_project_directory="$(cd -- "$script_directory/.." && pwd)"
repository_directory="$(cd -- "$android_project_directory/.." && pwd)"
source_directory="$android_project_directory/crossa"
generated_directory="${CROSSA_AAR_BUILD_DIR:-$android_project_directory/build/generated-crossa-aar}"
aar_destination="${CROSSA_AAR_DESTINATION:-$android_project_directory/app/libs/crossa-generated-debug.aar}"
crossa_cli="${CROSSA_CLI:-$repository_directory/Crossa/build-host/crossa}"

if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    android_sdk_directory="$ANDROID_SDK_ROOT"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
    android_sdk_directory="$ANDROID_HOME"
else
    android_sdk_directory="$repository_directory/Crossa/build/android-sdk"
fi

[[ -x "$crossa_cli" ]] || {
    printf 'Crossa CLI was not found or is not executable: %s\n' "$crossa_cli" >&2
    exit 1
}

[[ -d "$android_sdk_directory" ]] || {
    printf 'Android SDK was not found: %s\n' "$android_sdk_directory" >&2
    exit 1
}

rm -rf "$generated_directory"
mkdir -p "$(dirname -- "$aar_destination")"

"$crossa_cli" generate-build android "$source_directory" --output "$generated_directory"
printf 'sdk.dir=%s\n' "$android_sdk_directory" > "$generated_directory/local.properties"

generated_gradle="$generated_directory/gradlew"
generated_aar="$generated_directory/library/build/outputs/aar/library-debug.aar"

[[ -x "$generated_gradle" ]] || {
    printf 'Generated Gradle wrapper was not found or is not executable: %s\n' "$generated_gradle" >&2
    exit 1
}

"$generated_gradle" -p "$generated_directory" :library:assembleDebug --no-daemon

[[ -f "$generated_aar" ]] || {
    printf 'Generated AAR was not found: %s\n' "$generated_aar" >&2
    exit 1
}

cp "$generated_aar" "$aar_destination"
printf 'Prepared Android AAR: %s\n' "$aar_destination"
printf 'AAR size: %s bytes\n' "$(stat -f '%z' "$aar_destination")"
