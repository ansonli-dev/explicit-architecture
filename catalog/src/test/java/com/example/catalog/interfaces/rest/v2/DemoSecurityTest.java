package com.example.catalog.interfaces.rest.v2;

import com.example.catalog.application.query.book.BookV2Result;
import com.example.catalog.application.query.book.GetBookV2Query;
import com.example.catalog.application.query.book.ListBooksV2Query;
import com.example.catalog.application.query.book.CheckoutSnapshotQuery;
import com.example.catalog.interfaces.rest.security.DemoJwtProperties;
import com.example.catalog.interfaces.rest.security.DemoSecurityConfiguration;
import com.example.catalog.support.DemoTokens;
import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.application.bus.QueryBus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BookV2Controller.class, CheckoutInventoryController.class},
        properties = "demo.v2.admin-writes-enabled=false")
@Import(DemoSecurityConfiguration.class)
@ActiveProfiles("demo-integrated")
class DemoSecurityTest {

    private static final DemoTokens TOKENS = new DemoTokens();
    private static final UUID BOOK = UUID.randomUUID();
    private static final String ADMIN = "/api/v2/admin/books/" + BOOK;
    private static final String SNAPSHOT = "/api/v2/internal/checkout/snapshots";

    @Autowired MockMvc mvc;
    @MockBean QueryBus queries;
    @MockBean CommandBus commands;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        TOKENS.properties(registry);
    }

    @AfterAll
    static void closeJwks() {
        TOKENS.close();
    }

    @Test
    void publicPathsAreGetOnlyAndLegacyAndUnlistedPathsStayClosed() throws Exception {
        when(queries.dispatch(any(ListBooksV2Query.class))).thenReturn(new ListBooksV2Query.Result(List.of(), 1, false));
        mvc.perform(get("/api/v2/books")).andExpect(status().isOk());
        mvc.perform(post("/api/v2/books")).andExpect(status().isUnauthorized());
        mvc.perform(bearer(post("/api/v1/books"), TOKENS.admin())).andExpect(status().isForbidden());
        mvc.perform(bearer(get("/api/v1/books"), TOKENS.admin())).andExpect(status().isForbidden());
        mvc.perform(bearer(get("/actuator/env"), TOKENS.admin())).andExpect(status().isForbidden());
    }

    @Test
    void signedIdentitiesCannotCrossAdminAndInternalBoundaries() throws Exception {
        when(queries.dispatch(any(GetBookV2Query.class)))
                .thenReturn(new BookV2Result(BOOK, "Title", "Author", "Category", 1200, "CNY", 3, 0L));
        when(queries.dispatch(any(CheckoutSnapshotQuery.class))).thenReturn(List.of());
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(bearer(patch(ADMIN), TOKENS.reader())).andExpect(status().isForbidden());
        mvc.perform(bearer(get(ADMIN), TOKENS.orderService())).andExpect(status().isForbidden());
        mvc.perform(bearer(get(ADMIN), TOKENS.admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(bearer(snapshot(), TOKENS.admin())).andExpect(status().isForbidden());
        mvc.perform(bearer(snapshot(), TOKENS.sign("bookstore-order", "bookstore-catalog-internal", "bookstore-reader")))
                .andExpect(status().isForbidden());
        mvc.perform(bearer(snapshot(), TOKENS.orderService())).andExpect(status().isOk());
    }

    @Test
    void adminWriteSwitchKeepsReadsAndServicePathsAvailable() throws Exception {
        when(queries.dispatch(any(GetBookV2Query.class)))
                .thenReturn(new BookV2Result(BOOK, "Title", "Author", "Category", 1200, "CNY", 3, 0L));
        when(queries.dispatch(any(CheckoutSnapshotQuery.class))).thenReturn(List.of());
        mvc.perform(bearer(patch(ADMIN).contentType("application/json")
                        .content("""
                                {"title":"Edited","expectedVersion":0}
                                """), TOKENS.admin()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ADMIN_WRITES_CLOSED"));
        mvc.perform(bearer(get(ADMIN), TOKENS.admin())).andExpect(status().isOk());
        mvc.perform(bearer(snapshot(), TOKENS.orderService())).andExpect(status().isOk());
    }

    @Test
    void rejectsBadSignatureIssuerAudienceClientOrTime() throws Exception {
        var expiry = Instant.now().plusSeconds(120);
        var unauthorized = List.of(
                TOKENS.sign("http://elsewhere.test/realms/bookstore", "bookstore-bff", "bookstore-catalog", "bookstore-admin", expiry, true),
                TOKENS.sign(DemoTokens.ISSUER, "bookstore-bff", "wrong-audience", "bookstore-admin", expiry, true),
                TOKENS.sign(DemoTokens.ISSUER, "wrong-client", "bookstore-catalog", "bookstore-admin", expiry, true),
                TOKENS.sign(DemoTokens.ISSUER, "bookstore-bff", "bookstore-catalog", "bookstore-admin", Instant.now().minusSeconds(10), true),
                TOKENS.sign(DemoTokens.ISSUER, "bookstore-bff", "bookstore-catalog", "bookstore-admin", null, true),
                TOKENS.sign(DemoTokens.ISSUER, "bookstore-bff", "bookstore-catalog", "bookstore-admin", expiry, false));
        for (String token : unauthorized) {
            mvc.perform(bearer(get(ADMIN), token)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void requiresBothIssuerAndJwksConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(JwtPropertiesConfig.class)
                .withPropertyValues("demo.jwt.issuer=http://issuer.test")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("DEMO_JWT_JWK_SET_URI"));
        new ApplicationContextRunner().withUserConfiguration(JwtPropertiesConfig.class)
                .withPropertyValues("demo.jwt.jwk-set-uri=http://keycloak.test/certs")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("DEMO_JWT_ISSUER"));
    }

    private static MockHttpServletRequestBuilder snapshot() {
        return post(SNAPSHOT).contentType("application/json").content("""
                {"lines":[{"bookId":"%s","quantity":1}]}
                """.formatted(BOOK));
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    @EnableConfigurationProperties(DemoJwtProperties.class)
    static class JwtPropertiesConfig {}
}
