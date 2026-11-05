package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
import com.red_escuela.backend.academicstructure.entity.SectionModificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SectionModificationProposalRepository extends JpaRepository<SectionModificationProposal, Long> {

    Optional<SectionModificationProposal> findFirstBySubjectSectionIdAndStatus(Long subjectSectionId, SectionModificationStatus status);

    Optional<SectionModificationProposal> findFirstBySubjectSectionIdOrderByIdDesc(Long subjectSectionId);

    @Query("""
            select distinct p.subjectSection.name from SectionModificationProposal p
            join p.slots s
            where p.status = com.red_escuela.backend.academicstructure.entity.SectionModificationStatus.PENDING
            and s.classroom.id = :classroomId
            """)
    List<String> findSectionNamesWithPendingProposalIn(@Param("classroomId") Long classroomId);
}
