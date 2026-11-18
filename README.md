# Cartel

**English** · [Français](README.fr.md)

Cartel reads museum labels (*cartels*) aloud, offline, and tells the story of the artwork when open data exists. Point your phone at a label, listen to it, find the artwork in a downloaded museum pack and keep it in your favorites.

Open source (Apache-2.0), Android and iOS, built with Kotlin Multiplatform and Compose Multiplatform. Museums covered: **Louvre**, **Musée d'Orsay** and **Musée de l'Orangerie** (Paris).

> **Status:** in development, not yet published on the App Store or Google Play. The automated tests pass, but scanning and speech have not yet been checked on a real device (see [What is not verified](#what-is-not-verified)).

## Features

- **Scan**: live scan (CameraX + ML Kit on Android, VisionKit on iOS) or an imported photo, then OCR, **label isolation**, text cleanup and offline speech. The label's language is detected; on a **bilingual label**, the app only offers that label's languages and reads the chosen one (the phone's language by default). The text can be **edited** before listening. If an installed pack contains the artwork, it is recognized (inventory number, title, artist) and its page is offered.
- **Search**: by title, artist or inventory number, in the downloaded museums. Well-known artworks come first. Detailed artwork page, read aloud.
- **Stories**: when the artwork has a French Wikipedia article, its introduction is read before the technical details, with attribution (CC BY-SA 4.0) and a link to the article.
- **Images**: only freely licensed Wikimedia Commons images, loaded online when the page opens and then cached, with author and license.
- **Favorites**: a heart on an artwork page or on the recognized artwork, or "Keep this label" for text with no known artwork. A snapshot is kept: favorites stay readable offline, even if the pack is deleted. Personal note, Markdown export (share sheet, e.g. to Obsidian).
- **Museums**: download, update and delete packs.
- **Settings**: speech rate, **voice per language** (standard / enhanced / premium quality, with a preview), credits, privacy.

UI in French and English, five tabs, dark mode. Once a pack is downloaded, everything works offline except artwork images.

## How a label is read

OCR returns **lines with their position** (`TextLine`), not flat text. Everything after that lives in `:core` and is testable without a device:

1. `LabelReader`: a small or doubtful photo is read again **cropped to the text and enlarged** (the biggest OCR gain: a 337 px banner goes from 0.87 to 1.00 confidence). Keeps the better of the two reads.
2. `LabelExtractor`: drops noise (logos, watermarks, web addresses, phone numbers, vertical text, low-confidence lines), keeps the main text area (the densest, most centered one), finds **columns** by their gutter, joins the pieces of a line column by column and rebuilds **paragraphs from line spacing** (measured on line centers, because a skewed photo distorts box heights).
3. `LabelLanguages`: one language per column (or line by line if no columns were found), then `CartelCleaner` cleans each version (inventory numbers, dates, abbreviations).
4. `SpeechText`: final punctuation, softened capitals ("GAUGUIN" becomes "Gauguin"), then the synthesizers insert **a pause between paragraphs**.

The fixtures in `core/src/jvmTest/resources/ocr/*.json` are real OCR outputs (anonymized text) of three photographed labels: they protect the extractor's tuning.

## Project structure

```
core/          KMP, no UI: label domain, catalog, favorites, settings, pack format
shared/        Compose Multiplatform (screens, navigation, theme) + native access (OCR, speech, camera, sharing)
androidApp/    Android application
iosApp/        Xcode project (SwiftUI hosting Compose + VisionKit scanner in Swift)
pack-builder/  JVM tool: builds the packs from the Joconde database
tools/         make-icons.py: regenerates the icon (Pillow)
packs/         Generated packs (ignored by git)
```

OCR and speech are native, behind interfaces injected with Koin. Storage uses bundled `androidx.sqlite`; networking uses Ktor; images use Coil 3. A new tab is declared in `TopLevel` (`shared/.../ui/navigation/Navigation.kt`).

## Build and test

Requires JDK 21 (for `:pack-builder`) and JDK 25 (the Gradle daemon is pinned in `gradle/gradle-daemon-jvm.properties`; `:core` targets Java 11 so that `:pack-builder` can load it). Android: minSdk 24. iOS: 16.0 or later.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
./gradlew :core:jvmTest :core:iosSimulatorArm64Test :pack-builder:test :shared:jvmTest   # tests
./gradlew :androidApp:assembleDebug                                                      # debug APK
```

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build/ios \
  CODE_SIGNING_ALLOWED=NO build
```

`local.properties` (ignored by git) must contain `sdk.dir=` pointing to your Android SDK.

CI (`.github/workflows/ci.yml`) runs the JVM tests, the debug APK and the iOS simulator tests on every push and pull request.

## Building and serving packs

```bash
./gradlew :pack-builder:run --args="--out packs --cache build/pack-cache"   # all museums
./gradlew :pack-builder:run --args="--museum orsay"                          # a single one
python3 -m http.server 8080 --directory packs                                # development server
```

Packs are written to `packs/` along with `manifest.json`. `--stories no` builds packs without stories.

**Pack server address**: on Android, the Gradle property `-PpackBaseUrl=https://…` (for a local server seen from the emulator: `-PpackBaseUrl=http://10.0.2.2:8080/`; not reachable from a real phone). On iOS, `PACK_BASE_URL` in `iosApp/Configuration/Config.xcconfig` (`http:/$()/localhost:8080/` in debug: `$()` prevents `//` from being read as a comment). By default, Android (debug and release) and iOS release point to `https://github.com/anishda/cartel/releases/latest/download/`.

**Publishing**: the workflow `.github/workflows/packs.yml` rebuilds the packs every Thursday (Joconde is updated on Wednesdays) and publishes them in a `packs-YYYY-MM-DD` release marked "latest". Manual run: Actions tab, "Packs musée", Run workflow.

On Android 17, to let the emulator reach your computer, grant the local network permission to the debug build:

```bash
adb shell pm grant io.github.anishda.cartel android.permission.ACCESS_LOCAL_NETWORK
```

## Data sources and licenses

The code is licensed under Apache-2.0 (`LICENSE`). Packs carry their own licenses, per source.

- **Artwork records**: the Joconde database (French Ministry of Culture, via POP), Licence Ouverte 2.0. Source and date are shown in the app (artwork pages and Settings). Texts written by the Louvre and Orsay are not reused.
- **Stories (pack format v2)**: `pack-builder` links each artwork to Wikidata by its inventory number (P217) in a museum collection (P195), confirmed by a close artist name or title (one number sometimes refers to two objects: RF 644 is both Olympia and a bust). The introduction of the French Wikipedia article becomes the story, under CC BY-SA 4.0 with a link to the article. Notability (number of Wikimedia articles) ranks well-known artworks first in search. Packs of 2026-11-17: Louvre 6,883 linked artworks and 640 stories, Orsay 2,209 and 324, Orangerie 11 and 7.
- **Images (pack format v3)**: Wikidata gives the artwork's image (P18); `pack-builder` queries Commons and keeps only **free licenses** (public domain, CC0, CC BY, CC BY-SA; CC BY and CC BY-SA require an author). The pack only holds the file name, author and license. The app loads the thumbnail (500 px, about 100 KB) when the page opens, caches it on disk, and shows the author and license under the image with a link to its Commons page. Offline, an artwork never opened has no image; the rest of the page is unchanged. No image is embedded in the packs (embedding thumbnails would cost about 150 MB for the Louvre). Packs of 2026-11-17: Louvre 3,508 images, Orsay 1,119, Orangerie 9.

No generative AI is used to write artwork stories: only sourced open data. Pack formats v1, v2 and v3 remain readable by the app.

## What is not verified

Nothing involving OCR and speech **on a device**: ML Kit and Vision were only exercised through fixtures and calibration on photos (macOS OCR), not in the app; live scanning (frame rate, 1080p resolution, VisionKit) and photo import on a real label; voice quality by ear and the list of installed voices. The automated tests (JVM, iOS, Gradle) cover the core of `:core` and the scan session.

Work in progress in open pull requests, not yet merged: an optional natural voice on Android (Piper via sherpa-onnx), precomputed phonemes in packs (format v4), numbers spelled out for French speech, thumbnails and a player in favorites.

## Identifiers

Android application and iOS bundle: `io.github.anishda.cartel`. The Kotlin package remains `app.audioguide` (not visible on the stores). Cartel was previously developed under the working name "Audioguide Musées".
