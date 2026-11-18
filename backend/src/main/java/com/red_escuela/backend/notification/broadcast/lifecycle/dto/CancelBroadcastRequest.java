package com.red_escuela.backend.notification.broadcast.lifecycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelBroadcastRequest(@NotBlank @Size(max = 500) String reason) {
}
