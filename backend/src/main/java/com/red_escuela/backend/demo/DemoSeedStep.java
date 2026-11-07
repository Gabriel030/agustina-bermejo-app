package com.red_escuela.backend.demo;

public interface DemoSeedStep {

    int order();

    String label();

    void seed(DemoDataset dataset);
}
