# Cartel

Anciennement « Audioguide Musées » (nom de travail).

App Kotlin Multiplatform (Android + iOS, UI Compose Multiplatform) : scanner le cartel d'une œuvre et l'écouter, hors ligne ; retrouver une œuvre dans des « packs musée » téléchargés ; garder ses favoris. Cadrage complet dans la note du vault Veaucer : `01.Projects/Dev - Audioguide Musées (KMP).md`.

## Ce que fait l'app

- **Scanner** : scan en direct (CameraX + ML Kit sur Android, VisionKit sur iOS) ou photo importée → OCR → **isolation du cartel** → nettoyage du texte → lecture vocale hors ligne. La langue du cartel est devinée ; sur un **cartel bilingue**, l'app ne propose que ses langues et lit celle choisie (celle du téléphone par défaut). Le texte lu est **modifiable** avant l'écoute. Si un pack installé contient l'œuvre, elle est reconnue (inventaire, titre, artiste) et sa fiche est proposée.
- **Rechercher** : par titre, artiste ou numéro d'inventaire, dans les musées téléchargés. Fiche détaillée, lecture à voix haute.
- **Favoris** : cœur sur une fiche ou sur l'œuvre reconnue, ou « Garder ce cartel » pour un texte sans œuvre connue. Un instantané est gardé : lisibles sans réseau, même si le pack est supprimé. Note personnelle, export en Markdown (feuille de partage, pour Obsidian).
- **Musées** : téléchargement, mise à jour et suppression des packs (Louvre, Orsay, Orangerie).
- **Réglages** : vitesse de lecture, **choix de la voix par langue** (qualité standard / améliorée / premium, essai), crédits, vie privée.

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

Un nouvel onglet se déclare dans `TopLevel` (`shared/.../ui/navigation/Navigation.kt`).

## Commandes

JDK 21 requis (le JDK par défaut de la machine est un build Valhalla expérimental). Le démon Gradle est épinglé sur un JDK 25 (`gradle/gradle-daemon-jvm.properties`) : `:core` vise donc explicitement Java 11 pour que `:pack-builder` (JDK 21) puisse le charger.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :core:jvmTest :core:iosSimulatorArm64Test :pack-builder:test :shared:jvmTest   # tests
./gradlew :androidApp:assembleDebug                                                      # APK debug
```

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build/ios \
  CODE_SIGNING_ALLOWED=NO build
```

`local.properties` (ignoré par git) doit contenir `sdk.dir=/Users/<toi>/Library/Android/sdk`.

## Fabriquer et servir les packs

```bash
./gradlew :pack-builder:run --args="--out packs --cache build/pack-cache"   # tous les musées
./gradlew :pack-builder:run --args="--museum orsay"                          # un seul
python3 -m http.server 8080 --directory packs                                # serveur de développement
```

Les packs sortent dans `packs/` avec `manifest.json`.

**Adresse du serveur de packs** : Android, propriété Gradle `-PpackBaseUrl=https://…` (pour un serveur local depuis l'émulateur : `-PpackBaseUrl=http://10.0.2.2:8080/`, le Mac vu de l'émulateur ; ce n'est pas joignable depuis un vrai téléphone). iOS, `PACK_BASE_URL` dans `iosApp/Configuration/Config.xcconfig` (`http:/$()/localhost:8080/` en debug : `$()` évite que `//` soit pris pour un commentaire). Par défaut, Android (debug et release) et iOS en release pointent vers `https://github.com/anishda/cartel/releases/latest/download/`.

**Publication des packs** : le workflow `.github/workflows/packs.yml` régénère les packs chaque jeudi (Joconde est mise à jour le mercredi) et les publie dans une release `packs-AAAA-MM-JJ` marquée « latest ». Lancement manuel : onglet Actions, « Packs musée », Run workflow. `.github/workflows/ci.yml` lance les tests JVM, l'APK debug et les tests iOS à chaque push et pull request.

Android 17 : pour que l'émulateur joigne le Mac, accorder la permission réseau local au build debug :

```bash
adb shell pm grant io.github.anishda.cartel android.permission.ACCESS_LOCAL_NETWORK
```

## Sources et licences

Code sous licence Apache-2.0 (`LICENSE`). Les packs ont leurs propres licences, par source.


Les fiches viennent de la base Joconde (ministère de la Culture), Licence Ouverte 2.0 : la source et la date sont affichées dans l'app (fiches et écran Réglages). Aucune image n'est embarquée dans les packs. Les textes du Louvre et d'Orsay ne sont pas repris (pas de demande d'autorisation, décision du 2026-11-17).

**Récits (format de pack v2)** : `pack-builder` relie chaque œuvre à Wikidata par son numéro d'inventaire (P217) dans une collection du musée (P195), confirmé par un nom d'artiste ou un titre proche (un même numéro désigne parfois deux objets : RF 644 est Olympia et un buste). Quand l'œuvre a un article sur Wikipédia en français, son introduction devient le récit de la fiche, lu avant la fiche technique, avec l'attribution (CC BY-SA 4.0) et un lien vers l'article. La notoriété (nombre d'articles Wikimedia) fait remonter les œuvres connues dans la recherche. Packs du 2026-11-17 : Louvre 6 883 œuvres reliées et 640 récits, Orsay 2 209 et 324, Orangerie 11 et 7. `--stories no` fabrique des packs sans récits. Les packs v1 et v2 restent lisibles par l'app.

**Images (format de pack v3)** : Wikidata donne l'image de l'œuvre (P18) ; `pack-builder` interroge Commons et ne garde que les **licences libres** (domaine public, CC0, CC BY, CC BY-SA ; CC BY et CC BY-SA exigent un auteur). Le pack ne contient que le nom du fichier, l'auteur et la licence : l'app charge la miniature (500 px, environ 100 Ko) quand la fiche s'ouvre, avec **Coil 3** (nouvelle dépendance, `coil-compose` et `coil-network-ktor3`), la garde dans son cache disque, et affiche l'auteur et la licence sous l'image avec un lien vers sa page Commons. Sans réseau, une œuvre jamais ouverte n'a pas d'image ; le reste de la fiche ne change pas. Embarquer les miniatures coûterait environ 150 Mo pour le Louvre. Packs du 2026-11-17 : Louvre 3 508 images, Orsay 1 119, Orangerie 9.

**Phonèmes pour la voix neuronale (format de pack v4)** : la voix Piper (sherpa-onnx, Apache-2.0) ne transforme plus le texte en phonèmes elle-même, car espeak-ng est sous GPL-3. `pack-builder --phonemizer "<commande>"` phonétise donc à la fabrication les textes lus des œuvres qui ont un récit (écoute courte et complète, segment de lecture par segment) avec `tools/phonemize.py` (`pip install piper-tts`, voix `fr_FR-siwis-medium` ; espeak-ng reste dans l'outil, jamais dans l'app), et les range dans la table `narration_phoneme`, clé = empreinte FNV-1a du segment (`NarrationPhonemes.key`). Les nombres sont déjà écrits en lettres et les corrections de prononciation (`PiperPronunciations`) appliquées. Pour le Louvre : 2 334 segments, +0,8 Mo (+14 %). Sans `--phonemizer`, les packs n'ont pas la table et l'app lit à la voix du système. Les packs v1 à v3 restent lisibles.

**Voix neuronale (Android)** : réglage facultatif « Voix naturelle » (Piper, voix SIWIS fr_FR, int8). Le modèle (`model.onnx` et `tokens.txt`, 13 Mo en zip) se télécharge une fois depuis `PiperVoiceModel.MODEL_URL` (empreinte SHA-256 vérifiée), puis tout est hors ligne. `PiperSpeechSynthesizer` lit un texte à la voix Piper quand son segment a des phonèmes dans le pack (clé `NarrationPhonemes.key`) et passe sinon à la voix du système ; il ne parle que français. Moteur : **sherpa-onnx 2.0.1** (Apache-2.0, nouvelle dépendance), dont l'API Kotlin (`Tts.kt`, copie sous `shared/src/androidMain/kotlin/com/k2fsa/sherpa/onnx/`) et les bibliothèques natives (`libonnxruntime.so`, `libsherpa-onnx-jni.so`, téléchargées à la compilation par la tâche `fetchSherpaOnnx` avec empreinte vérifiée) pèsent environ 27 Mo en arm64 et 19 Mo en armeabi-v7a (l'ABI x86 n'est plus livrée). Voix SIWIS : université d'Édimbourg, CC BY 4.0 ; Piper : MIT. Pas encore sur iOS.

**Lexique de la voix neuronale (textes hors pack)** : un cartel scanné n'est pas dans les packs, donc sans phonèmes précalculés. `pack-builder --lexicon <mots.tsv> --phonemizer "…"` fabrique `lexicon-fr.tsv.gz` (environ 98 000 mots, 600 Ko) : le vocabulaire de `fr_lexicon.tsv` ([beshkenadze/kokoro-ipa-lexicons](https://huggingface.co/beshkenadze/kokoro-ipa-lexicons), tiré de la base espeak de [gruut](https://github.com/rhasspy/gruut), MIT) et les mots des packs, chaque mot rephonétisé par `tools/phonemize.py`, avec le repère des h aspirés. L'app le télécharge avec la voix (`PiperVoiceModel.LEXICON_URL`, empreinte vérifiée) et `FrenchPhonemizer` (`:core`) phonétise le texte : élisions, liaisons, mots outils sans accent, règles d'orthographe pour les mots absents (`FrenchSpellingRules`). Comme les phonèmes des packs, les prononciations sont une sortie d'espeak-ng (GPL-3) : aucun code d'espeak-ng n'est dans l'app, mais le statut d'une telle sortie se discute (la liste d'origine le signale aussi).

## Ce qui n'est pas vérifié

Rien de ce qui touche l'OCR et la voix **sur un appareil** : ML Kit et Vision n'ont été exercés que via les fixtures et le calibrage sur des photos (OCR du Mac), pas dans l'app ; le scan en direct (cadence, résolution 1080p, VisionKit) et l'import de photo sur un vrai cartel ; la qualité des voix à l'oreille et la liste des voix installées. Les tests automatiques (JVM, iOS, Gradle) couvrent le cœur de `:core` et la session de scan.

## Identifiants

Application Android et bundle iOS : `io.github.anishda.cartel` (décidé le 2026-11-17). Le package Kotlin reste `app.audioguide` : il n'est pas visible des stores.
