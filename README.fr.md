# Cartel

[English](README.md) · **Français**

Cartel lit à voix haute les cartels de musée, hors ligne, et raconte l'histoire de l'œuvre quand des données ouvertes existent. On vise le cartel avec son téléphone, on l'écoute, on retrouve l'œuvre dans un pack musée téléchargé et on la garde dans ses favoris.

Open source (Apache-2.0), Android et iOS, en Kotlin Multiplatform et Compose Multiplatform. Musées couverts : **Louvre**, **musée d'Orsay** et **musée de l'Orangerie** (Paris).

> **État :** en développement, pas encore publiée sur l'App Store ni sur Google Play. Les tests automatiques passent, mais le scan et la voix n'ont pas encore été vérifiés sur un vrai appareil (voir [Ce qui n'est pas vérifié](#ce-qui-nest-pas-vérifié)).

## Fonctionnalités

- **Scanner** : scan en direct (CameraX + ML Kit sur Android, VisionKit sur iOS) ou photo importée, puis OCR, **isolation du cartel**, nettoyage du texte et lecture vocale hors ligne. La langue du cartel est devinée ; sur un **cartel bilingue**, l'app ne propose que ses langues et lit celle choisie (celle du téléphone par défaut). Le texte lu est **modifiable** avant l'écoute. Si un pack installé contient l'œuvre, elle est reconnue (inventaire, titre, artiste) et sa fiche est proposée.
- **Rechercher** : par titre, artiste ou numéro d'inventaire, dans les musées téléchargés. Les œuvres connues remontent en premier. Fiche détaillée, lecture à voix haute.
- **Récits** : quand l'œuvre a un article sur Wikipédia en français, son introduction est lue avant la fiche technique, avec l'attribution (CC BY-SA 4.0) et un lien vers l'article.
- **Images** : uniquement des images Wikimedia Commons sous licence libre, chargées en ligne à l'ouverture de la fiche puis gardées en cache, avec auteur et licence.
- **Favoris** : cœur sur une fiche ou sur l'œuvre reconnue, ou « Garder ce cartel » pour un texte sans œuvre connue. Un instantané est gardé : les favoris restent lisibles sans réseau, même si le pack est supprimé. Note personnelle, export en Markdown (feuille de partage, par exemple vers Obsidian).
- **Musées** : téléchargement, mise à jour et suppression des packs.
- **Réglages** : vitesse de lecture, **choix de la voix par langue** (qualité standard / améliorée / premium, essai), crédits, vie privée.

Interface en français et en anglais, cinq onglets, mode sombre. Une fois un pack téléchargé, tout fonctionne hors ligne sauf les images des œuvres.

## Comment le scan lit un cartel

L'OCR ne rend pas du texte à plat mais des **lignes avec leur position** (`TextLine`). Ensuite, tout est dans `:core`, testable sans appareil :

1. `LabelReader` : une photo petite ou douteuse est relue en **recadrant sur le texte et en l'agrandissant** (le gain d'OCR le plus net : une bannière de 337 px passe de 0,87 à 1,00 de confiance). Garde la meilleure des deux lectures.
2. `LabelExtractor` : écarte le bruit (logos, filigranes, adresses web, téléphones, texte vertical, lignes peu sûres), retient la zone de texte principale (la plus fournie, la plus centrée), repère les **colonnes** par leur gouttière, recolle les morceaux d'une même ligne colonne par colonne et rebâtit les **paragraphes d'après l'interligne** (mesuré sur le centre des lignes : une photo de biais fausse la hauteur des boîtes).
3. `LabelLanguages` : une langue par colonne (ou ligne à ligne si les colonnes n'ont pas été reconnues), puis `CartelCleaner` nettoie chaque version (n° d'inventaire, dates, abréviations).
4. `SpeechText` : ponctuation finale, capitales adoucies (« GAUGUIN » → « Gauguin »), puis les synthétiseurs marquent **une pause entre les paragraphes**.

Les fixtures `core/src/jvmTest/resources/ocr/*.json` sont de vraies sorties d'OCR (texte anonymisé) de trois cartels photographiés : elles protègent les réglages de l'extracteur.

## Structure

```
core/          KMP sans interface : domaine du cartel, catalogue, favoris, réglages, format des packs
shared/        Compose Multiplatform (écrans, navigation, thème) + accès natifs (OCR, voix, caméra, partage)
androidApp/    Application Android
iosApp/        Projet Xcode (SwiftUI qui embarque Compose + scanner VisionKit en Swift)
pack-builder/  Outil JVM : fabrique les packs à partir de la base Joconde
tools/         make-icons.py : régénère l'icône (Pillow)
packs/         Packs générés (ignorés par git)
```

OCR et voix sont natifs, derrière des interfaces injectées avec Koin. Stockage : `androidx.sqlite` bundled ; réseau : Ktor ; images : Coil 3. Un nouvel onglet se déclare dans `TopLevel` (`shared/.../ui/navigation/Navigation.kt`).

## Compiler et tester

JDK 21 (pour `:pack-builder`) et JDK 25 requis (le démon Gradle est épinglé dans `gradle/gradle-daemon-jvm.properties` ; `:core` vise Java 11 pour que `:pack-builder` puisse le charger). Android : minSdk 24. iOS : 16.0 ou plus.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
./gradlew :core:jvmTest :core:iosSimulatorArm64Test :pack-builder:test :shared:jvmTest   # tests
./gradlew :androidApp:assembleDebug                                                      # APK debug
```

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build/ios \
  CODE_SIGNING_ALLOWED=NO build
```

`local.properties` (ignoré par git) doit contenir `sdk.dir=` avec le chemin de votre SDK Android.

La CI (`.github/workflows/ci.yml`) lance les tests JVM, l'APK debug et les tests sur simulateur iOS à chaque push et pull request.

## Fabriquer et servir les packs

```bash
./gradlew :pack-builder:run --args="--out packs --cache build/pack-cache"   # tous les musées
./gradlew :pack-builder:run --args="--museum orsay"                          # un seul
python3 -m http.server 8080 --directory packs                                # serveur de développement
```

Les packs sortent dans `packs/` avec `manifest.json`. `--stories no` fabrique des packs sans récits.

**Adresse du serveur de packs** : Android, propriété Gradle `-PpackBaseUrl=https://…` (pour un serveur local vu depuis l'émulateur : `-PpackBaseUrl=http://10.0.2.2:8080/` ; pas joignable depuis un vrai téléphone). iOS, `PACK_BASE_URL` dans `iosApp/Configuration/Config.xcconfig` (`http:/$()/localhost:8080/` en debug : `$()` évite que `//` soit pris pour un commentaire). Par défaut, Android (debug et release) et iOS en release pointent vers `https://github.com/anishda/cartel/releases/latest/download/`.

**Publication** : le workflow `.github/workflows/packs.yml` régénère les packs chaque jeudi (Joconde est mise à jour le mercredi) et les publie dans une release `packs-AAAA-MM-JJ` marquée « latest ». Lancement manuel : onglet Actions, « Packs musée », Run workflow.

Android 17 : pour que l'émulateur joigne l'ordinateur, accorder la permission réseau local au build debug :

```bash
adb shell pm grant io.github.anishda.cartel android.permission.ACCESS_LOCAL_NETWORK
```

## Sources et licences

Code sous licence Apache-2.0 (`LICENSE`). Les packs ont leurs propres licences, par source.

- **Fiches** : base Joconde (ministère de la Culture, via POP), Licence Ouverte 2.0. La source et la date sont affichées dans l'app (fiches et écran Réglages). Les textes rédigés par le Louvre et Orsay ne sont pas repris.
- **Récits (format de pack v2)** : `pack-builder` relie chaque œuvre à Wikidata par son numéro d'inventaire (P217) dans une collection du musée (P195), confirmé par un nom d'artiste ou un titre proche (un même numéro désigne parfois deux objets : RF 644 est Olympia et un buste). L'introduction de l'article Wikipédia en français devient le récit, sous CC BY-SA 4.0 avec un lien vers l'article. La notoriété (nombre d'articles Wikimedia) fait remonter les œuvres connues dans la recherche. Packs du 2026-11-17 : Louvre 6 883 œuvres reliées et 640 récits, Orsay 2 209 et 324, Orangerie 11 et 7.
- **Images (format de pack v3)** : Wikidata donne l'image de l'œuvre (P18) ; `pack-builder` interroge Commons et ne garde que les **licences libres** (domaine public, CC0, CC BY, CC BY-SA ; CC BY et CC BY-SA exigent un auteur). Le pack ne contient que le nom du fichier, l'auteur et la licence. L'app charge la miniature (500 px, environ 100 Ko) à l'ouverture de la fiche, la garde dans son cache disque, et affiche l'auteur et la licence sous l'image avec un lien vers sa page Commons. Sans réseau, une œuvre jamais ouverte n'a pas d'image ; le reste de la fiche ne change pas. Aucune image n'est embarquée dans les packs (embarquer les miniatures coûterait environ 150 Mo pour le Louvre). Packs du 2026-11-17 : Louvre 3 508 images, Orsay 1 119, Orangerie 9.

Aucune IA générative n'écrit l'histoire des œuvres : uniquement des données ouvertes sourcées. Les formats de pack v1, v2 et v3 restent lisibles par l'app.

## Ce qui n'est pas vérifié

Rien de ce qui touche l'OCR et la voix **sur un appareil** : ML Kit et Vision n'ont été exercés que via les fixtures et le calibrage sur des photos (OCR de macOS), pas dans l'app ; le scan en direct (cadence, résolution 1080p, VisionKit) et l'import de photo sur un vrai cartel ; la qualité des voix à l'oreille et la liste des voix installées. Les tests automatiques (JVM, iOS, Gradle) couvrent le cœur de `:core` et la session de scan.

En cours dans des pull requests ouvertes, pas encore fusionnées : voix naturelle optionnelle sur Android (Piper via sherpa-onnx), phonèmes précalculés dans les packs (format v4), nombres épelés pour la voix française, miniatures et lecteur dans les favoris.

## Identifiants

Application Android et bundle iOS : `io.github.anishda.cartel`. Le package Kotlin reste `app.audioguide` (invisible sur les stores). Cartel a d'abord été développée sous le nom de travail « Audioguide Musées ».
