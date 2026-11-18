package com.example.catalog.interfaces.rest.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@Profile("demo-integrated")
@EnableWebSecurity
@EnableConfigurationProperties({DemoJwtProperties.class, DemoCatalogProperties.class})
public class DemoSecurityConfiguration {

    private static final String BFF = "bookstore-bff";
    private static final String ORDER = "bookstore-order";
    private static final String USER_AUDIENCE = "bookstore-catalog";
    private static final String INTERNAL_AUDIENCE = "bookstore-catalog-internal";

    @Bean
    SecurityFilterChain demoSecurity(HttpSecurity http, JwtDecoder decoder) throws Exception {
        var authenticationEntryPoint = new BearerTokenAuthenticationEntryPoint();
        var accessDenied = new BearerTokenAccessDeniedHandler();
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/v1/books",
                                "/api/v1/books/{bookId}", "/api/v1/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/books/{bookId}")
                                .access(requires(BFF, USER_AUDIENCE, "bookstore-admin"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/books/{bookId}")
                                .access(requires(BFF, USER_AUDIENCE, "bookstore-admin"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/internal/checkout/snapshots")
                                .access(requires(ORDER, INTERNAL_AUDIENCE, "bookstore-order-service"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/internal/checkout/reservations/{submissionId}")
                                .access(requires(ORDER, INTERNAL_AUDIENCE, "bookstore-order-service"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/internal/checkout/reservations/{submissionId}")
                                .access(requires(ORDER, INTERNAL_AUDIENCE, "bookstore-order-service"))
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, cause) -> {
                            authenticationEntryPoint.commence(request, response, cause);
                            writeCode(response, "UNAUTHORIZED");
                        })
                        .accessDeniedHandler((request, response, cause) -> {
                            accessDenied.handle(request, response, cause);
                            writeCode(response, "FORBIDDEN");
                        }))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint((request, response, cause) -> {
                            authenticationEntryPoint.commence(request, response, cause);
                            writeCode(response, "UNAUTHORIZED");
                        })
                        .jwt(jwt -> jwt.decoder(decoder)))
                .build();
    }

    @Bean
    JwtDecoder demoJwtDecoder(DemoJwtProperties config) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(config.jwkSetUri().toString()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(config.issuer().toString()), new JwtTimestampValidator(Duration.ZERO), token -> {
                    if (token.getExpiresAt() != null && token.getSubject() != null
                            && (matchesClient(token, BFF, USER_AUDIENCE)
                            || matchesClient(token, ORDER, INTERNAL_AUDIENCE))) {
                        return OAuth2TokenValidatorResult.success();
                    }
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                }));
        return decoder;
    }

    private static AuthorizationManager<RequestAuthorizationContext> requires(String client, String audience,
                                                                                String role) {
        return (auth, ignored) -> new AuthorizationDecision(auth.get() instanceof JwtAuthenticationToken token
                && matchesClient(token.getToken(), client, audience)
                && token.getToken().getClaims().get("realm_access") instanceof Map<?, ?> realm
                && realm.get("roles") instanceof Collection<?> roles && roles.contains(role));
    }

    private static boolean matchesClient(Jwt token, String client, String audience) {
        return client.equals(token.getClaims().get("azp"))
                && token.getAudience() != null && token.getAudience().contains(audience);
    }

    private static void writeCode(HttpServletResponse response, String code) throws IOException {
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
