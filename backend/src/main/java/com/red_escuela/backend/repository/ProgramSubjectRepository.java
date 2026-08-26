package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.StatusType;
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
