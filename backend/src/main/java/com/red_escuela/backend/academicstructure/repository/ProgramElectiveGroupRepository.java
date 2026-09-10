package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.ProgramElectiveGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramElectiveGroupRepository extends JpaRepository<ProgramElectiveGroup, Long> {

    List<ProgramElectiveGroup> findByProgramId(Long programId);

    Optional<ProgramElectiveGroup> findByProgramIdAndElectiveGroupId(Long programId, Long electiveGroupId);
}
