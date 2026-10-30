package app.audioguide.di

import org.koin.core.module.Module
import org.koin.dsl.module

// La cible JVM ne sert qu'aux tests et à vérifier l'indépendance du code commun : pas d'OCR, de voix ni de fichiers.
actual val platformModule: Module = module {}
