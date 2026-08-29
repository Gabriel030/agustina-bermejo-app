package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.ExamBoard;
import com.red_escuela.backend.entity.ExamBoardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamBoardRepository extends JpaRepository<ExamBoard, Long> {

    List<ExamBoard> findBySubjectIdAndTermId(Long subjectId, Long termId);

    List<ExamBoard> findByCourseSubjectIdAndTermId(Long courseSubjectId, Long termId);

    // Mesas ofrecidas de una materia sin importar el Term (una mesa puede ser de un turno
    // posterior al que se cursó) — usado para que el alumno vea a qué llamado anotarse.
    List<ExamBoard> findBySubjectIdAndStatus(Long subjectId, ExamBoardStatus status);
}
