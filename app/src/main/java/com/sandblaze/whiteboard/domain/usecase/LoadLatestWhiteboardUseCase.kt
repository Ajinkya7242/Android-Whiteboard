package com.sandblaze.whiteboard.domain.usecase

import com.sandblaze.whiteboard.domain.model.WhiteboardState
import com.sandblaze.whiteboard.domain.repository.WhiteboardRepository
import javax.inject.Inject

class LoadLatestWhiteboardUseCase @Inject constructor(
    private val repository: WhiteboardRepository
) {
    suspend operator fun invoke(): Result<WhiteboardState> = repository.loadLatest()
}
