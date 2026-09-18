package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamBoardInstructorRepository extends JpaRepository<ExamBoardInstructor, Long> {

    List<ExamBoardInstructor> findByExamBoardId(Long examBoardId);

    boolean existsByExamBoardIdAndInstructorId(Long examBoardId, Long instructorId);
}
