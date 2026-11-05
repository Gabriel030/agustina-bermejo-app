package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.StudentStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentStatusChangeRepository extends JpaRepository<StudentStatusChange, Long> {

    List<StudentStatusChange> findByStudentIdOrderByOccurredAtDesc(Long studentId);
}
