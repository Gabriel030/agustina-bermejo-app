package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.SectionSlotView;

import java.util.List;

public interface SectionScheduleQueries {

    List<SectionSlotView> findProgramSectionSlots(Long programSectionId);
}
