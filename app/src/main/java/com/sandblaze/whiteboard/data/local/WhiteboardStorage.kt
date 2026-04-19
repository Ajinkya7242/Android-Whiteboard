package com.sandblaze.whiteboard.data.local

import com.sandblaze.whiteboard.domain.model.WhiteboardState
import java.io.File

interface WhiteboardStorage {
    fun save(state: WhiteboardState): File
    fun listSaved(): List<File>
    fun load(file: File): WhiteboardState
}
