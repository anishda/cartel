# Audioguide Musées

Suivi des tâches avec `kechup` (voir `kechup help`). ID agent : `export KECHUP_AGENT=claude:vex`.

Début de session : `kechup show` puis `kechup list --state open`, et lire `keche.md` (décisions, questions ouvertes).
Pendant : `kechup next --claim`, `kechup note T<n> "…"` à chaque jalon.
Fin de tâche : écrire `REPORT.md` dans le dossier de la tâche, `kechup commit <fichiers> -m "…" --task T<n>`, puis `kechup done T<n> --evidence <REPORT.md>` après tests.
Ne pas utiliser `git add -A` ni `git commit -a` ; ne pas éditer la section Work de `keche.md` ni `.kechup/`.
Anis teste lui-même sur appareil : pas de test émulateur.
