package com.red_escuela.backend.academicrecord.usecase;

public interface ExportMyCalendarUseCase {

    byte[] export(ExportMyCalendarCommand command);
}
