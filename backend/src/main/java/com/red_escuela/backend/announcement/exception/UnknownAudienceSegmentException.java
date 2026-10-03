package com.red_escuela.backend.announcement.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;

public class UnknownAudienceSegmentException extends BadRequestException {

    public UnknownAudienceSegmentException(AudienceSegmentKey key) {
        super("No resolver for audience segment " + key);
    }
}
