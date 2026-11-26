package com.red_escuela.backend.security;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.exception.InvalidUnsubscribeTokenException;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenIssuer;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenSigner;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenVerifier;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretsRotationTest {

    private static final String OLD_JWT_SECRET = "old-jwt-secret-old-jwt-secret-old-jwt-0";
    private static final String NEW_JWT_SECRET = "new-jwt-secret-new-jwt-secret-new-jwt-1";
    private static final String UNSUBSCRIBE_SECRET = "own-unsubscribe-secret-own-unsubscribe-2";
    private static final String ISSUER = "agustina-bermejo-app";

    private final ApplicationContextRunner productionRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
            .withBean(JwtService.class)
            .withBean(UnsubscribeTokenSigner.class)
            .withPropertyValues("jwt.access-token-expiration-minutes=15");

    private static Person person() {
        Person person = new Person();
        person.setId(42L);
        person.setRoles(new HashSet<>());
        return person;
    }

    private static String issueUnsubscribeLink(UnsubscribeTokenSigner signer) {
        return new UnsubscribeTokenIssuer(signer, Clock.systemUTC())
                .issueToken(9L, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL);
    }

    @Test
    void accessTokenSignedWithOldSecretIsRejectedAfterRotationButAcceptedByOldSecret() {
        String token = new JwtService(OLD_JWT_SECRET, 15, ISSUER, "agustina-bermejo-app-api", false).generateAccessToken(person());

        assertThat(new JwtService(OLD_JWT_SECRET, 15, ISSUER, "agustina-bermejo-app-api", false).validateAndParse(token).getSubject()).isEqualTo("42");
        assertThatThrownBy(() -> new JwtService(NEW_JWT_SECRET, 15, ISSUER, "agustina-bermejo-app-api", false).validateAndParse(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void unsubscribeLinkSignedBeforeJwtRotationStillVerifiesAfterIt() {
        AtomicReference<String> link = new AtomicReference<>();
        productionRunner
                .withPropertyValues("jwt.secret=" + OLD_JWT_SECRET, "broadcast.unsubscribe.secret=" + UNSUBSCRIBE_SECRET)
                .run(context -> link.set(issueUnsubscribeLink(context.getBean(UnsubscribeTokenSigner.class))));

        productionRunner
                .withPropertyValues("jwt.secret=" + NEW_JWT_SECRET, "broadcast.unsubscribe.secret=" + UNSUBSCRIBE_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var claims = new UnsubscribeTokenVerifier(context.getBean(UnsubscribeTokenSigner.class))
                            .verifyToken(link.get());
                    assertThat(claims.personId()).isEqualTo(9L);
                });
    }

    @Test
    void unsubscribeLinkIsRejectedWhenItsOwnSecretChanges() {
        String link = issueUnsubscribeLink(new UnsubscribeTokenSigner(UNSUBSCRIBE_SECRET));

        productionRunner
                .withPropertyValues("jwt.secret=" + OLD_JWT_SECRET,
                        "broadcast.unsubscribe.secret=another-own-secret-another-own-secret")
                .run(context -> assertThatThrownBy(
                        () -> new UnsubscribeTokenVerifier(context.getBean(UnsubscribeTokenSigner.class)).verifyToken(link))
                        .isInstanceOf(InvalidUnsubscribeTokenException.class));
    }

    @Test
    void productionBootFailsWithoutJwtSecret() {
        productionRunner
                .withPropertyValues("broadcast.unsubscribe.secret=" + UNSUBSCRIBE_SECRET)
                .run(context -> assertThat(context).getFailure().hasStackTraceContaining("jwt.secret"));
    }

    @Test
    void productionBootFailsWithoutUnsubscribeSecret() {
        productionRunner
                .withPropertyValues("jwt.secret=" + NEW_JWT_SECRET)
                .run(context -> assertThat(context).getFailure()
                        .hasStackTraceContaining("broadcast.unsubscribe.secret es obligatorio en el perfil prod"));
    }

    @Test
    void productionBootFailsWithAJwtSecretShorterThanTheMinimum() {
        productionRunner
                .withPropertyValues("jwt.secret=short", "broadcast.unsubscribe.secret=" + UNSUBSCRIBE_SECRET)
                .run(context -> assertThat(context).getFailure().hasStackTraceContaining("jwt.secret debe tener al menos"));
    }

    @Test
    void productionBootSucceedsWithAllRequiredSecrets() {
        productionRunner
                .withPropertyValues("jwt.secret=" + NEW_JWT_SECRET, "broadcast.unsubscribe.secret=" + UNSUBSCRIBE_SECRET)
                .run(context -> assertThat(context).hasNotFailed());
    }
}
