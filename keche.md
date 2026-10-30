# Audioguide Musées

## Objectif
App KMP (Android + iOS, Compose Multiplatform) : scanner un cartel de musée, l'écouter hors ligne, retrouver l'œuvre dans des packs musée, garder des favoris. Cadrage complet : note du vault Veaucer `01.Projects/Dev - Audioguide Musées (KMP).md`. Détails techniques : `README.md`.

## Décisions
- Hors ligne d'abord ; packs générés par `pack-builder` (base Joconde), ignorés par git (`/packs/`).
- Orsay : autorisation écrite requise avant d'embarquer ses données.
- Anis teste lui-même sur appareil ; pas de test émulateur par l'agent.

## Questions ouvertes
-

## Work (kechup — do not edit)

Doing:
- (none)

Blocked:
- T2: Décider du scan : photo plein format via LabelReader ou scanner de documents natif (waits for T1)

Todo:
- T1: Essai sur appareil réel (Anis) : scan live, import, voix, vitesse, bilingue depuis la refonte

Done (last 5):
- (none)

<!-- kechup:end -->
