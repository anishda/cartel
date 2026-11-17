# T14 — Version de pack unique par build

Cause : la version d'un pack était la date du jour (`2026-11-17`). Deux builds le même jour portaient la même version : l'app, qui propose la mise à jour quand `entry.version != installed.version`, ne voyait pas le pack avec images (v3) comme une mise à jour du pack sans images installé le même jour.

Fix : version = `AAAA-MM-JJ.HHmm` (UTC), dans `PackBuilder`. `:pack-builder:test` : 0 échec.
