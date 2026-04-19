package com.sandblaze.whiteboard.domain.usecase

import com.sandblaze.whiteboard.domain.repository.WhiteboardRepository
import javax.inject.Inject

class ListSavedWhiteboardsUseCase @Inject constructor(
    private val repository: WhiteboardRepository
) {
    suspend operator fun invoke(): Result<List<String>> = repository.listSavedFileNames()
}
