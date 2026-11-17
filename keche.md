# Audioguide Musées

## Objectif
App KMP (Android + iOS, Compose Multiplatform) : scanner un cartel de musée, l'écouter hors ligne, retrouver l'œuvre dans des packs musée, garder des favoris. Cadrage complet : note du vault Veaucer `01.Projects/Dev - Audioguide Musées (KMP).md`. Détails techniques : `README.md`.

## Décisions
- Hors ligne d'abord ; packs générés par `pack-builder` (base Joconde), ignorés par git (`/packs/`).
- Orsay : autorisation écrite requise avant d'embarquer ses données.
- 2026-11-02 : pas de mail au Louvre ni de demande à Orsay/RMN-GP. On reste dans la légalité et on ne réutilise pas leurs textes sous licence restreinte (donc T6, T7 et la dépendance de T8 sont caduques, à refondre).
- Anis teste lui-même sur appareil ; pas de test émulateur par l'agent.

## Questions ouvertes
-

## Work (kechup — do not edit)

Doing:
- T4: Héberger les packs en HTTPS (VPS hda.ynh.fr ou release GitHub) et renseigner l'adresse [claude:vex]
- T11: Trancher l'open source (Apache-2.0) et mettre en place la CI de régénération des packs [claude:vex]

Blocked:
- T2: Décider du scan : photo plein format via LabelReader ou scanner de documents natif (waits for T1)
- T3: Décider des voix : voix neuronale embarquée (Piper/sherpa-onnx) si voix système insuffisantes (waits for T1)
- T8: Corpus curaté de 100 à 300 chefs-d'œuvre par musée (récits sourcés, CC BY-SA) (waits for dropped T6; waits for dropped T7)
- T10: Stores : comptes développeur, fiches, politique de confidentialité (URL) (waits for T4; waits for T9)

Todo:
- T1: Essai sur appareil réel (Anis) : scan live, import, voix, vitesse, bilingue depuis la refonte
- T5: Filtre œuvres exposées : affiner au-delà de peinture+sculpture (image, croisement Wikidata)
- T9: Choisir nom et identifiant définitifs de l'app

Done (last 5):
- T12: Récit des œuvres depuis Wikipedia FR (liaison Wikidata, pack v2, fiche)

<!-- kechup:end -->
