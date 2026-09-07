# Android Repository Guide

## Purpose and Structure

This repository is a standalone Android consumer and benchmark app for a generated Crossa AAR. It compares Crossa, Retrofit + OkHttp, and Ktor against the same JSONPlaceholder posts endpoint; it is an observation harness, not a production app.

- `app/` is the only Gradle module. `app/build.gradle.kts` owns the Android configuration, dependency versions, build metadata, and local AAR dependency.
- `app/src/main/java/com/crossa/androiddemo/MainActivity.kt` contains the Compose benchmark screen and result export.
- `app/src/main/java/com/crossa/androiddemo/benchmark/` contains benchmark configuration, samples, statistics, and orchestration.
- `app/src/main/java/com/crossa/androiddemo/network/` contains the Crossa, Retrofit, and Ktor clients plus their shared client contract.
- `app/src/main/java/com/crossa/androiddemo/Models.kt` and `JsonPlaceholderService.kt` contain the Retrofit-facing data model and service.
- `crossa/` contains the `.cra` source used to generate the Crossa library. `app/libs/` contains the checked-in debug/release AAR consumed by the demo.
- `scripts/generate-build.sh` generates the Crossa Android project, builds the selected AAR, validates `arm64-v8a`, and copies it into `app/libs/`.

For a benchmark change, normally navigate from `MainActivity` to `BenchmarkRunner`, then to `PostsBenchmarkClient` and the implementation-specific client. Keep client behavior and measurement rules comparable across implementations.

## Build and Validation

From this directory:

```sh
./scripts/generate-build.sh
./gradlew verifyDemo
./gradlew :app:assembleRelease
```

Use `CROSSA_AAR_VARIANT=debug ./scripts/generate-build.sh` for native troubleshooting. Set `CROSSA_CLI` when validating a specific Crossa compiler build. Use a physical ARM64 device and a Release build for benchmark evidence; remote network latency is part of every sample.

## Change Guidelines

Use Kotlin lowerCamelCase for functions and variables, PascalCase for types, and four-space indentation. Keep generated AARs reproducible and do not edit them manually. Update benchmark metadata when changing the artifact or source commit. Preserve existing uncommitted changes, and report the exact Gradle or generation commands used for validation.
