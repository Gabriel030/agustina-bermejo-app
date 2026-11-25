package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsTransaction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Component
public class SpringApprovedSubjectsTransaction implements ApprovedSubjectsTransaction {

    @Override
    @Transactional
    public <T> T run(Supplier<T> work) {
        return work.get();
    }
}
