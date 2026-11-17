package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.notification.broadcast.lifecycle.dto.*;

public interface CancelBroadcastUseCase {

    BroadcastStatusResponse cancelBroadcast(Long broadcastId, String reason);
}
