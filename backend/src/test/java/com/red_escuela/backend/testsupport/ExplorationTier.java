package com.red_escuela.backend.testsupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ExplorationTier {
    PR,
    NIGHTLY;

    public static final String PROPERTY = "test.exploration";
    public static final String SEED_PROPERTY = "test.exploration.seed";

    private static final Logger LOG = LoggerFactory.getLogger(ExplorationTier.class);

    public static ExplorationTier current() {
        return parse(System.getProperty(PROPERTY));
    }

    static ExplorationTier parse(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("pr")) {
            return PR;
        }
        if (value.equalsIgnoreCase("nightly")) {
            return NIGHTLY;
        }
        throw new IllegalArgumentException(PROPERTY + " must be pr or nightly but was " + value);
    }

    public static int sequences(int prCount, int nightlyCount) {
        return current() == NIGHTLY ? nightlyCount : prCount;
    }

    public static long seed(String owner, long fixedSeed) {
        if (current() == PR) {
            return fixedSeed;
        }
        long seed = Long.getLong(SEED_PROPERTY, System.nanoTime());
        LOG.warn("Exploration seed of {} in tier nightly: {} (replay with -D{}={})", owner, seed, SEED_PROPERTY, seed);
        return seed;
    }
}
