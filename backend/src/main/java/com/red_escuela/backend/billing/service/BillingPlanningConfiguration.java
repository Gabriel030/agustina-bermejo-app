package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.planning.service.CourseMonthlyInvoicePlanner;
import com.red_escuela.backend.billing.planning.service.ProgramMonthlyInvoicePlanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingPlanningConfiguration {

    @Bean
    public ProgramMonthlyInvoicePlanner monthlyChargePlanner() {
        return new ProgramMonthlyInvoicePlanner();
    }

    @Bean
    public CourseMonthlyInvoicePlanner courseMonthlyInvoicePlanner() {
        return new CourseMonthlyInvoicePlanner();
    }
}
