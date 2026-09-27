package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.SegmentNotAvailableForSectionException;
import com.red_escuela.backend.announcement.exception.UnknownAudienceSegmentException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AudienceService {

    private final SectionSnapshotLoader snapshotLoader;
    private final List<AudienceResolver> resolvers;

    public AudienceService(SectionSnapshotLoader snapshotLoader, List<AudienceResolver> resolvers) {
        this.snapshotLoader = snapshotLoader;
        this.resolvers = resolvers;
    }

    public SectionSnapshot snapshotOf(SectionType type, Long sectionId) {
        return snapshotLoader.load(type, sectionId);
    }

    public List<StudentSnapshot> resolve(SectionSnapshot section, AudienceSpec spec) {
        AudienceResolver resolver = resolverFor(spec.getKey());
        if (!resolver.availableFor(section, spec.getKey())) {
            throw new SegmentNotAvailableForSectionException(spec.getKey());
        }
        return section.students().stream()
                .filter(student -> resolver.matches(student, spec, section))
                .toList();
    }

    public List<AudienceSegmentKey> availableKeys(SectionSnapshot section) {
        return java.util.Arrays.stream(AudienceSegmentKey.values())
                .filter(key -> resolverFor(key).availableFor(section, key))
                .toList();
    }

    public int countFor(SectionSnapshot section, AudienceSegmentKey key) {
        if (AudienceLabels.needsInstances(key) || AudienceLabels.needsThreshold(key)) {
            return -1;
        }
        AudienceSpec spec = new AudienceSpec(key, null, null, null);
        return resolve(section, spec).size();
    }

    private AudienceResolver resolverFor(AudienceSegmentKey key) {
        return resolvers.stream()
                .filter(resolver -> resolver.supports(key))
                .findFirst()
                .orElseThrow(() -> new UnknownAudienceSegmentException(key));
    }
}
