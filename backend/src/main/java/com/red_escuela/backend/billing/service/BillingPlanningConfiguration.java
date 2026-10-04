package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.planning.CourseChargePlanner;
import com.red_escuela.backend.billing.planning.MonthlyChargePlanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingPlanningConfiguration {

    @Bean
    public MonthlyChargePlanner monthlyChargePlanner() {
        return new MonthlyChargePlanner();
    }

    @Bean
    public CourseChargePlanner courseChargePlanner() {
        return new CourseChargePlanner();
    }
}
