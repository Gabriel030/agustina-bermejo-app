package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountLabels;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.enrollment.api.EnrollmentNameLookup;
import com.red_escuela.backend.identity.api.StudentNameLookup;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StudentDiscountLabelReader {
    private final EnrollmentNameLookup enrollmentNames;
    private final DiscountDefinitionRepository definitionRepository;
    private final StudentNameLookup studentNames;

    public StudentDiscountLabelReader(EnrollmentNameLookup enrollmentNames, DiscountDefinitionRepository definitionRepository, StudentNameLookup studentNames) {
        this.enrollmentNames = enrollmentNames;
        this.definitionRepository = definitionRepository;
        this.studentNames = studentNames;
    }

    public Map<Long, StudentDiscountLabels> readLabelsOf(List<StudentDiscount> discounts, boolean withStudentNames) {
        if (discounts.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> programNames = enrollmentNames.findProgramNamesByProgramEnrollmentId(idsOf(discounts, StudentDiscount::getProgramEnrollmentId));
        Map<Long, String> courseNames = enrollmentNames.findCourseNamesByCourseEnrollmentId(idsOf(discounts, StudentDiscount::getCourseEnrollmentId));
        Map<Long, DiscountDefinition> definitions = definitionRepository.findAllById(idsOf(discounts, StudentDiscount::getDiscountDefinitionId)).stream()
                .collect(Collectors.toMap(DiscountDefinition::getId, Function.identity()));
        Map<Long, String> students = withStudentNames ? studentNames.findFullNamesByStudentId(idsOf(discounts, StudentDiscount::getStudentId)) : Map.of();
        Map<Long, StudentDiscountLabels> labels = new HashMap<>();
        for (StudentDiscount discount : discounts) {
            DiscountDefinition definition = definitions.get(discount.getDiscountDefinitionId());
            String enrollmentName = discount.getProgramEnrollmentId() != null ? programNames.get(discount.getProgramEnrollmentId()) : courseNames.get(discount.getCourseEnrollmentId());
            labels.put(discount.getId(), new StudentDiscountLabels(students.get(discount.getStudentId()), enrollmentName,
                    definition == null ? null : definition.getName(), definition == null ? null : definition.getDiscountType()));
        }
        return labels;
    }

    private List<Long> idsOf(List<StudentDiscount> discounts, Function<StudentDiscount, Long> idOf) {
        return discounts.stream().map(idOf).filter(Objects::nonNull).distinct().toList();
    }
}
