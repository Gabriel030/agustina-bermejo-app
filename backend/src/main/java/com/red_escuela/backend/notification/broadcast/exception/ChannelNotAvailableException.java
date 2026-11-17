package com.red_escuela.backend.notification.broadcast.exception;

import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.shared.exception.ConflictException;

public class ChannelNotAvailableException extends ConflictException {

    public ChannelNotAvailableException(ChannelType channel) {
        super("Channel " + channel + " is not available");
    }
}
