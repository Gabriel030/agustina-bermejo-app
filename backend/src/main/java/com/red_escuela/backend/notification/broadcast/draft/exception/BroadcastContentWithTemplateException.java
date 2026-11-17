package com.red_escuela.backend.notification.broadcast.draft.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class BroadcastContentWithTemplateException extends BadRequestException {

    public BroadcastContentWithTemplateException() {
        super("Subject and body cannot be sent together with a template");
    }
}
