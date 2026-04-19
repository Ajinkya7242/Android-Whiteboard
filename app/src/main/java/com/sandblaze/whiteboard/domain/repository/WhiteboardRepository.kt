package com.sandblaze.whiteboard.domain.repository

import com.sandblaze.whiteboard.domain.model.WhiteboardState

interface WhiteboardRepository {
    suspend fun save(state: WhiteboardState): Result<String>
    suspend fun loadLatest(): Result<WhiteboardState>
    suspend fun loadByFileName(fileName: String): Result<WhiteboardState>
    suspend fun listSavedFileNames(): Result<List<String>>
}
