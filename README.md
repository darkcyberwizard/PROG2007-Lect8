# PROG2007 — Lecture 8: Accident Reporter (Debugging & Testing Case Study)

This repo holds the case study app used in **PROG2007, Lecture 8 — Debugging and Testing in Android**. It's a small Compose app called **Accident Reporter**: it captures a real photo, fetches a real GPS location, shows both on a real Google Map, and submits a "report" — deliberately including one unguarded bug used for the lecture's live debugging demo.

The lecture slides reference this exact code (down to file and line), including a real, runnable JUnit test file. This README covers everything needed to get it running and testing green.

## What's in here

- `app/` — the Android app (`com.example.lect8testdebug`), currently as a single `MainActivity.kt` — this is intentional; splitting it into `ui/`, `viewmodel/`, `model/` packages is the accompanying lab exercise, not something already done for you.
- `app/src/test/.../AccidentReportTest.kt` — the local JUnit test behind the lecture's Unit Testing slides. Real, unmodified, and expected to go 6/6 green.
- `app/build.gradle.kts` — already wired with every dependency below.

## Prerequisites

- Android Studio (a recent stable release).
- **A JDK 21 installation, used as the *Gradle* JDK** — see [Known gotchas](#known-gotchas) below for why this matters. Android Studio can download one for you if you don't have it.
- A Google Maps API key (free tier is fine) — see below.
- An emulator or physical device with Google Play services, camera, and location.

## Setup

1. **Clone and open** the project in Android Studio.
2. **Set the Gradle JDK to 21**, *before* your first sync:
   File → Settings (Android Studio → Settings on macOS) → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK** → pick a JDK 21 entry, or use "Download JDK…" right there if you don't have one installed. This is required — see [Known gotchas](#known-gotchas).
3. **Add a Google Maps API key.** Get one from the [Google Cloud Console](https://console.cloud.google.com/) (enable the "Maps SDK for Android" API), then add it to your manifest:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_API_KEY_HERE" />
   ```
   Don't commit a real key to a public repo — prefer injecting it via `local.properties` + `buildConfigField`, or a placeholder + a note for whoever runs it next.
4. **Sync Gradle**, then **Run** on an emulator or device.
5. On first launch, grant the camera and location permissions when prompted — both are requested at runtime, not just declared in the manifest.

## Running the app

- **Start screen → Report screen**, then:
  - **Take Photo** saves a real photo via CameraX to `MediaStore` and logs `Log.d(TAG, "photo saved: ...")`.
  - **Get Current Location** fetches a real fix via `FusedLocationProviderClient` and drops a marker on a real Google Map. If location services are off, it logs `Log.w(TAG, "no location fix available")` instead of crashing.
  - **Submit** is intentionally unguarded — submitting without a location first triggers a real `NullPointerException` in `submitReport()`. This is the planted bug the lecture's live debugging demo walks through with breakpoints and Logcat; it's not a bug in this repo you need to fix before using it.

## Running the tests

`AccidentReportTest.kt` covers `isReportValid()` with the AAA pattern and positive/negative/boundary cases — the exact code shown on the Unit Testing slides.

1. Open `app/src/test/java/com/example/lect8testdebug/AccidentReportTest.kt`.
2. Confirm the Gradle JDK is 21 (see Setup, step 2) — this is the single most common reason this fails on a fresh machine.
3. Right-click the class → **Run 'AccidentReportTest'**.
4. First run only: Robolectric downloads its Android SDK "shadow" jars over the network — this can take a minute. Do this once before you need it live.
5. Expect **6/6 green**.

## Project dependencies

Already declared in `app/build.gradle.kts` — nothing to add manually:

```kotlin
// Compose + core
implementation(libs.androidx.activity.compose)
implementation(libs.androidx.compose.material3)
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.5")

// CameraX — real photo capture
implementation("androidx.camera:camera-core:1.4.1")
implementation("androidx.camera:camera-camera2:1.4.1")
implementation("androidx.camera:camera-lifecycle:1.4.1")
implementation("androidx.camera:camera-view:1.4.1")

// Real location + real map
implementation("com.google.android.gms:play-services-location:21.3.0")
implementation("com.google.android.gms:play-services-maps:19.1.0")
implementation("com.google.maps.android:maps-compose:6.1.2")

// Local unit tests
testImplementation(libs.junit)
testImplementation("org.robolectric:robolectric:4.14.1")
```

Also relevant, in the `android { }` block:

```kotlin
testOptions {
    unitTests {
        isReturnDefaultValues = true
    }
}
```

## Known gotchas

These are real issues hit while building this project — documented here so you don't have to rediscover them.

**"Method X not mocked" (`RuntimeException`) when running local unit tests.**
The JVM used for local (`src/test`) unit tests runs against a stub `android.jar` where every real Android method throws by default. Fixed by `testOptions.unitTests.isReturnDefaultValues = true` in `build.gradle.kts` (already set in this repo). Note this makes unmocked calls return defaults (`0`/`false`/`null`) instead of throwing — which is why a plain `Uri.parse(...)` or a plain `Mockito.mock(Location::class.java)` alone *isn't* enough (see next two points).

**`NullPointerException: parse(...) must not be null` / `EMPTY must not be null`.**
Under `isReturnDefaultValues = true`, *any* unmocked Android method or field — including `Uri.parse(...)` and even the static field `Uri.EMPTY` — returns `null`. There's no plain-JVM workaround for getting a genuinely non-null `Uri`/`Location` this way.

**Mockito: "cannot mock this class" / Byte Buddy Java-version error.**
Mockito's mocking engine (Byte Buddy) may not yet support very new JDKs. If you hit this, it's not something to chase by bumping Mockito versions — see the next point.

**The actual fix used here: Robolectric.** `AccidentReportTest.kt` is annotated `@RunWith(RobolectricTestRunner::class)` with `@Config(sdk = [34])`. Robolectric swaps in real, working shadow implementations of Android framework classes, so `Location`/`Uri` behave correctly on the plain JVM — no emulator, no mocking-library version chasing.

**`Unsupported class file major version 69` from Robolectric/ASM.**
This is JDK 25's class file format. Robolectric 4.14.1's bundled bytecode reader doesn't support it yet. Root cause: Android Studio's *bundled* JDK (the JetBrains Runtime) may be newer than the JDK your test tooling supports. **Fix: point Android Studio's Gradle JDK at a separate JDK 21 install** (Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK). This single change also resolves the Mockito/Byte Buddy issue above, if you go that route instead. This is the most important setup step in this whole README — do it before anything else.

## Course context

Built for **PROG2007 — Mobile Programming**, Lecture 8, as the running example for the "Debugging and Testing in Android" lecture. The lecture slides intentionally show this code's *current*, unrefactored, single-file state on the "File Organization" slide as the "BEFORE" example — splitting `MainActivity.kt` into `ui/`, `viewmodel/`, and `model/` packages is this week's lab exercise, not a bug.
