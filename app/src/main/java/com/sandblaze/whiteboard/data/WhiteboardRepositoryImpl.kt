package com.sandblaze.whiteboard.data

import com.sandblaze.whiteboard.data.local.LocalWhiteboardStorage
import com.sandblaze.whiteboard.domain.WhiteboardRepository
import com.sandblaze.whiteboard.domain.model.WhiteboardState
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhiteboardRepositoryImpl @Inject constructor(
    private val storage: LocalWhiteboardStorage
) : WhiteboardRepository {
    override suspend fun save(state: WhiteboardState): File {
        return storage.save(state)
    }

    override suspend fun loadLatest(): WhiteboardState {
        val latest = storage.listSaved().firstOrNull() ?: throw FileNotFoundException("No saved whiteboards found.")
        return storage.load(latest)
    }

    override suspend fun listSavedFiles(): List<File> = storage.listSaved()

    override suspend fun loadByFileName(fileName: String): WhiteboardState {
        val match = storage.listSaved().firstOrNull { it.name == fileName }
            ?: throw FileNotFoundException("File not found: $fileName")
        return storage.load(match)
    }
}

