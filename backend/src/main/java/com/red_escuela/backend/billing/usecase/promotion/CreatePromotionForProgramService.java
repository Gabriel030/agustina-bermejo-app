package com.red_escuela.backend.billing.usecase.promotion;

import com.red_escuela.backend.billing.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.repository.PromotionRepository;
import com.red_escuela.backend.billing.service.PromotionBuilder;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreatePromotionForProgramService implements CreatePromotionForProgramUseCase {

    private final PromotionRepository promotionRepository;
    private final ProgramRepository programRepository;
    private final PromotionBuilder promotionBuilder;

    public CreatePromotionForProgramService(PromotionRepository promotionRepository, ProgramRepository programRepository, PromotionBuilder promotionBuilder) {
        this.promotionRepository = promotionRepository;
        this.programRepository = programRepository;
        this.promotionBuilder = promotionBuilder;
    }

    @Override
    @Transactional
    public Promotion create(Long programId, CreatePromotionRequest request) {
        Program program = programRepository.findById(programId).orElseThrow(() -> new ProgramNotFoundException(programId));
        Promotion promotion = promotionBuilder.build(request, program.getCurrency());
        promotion.setProgram(program);
        return promotionRepository.save(promotion);
    }
}
