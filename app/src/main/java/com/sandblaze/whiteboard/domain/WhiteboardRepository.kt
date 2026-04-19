package com.sandblaze.whiteboard.domain

import com.sandblaze.whiteboard.domain.model.WhiteboardState
import java.io.File

interface WhiteboardRepository {
    suspend fun save(state: WhiteboardState): File
    suspend fun loadLatest(): WhiteboardState
    suspend fun listSavedFiles(): List<File>
    suspend fun loadByFileName(fileName: String): WhiteboardState
}
