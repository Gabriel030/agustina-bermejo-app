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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import java.util.List;
import java.util.Optional;
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

        Currency currency = resolveCurrency(request.price(), request.currency());
        Course course = Course.builder()
                .name(request.name())
                .term(term)
                .schedule(request.schedule())
                .capacity(request.capacity())
                .price(request.price() == null ? null : request.price().amount())
                .durationMonths(request.durationMonths())
                .teachingMode(request.teachingMode())
                .registrationStart(request.registrationStart())
                .registrationEnd(request.registrationEnd())
                .isActive(StatusType.ACTIVE)
                .currency(currency)
                .build();
        course.requireValidRegistrationWindow();
        Course saved = courseRepository.save(course);

        Person actor = currentPersonService.resolveCurrentPerson();
        Money price = Optional.ofNullable(saved.priceAsMoney()).orElseGet(() -> Money.zero(saved.getCurrency()));
        priceRevisionService.registerForCourse(saved, FeeType.RECURRING_FEE, price, "Precio inicial", actor);
        return saved;
    }

    @Transactional
    public Course update(Long id, UpdateCourseRequest request) {
        Course course = findOrThrow(id);
        Optional.ofNullable(request.name()).ifPresent(course::setName);
        Optional.ofNullable(request.schedule()).ifPresent(course::setSchedule);
        Optional.ofNullable(request.capacity()).ifPresent(course::setCapacity);
        if (course.changePrice(request.price())) {
            Person actor = currentPersonService.resolveCurrentPerson();
            priceRevisionService.registerForCourse(course, FeeType.RECURRING_FEE, request.price(), "Ajuste de precio", actor);
        }
        Optional.ofNullable(request.durationMonths()).ifPresent(course::setDurationMonths);
        Optional.ofNullable(request.teachingMode()).ifPresent(course::setTeachingMode);
        Optional.ofNullable(request.registrationStart()).ifPresent(course::setRegistrationStart);
        Optional.ofNullable(request.registrationEnd()).ifPresent(course::setRegistrationEnd);
        course.requireValidRegistrationWindow();
        return courseRepository.save(course);
    }

    public List<Course> list(StatusType status, Boolean visibleInCatalog) {
        return courseRepository.findAll(Sort.by("name", "id")).stream()
                .filter(course -> status == null || course.getIsActive() == status)
                .filter(course -> visibleInCatalog == null || course.isVisibleInCatalog() == visibleInCatalog)
                .toList();
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

    private static Currency resolveCurrency(Money price, Currency requested) {
        if (price == null) {
            return Optional.ofNullable(requested).orElse(Currency.ARS);
        }
        if (requested != null && requested != price.currency()) {
            throw new CurrencyMismatchException(requested, price.currency());
        }
        return price.currency();
    }

    private Course findOrThrow(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new CourseNotFoundException(id));
    }
}
