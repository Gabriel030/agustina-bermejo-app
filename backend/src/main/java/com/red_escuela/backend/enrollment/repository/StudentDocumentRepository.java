package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentDocumentRepository extends JpaRepository<StudentDocument, Long> {

    List<StudentDocument> findByStudentId(Long studentId);

    // Usado para la lógica de reemplazo: un tipo de documento tiene un único registro vigente.
    Optional<StudentDocument> findByStudentIdAndType(Long studentId, DocumentType type);
}
