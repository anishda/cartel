package app.audioguide.catalog

import kotlinx.coroutines.CoroutineDispatcher

/** Contexte des accès disque et SQLite. `Dispatchers.IO` n'est pas visible du code commun sur iOS. */
internal expect val IoDispatcher: CoroutineDispatcher
