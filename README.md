# android-example

Android consumer of a generated Crossa **Release AAR**. The app compares Crossa, Retrofit+OkHttp, and Ktor against:

```text
GET https://jsonplaceholder.typicode.com/posts
```

The default Crossa artifact is `app/libs/crossa-generated-release.aar`. Debug generation remains available for native troubleshooting and is not the benchmark artifact.

## Verify

```sh
./scripts/generate-build.sh
./gradlew verifyDemo
./gradlew :app:assembleRelease
```

`generate-build.sh` runs `crossa generate-build android` and Gradle `assembleRelease`, then copies `library-release.aar`. Set `CROSSA_CLI` to the CLI built from the Crossa commit being validated. Optional Debug Crossa artifacts:

```sh
CROSSA_AAR_VARIANT=debug ./scripts/generate-build.sh
```

## Benchmark methodology

The in-app harness is an observation tool, not a product performance claim.

- Warm runs create Crossa, Retrofit, and Ktor clients once and reuse them.
- Warmups are excluded from statistics.
- Measured rounds rotate client order.
- Timing uses `SystemClock.elapsedRealtimeNanos()`.
- Failed samples are counted separately and are not stored as 0 ms successes.
- Crossa reports native-ready list availability and later field materialization separately.
- Remote JSONPlaceholder latency includes the network and is not SDK-only overhead.

Use a physical ARM64 device and the app Release build before treating numbers as production evidence.
