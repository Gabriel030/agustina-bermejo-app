package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.platform.exception.FeatureChangeReasonInvalidException;
import org.springframework.stereotype.Service;

@Service
public class FeatureChangeReasonValidator {

    public static final int MIN_LENGTH = 5;
    public static final int MAX_LENGTH = 500;

    public String requireValidReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new FeatureChangeReasonInvalidException(MIN_LENGTH, MAX_LENGTH);
        }
        return trimmed;
    }
}
