package com.red_escuela.backend.config.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local & demo")
public class DemoBillingSeeder {

    public void seed(DemoDataset dataset) {
    }
}
