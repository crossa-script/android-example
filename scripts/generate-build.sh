#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
android_project_directory="$(cd -- "$script_directory/.." && pwd)"
repository_directory="$(cd -- "$android_project_directory/.." && pwd)"
source_directory="$android_project_directory/crossa"
generated_directory="${CROSSA_AAR_BUILD_DIR:-$android_project_directory/build/generated-crossa-aar}"
variant="${CROSSA_AAR_VARIANT:-release}"
if [[ -n "${CROSSA_CLI:-}" ]]; then
    crossa_cli="$CROSSA_CLI"
elif [[ -x "$repository_directory/Crossa/build/crossa" ]]; then
    crossa_cli="$repository_directory/Crossa/build/crossa"
else
    crossa_cli="$repository_directory/Crossa/build-host/crossa"
fi

case "$variant" in
    debug)
        gradle_task=':library:assembleDebug'
        generated_aar="$generated_directory/library/build/outputs/aar/library-debug.aar"
        aar_destination="${CROSSA_AAR_DESTINATION:-$android_project_directory/app/libs/crossa-generated-debug.aar}"
        ;;
    release)
        gradle_task=':library:assembleRelease'
        generated_aar="$generated_directory/library/build/outputs/aar/library-release.aar"
        aar_destination="${CROSSA_AAR_DESTINATION:-$android_project_directory/app/libs/crossa-generated-release.aar}"
        ;;
    *)
        printf 'Unsupported Crossa AAR variant: %s\n' "$variant" >&2
        exit 1
        ;;
esac

android_sdk_directory=""
for candidate in \
    "${ANDROID_SDK_ROOT:-}" \
    "${ANDROID_HOME:-}" \
    "${HOME:-}/Library/Android/sdk" \
    "${HOME:-}/Library/Android" \
    "${HOME:-}/Android/Sdk" \
    "$repository_directory/Crossa/build/android-sdk"; do
    if [[ -n "$candidate" && -d "$candidate/platform-tools" && -d "$candidate/platforms" ]]; then
        android_sdk_directory="$candidate"
        break
    fi
done

[[ -x "$crossa_cli" ]] || {
    printf 'Crossa CLI was not found or is not executable: %s\n' "$crossa_cli" >&2
    exit 1
}

crossa_source_commit="unknown"
if git -C "$repository_directory/Crossa" rev-parse HEAD >/dev/null 2>&1; then
    crossa_source_commit="$(git -C "$repository_directory/Crossa" rev-parse HEAD)"
fi
crossa_cli_sha256="unknown"
if command -v shasum >/dev/null 2>&1; then
    crossa_cli_sha256="$(shasum -a 256 "$crossa_cli" | awk '{print $1}')"
fi
printf 'Crossa CLI:\n  path: %s\n  version: %s\n  source commit: %s\n  sha256: %s\n' \
    "$crossa_cli" "$($crossa_cli --version)" "$crossa_source_commit" "$crossa_cli_sha256"

[[ -d "$android_sdk_directory" ]] || {
    printf 'Android SDK was not found: %s\n' "$android_sdk_directory" >&2
    exit 1
}

rm -rf "$generated_directory"
mkdir -p "$(dirname -- "$aar_destination")"

"$crossa_cli" generate-build android "$source_directory" --output "$generated_directory"
printf 'sdk.dir=%s\n' "$android_sdk_directory" > "$generated_directory/local.properties"

generated_gradle="$generated_directory/gradlew"

[[ -x "$generated_gradle" ]] || {
    printf 'Generated Gradle wrapper was not found or is not executable: %s\n' "$generated_gradle" >&2
    exit 1
}

"$generated_gradle" -p "$generated_directory" "$gradle_task" --no-daemon

[[ -f "$generated_aar" ]] || {
    printf 'Generated %s AAR was not found: %s\n' "$variant" "$generated_aar" >&2
    exit 1
}

if ! unzip -l "$generated_aar" | grep -q 'jni/arm64-v8a/libcrossa_runtime.so'; then
    printf 'Generated AAR is missing the arm64-v8a native library.\n' >&2
    exit 1
fi

cp "$generated_aar" "$aar_destination"
aar_sha256="$(shasum -a 256 "$aar_destination" | awk '{print $1}')"
configuration="Release"
if [[ "$variant" == "debug" ]]; then
    configuration="Debug"
fi
cat > "$android_project_directory/app/libs/crossa-generated-${variant}.manifest.json" <<EOF
{
  "crossaVersion": "$($crossa_cli --version)",
  "sourceCommit": "$crossa_source_commit",
  "cliPath": "$crossa_cli",
  "cliSha256": "$crossa_cli_sha256",
  "runtimeAbi": 1,
  "target": "android",
  "configuration": "$configuration",
  "artifactSha256": "$aar_sha256"
}
EOF
printf 'Prepared Android %s AAR: %s\n' "$variant" "$aar_destination"
printf 'AAR size: %s bytes\n' "$(stat -f '%z' "$aar_destination")"
printf 'AAR source: %s\n' "$generated_aar"
