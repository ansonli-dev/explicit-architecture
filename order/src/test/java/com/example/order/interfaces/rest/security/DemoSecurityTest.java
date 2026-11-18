package com.example.order.interfaces.rest.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoSecurityTest.ProbeController.class)
@Import({DemoSecurityTest.ProbeController.class, DemoSecurityConfiguration.class})
@ActiveProfiles("demo-integrated")
class DemoSecurityTest {
    private static final String ISSUER = "https://issuer.example/realms/bookstore";
    private static final String READER_ID = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String ORDER_ID = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String ORDERS = "/api/v1/me/orders";
    private static final String SUBMISSIONS = "/api/v1/me/order-submissions";
    private static final String QUOTES = "/api/v1/me/order-quotes";
    private static final RSAKey TRUSTED_KEY = key();
    private static final MockWebServer JWKS = jwks();

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void jwtSettings(DynamicPropertyRegistry properties) {
        properties.add("demo.jwt.issuer", () -> ISSUER);
        properties.add("demo.jwt.jwk-set-uri", () -> JWKS.url("/jwks").toString());
    }

    @AfterAll
    static void stopJwks() throws IOException {
        JWKS.close();
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void ordersRequireBearerAuthenticationEvenWithACookie() throws Exception {
        mvc.perform(get(ORDERS).cookie(new Cookie("access_token", sign(reader(), TRUSTED_KEY))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @CsvSource({
            "GET, " + ORDERS,
            "GET, " + ORDERS + "/" + ORDER_ID,
            "GET, " + ORDERS + "/" + ORDER_ID + "/cancellation",
            "PUT, " + ORDERS + "/" + ORDER_ID + "/cancellation",
            "POST, " + QUOTES,
            "POST, " + SUBMISSIONS,
            "GET, " + SUBMISSIONS + "/" + ORDER_ID
    })
    void allowedRoutesReceiveTheSignedReaderSubject(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header(AUTHORIZATION, bearer(reader())))
                .andExpect(status().isOk())
                .andExpect(content().string(READER_ID));
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/orders",
            "PUT, /api/v1/orders/" + ORDER_ID + "/cancel",
            "GET, /api/v1/orders",
            "POST, " + ORDERS,
            "PUT, " + ORDERS + "/" + ORDER_ID + "/cancel",
            "GET, /api/v2/me/orders",
            "GET, " + SUBMISSIONS,
            "POST, " + QUOTES + "/extra",
            "GET, " + ORDERS + "/" + ORDER_ID + "/extra",
            "GET, /actuator/prometheus"
    })
    void deniesRoutesAndMethodsOutsideTheAllowlist(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header(AUTHORIZATION, bearer(reader())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void otherIssuerIsRejected() throws Exception {
        expectUnauthorized(reader().issuer(ISSUER + "/other"));
    }

    @Test
    void aDifferentAudienceIsRejected() throws Exception {
        expectUnauthorized(reader().audience("bookstore-order-api-extra"));
    }

    @Test
    void aDifferentAuthorizedPartyIsRejected() throws Exception {
        expectUnauthorized(reader().claim("azp", "bookstore-order"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        expectUnauthorized(reader().expirationTime(Date.from(Instant.now().minusSeconds(10))));
    }

    @Test
    void tokenWithoutExpirationIsRejected() throws Exception {
        expectUnauthorized(reader().expirationTime(null));
    }

    @Test
    void nonCanonicalUuidSubjectIsRejected() throws Exception {
        expectUnauthorized(reader().subject("aaaaaaaa-0-0-0-1"));
    }

    @Test
    void badSignatureIsRejectedEvenWithTrustedKeyId() throws Exception {
        String token = sign(reader(), key());
        mvc.perform(get(ORDERS).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scopeOrAdminRoleDoesNotGrantReaderAccess() throws Exception {
        var admin = reader().claim("realm_access", Map.of("roles", List.of("bookstore-admin")))
                .claim("scope", "bookstore-reader");
        mvc.perform(get(ORDERS).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "demo.jwt.issuer=https://issuer.example, DEMO_JWT_JWK_SET_URI",
            "demo.jwt.jwk-set-uri=https://issuer.example/jwks, DEMO_JWT_ISSUER"
    })
    void missingRequiredTokenConfigurationFailsStartup(String otherProperty, String missingSetting) {
        new WebApplicationContextRunner().withUserConfiguration(DemoSecurityConfiguration.class)
                .withPropertyValues("spring.profiles.active=demo-integrated", otherProperty)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining(missingSetting));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{\"quantity\":1,\"customerId\":\"spoofed\"}",
            "{\"quantity\":1.5}",
            "{\"quantity\":\"1\"}",
            "{\"quantity\":1,\"quantity\":2}",
            "{\"quantity\":1} {}"
    }, delimiter = '|')
    void demoJacksonRejectsInvalidQuoteBodies(String body) throws Exception {
        mvc.perform(post(QUOTES).header(AUTHORIZATION, bearer(reader()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private void expectUnauthorized(JWTClaimsSet.Builder claims) throws Exception {
        mvc.perform(get(ORDERS).header(AUTHORIZATION, bearer(claims)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static JWTClaimsSet.Builder reader() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(READER_ID)
                .audience(List.of("bookstore-catalog", "bookstore-order-api"))
                .claim("azp", "bookstore-bff")
                .claim("realm_access", Map.of("roles", List.of("bookstore-reader")))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }

    private static String bearer(JWTClaimsSet.Builder claims) throws JOSEException {
        return "Bearer " + sign(claims, TRUSTED_KEY);
    }

    private static String sign(JWTClaimsSet.Builder claims, RSAKey key) throws JOSEException {
        var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims.build());
        token.sign(new RSASSASigner(key));
        return token.serialize();
    }

    private static RSAKey key() {
        try {
            return new RSAKeyGenerator(2048).keyID("bookstore-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockWebServer jwks() {
        var server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setHeader("Content-Type", "application/json")
                        .setBody(new JWKSet(TRUSTED_KEY.toPublicJWK()).toString());
            }
        });
        return server;
    }

    @RestController
    static class ProbeController {
        @RequestMapping("/actuator/health")
        String health() {
            return "UP";
        }

        @RequestMapping({ORDERS, ORDERS + "/{id}", ORDERS + "/{id}/cancellation", QUOTES, SUBMISSIONS,
                SUBMISSIONS + "/{id}", "/api/v1/orders", "/api/v1/orders/{id}/cancel"})
        String probe(@AuthenticationPrincipal Jwt jwt) {
            return jwt.getSubject();
        }

        @PostMapping(value = QUOTES, consumes = MediaType.APPLICATION_JSON_VALUE)
        int quote(@RequestBody QuoteProbe quote) {
            return quote.quantity();
        }
    }

    record QuoteProbe(int quantity) {}
}
