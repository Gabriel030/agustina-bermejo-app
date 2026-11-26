package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.InformativeSessionStatus;

import java.time.LocalDate;

public record ListInformativeSessionsQuery(InformativeSessionStatus status, LocalDate from) {
}
