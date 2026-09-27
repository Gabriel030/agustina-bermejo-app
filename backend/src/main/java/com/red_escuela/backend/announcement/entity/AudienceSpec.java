package com.red_escuela.backend.announcement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class AudienceSpec {

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_key", nullable = false)
    private AudienceSegmentKey key;

    @Column(name = "audience_instances")
    private String instances;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_quantifier")
    private AudienceQuantifier quantifier;

    @Column(name = "audience_threshold")
    private Integer threshold;

    public static AudienceSpec wholeSection() {
        return new AudienceSpec(AudienceSegmentKey.ALL, null, null, null);
    }

    public List<Integer> instanceNumbers() {
        if (instances == null || instances.isBlank()) {
            return List.of();
        }
        return Arrays.stream(instances.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .sorted()
                .toList();
    }

    public AudienceQuantifier quantifierOrAny() {
        return quantifier == null ? AudienceQuantifier.ANY : quantifier;
    }

    public boolean isWholeSection() {
        return key == AudienceSegmentKey.ALL;
    }
}
