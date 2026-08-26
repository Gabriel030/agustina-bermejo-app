package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.SubjectSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectSectionRepository extends JpaRepository<SubjectSection, Long> {

    List<SubjectSection> findBySubjectId(Long subjectId);

    List<SubjectSection> findBySubjectIdAndStatus(Long subjectId, StatusType status);

    List<SubjectSection> findByTermId(Long termId);
}
