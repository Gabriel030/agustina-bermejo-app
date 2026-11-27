package com.red_escuela.backend.academicstructure.domain;

import java.util.List;

public enum HolidayScope {
    CLASSES,
    BILLING,
    BOTH;

    public static final List<HolidayScope> AFFECTING_CLASSES = List.of(CLASSES, BOTH);
    public static final List<HolidayScope> AFFECTING_BILLING = List.of(BILLING, BOTH);
}
