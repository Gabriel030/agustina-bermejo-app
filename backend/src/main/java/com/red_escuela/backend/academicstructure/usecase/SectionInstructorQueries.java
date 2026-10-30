package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.SectionInstructorView;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SectionInstructorQueries {

    List<SectionInstructorView> findProgramSectionInstructors(Long programSectionId);

    Map<Long, List<SectionInstructorView>> findProgramSectionInstructors(Collection<Long> programSectionIds);

    Optional<Long> findProgramSectionLeadPersonId(Long programSectionId);
}
