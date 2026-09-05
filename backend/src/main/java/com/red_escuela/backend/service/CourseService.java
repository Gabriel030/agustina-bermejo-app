package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateCourseRequest;
import com.red_escuela.backend.dto.UpdateCourseRequest;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.FeeType;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.exception.TermNotFoundException;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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
    public Course crearCurso(CreateCourseRequest request) {
        Term term = termRepository.findById(request.termId())
                .orElseThrow(() -> new TermNotFoundException(request.termId()));

        Course course = new Course();
        course.setName(request.name());
        course.setTerm(term);
        course.setSchedule(request.schedule());
        course.setCapacity(request.capacity());
        course.setPrice(request.price());
        course.setDurationMonths(request.durationMonths());
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
    public Course editarCurso(Long id, UpdateCourseRequest request) {
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
        if (request.registrationStart() != null) {
            course.setRegistrationStart(request.registrationStart());
        }
        if (request.registrationEnd() != null) {
            course.setRegistrationEnd(request.registrationEnd());
        }
        return courseRepository.save(course);
    }

    public List<Course> listar(StatusType status) {
        return status == null ? courseRepository.findAll() : courseRepository.findByIsActive(status);
    }

    public Course verDetalle(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void desactivar(Long id) {
        Course course = findOrThrow(id);
        course.setIsActive(StatusType.INACTIVE);
        courseRepository.save(course);
    }

    @Transactional
    public void reactivar(Long id) {
        Course course = findOrThrow(id);
        course.setIsActive(StatusType.ACTIVE);
        courseRepository.save(course);
    }

    private Course findOrThrow(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new CourseNotFoundException(id));
    }
}
