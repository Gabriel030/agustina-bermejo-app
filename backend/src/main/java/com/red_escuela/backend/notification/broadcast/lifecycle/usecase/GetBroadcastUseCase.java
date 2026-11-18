package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.notification.broadcast.lifecycle.dto.*;

public interface GetBroadcastUseCase {

    BroadcastDetailResponse getBroadcast(Long broadcastId);
}
