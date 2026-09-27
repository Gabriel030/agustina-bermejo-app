package com.red_escuela.backend.announcement.exception;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;

public class SegmentNotAvailableForSectionException extends RuntimeException {

    public SegmentNotAvailableForSectionException(AudienceSegmentKey key) {
        super("Audience segment " + key + " is not available for this section");
    }
}
