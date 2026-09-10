package com.red_escuela.backend.billing.usecase.promotion;

import com.red_escuela.backend.billing.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.repository.PromotionRepository;
import com.red_escuela.backend.billing.service.PromotionBuilder;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreatePromotionForCourseService implements CreatePromotionForCourseUseCase {

    private final PromotionRepository promotionRepository;
    private final CourseRepository courseRepository;
    private final PromotionBuilder promotionBuilder;

    public CreatePromotionForCourseService(PromotionRepository promotionRepository, CourseRepository courseRepository, PromotionBuilder promotionBuilder) {
        this.promotionRepository = promotionRepository;
        this.courseRepository = courseRepository;
        this.promotionBuilder = promotionBuilder;
    }

    @Override
    @Transactional
    public Promotion create(Long courseId, CreatePromotionRequest request) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
        Promotion promotion = promotionBuilder.build(request);
        promotion.setCourse(course);
        return promotionRepository.save(promotion);
    }
}
