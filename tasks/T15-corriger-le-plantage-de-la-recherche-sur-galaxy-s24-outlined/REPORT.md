# T15 — Plantage de la recherche sur Galaxy S24

Symptôme : l'app plante dès qu'on ouvre l'onglet Recherche sur le Galaxy S24 de Zac (`NoSuchElementException: Collection contains no element matching the predicate`, mesure de `OutlinedTextField`, Material3 1.11.0-alpha07).

Cause : le `OutlinedTextField` de la version alpha de Material3 ne retrouve pas son champ interne à la mesure sur cet appareil. Passer `trailingIcon = null` quand le champ est vide ne suffit pas (testé sur l'appareil).

Fix : composant `AppTextField` (`BasicTextField` + décor maison : contour, label, placeholder, icônes) utilisé à la place d'`OutlinedTextField` dans Recherche, Favoris (note) et Scan (texte modifiable) : les trois champs étaient exposés au même plantage.

Vérifié sur le S24 : Recherche s'ouvre, la saisie filtre les résultats, la fiche de la Joconde affiche l'image. Tests JVM, APK release, build Xcode simulateur OK. Champs Favoris (note) et Scan (texte modifiable) non essayés sur l'appareil.
