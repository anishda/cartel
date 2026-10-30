package app.audioguide.feature.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.audioguide.catalog.PackInfo
import app.audioguide.packs.PackManifest
import app.audioguide.packs.PackManifestEntry
import app.audioguide.packs.PackStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RemoteState {
    data object Loading : RemoteState
    data class Loaded(val manifest: PackManifest) : RemoteState
    data class Failed(val reason: String) : RemoteState
}

sealed interface PackOperation {
    data object None : PackOperation
    data class Downloading(val progress: Float) : PackOperation
    data class Failed(val message: String) : PackOperation
}

/** Un musée tel que l'écran le montre : ce que propose le serveur et ce qui est installé. */
data class PackRow(
    val id: String,
    val name: String,
    val city: String?,
    val entry: PackManifestEntry?,
    val installed: PackInfo?,
    val operation: PackOperation,
) {
    val updateAvailable: Boolean get() = entry != null && installed != null && entry.version != installed.version
}

data class PacksUiState(
    val remote: RemoteState = RemoteState.Loading,
    val rows: List<PackRow> = emptyList(),
)

class PacksViewModel(
    private val store: PackStore,
    private val remote: PackRemote,
) : ViewModel() {
    private val remoteState = MutableStateFlow<RemoteState>(RemoteState.Loading)
    private val operations = MutableStateFlow<Map<String, PackOperation>>(emptyMap())

    val state: StateFlow<PacksUiState> = combine(remoteState, operations, store.installed) { remote, ops, installed ->
        val entries = (remote as? RemoteState.Loaded)?.manifest?.packs.orEmpty()
        val fromServer = entries.map { entry ->
            PackRow(entry.id, entry.name, entry.city, entry, installed.firstOrNull { it.museumId == entry.id }, ops[entry.id] ?: PackOperation.None)
        }
        // Un pack installé que le serveur ne liste plus (ou serveur injoignable) reste visible.
        val local = installed.filter { info -> entries.none { it.id == info.museumId } }.map { info ->
            PackRow(info.museumId, info.museumName, info.city, null, info, ops[info.museumId] ?: PackOperation.None)
        }
        PacksUiState(remote, fromServer + local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PacksUiState())

    init {
        refresh()
    }

    fun refresh() {
        remoteState.value = RemoteState.Loading
        viewModelScope.launch {
            remoteState.value = try {
                RemoteState.Loaded(remote.manifest())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RemoteState.Failed(e.message ?: e::class.simpleName.orEmpty())
            }
        }
    }

    fun download(entry: PackManifestEntry) {
        if (operations.value[entry.id] is PackOperation.Downloading) return
        setOperation(entry.id, PackOperation.Downloading(0f))
        viewModelScope.launch {
            val target = store.downloadPath(entry.id)
            try {
                remote.download(entry, target) { setOperation(entry.id, PackOperation.Downloading(it)) }
                store.install(entry.id, target, entry.sha256)
                setOperation(entry.id, PackOperation.None)
            } catch (e: CancellationException) {
                store.discardDownload(entry.id)
                throw e
            } catch (e: Exception) {
                store.discardDownload(entry.id)
                setOperation(entry.id, PackOperation.Failed(e.message ?: e::class.simpleName.orEmpty()))
            }
        }
    }

    fun remove(museumId: String) {
        viewModelScope.launch { store.remove(museumId) }
    }

    private fun setOperation(id: String, operation: PackOperation) {
        operations.update { if (operation == PackOperation.None) it - id else it + (id to operation) }
    }
}
