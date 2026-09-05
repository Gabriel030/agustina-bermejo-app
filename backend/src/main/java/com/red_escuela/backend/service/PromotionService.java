package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreatePromotionRequest;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.DiscountType;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.Promotion;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.exception.InvalidPricingTargetException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.PromotionRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Promoción segmentada por carrera o curso — se suma con cualquier
 * {@link com.red_escuela.backend.entity.Discount} individual que también le
 * aplique al mismo alumno (decisión de negocio, ver {@link PricingCalculator}).
 */
@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;

    public PromotionService(
            PromotionRepository promotionRepository, ProgramRepository programRepository,
            CourseRepository courseRepository) {
        this.promotionRepository = promotionRepository;
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public Promotion create(CreatePromotionRequest request, Person createdBy) {
        if (request.programId() == null && request.courseId() == null) {
            throw new InvalidPricingTargetException(
                    "Una Promotion tiene que segmentar por al menos una carrera o un curso");
        }
        if (request.tipo() == DiscountType.BECA_TOTAL) {
            throw new InvalidPricingTargetException("Una promoción segmentada no puede ser BECA_TOTAL, solo PORCENTAJE o MONTO_FIJO");
        }

        Promotion promotion = new Promotion();
        if (request.programId() != null) {
            Program program = programRepository.findById(request.programId())
                    .orElseThrow(() -> new ProgramNotFoundException(request.programId()));
            promotion.setProgram(program);
        }
        if (request.courseId() != null) {
            Course course = courseRepository.findById(request.courseId())
                    .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
            promotion.setCourse(course);
        }
        promotion.setType(request.tipo());
        promotion.setAmount(request.valor());
        promotion.setValidFrom(request.vigenteDesde());
        promotion.setValidUntil(request.vigenteHasta());
        promotion.setCreatedBy(createdBy);
        promotion.setCreatedAt(LocalDateTime.now());
        return promotionRepository.save(promotion);
    }

    public List<Promotion> list() {
        return promotionRepository.findAll();
    }

    List<Promotion> vigentesParaPrograma(Long programId, LocalDate date) {
        return promotionRepository.findByProgramId(programId).stream()
                .filter(promotion -> promotion.validOn(date))
                .toList();
    }

    List<Promotion> vigentesParaCurso(Long courseId, LocalDate date) {
        return promotionRepository.findByCourseId(courseId).stream()
                .filter(promotion -> promotion.validOn(date))
                .toList();
    }
}
