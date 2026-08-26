package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.StatusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByIsActive(StatusType status);
}
