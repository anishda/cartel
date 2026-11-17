# T13 — Images libres d'œuvres

## Fait
- Format de pack v3 : `image_file`, `image_author`, `image_license` (nom du fichier Commons, pas l'image). Packs v1 et v2 lisibles.
- `pack-builder` : image Wikidata (P18), licence et auteur via l'API Commons (lots de 50, cache). Gardé : domaine public, CC0, CC BY, CC BY-SA ; CC BY et CC BY-SA sans auteur écartés ; tout le reste écarté.
- App : miniature 500 px chargée en ligne avec Coil 3 (User-Agent Cartel pour Wikimedia), cache disque, crédit (auteur, licence) sous l'image avec lien vers la page Commons, rien d'affiché hors ligne tant que l'image n'est pas en cache. Crédit dans Réglages (FR/EN).
- Nouvelle dépendance : `io.coil-kt.coil3:coil-compose` et `coil-network-ktor3` 3.6.3.

## Résultats (packs du 2026-11-17)
Louvre 3 508 images (sur 7 443 œuvres), Orsay 1 119 (sur 3 453), Orangerie 9 (sur 133). Louvre : 2 800 domaine public, 209 CC0, le reste CC BY / CC BY-SA.

## Tests
`:core` JVM et iOS, `:pack-builder`, `:shared` : 0 échec. APK debug et release, build Xcode simulateur OK. Installé sur le Galaxy S24 de Zac, packs servis depuis le Mac ; à vérifier à l'œil par Zac.
