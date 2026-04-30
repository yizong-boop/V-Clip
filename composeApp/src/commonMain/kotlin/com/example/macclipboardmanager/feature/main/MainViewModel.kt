package com.example.macclipboardmanager.feature.main

import com.example.macclipboardmanager.core.clipboard.ClipboardMonitor
import com.example.macclipboardmanager.core.hotkey.GlobalHotkeyManager
import com.example.macclipboardmanager.core.hotkey.Hotkey
import com.example.macclipboardmanager.core.hotkey.HotkeyModifier
import com.example.macclipboardmanager.domain.clipboard.ClipboardItem
import com.example.macclipboardmanager.domain.clipboard.ClipboardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainViewModel(
    private val repository: ClipboardRepository,
    private val clipboardMonitor: ClipboardMonitor,
    private val globalHotkeyManager: GlobalHotkeyManager,
    private val defaultHotkey: Hotkey = DEFAULT_HOTKEY,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val toastDurationMillis: Long = 3_000L,
    scope: CoroutineScope? = null,
) {
    private val effectiveScope: CoroutineScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ownsScope = scope == null

    private val mutableUiState = MutableStateFlow(MainUiState())
    private val mutableEffects = MutableSharedFlow<MainEffect>(extraBufferCapacity = 16)
    private val searchQuery = MutableStateFlow("")
    private val workerScope = CoroutineScope(
        effectiveScope.coroutineContext + SupervisorJob(effectiveScope.coroutineContext[Job]),
    )

    private var started = false
    private var cleanedUp = false
    private var clearToastJob: Job? = null
    private var confirmSelectionInProgress = false

    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()
    val effects: SharedFlow<MainEffect> = mutableEffects.asSharedFlow()

    init {
        workerScope.launch {
            combine(repository.items, searchQuery) { items, query ->
                buildState(items = items, searchQuery = query)
            }.collect { state ->
                mutableUiState.update { currentState ->
                    state.copy(toastMessage = currentState.toastMessage)
                }
            }
        }
    }

    fun start() {
        if (started) {
            return
        }
        started = true

        clipboardMonitor.start()
        globalHotkeyManager.register(defaultHotkey)

        workerScope.launch {
            repository.loadFromStore()
            clipboardMonitor.events.collect { event ->
                repository.add(event.text)
            }
        }

        workerScope.launch {
            globalHotkeyManager.activations.collect {
                mutableEffects.emit(
                    MainEffect.ShowWindow(
                        requestedAtEpochMillis = clock(),
                    ),
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearSearchQuery() {
        searchQuery.value = ""
    }

    fun resetSelectionToFirst() {
        mutableUiState.update { currentState ->
            currentState.copy(selectedItemId = currentState.filteredItems.firstOrNull()?.id)
        }
    }

    fun selectItem(itemId: String) {
        mutableUiState.update { currentState ->
            if (currentState.filteredItems.none { it.id == itemId }) {
                currentState
            } else {
                currentState.copy(selectedItemId = itemId)
            }
        }
    }

    fun selectNext() {
        moveSelection(step = 1)
    }

    fun selectPrevious() {
        moveSelection(step = -1)
    }

    fun confirmSelection() {
        if (confirmSelectionInProgress) {
            return
        }

        val selectedText = mutableUiState.value.selectedItem?.text ?: return
        confirmSelectionInProgress = true
        repository.bumpSelectedItem(selectedText)
        val emitted = mutableEffects.tryEmit(
            MainEffect.ConfirmSelection(
                text = selectedText,
            ),
        )
        if (!emitted) {
            confirmSelectionInProgress = false
        }
    }

    fun completeConfirmSelection() {
        confirmSelectionInProgress = false
    }

    fun showToast(message: String) {
        clearToastJob?.cancel()
        updateToastMessage(message)
        clearToastJob = workerScope.launch {
            delay(toastDurationMillis)
            clearToastJob = null
            updateToastMessage(null)
        }
    }

    fun clearToast() {
        clearToastJob?.cancel()
        clearToastJob = null
        updateToastMessage(null)
    }

    fun close() {
        cleanup()
    }

    private fun buildState(
        items: List<ClipboardItem>,
        searchQuery: String,
    ): MainUiState {
        val filteredItems = filterItems(items = items, searchQuery = searchQuery)
        return MainUiState(
            searchQuery = searchQuery,
            filteredItems = filteredItems,
            selectedItemId = filteredItems.firstOrNull()?.id,
        )
    }

    private fun filterItems(
        items: List<ClipboardItem>,
        searchQuery: String,
    ): List<ClipboardItem> {
        if (searchQuery.isBlank()) {
            return items
        }

        return items.filter { item ->
            item.text.contains(searchQuery, ignoreCase = true)
        }
    }

    private fun moveSelection(step: Int) {
        mutableUiState.update { currentState ->
            if (currentState.filteredItems.isEmpty()) {
                return@update currentState.copy(selectedItemId = null)
            }

            val currentIndex = currentState.filteredItems.indexOfFirst { it.id == currentState.selectedItemId }
                .takeIf { it >= 0 }
                ?: 0
            val nextIndex = (currentIndex + step).coerceIn(0, currentState.filteredItems.lastIndex)

            currentState.copy(
                selectedItemId = currentState.filteredItems[nextIndex].id,
            )
        }
    }

    private fun updateToastMessage(message: String?) {
        mutableUiState.update { currentState ->
            if (currentState.toastMessage == message) {
                currentState
            } else {
                currentState.copy(toastMessage = message)
            }
        }
    }

    private fun cleanup() {
        if (cleanedUp) {
            return
        }
        cleanedUp = true

        clipboardMonitor.stop()
        globalHotkeyManager.unregister()
        clipboardMonitor.close()
        globalHotkeyManager.close()
        clearToastJob?.cancel()
        confirmSelectionInProgress = false
        workerScope.cancel()
        if (ownsScope) {
            effectiveScope.cancel()
        }
        runBlocking {
            repository.flush()
        }
    }

    companion object {
        val DEFAULT_HOTKEY = Hotkey(
            keyCode = 9,
            modifiers = setOf(HotkeyModifier.OPTION),
        )
    }
}
