package com.sandblaze.whiteboard.domain.usecase

import com.sandblaze.whiteboard.domain.model.WhiteboardState
import com.sandblaze.whiteboard.domain.repository.WhiteboardRepository
import javax.inject.Inject

class SaveWhiteboardUseCase @Inject constructor(
    private val repository: WhiteboardRepository
) {
    suspend operator fun invoke(state: WhiteboardState): Result<String> = repository.save(state)
}
