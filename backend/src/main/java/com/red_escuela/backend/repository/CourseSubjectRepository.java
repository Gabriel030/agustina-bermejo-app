package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CourseSubject;
import com.red_escuela.backend.entity.StatusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseSubjectRepository extends JpaRepository<CourseSubject, Long> {

    List<CourseSubject> findByCourseIdOrderBySortOrder(Long courseId);

    List<CourseSubject> findByCourseIdAndStatusOrderBySortOrder(Long courseId, StatusType status);
}
