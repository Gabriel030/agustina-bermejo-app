package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramSubjectRepository extends JpaRepository<ProgramSubject, Long> {

    List<ProgramSubject> findByProgramId(Long programId);

    List<ProgramSubject> findByProgramIdAndStatus(Long programId, StatusType status);

    Optional<ProgramSubject> findByProgramIdAndSubjectId(Long programId, Long subjectId);

    List<ProgramSubject> findByProgramIdAndElectiveGroupId(Long programId, Long electiveGroupId);
}
