package com.sandblaze.whiteboard.data

import com.sandblaze.whiteboard.data.local.LocalWhiteboardStorage
import com.sandblaze.whiteboard.domain.model.WhiteboardState
import java.io.File
import java.io.FileNotFoundException

class WhiteboardRepositoryImpl(
    private val storage: LocalWhiteboardStorage
) {
    fun save(state: WhiteboardState) {
        storage.save(state)
    }

    fun loadLatest(): WhiteboardState {
        val latest = storage.listSaved().firstOrNull() ?: throw FileNotFoundException("No saved whiteboards found.")
        return storage.load(latest)
    }

    fun listSavedFiles(): List<File> = storage.listSaved()

    fun loadByFileName(fileName: String): WhiteboardState {
        val match = storage.listSaved().firstOrNull { it.name == fileName }
            ?: throw FileNotFoundException("File not found: $fileName")
        return storage.load(match)
    }
}

