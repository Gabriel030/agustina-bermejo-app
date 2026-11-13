package com.red_escuela.backend.identity.infrastructure;

import com.red_escuela.backend.identity.repository.AccountAccessState;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountAccessStateCache {
    private static final int PRUNE_THRESHOLD = 10_000;

    private final PersonRepository personRepository;
    private final Duration ttl;
    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();

    public AccountAccessStateCache(PersonRepository personRepository,
                                   @Value("${auth.access-state-cache-seconds}") long ttlSeconds) {
        this.personRepository = personRepository;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<AccountAccessState> find(long personId) {
        Instant now = ApplicationTime.instant();
        Entry cached = entries.get(personId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return Optional.of(cached.state());
        }
        Optional<AccountAccessState> loaded = personRepository.findAccessStateById(personId);
        loaded.ifPresentOrElse(state -> store(personId, state, now), () -> entries.remove(personId));
        return loaded;
    }

    public void evict(Long personId) {
        if (personId != null) {
            entries.remove(personId);
        }
    }

    private void store(long personId, AccountAccessState state, Instant now) {
        if (entries.size() >= PRUNE_THRESHOLD) {
            entries.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
        }
        entries.put(personId, new Entry(state, now.plus(ttl)));
    }

    private record Entry(AccountAccessState state, Instant expiresAt) {
    }
}
