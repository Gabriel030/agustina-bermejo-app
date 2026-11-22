package com.red_escuela.backend.academicrecord;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.red_escuela.backend.academicrecord.specialist.MyCalendarEventsFinder;
import com.red_escuela.backend.academicrecord.specialist.MyCalendarProvider;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@ActiveProfiles("test")
class CalendarFeedHardeningIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(82_000_000);
    private static final int ROUNDS = 5;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MyCalendarEventsFinder eventsFinder;

    private record Actor(Person person, Student student) {
    }

    private Actor newStudent() {
        return transactionTemplate.execute(status -> {
            int number = SEQUENCE.incrementAndGet();
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Nombre");
            person.setLastname("Apellido " + number);
            person.setUsername("calhard." + number);
            person.setDni(String.valueOf(number));
            person.setEmail("calhard." + number + "@example.com");
            person.setAuthProvider(AuthProvider.LOCAL);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            Role role = roleRepository.findByName("ALUMNO").orElseThrow();
            person.getRoles().add(role);
            entityManager.persist(person);
            Institution institution = new Institution();
            institution.setName("Instituto " + number);
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            entityManager.flush();
            return new Actor(person, student);
        });
    }

    private String bearer(Actor actor) {
        return "Bearer " + jwtService.generateAccessToken(actor.person());
    }

    @Test
    void concurrentRotationsNeverFailAndLeaveExactlyOneActiveToken() throws Exception {
        Actor owner = newStudent();
        String authorization = bearer(owner);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                CyclicBarrier barrier = new CyclicBarrier(2);
                Callable<Integer> rotation = () -> {
                    barrier.await();
                    return mockMvc.perform(post("/me/calendar/feed/token").header("Authorization", authorization))
                            .andReturn().getResponse().getStatus();
                };
                List<Future<Integer>> results = List.of(executor.submit(rotation), executor.submit(rotation));

                for (Future<Integer> result : results) {
                    assertThat(result.get()).isEqualTo(201);
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("select count(*) from calendar_feed_token where student_id = ? and revoked_at is null",
                Integer.class, owner.student().getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from calendar_feed_token where student_id = ?",
                Integer.class, owner.student().getId())).isEqualTo(2 * ROUNDS);
    }

    private void withoutCalendarProvider(ThrowingRunnable action) throws Exception {
        Object original = ReflectionTestUtils.getField(eventsFinder, "providers");
        ObjectProvider<MyCalendarProvider> none = new StaticListableBeanFactory().getBeanProvider(MyCalendarProvider.class);
        ReflectionTestUtils.setField(eventsFinder, "providers", none);
        try {
            action.run();
        } finally {
            ReflectionTestUtils.setField(eventsFinder, "providers", original);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    void realCalendarProviderIsRegisteredSoFeedAndExportDoNotAnswerServiceUnavailable() throws Exception {
        Actor owner = newStudent();
        MockHttpServletResponse export = mockMvc.perform(get("/me/calendar/export.ics").header("Authorization", bearer(owner)))
                .andReturn().getResponse();

        assertThat(export.getStatus()).isEqualTo(200);
    }

    @Test
    void feedAndExportAnswerServiceUnavailableWhenNoCalendarProviderIsRegistered() throws Exception {
        Actor owner = newStudent();
        MockHttpServletResponse created = mockMvc.perform(post("/me/calendar/feed/token").header("Authorization", bearer(owner)))
                .andReturn().getResponse();
        String token = objectMapper.readTree(created.getContentAsString()).get("token").asString();

        withoutCalendarProvider(() -> {
            MockHttpServletResponse feed = mockMvc.perform(get("/public/calendar/" + token + ".ics")).andReturn().getResponse();
            MockHttpServletResponse export = mockMvc.perform(get("/me/calendar/export.ics").header("Authorization", bearer(owner)))
                    .andReturn().getResponse();

            assertThat(feed.getStatus()).isEqualTo(503);
            assertThat(feed.getContentAsString()).contains("EXTERNAL_SERVICE_UNAVAILABLE");
            assertThat(feed.getHeader("Retry-After")).isNotBlank();
            assertThat(export.getStatus()).isEqualTo(503);
            assertThat(export.getContentAsString()).contains("EXTERNAL_SERVICE_UNAVAILABLE");
        });
    }

    @Test
    void startupWarnsWhenNoCalendarProviderIsRegistered() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(MyCalendarEventsFinder.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            withoutCalendarProvider(() -> ReflectionTestUtils.invokeMethod(eventsFinder, "warnWhenNoProviderIsRegistered"));

            assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("No MyCalendarProvider bean"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
