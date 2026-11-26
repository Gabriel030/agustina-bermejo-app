package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.repository.PaymentPlanOptionRepository;
import com.red_escuela.backend.billing.service.PaymentPlanOptionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramPaymentPlanOptionCreation {

    private final PaymentPlanOptionRepository paymentPlanOptionRepository;
    private final AcademicReferenceFinder academicReferenceFinder;
    private final PaymentPlanOptionBuilder paymentPlanOptionBuilder;

    public ProgramPaymentPlanOptionCreation(PaymentPlanOptionRepository paymentPlanOptionRepository, AcademicReferenceFinder academicReferenceFinder,
                                         PaymentPlanOptionBuilder paymentPlanOptionBuilder) {
        this.paymentPlanOptionRepository = paymentPlanOptionRepository;
        this.academicReferenceFinder = academicReferenceFinder;
        this.paymentPlanOptionBuilder = paymentPlanOptionBuilder;
    }

    @Transactional
    public PaymentPlanOption create(Long programId, CreatePaymentPlanOptionRequest request) {
        Program program = academicReferenceFinder.findProgram(programId);
        PaymentPlanOption option = paymentPlanOptionBuilder.build(request, program.getCurrency());
        option.setProgram(program);
        return paymentPlanOptionRepository.save(option);
    }
}
