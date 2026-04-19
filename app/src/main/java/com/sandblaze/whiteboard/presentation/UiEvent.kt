package com.sandblaze.whiteboard.presentation

sealed interface UiEvent {
    data class SaveSuccess(val fileName: String) : UiEvent
    data class SaveError(val message: String) : UiEvent
    data object LoadSuccess : UiEvent
    data class LoadError(val message: String) : UiEvent
    data class FilesListed(val names: List<String>) : UiEvent
    data class FileListError(val message: String) : UiEvent
}
