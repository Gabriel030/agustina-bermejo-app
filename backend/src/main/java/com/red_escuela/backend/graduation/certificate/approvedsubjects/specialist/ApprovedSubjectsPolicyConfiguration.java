package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsEligibilityPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApprovedSubjectsPolicyConfiguration {

    @Bean
    public ApprovedSubjectsEligibilityPolicy approvedSubjectsEligibilityPolicy() {
        return new ApprovedSubjectsEligibilityPolicy();
    }
}
