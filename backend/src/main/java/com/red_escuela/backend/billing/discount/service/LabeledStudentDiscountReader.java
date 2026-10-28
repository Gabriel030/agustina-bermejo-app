package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.LabeledStudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountLabels;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountWithUsage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LabeledStudentDiscountReader {
    private final StudentDiscountUsageReader usageReader;
    private final StudentDiscountLabelReader labelReader;

    public LabeledStudentDiscountReader(StudentDiscountUsageReader usageReader, StudentDiscountLabelReader labelReader) {
        this.usageReader = usageReader;
        this.labelReader = labelReader;
    }

    public List<LabeledStudentDiscount> readLabeled(List<StudentDiscount> discounts, boolean withStudentNames) {
        Map<Long, StudentDiscountLabels> labels = labelReader.readLabelsOf(discounts, withStudentNames);
        List<StudentDiscountWithUsage> withUsage = usageReader.readUsageOf(discounts);
        return withUsage.stream().map(item -> new LabeledStudentDiscount(item, labels.get(item.discount().getId()))).toList();
    }
}
