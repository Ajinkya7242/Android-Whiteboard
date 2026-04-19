package com.sandblaze.whiteboard.data

import com.sandblaze.whiteboard.data.local.WhiteboardStorage
import com.sandblaze.whiteboard.domain.model.WhiteboardState
import com.sandblaze.whiteboard.domain.repository.WhiteboardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhiteboardRepositoryImpl @Inject constructor(
    private val storage: WhiteboardStorage
) : WhiteboardRepository {

    override suspend fun save(state: WhiteboardState): Result<String> = withContext(Dispatchers.IO) {
        runCatching { storage.save(state).name }
    }

    override suspend fun loadLatest(): Result<WhiteboardState> = withContext(Dispatchers.IO) {
        runCatching {
            val latest = storage.listSaved().firstOrNull()
                ?: throw FileNotFoundException("No saved whiteboards found.")
            storage.load(latest)
        }
    }

    override suspend fun listSavedFileNames(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching { storage.listSaved().map { it.name } }
    }

    override suspend fun loadByFileName(fileName: String): Result<WhiteboardState> = withContext(Dispatchers.IO) {
        runCatching {
            val match = storage.listSaved().firstOrNull { it.name == fileName }
                ?: throw FileNotFoundException("File not found: $fileName")
            storage.load(match)
        }
    }
}
