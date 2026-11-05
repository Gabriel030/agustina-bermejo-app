package com.red_escuela.backend.security;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AuthorizationMatrixSupport extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    protected RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    protected AuthorizationMatrixFixtures fixtures;

    protected OwnershipFixtures ownership;

    protected String lastResponseBody = "";

    protected final MatrixRequestFactory requestFactory = new MatrixRequestFactory(new MinimalRequestBodies());

    @BeforeAll
    void createRoleFixtures() {
        fixtures = new AuthorizationMatrixFixtures(
                new TransactionTemplate(transactionManager), entityManager, roleRepository, jwtService);
        ownership = new OwnershipFixtures(
                new TransactionTemplate(transactionManager), entityManager, roleRepository, jwtService);
    }

    protected List<MappedEndpoint> mappedEndpoints() {
        return MappedEndpointCatalog.listApplicationEndpoints(handlerMapping);
    }

    protected int statusWithToken(MappedEndpoint endpoint, String bodyOverride, String token, Long resourceId) throws Exception {
        var result = mockMvc.perform(requestFactory.build(endpoint, bodyOverride, token, resourceId)).andReturn();
        lastResponseBody = result.getResponse().getContentAsString() + " EX " + result.getResolvedException();
        return result.getResponse().getStatus();
    }

    protected int statusOf(MappedEndpoint endpoint, String bodyOverride, MatrixRole role) throws Exception {
        String token = role == null ? null : fixtures.tokenFor(role);
        var result = mockMvc.perform(requestFactory.build(endpoint, bodyOverride, token)).andReturn();
        var response = result.getResponse();
        lastResponseBody = response.getContentAsString() + " EX " + result.getResolvedException();
        return response.getStatus();
    }
}
