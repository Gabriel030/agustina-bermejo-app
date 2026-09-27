package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.announcement.entity.AudienceQuantifier;
import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;

import java.util.List;

public final class AudienceLabels {

    private AudienceLabels() {
    }

    public static String optionLabel(AudienceSegmentKey key) {
        return switch (key) {
            case ALL -> "Toda la comisión";
            case MUST_TAKE_FINAL -> "Tienen que rendir el final";
            case PROMOTED -> "Promocionaron";
            case PASSED -> "Ya aprobaron la materia";
            case FAILED_COURSE -> "Desaprobaron la cursada";
            case EXAM_FAILED -> "Desaprobaron parciales…";
            case EXAM_PASSED -> "Aprobaron parciales…";
            case EXAM_MISSING -> "Sin nota en parciales…";
            case TP_FAILED -> "Desaprobaron TPs…";
            case TP_MISSING -> "No entregaron TPs…";
            case TP_PASSED -> "Aprobaron TPs…";
            case FINAL_PASSED -> "Aprobaron el final…";
            case FINAL_FAILED -> "Desaprobaron el final…";
            case ABSENCES -> "Con N o más inasistencias…";
        };
    }

    public static boolean needsInstances(AudienceSegmentKey key) {
        return switch (key) {
            case EXAM_FAILED, EXAM_PASSED, EXAM_MISSING,
                 TP_FAILED, TP_MISSING, TP_PASSED,
                 FINAL_PASSED, FINAL_FAILED -> true;
            default -> false;
        };
    }

    public static boolean needsThreshold(AudienceSegmentKey key) {
        return key == AudienceSegmentKey.ABSENCES;
    }

    public static String resolvedLabel(AudienceSpec spec) {
        AudienceSegmentKey key = spec.getKey();
        if (key == AudienceSegmentKey.ABSENCES) {
            int threshold = spec.getThreshold() == null ? 3 : spec.getThreshold();
            return "Con " + threshold + " o más inasistencias";
        }
        if (!needsInstances(key)) {
            return optionLabel(key);
        }
        String verb = verbOf(key);
        String quantifier = spec.quantifierOrAny() == AudienceQuantifier.ALL ? "todos" : "alguno";
        return verb + " " + quantifier + " de: " + enumerate(instanceNames(key, spec.instanceNumbers()));
    }

    private static String verbOf(AudienceSegmentKey key) {
        return switch (key) {
            case EXAM_FAILED, TP_FAILED -> "Desaprobaron";
            case EXAM_PASSED, TP_PASSED -> "Aprobaron";
            case EXAM_MISSING -> "No tienen nota en";
            case TP_MISSING -> "No entregaron";
            case FINAL_PASSED -> "Aprobaron el final en";
            case FINAL_FAILED -> "Desaprobaron el final en";
            default -> "";
        };
    }

    private static List<String> instanceNames(AudienceSegmentKey key, List<Integer> instances) {
        return instances.stream().map(i -> switch (key) {
            case TP_FAILED, TP_MISSING, TP_PASSED -> "TP" + i;
            case FINAL_PASSED, FINAL_FAILED -> i + "° llamado";
            default -> i + "° parcial";
        }).toList();
    }

    private static String enumerate(List<String> names) {
        if (names.isEmpty()) {
            return "—";
        }
        if (names.size() == 1) {
            return names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " y " + names.getLast();
    }
}
