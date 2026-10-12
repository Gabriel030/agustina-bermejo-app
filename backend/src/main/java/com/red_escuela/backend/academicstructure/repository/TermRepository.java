package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.entity.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TermRepository extends JpaRepository<Term, Long> {

    List<Term> findByStatus(StatusType status);

    @Query("""
            select distinct ss.term from SubjectSection ss, ProgramSubject ps
            where ps.subject = ss.subject and ps.program.id = :programId and ss.term is not null
            """)
    List<Term> findTermsOfProgram(@Param("programId") Long programId);
}
