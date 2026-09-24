package com.red_escuela.backend.academicstructure.service;
import com.red_escuela.backend.identity.service.CurrentPersonService;

import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseRequest;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import com.red_escuela.backend.billing.service.PriceRevisionService;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final TermRepository termRepository;
    private final PriceRevisionService priceRevisionService;
    private final CurrentPersonService currentPersonService;

    public CourseService(CourseRepository courseRepository, TermRepository termRepository,
                          PriceRevisionService priceRevisionService, CurrentPersonService currentPersonService) {
        this.courseRepository = courseRepository;
        this.termRepository = termRepository;
        this.priceRevisionService = priceRevisionService;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public Course create(CreateCourseRequest request) {
        Term term = termRepository.findById(request.termId())
                .orElseThrow(() -> new TermNotFoundException(request.termId()));

        Course course = new Course();
        course.setName(request.name());
        course.setTerm(term);
        course.setSchedule(request.schedule());
        course.setCapacity(request.capacity());
        course.setPrice(request.price());
        course.setDurationMonths(request.durationMonths());
        course.setTeachingMode(request.teachingMode());
        course.setRegistrationStart(request.registrationStart());
        course.setRegistrationEnd(request.registrationEnd());
        course.setIsActive(StatusType.ACTIVE);
        course = courseRepository.save(course);

        Person actor = currentPersonService.resolveCurrentPerson();
        BigDecimal price = course.getPrice() == null ? BigDecimal.ZERO : course.getPrice();
        priceRevisionService.registerForCourse(course, FeeType.RECURRING_FEE, price, "Precio inicial", actor);
        return course;
    }

    @Transactional
    public Course update(Long id, UpdateCourseRequest request) {
        Course course = findOrThrow(id);
        if (request.name() != null) {
            course.setName(request.name());
        }
        if (request.schedule() != null) {
            course.setSchedule(request.schedule());
        }
        if (request.capacity() != null) {
            course.setCapacity(request.capacity());
        }
        if (request.price() != null && request.price().compareTo(course.getPrice()) != 0) {
            course.setPrice(request.price());
            Person actor = currentPersonService.resolveCurrentPerson();
            priceRevisionService.registerForCourse(course, FeeType.RECURRING_FEE, request.price(), "Ajuste de precio", actor);
        }
        if (request.durationMonths() != null) {
            course.setDurationMonths(request.durationMonths());
        }
        if (request.teachingMode() != null) {
            course.setTeachingMode(request.teachingMode());
        }
        if (request.registrationStart() != null) {
            course.setRegistrationStart(request.registrationStart());
        }
        if (request.registrationEnd() != null) {
            course.setRegistrationEnd(request.registrationEnd());
        }
        return courseRepository.save(course);
    }

    public List<Course> list(StatusType status) {
        return status == null ? courseRepository.findAll() : courseRepository.findByIsActive(status);
    }

    public Course get(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void deactivate(Long id) {
        Course course = findOrThrow(id);
        course.setIsActive(StatusType.INACTIVE);
        courseRepository.save(course);
    }

    @Transactional
    public void reactivate(Long id) {
        Course course = findOrThrow(id);
        course.setIsActive(StatusType.ACTIVE);
        courseRepository.save(course);
    }

    private Course findOrThrow(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new CourseNotFoundException(id));
    }
}
