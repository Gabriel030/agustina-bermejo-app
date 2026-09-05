package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateCourseSubjectRequest;
import com.red_escuela.backend.dto.UpdateCourseSubjectRequest;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseSubject;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.exception.CourseSubjectNotFoundException;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.CourseSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CourseSubjectService {

    private final CourseSubjectRepository courseSubjectRepository;
    private final CourseRepository courseRepository;

    public CourseSubjectService(CourseSubjectRepository courseSubjectRepository, CourseRepository courseRepository) {
        this.courseSubjectRepository = courseSubjectRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public CourseSubject addModule(Long courseId, CreateCourseSubjectRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        courseSubject.setName(request.name());
        courseSubject.setSortOrder(request.sortOrder());
        courseSubject.setWeeklyWorkload(request.weeklyWorkload());
        courseSubject.setSubjectRecurrence(request.subjectRecurrence());
        courseSubject.setQualificationType(request.qualificationType());
        courseSubject.setStatus(StatusType.ACTIVE);
        return courseSubjectRepository.save(courseSubject);
    }

    @Transactional
    public CourseSubject updateCourseSubject(Long id, UpdateCourseSubjectRequest request) {
        CourseSubject courseSubject = findOrThrow(id);
        if (request.name() != null) {
            courseSubject.setName(request.name());
        }
        if (request.sortOrder() != null) {
            courseSubject.setSortOrder(request.sortOrder());
        }
        if (request.weeklyWorkload() != null) {
            courseSubject.setWeeklyWorkload(request.weeklyWorkload());
        }
        if (request.subjectRecurrence() != null) {
            courseSubject.setSubjectRecurrence(request.subjectRecurrence());
        }
        if (request.qualificationType() != null) {
            courseSubject.setQualificationType(request.qualificationType());
        }
        return courseSubjectRepository.save(courseSubject);
    }

    public List<CourseSubject> listByCourse(Long courseId, StatusType status) {
        return status == null
                ? courseSubjectRepository.findByCourseIdOrderBySortOrder(courseId)
                : courseSubjectRepository.findByCourseIdAndStatusOrderBySortOrder(courseId, status);
    }

    public CourseSubject viewDetail(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void deactivate(Long id) {
        CourseSubject courseSubject = findOrThrow(id);
        courseSubject.setStatus(StatusType.INACTIVE);
        courseSubjectRepository.save(courseSubject);
    }

    @Transactional
    public void reactivate(Long id) {
        CourseSubject courseSubject = findOrThrow(id);
        courseSubject.setStatus(StatusType.ACTIVE);
        courseSubjectRepository.save(courseSubject);
    }

    private CourseSubject findOrThrow(Long id) {
        return courseSubjectRepository.findById(id)
                .orElseThrow(() -> new CourseSubjectNotFoundException(id));
    }
}
