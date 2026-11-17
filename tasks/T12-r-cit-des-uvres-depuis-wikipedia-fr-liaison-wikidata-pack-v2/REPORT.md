# T12 — Récit des œuvres depuis Wikipedia FR

## Fait
- Format de pack v2 (`PackSchema.VERSION = 2`) : `wikidata_id`, `notability` (sitelinks Wikimedia), `story_text`, `story_title`, `story_url` ; méta `story_license`. Les packs v1 restent lisibles (`PackSchema.columns(version)`).
- `pack-builder` : `WikimediaApi` (SPARQL Wikidata par musée, intros Wikipedia FR par lots de 20, cache disque), `WorkMatcher` (inventaire + artiste commun ou titre proche : RF 644 est à la fois Olympia et un buste), `StoryFormatter` (paragraphes, phonétique retirée, 1 800 caractères max), `StoryEnricher`. Option `--stories no`.
- Recherche : à rang égal, les œuvres connues (notoriété) puis celles avec photo passent en premier.
- App : section « Histoire de l'œuvre » sur la fiche avec attribution CC BY-SA et lien vers l'article ; le récit est lu entre l'identité et la fiche technique, suivi de sa source ; crédit dans Réglages (FR/EN).

## Résultats (packs du 2026-11-17)
| Musée | Œuvres | Reliées à Wikidata | Avec récit |
|---|---|---|---|
| Louvre | 7 443 | 6 883 | 640 |
| Orsay | 3 453 | 2 209 | 324 |
| Orangerie | 133 | 11 | 7 |

## Tests
270 tests JVM + iOS simulateur (`:core`, `:shared`, `:pack-builder`), 0 échec. APK debug et build Xcode simulateur OK. Pas testé sur appareil (Anis teste lui-même).
