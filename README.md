# android-example

Android network comparison demo for the same request:

```text
GET https://jsonplaceholder.typicode.com/posts
```

The app has one screen with three scenarios:

- Retrofit + OkHttp interceptor headers and response metadata.
- Ktor client with coroutines.
- Crossa `.cra` + `config.cra` generated into an Android `.aar`, linked by the demo app, and called through the generated `@AsyncAfter` callback API.

Each scenario runs 5 requests with a 750ms delay. The screen shows success count, total time, average time, min/max, mapped post count, the first mapped post, request headers, and response preview.
After all three results are available, the Activity prints the average milliseconds for each scenario and marks the fastest successful 5/5 run as the winner.

## Verify

```sh
./scripts/generate-crossa-aar.sh
./gradlew verifyDemo
```

The script calls the Crossa CLI, builds `library-debug.aar`, and copies it to `app/libs/crossa-generated-debug.aar`. `verifyDemo` runs the same generation through the `generateCrossaAar` Gradle task before building the APK.
