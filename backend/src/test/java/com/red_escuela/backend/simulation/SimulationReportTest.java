package com.red_escuela.backend.simulation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationReportTest {

    private static SimulationReport reportWith(String event, String outcome) {
        SimulationReport report = new SimulationReport(1, 2026);
        report.recordEvent(event);
        report.count("attendanceSheets");
        report.recordOutcome(outcome);
        report.finish(10, 123);
        return report;
    }

    @Test
    void identicalContentGivesTheSameDigestWhateverTheElapsedTime() {
        SimulationReport slow = reportWith("a", "x");
        slow.finish(10, 99999);
        assertThat(slow.digest()).isEqualTo(reportWith("a", "x").digest());
    }

    @Test
    void anyChangedEventOrOutcomeChangesTheDigest() {
        String base = reportWith("a", "x").digest();
        assertThat(reportWith("b", "x").digest()).isNotEqualTo(base);
        assertThat(reportWith("a", "y").digest()).isNotEqualTo(base);
    }

    @Test
    void jsonExposesTheDigestAndTheCounters() {
        String json = reportWith("a", "x").toJson();
        assertThat(json).contains("\"digest\": \"" + reportWith("a", "x").digest() + "\"").contains("\"attendanceSheets\": 1");
    }
}
