# PROG2007 — Lecture 8: Intro to Debugging and Testing in Android - Accident Reporter Debugging & Testing Case Study)

This repo holds the case study app used in **PROG2007, Lecture 8 — Intro to Debugging and Testing in Android**. It's a small Compose app called **Accident Reporter**: it captures a real photo, fetches a real GPS location, shows both on a real Google Map, and submits a "report" — deliberately including one unguarded bug used for the lecture's live debugging demo.

The lecture slides reference this exact code (down to file and line), including a real, runnable JUnit test file. This README covers **everything** needed to get it running and testing green, including the one file this repo does *not* include.

## `app/build.gradle.kts` is not in this repo

That file isn't committed here. You need to create it yourself, once, when you first set the project up — full content is given below in [Setup](#setup), step 3. Nothing else needs it recreated; the app source and the test file are both in the repo as normal.

## What's in here

- `app/` — the Android app (`com.example.lect8testdebug`), currently as a single `MainActivity.kt` — this is intentional; splitting it into `ui/`, `viewmodel/`, `model/` packages is the accompanying lab exercise, not something already done for you.
- `app/src/test/.../AccidentReportTest.kt` — the local JUnit test behind the lecture's Unit Testing slides. Real, unmodified, and expected to go 6/6 green.
- **Not included:** `app/build.gradle.kts` — see above and step 3 below.

## Prerequisites

- Android Studio (a recent stable release).
- **A JDK 21 installation, used as the *Gradle* JDK** — see [Known gotchas](#known-gotchas) below for why this matters. Android Studio can download one for you if you don't have it.
- A Google Maps API key (free tier is fine) — see step 4 below.
- An emulator or physical device with Google Play services, camera, and location.

## Setup

1. **Clone and open** the project in Android Studio.
2. **Set the Gradle JDK to 21**, *before* your first sync:
   File → Settings (Android Studio → Settings on macOS) → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK** → pick a JDK 21 entry, or use "Download JDK…" right there if you don't have one installed. This is required — see [Known gotchas](#known-gotchas).
3. **Create `app/build.gradle.kts`** with exactly this content (this file is not in the repo — see above):

   ```kotlin
   plugins {
       alias(libs.plugins.android.application)
       alias(libs.plugins.kotlin.compose)
   }

   android {
       namespace = "com.example.lect8testdebug"
       compileSdk {
           version = release(37)
       }

       defaultConfig {
           applicationId = "com.example.lect8testdebug"
           minSdk = 24
           targetSdk = 37
           versionCode = 1
           versionName = "1.0"

           testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }

       buildTypes {
           release {
               optimization {
                   enable = false
               }
           }
       }
       compileOptions {
           sourceCompatibility = JavaVersion.VERSION_11
           targetCompatibility = JavaVersion.VERSION_11
       }
       buildFeatures {
           compose = true
       }
       testOptions {
           unitTests {
               isReturnDefaultValues = true
           }
       }
   }

   dependencies {
       implementation(platform(libs.androidx.compose.bom))
       implementation(libs.androidx.activity.compose)
       implementation(libs.androidx.compose.material3)
       implementation(libs.androidx.compose.ui)
       implementation(libs.androidx.compose.ui.graphics)
       implementation(libs.androidx.compose.ui.tooling.preview)
       implementation(libs.androidx.core.ktx)
       implementation(libs.androidx.lifecycle.runtime.ktx)
       testImplementation(libs.junit)
       testImplementation("org.robolectric:robolectric:4.14.1")
       androidTestImplementation(platform(libs.androidx.compose.bom))
       androidTestImplementation(libs.androidx.compose.ui.test.junit4)
       androidTestImplementation(libs.androidx.espresso.core)
       androidTestImplementation(libs.androidx.junit)
       debugImplementation(libs.androidx.compose.ui.test.manifest)
       debugImplementation(libs.androidx.compose.ui.tooling)

       // ViewModel support for the viewModel() composable function
       implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.5")

       // CameraX — real photo capture
       implementation("androidx.camera:camera-core:1.4.1")
       implementation("androidx.camera:camera-camera2:1.4.1")
       implementation("androidx.camera:camera-lifecycle:1.4.1")
       implementation("androidx.camera:camera-view:1.4.1")

       // Real location
       implementation("com.google.android.gms:play-services-location:21.3.0")

       // Real Google Map
       implementation("com.google.android.gms:play-services-maps:19.1.0")
       implementation("com.google.maps.android:maps-compose:6.1.2")
   }
   ```

   This references version-catalog aliases (`libs.androidx.activity.compose` and similar) from `gradle/libs.versions.toml`. That file *is* a standard part of any Android Studio project created from the "Empty Activity" (Compose) template, so if this repo doesn't include one either, create a fresh "Empty Activity" Compose project first (package name `com.example.lect8testdebug`) to get a working `gradle/libs.versions.toml`, `settings.gradle.kts`, and root `build.gradle.kts` — then drop this repo's `app/src/main` and `app/src/test` contents into that scaffold, and replace the generated `app/build.gradle.kts` with the block above.

4. **Add a Google Maps API key.** Get one from the [Google Cloud Console](https://console.cloud.google.com/) (enable the "Maps SDK for Android" API), then add it to `app/src/main/AndroidManifest.xml`, inside the `<application>` tag:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_API_KEY_HERE" />
   ```
   Don't commit a real key to a public repo — prefer injecting it via `local.properties` + `buildConfigField`, or a placeholder + a note for whoever runs it next.
5. **Sync Gradle**, then **Run** on an emulator or device.
6. On first launch, grant the camera and location permissions when prompted — both are requested at runtime, not just declared in the manifest.

## Running the app

- **Start screen → Report screen**, then:
  - **Take Photo** saves a real photo via CameraX to `MediaStore` and logs `Log.d(TAG, "photo saved: ...")`.
  - **Get Current Location** fetches a real fix via `FusedLocationProviderClient` and drops a marker on a real Google Map. If location services are off, it logs `Log.w(TAG, "no location fix available")` instead of crashing.
  - **Submit** is intentionally unguarded — submitting without a location first triggers a real `NullPointerException` in `submitReport()`. This is the planted bug the lecture's live debugging demo walks through with breakpoints and Logcat; it's not a bug in this repo you need to fix before using it.

## Running the tests

`AccidentReportTest.kt` covers `isReportValid()` with the AAA pattern and positive/negative/boundary cases — the exact code shown on the Unit Testing slides.

1. Open `app/src/test/java/com/example/lect8testdebug/AccidentReportTest.kt`.
2. Confirm the Gradle JDK is 21 (Setup, step 2) — this is the single most common reason this fails on a fresh machine.
3. Confirm `app/build.gradle.kts` exists with the content from Setup, step 3, including the `testOptions` block and the `robolectric` dependency — without those, every test fails with a "not mocked" error (see [Known gotchas](#known-gotchas)).
4. Right-click the class → **Run 'AccidentReportTest'**.
5. First run only: Robolectric downloads its Android SDK "shadow" jars over the network — this can take a minute. Do this once before you need it live.
6. Expect **6/6 green**.

## Known gotchas

These are real issues hit while building this project — documented here so you don't have to rediscover them.

**"Method X not mocked" (`RuntimeException`) when running local unit tests.**
The JVM used for local (`src/test`) unit tests runs against a stub `android.jar` where every real Android method throws by default. Fixed by `testOptions.unitTests.isReturnDefaultValues = true` in `build.gradle.kts` (included in the block above). Note this makes unmocked calls return defaults (`0`/`false`/`null`) instead of throwing — which is why a plain `Uri.parse(...)` or a plain `Mockito.mock(Location::class.java)` alone *isn't* enough (see next two points).

**`NullPointerException: parse(...) must not be null` / `EMPTY must not be null`.**
Under `isReturnDefaultValues = true`, *any* unmocked Android method or field — including `Uri.parse(...)` and even the static field `Uri.EMPTY` — returns `null`. There's no plain-JVM workaround for getting a genuinely non-null `Uri`/`Location` this way.

**Mockito: "cannot mock this class" / Byte Buddy Java-version error.**
Mockito's mocking engine (Byte Buddy) may not yet support very new JDKs. If you hit this, it's not something to chase by bumping Mockito versions — see the next point.

**The actual fix used here: Robolectric.** `AccidentReportTest.kt` is annotated `@RunWith(RobolectricTestRunner::class)` with `@Config(sdk = [34])`. Robolectric swaps in real, working shadow implementations of Android framework classes, so `Location`/`Uri` behave correctly on the plain JVM — no emulator, no mocking-library version chasing.

**`Unsupported class file major version 69` from Robolectric/ASM.**
This is JDK 25's class file format. Robolectric 4.14.1's bundled bytecode reader doesn't support it yet. Root cause: Android Studio's *bundled* JDK (the JetBrains Runtime) may be newer than the JDK your test tooling supports. **Fix: point Android Studio's Gradle JDK at a separate JDK 21 install** (Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK). This single change also resolves the Mockito/Byte Buddy issue above, if you go that route instead. This is the most important setup step in this whole README — do it before anything else.

## Course context

Built for **PROG2007 — Mobile Programming**, Lecture 8, as the running example for the "Debugging and Testing in Android" lecture. The lecture slides intentionally show this code's *current*, unrefactored, single-file state on the "File Organization" slide as the "BEFORE" example — splitting `MainActivity.kt` into `ui/`, `viewmodel/`, and `model/` packages is this week's lab exercise, not a bug.
