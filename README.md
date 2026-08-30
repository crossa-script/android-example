# android-example

Android network comparison demo for the same request:

```text
GET https://jsonplaceholder.typicode.com/posts
```

The app has one screen with three scenarios:

- Retrofit + OkHttp interceptor headers and response metadata.
- Ktor client with coroutines.
- Crossa `.cra` + `config.cra` executed by the Crossa CLI through `syncCrossaCliScenario`.

Each scenario runs 5 requests with a 750ms delay. The screen shows success count, total time, average time, min/max, mapped post count, the first mapped post, request headers, and response preview.
After all three results are available, the Activity prints the average milliseconds for each scenario and marks the fastest successful 5/5 run as the winner.

## Verify

```sh
./gradlew verifyDemo
```

`syncCrossaCliScenario` runs `../Crossa/build/crossa run crossa/posts.cra` five times and writes the measured Crossa result into `app/src/main/assets/crossa_metrics.json`.
